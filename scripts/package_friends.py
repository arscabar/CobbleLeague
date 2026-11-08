"""Build a matched client release bundle from the two already-built addons."""
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
import hashlib,json,re,shutil
ROOT=Path(__file__).resolve().parents[1]
version=re.search(r"^version\s*=\s*'([^']+)'",(ROOT/'build.gradle').read_text(),re.M).group(1)
props=dict(l.split('=',1) for l in (ROOT/'server-addons/cobble-synergy/gradle.properties').read_text().splitlines() if '=' in l and not l.startswith('#'))
synergy_version=props['mod_version']
paths=[ROOT/'build/libs'/f'cobbleleague-{version}.jar',ROOT/'server-addons/cobble-synergy/build/libs'/f'cobblesynergy-{synergy_version}.jar']
mods=[]
for path in paths:
    with ZipFile(path) as z:
        m=json.loads(z.read('fabric.mod.json'))
        if m['id']=='cobblesynergy':
            assert m['depends']['cobbleleague']==version
            assert z.read('cobblesynergy/build.properties').decode().strip()=='test=false'
    mods.append(dict(id=m['id'],version=m['version'],filename=path.name,sha256=hashlib.sha256(path.read_bytes()).hexdigest()))
manifest=dict(schema=1,release=version,minecraft='1.21.1',cobblemon='1.7.3',mods=mods)
dist=ROOT/'dist';dist.mkdir(exist_ok=True)
package=dist/f'cobbleleague-friends-{version}';(package/'payload').mkdir(parents=True,exist_ok=True)
for p in (ROOT/'installer').iterdir():
    if p.is_file():shutil.copy2(p,package/p.name)
for p in paths:
    shutil.copy2(p,package/'payload'/p.name);shutil.copy2(p,dist/p.name)
for p in (package/'payload').glob('*.jar'):
    if p.name not in {m['filename'] for m in mods}:p.unlink()
(package/'client-release.json').write_text(json.dumps(manifest,indent=2),encoding='utf-8')
shutil.copy2(package/'client-release.json',dist/'client-release.json')
(package/'package.json').write_text(json.dumps(dict(name='CobbleLeague friends installer',version=version,clientVersion=version,validatedServerVersion=version,payload='payload/'+paths[0].name,sha256=mods[0]['sha256']),indent=2),encoding='utf-8')
shutil.copy2(ROOT/'LICENSE',package/'LICENSE-CobbleLeague.txt')
shutil.copy2(ROOT/'server-addons/cobble-synergy/UPSTREAM.md',package/'CobbleSynergy-UPSTREAM.md')
for name,args in [('설치.bat','-InstallLauncher'),('INSTALL.bat','-InstallLauncher'),('되돌리기.bat','-Restore'),('RESTORE.bat','-Restore'),('UPDATE-AND-PLAY.bat','-WaitForExit -Launch')]:
    (package/name).write_bytes(('@echo off\r\npowershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0client-updater.ps1" '+args+'\r\nif errorlevel 1 pause\r\n').encode('ascii'))
(package/'사용방법.txt').write_text(f'CobbleLeague {version} + CobbleSynergy {synergy_version}\n\n게임을 종료하고 전체 ZIP을 풀어 설치.bat를 실행합니다.\n바탕화면 CobbleLeague-PLAY.bat를 누르면 최신 GitHub 릴리스를 확인하고 두 애드온을 함께 업데이트한 뒤 Modrinth 게임을 실행합니다.\n모드린스의 기존 Play 버튼으로 직접 실행하면 이 업데이트 확인을 거치지 않습니다.\n실행 중인 게임은 강제로 종료하지 않으며 종료 후에만 파일을 바꿉니다.\n네트워크 연결이 없으면 포함된 마지막 검증 버전을 확인합니다. 더 높은 설치 버전으로부터 자동으로 낮추지는 않습니다.\n복구는 게임 종료 후 되돌리기.bat입니다.\nF4: 장비 관리·관전, U: 시너지 도감.\n',encoding='utf-8-sig')
output=dist/f'CobbleLeague-friends-{version}.zip'
with ZipFile(output,'w',ZIP_DEFLATED) as z:
    for p in sorted(package.rglob('*')):
        if p.is_file():z.write(p,p.relative_to(package).as_posix())
with ZipFile(output) as z:assert z.testzip() is None
artifacts=paths+[dist/'client-release.json',output]
(dist/'SHA256SUMS.txt').write_text(''.join(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+p.name+'\n' for p in artifacts),encoding='utf-8')
print(output)
