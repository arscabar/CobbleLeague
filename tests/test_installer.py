from pathlib import Path
import json, zipfile, hashlib, subprocess, uuid, shutil

workspace = Path(__file__).resolve().parents[1]
package = workspace / 'dist/cobbleleague-friends-0.7.3'
testroot = workspace / '.test-tmp' / ('친구 설치 검사 ' + uuid.uuid4().hex[:8])
testroot.mkdir(parents=True)
results = []

def fake_mod(path, identity, version):
    with zipfile.ZipFile(path, 'w') as z:
        z.writestr('fabric.mod.json', json.dumps({'schemaVersion': 1, 'id': identity, 'version': version}))

def profile(name, version='1.7.3+1.21.1'):
    p = testroot / name
    (p / 'mods').mkdir(parents=True)
    for filename, identity, ver in [('cobblemon.jar','cobblemon',version),('api.jar','fabric-api','0.116.14+1.21.1'),
                                     ('kotlin.jar','fabric-language-kotlin','1.13.13+kotlin.2.4.10'),
                                     ('teams.jar','cobblemon_quick_teams','1.5.5')]:
        fake_mod(p / 'mods' / filename, identity, ver)
    (p / 'config').mkdir(); (p / 'config/keep.txt').write_text('keep config')
    (p / 'saves').mkdir(); (p / 'saves/keep.txt').write_text('keep world')
    return p

def snapshot(p):
    return {f.relative_to(p).as_posix(): hashlib.sha256(f.read_bytes()).hexdigest() for f in p.rglob('*') if f.is_file()}

def run(p, *args, code=0, script=None):
    cmd = ['powershell.exe','-NoProfile','-ExecutionPolicy','Bypass','-File',str(script or package / 'install-client.ps1'),
           '-TargetPath',str(p),'-NonInteractive',*args]
    completed = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    if completed.returncode != code:
        raise AssertionError(completed.stdout.decode('utf-8',errors='replace') + f'\nExpected {code}, got {completed.returncode}')
    return completed.stdout.decode('utf-8',errors='replace')

manifest = json.loads((package / 'package.json').read_text())
expected = manifest['sha256']

p = profile('한글 공백 새 설치')
before = snapshot(p)
run(p, '-CheckOnly'); assert snapshot(p) == before
run(p)
assert hashlib.sha256((p / 'mods/cobbleleague-0.7.3.jar').read_bytes()).hexdigest() == expected
for relative, digest in before.items(): assert snapshot(p)[relative] == digest
installed = snapshot(p)
run(p); assert snapshot(p) == installed  # Idempotent install.
run(p, '-Action','Restore')
for relative, digest in before.items(): assert snapshot(p)[relative] == digest
assert not (p / 'mods/cobbleleague-0.7.3.jar').exists()
results.append('fresh install, check-only, repeated install and restore; other mods/config/world preserved')

p = profile('구버전 이름 변경')
fake_mod(p / 'mods/custom-filename.jar','cobbleleague','0.1.5')
old = (p / 'mods/custom-filename.jar').read_bytes()
run(p); assert not (p / 'mods/custom-filename.jar').exists()
run(p, '-Action','Restore')
assert (p / 'mods/custom-filename.jar').read_bytes() == old
assert not (p / 'mods/cobbleleague-0.7.3.jar').exists()
results.append('old version detected by mod ID regardless of filename; exact backup restored')

p = profile('호환 안 되는 1.8', '1.8.2+1.21.1'); before = snapshot(p)
run(p, code=1); assert snapshot(p) == before
results.append('Cobblemon 1.8 rejected without changing any files')

p = profile('손상 배포 검사'); bad = testroot / '손상된 설치 파일'; shutil.copytree(package,bad)
(bad / 'payload/cobbleleague-0.7.3.jar').write_bytes(b'broken')
before = snapshot(p); run(p, code=1, script=bad / 'install-client.ps1'); assert snapshot(p) == before
results.append('damaged payload rejected without changing profile')

p = profile('복원 충돌 보호'); run(p)
(p / 'mods/cobbleleague-0.7.3.jar').write_bytes(b'changed after install')
before = snapshot(p); run(p,'-Action','Restore',code=1); assert snapshot(p) == before
results.append('changed installed file rejected during restore; current data preserved')

p = profile('서버 폴더 오선택')
(p / 'server.properties').write_text('server-port=25565')
fake_mod(p / 'mods/server-patch.jar','cobbleleague','0.1.6+server.5')
before = snapshot(p)
run(p,code=1); assert snapshot(p)==before
results.append('server directory rejected without downgrading or changing server patch')

output = {'passed':len(results),'checks':results,'runtime':'Windows PowerShell 5.1','fixtureRoot':str(testroot)}
(workspace / 'dist/installer-tests.json').write_text(json.dumps(output,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps({'passed':len(results),'checks':results},ensure_ascii=False,indent=2))
