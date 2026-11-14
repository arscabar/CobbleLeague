from pathlib import Path
import hashlib,json,subprocess,uuid,zipfile,shutil
ROOT=Path(__file__).resolve().parents[1]
VERSION=json.loads((ROOT/'dist/client-release.json').read_text())['release']
PACKAGE=ROOT/'dist'/f'cobbleleague-friends-{VERSION}'
MANIFEST=json.loads((PACKAGE/'client-release.json').read_text())
CORE=next(m['version'] for m in MANIFEST['mods'] if m['id']=='cobbleleague')
COUNT=len(MANIFEST['mods'])
BASE=ROOT/'.test-tmp'/('자동 업데이트 '+uuid.uuid4().hex[:8]);BASE.mkdir(parents=True)
checks=[]
def fake(path,id,version,depends=None):
 with zipfile.ZipFile(path,'w') as z:z.writestr('fabric.mod.json',json.dumps(dict(id=id,version=version,depends=depends or {})))
def profile(name,version='1.7.3+1.21.1',core='0.7.3'):
 p=BASE/name;(p/'mods').mkdir(parents=True)
 for name,id,v in [('cobblemon','cobblemon',version),('api','fabric-api','0.116.14+1.21.1'),('kotlin','fabric-language-kotlin','1.13.13+kotlin.2.4.10'),('teams','cobblemon_quick_teams','1.5.5'),('old-core','cobbleleague',core),('old-synergy','cobblesynergy','0.2.0+cl0.7.3')]:fake(p/'mods'/f'{name}.jar',id,v)
 fake(p/'mods/unrelated.jar','unrelated','1.0.0')
 for folder in ['config','saves','world']:(p/folder).mkdir();(p/folder/'keep.txt').write_text('keep')
 return p
def snapshot(p):return {f.relative_to(p).as_posix():hashlib.sha256(f.read_bytes()).hexdigest() for f in p.rglob('*') if f.is_file() and any(n in f.parts for n in ['mods','config','saves','world'])}
def run(p,*extra,code=0,package=PACKAGE,offline=True):
 result=subprocess.run(['powershell.exe','-NoProfile','-ExecutionPolicy','Bypass','-File',str(package/'client-updater.ps1'),'-TargetPath',str(p),'-ManifestPath',str(package/'client-release.json'),'-BundleDirectory',str(package/'payload'),*(['-Offline'] if offline else []),*extra],capture_output=True)
 assert result.returncode==code,(result.stdout+result.stderr).decode('utf-8',errors='replace')+' expected '+str(code)
 return result
p=profile('정상');before=snapshot(p);run(p,'-CheckOnly');assert before==snapshot(p);checks.append('check only preserves both addons and world')
run(p);after=snapshot(p);assert (p/'mods'/f'cobbleleague-{CORE}.jar').exists();assert len(list((p/'mods').glob('*.jar')))==5+COUNT
assert (p/'config/keep.txt').read_text()=='keep' and (p/'saves/keep.txt').read_text()=='keep';checks.append('atomic paired install and other files preserved')
run(p);assert after==snapshot(p);assert len(list((p/'cobbleleague-auto-backups').iterdir()))==1;checks.append('repeat install does not duplicate backup')
run(p,'-Restore');assert before==snapshot(p);checks.append('paired restore returns exact old files')
wrong=profile('지원안됨',version='1.8.0');old=snapshot(wrong);run(wrong,code=1);assert snapshot(wrong)==old;checks.append('unsupported base pack rejected')
newer=profile('최신 개발버전',core='9.0.0');old=snapshot(newer);run(newer,code=1);assert snapshot(newer)==old;checks.append('never downgrade a newer installation')
bad=BASE/'손상된 배포';shutil.copytree(PACKAGE,bad);m=json.loads((bad/'client-release.json').read_text());m['mods'][1]['sha256']='0'*64;(bad/'client-release.json').write_text(json.dumps(m));p=profile('손상 거부');old=snapshot(p);run(p,code=1,package=bad);assert snapshot(p)==old;checks.append('bad second addon hash cannot partially update first addon')
p=profile('변경 후 복구 거부');run(p);manifest=json.loads((PACKAGE/'client-release.json').read_text());target=p/'mods'/manifest['mods'][0]['filename'];target.write_bytes(target.read_bytes()+b'changed');old=snapshot(p);run(p,'-Restore',code=1);assert snapshot(p)==old;checks.append('changed installed file blocks unsafe restore')
def modified(name, mutate):
 target=BASE/name;shutil.copytree(PACKAGE,target)
 manifest=json.loads((target/'client-release.json').read_text());mutate(manifest,target)
 (target/'client-release.json').write_text(json.dumps(manifest))
 return target

if COUNT==3:
 p=profile('개발명시필수');old=snapshot(p);run(p,offline=False,code=1);assert old==snapshot(p);checks.append('development manifest requires explicit offline consent')
 p=profile('기존 세번째 중복');fake(p/'mods/renamed.jar','cobblespire','0.5.0');fake(p/'mods/duplicate.jar','cobblespire','0.5.1');old=snapshot(p);run(p);run(p,'-Restore');assert old==snapshot(p);checks.append('duplicate Spire IDs replaced and exact originals restored')
 p=profile('세번째 최신');fake(p/'mods/newer.jar','cobblespire','99.0.0');old=snapshot(p);run(p,code=1);assert old==snapshot(p);checks.append('Spire downgrade blocks entire transaction')
 pair=modified('구형 두 모드',lambda m,t:m.update(mods=m['mods'][:2],channel='stable'))
 p=profile('두 모드 보존');fake(p/'mods/keep-spire.jar','cobblespire','0.5.0');old_spire=(p/'mods/keep-spire.jar').read_bytes();run(p,package=pair);assert (p/'mods/keep-spire.jar').read_bytes()==old_spire;checks.append('legacy pair upgrade leaves installed Spire untouched')
 bad=modified('세번째 손상',lambda m,t:m['mods'][2].update(sha256='0'*64))
 p=profile('세번째 실패');old=snapshot(p);run(p,package=bad,code=1);assert old==snapshot(p);checks.append('bad third hash preserves all originals')
 def wrong_dep(m,t):
  entry=m['mods'][2];path=t/'payload'/entry['filename'];fake(path,entry['id'],entry['version'],{'absent-dependency':'>=1.0.0'});entry['sha256']=hashlib.sha256(path.read_bytes()).hexdigest()
 bad=modified('의존 오류',wrong_dep);p=profile('의존 실패');old=snapshot(p);run(p,package=bad,code=1);assert old==snapshot(p);checks.append('third mod missing dependency blocks before mutation')
 def spire_update(m,t):
  entry=m['mods'][2];path=t/'payload'/entry['filename']
  with zipfile.ZipFile(path) as z:deps=json.loads(z.read('fabric.mod.json'))['depends']
  entry['version']='0.6.0-dev.2';fake(path,entry['id'],entry['version'],deps);entry['sha256']=hashlib.sha256(path.read_bytes()).hexdigest()
 updated=modified('세번째 단독업데이트',spire_update)
 p=profile('독립버전');run(p);old=snapshot(p);run(p,package=updated);new=snapshot(p)
 assert old!=new and all(new[k]==v for k,v in old.items() if 'cobblespire' not in k)
 run(p,package=updated);assert new==snapshot(p);assert len(list((p/'cobbleleague-auto-backups').iterdir()))==2
 run(p,'-Restore',package=updated);assert old==snapshot(p);checks.append('Spire-only preview update installs once and restores without changing pair')

for name,mutate in [
 ('알수없음',lambda m,t:m['mods'][-1].update(id='unknown')),
 ('중복ID',lambda m,t:m['mods'][-1].update(id='cobbleleague')),
 ('중복파일',lambda m,t:m['mods'][-1].update(filename=m['mods'][0]['filename'].upper())),
 ('탈출경로',lambda m,t:m['mods'][-1].update(filename='../escape.jar')),
 ('ADS경로',lambda m,t:m['mods'][-1].update(filename='file:escape.jar')),
 ('메타데이터',lambda m,t:m['mods'][-1].update(version='9.1.1')),
 ('안정판위장',lambda m,t:m.update(channel='stable')),
]:
 if name=='안정판위장' and COUNT!=3:continue
 bad=modified(name,mutate);p=profile(name);old=snapshot(p);run(p,package=bad,code=1);assert old==snapshot(p);checks.append(name+' rejected without file changes')

# Inject a failure after placing the first JAR, using the actual transaction code.
failure=BASE/'주입실패';shutil.copytree(PACKAGE,failure)
script=(failure/'client-updater.ps1').read_text(encoding='utf-8-sig')
injection="""function Move-Item { param($LiteralPath,$Destination) if($LiteralPath -like '*stage*' -and $LiteralPath -like '*cobblesynergy*'){throw 'Injected move failure'} Microsoft.PowerShell.Management\\Move-Item -LiteralPath $LiteralPath -Destination $Destination }
"""
(failure/'client-updater.ps1').write_text(script.replace('$lock=$null;$stage=$null;',injection+'$lock=$null;$stage=$null;'),encoding='utf-8-sig')
p=profile('部分失敗');old=snapshot(p);run(p,package=failure,code=1);assert old==snapshot(p);checks.append('mid-transaction placement failure rolls back exact original mods')

# A previously installed helper is replaced by invoking the new ZIP once, without desktop writes.
p=profile('구 helper 전환');home=p/'.cobbleleague-updater';home.mkdir()
legacy=subprocess.run(['git','show','2ed6208:installer/client-updater.ps1'],capture_output=True,check=True).stdout
(home/'client-updater.ps1').write_bytes(legacy)
(home/'auto-update.json').write_text(json.dumps({'enabled':False,'repository':'arscabar/CobbleLeague'}))
shutil.copy2(PACKAGE/'client-release.json',home/'client-release.json');shutil.copytree(PACKAGE/'payload',home/'payload')
if COUNT==3:
 old=snapshot(p);run(p,package=home,offline=False,code=1);assert snapshot(p)==old;checks.append('actual old two-mod helper rejects triple before migration')
run(p)
assert (home/'client-updater.ps1').read_bytes()==(PACKAGE/'client-updater.ps1').read_bytes()
config=json.loads((home/'auto-update.json').read_text(encoding='utf-8-sig'));assert config['offlineBundle'] and not config['enabled']
assert '-Offline' in (home/'UPDATE-AND-PLAY.bat').read_text()
run(p,package=home);checks.append('one-time ZIP migrates old helper, preserves opt-out and persists offline channel')

# Simulate a running game without touching a real Java process or waiting in real time.
waiting=BASE/'종료대기';shutil.copytree(PACKAGE,waiting)
script=(waiting/'client-updater.ps1').read_text(encoding='utf-8-sig')
injection="""$script:gameChecks=0
function GameRunning($root){$script:gameChecks++;return $script:gameChecks -le 1}
function Start-Sleep { param($Seconds) }
"""
(waiting/'client-updater.ps1').write_text(script.replace('$lock=$null;$stage=$null;',injection+'$lock=$null;$stage=$null;'),encoding='utf-8-sig')
p=profile('게임중거부');old=snapshot(p);run(p,package=waiting,code=1);assert old==snapshot(p)
run(p,'-WaitForExit',package=waiting);checks.append('running game refuses immediate mutation and wait mode resumes after exit')

restoring=BASE/'복구실패';shutil.copytree(PACKAGE,restoring)
script=(restoring/'client-updater.ps1').read_text(encoding='utf-8-sig')
injection="""function Copy-Item { param($LiteralPath,$Destination,[switch]$Force) if($LiteralPath -like '*auto-backups*old-synergy*' -and $Destination -like '*mods*'){throw 'Injected restore failure'} Microsoft.PowerShell.Management\\Copy-Item -LiteralPath $LiteralPath -Destination $Destination -Force:$Force }
"""
(restoring/'client-updater.ps1').write_text(script.replace('$lock=$null;$stage=$null;',injection+'$lock=$null;$stage=$null;'),encoding='utf-8-sig')
p=profile('복구원자성');run(p);old=snapshot(p);run(p,'-Restore',package=restoring,code=1);assert old==snapshot(p);checks.append('mid-restore copy failure restores complete installed set')

(ROOT/'dist/client-updater-tests.json').write_text(json.dumps(dict(passed=len(checks),checks=checks),indent=2));print(json.dumps(dict(passed=len(checks),checks=checks),ensure_ascii=False))
