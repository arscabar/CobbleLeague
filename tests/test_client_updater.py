from pathlib import Path
import hashlib,json,subprocess,uuid,zipfile,shutil
ROOT=Path(__file__).resolve().parents[1]
VERSION=json.loads((ROOT/'dist/client-release.json').read_text())['release']
PACKAGE=ROOT/'dist'/f'cobbleleague-friends-{VERSION}'
BASE=ROOT/'.test-tmp'/('자동 업데이트 '+uuid.uuid4().hex[:8]);BASE.mkdir(parents=True)
checks=[]
def fake(path,id,version,depends=None):
 with zipfile.ZipFile(path,'w') as z:z.writestr('fabric.mod.json',json.dumps(dict(id=id,version=version,depends=depends or {})))
def profile(name,version='1.7.3+1.21.1',core='0.7.3'):
 p=BASE/name;(p/'mods').mkdir(parents=True)
 for name,id,v in [('cobblemon','cobblemon',version),('api','fabric-api','0.116.14+1.21.1'),('kotlin','fabric-language-kotlin','1.13.13+kotlin.2.4.10'),('teams','cobblemon_quick_teams','1.5.5'),('old-core','cobbleleague',core),('old-synergy','cobblesynergy','0.2.0+cl0.7.3')]:fake(p/'mods'/f'{name}.jar',id,v)
 for folder in ['config','saves']:(p/folder).mkdir();(p/folder/'keep.txt').write_text('keep')
 return p
def snapshot(p):return {f.relative_to(p).as_posix():hashlib.sha256(f.read_bytes()).hexdigest() for f in p.rglob('*') if f.is_file() and ('mods' in f.parts or 'config' in f.parts or 'saves' in f.parts)}
def run(p,*extra,code=0,package=PACKAGE):
 result=subprocess.run(['powershell.exe','-NoProfile','-ExecutionPolicy','Bypass','-File',str(package/'client-updater.ps1'),'-TargetPath',str(p),'-ManifestPath',str(package/'client-release.json'),'-BundleDirectory',str(package/'payload'),*extra],capture_output=True)
 assert result.returncode==code,result.stdout.decode('utf-8',errors='replace')+' expected '+str(code)
 return result
p=profile('정상');before=snapshot(p);run(p,'-CheckOnly');assert before==snapshot(p);checks.append('check only preserves both addons and world')
run(p);after=snapshot(p);assert (p/'mods'/f'cobbleleague-{VERSION}.jar').exists();assert len(list((p/'mods').glob('*.jar')))==6
assert (p/'config/keep.txt').read_text()=='keep' and (p/'saves/keep.txt').read_text()=='keep';checks.append('atomic paired install and other files preserved')
run(p);assert after==snapshot(p);assert len(list((p/'cobbleleague-auto-backups').iterdir()))==1;checks.append('repeat install does not duplicate backup')
run(p,'-Restore');assert before==snapshot(p);checks.append('paired restore returns exact old files')
wrong=profile('지원안됨',version='1.8.0');old=snapshot(wrong);run(wrong,code=1);assert snapshot(wrong)==old;checks.append('unsupported base pack rejected')
newer=profile('최신 개발버전',core='9.0.0');old=snapshot(newer);run(newer,code=1);assert snapshot(newer)==old;checks.append('never downgrade a newer installation')
bad=BASE/'손상된 배포';shutil.copytree(PACKAGE,bad);m=json.loads((bad/'client-release.json').read_text());m['mods'][1]['sha256']='0'*64;(bad/'client-release.json').write_text(json.dumps(m));p=profile('손상 거부');old=snapshot(p);run(p,code=1,package=bad);assert snapshot(p)==old;checks.append('bad second addon hash cannot partially update first addon')
p=profile('변경 후 복구 거부');run(p);manifest=json.loads((PACKAGE/'client-release.json').read_text());target=p/'mods'/manifest['mods'][0]['filename'];target.write_bytes(target.read_bytes()+b'changed');old=snapshot(p);run(p,'-Restore',code=1);assert snapshot(p)==old;checks.append('changed installed file blocks unsafe restore')
(ROOT/'dist/client-updater-tests.json').write_text(json.dumps(dict(passed=len(checks),checks=checks),indent=2));print(json.dumps(dict(passed=len(checks),checks=checks),ensure_ascii=False))
