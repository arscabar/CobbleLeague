[CmdletBinding()]
param([string]$TargetPath,[switch]$Launch,[switch]$WaitForExit,[switch]$CheckOnly,[switch]$InstallLauncher,[switch]$Restore,[string]$ManifestPath,[string]$BundleDirectory,[string]$Repository='arscabar/CobbleLeague',[string]$HandoffToken,[switch]$ResolveLaunchOnly)
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
[Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12
function ReadJson($path){[IO.File]::ReadAllText($path,[Text.Encoding]::UTF8)|ConvertFrom-Json}
function Hash($path){$stream=[IO.File]::OpenRead($path);$algorithm=[Security.Cryptography.SHA256]::Create();try{return ([BitConverter]::ToString($algorithm.ComputeHash($stream))).Replace('-','').ToLowerInvariant()}finally{$stream.Dispose();$algorithm.Dispose()}}
function Child($root,$name){
 $full=[IO.Path]::GetFullPath((Join-Path $root $name));$prefix=$root.TrimEnd('\')+'\'
 if(-not $full.StartsWith($prefix,[StringComparison]::OrdinalIgnoreCase)){throw '허용된 폴더 밖의 경로입니다.'};return $full
}
function Meta($path){
 $zip=$null;$reader=$null
 try{$zip=[IO.Compression.ZipFile]::OpenRead($path);$entry=$zip.GetEntry('fabric.mod.json');if(-not $entry){return $null};$reader=New-Object IO.StreamReader($entry.Open());return ($reader.ReadToEnd()|ConvertFrom-Json)}
 catch{return $null}finally{if($reader){$reader.Dispose()};if($zip){$zip.Dispose()}}
}
function Mods($root){@(Get-ChildItem -LiteralPath (Child $root 'mods') -Filter '*.jar' -File|ForEach-Object{[pscustomobject]@{path=$_.FullName;name=$_.Name;meta=(Meta $_.FullName)}})}
function GameRunning($root){
 $needle=$root.Replace('/','\')
 return [bool]@(Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'"|Where-Object{$_.CommandLine -and ($_.CommandLine.Replace('/','\').IndexOf($needle,[StringComparison]::OrdinalIgnoreCase)-ge 0 -or $_.CommandLine.Contains('com.modrinth.theseus.MinecraftLaunch'))}).Count
}
function PickProfile{
 $paths=@((Join-Path $env:APPDATA 'ModrinthApp\profiles'),(Join-Path $env:APPDATA 'com.modrinth.theseus\profiles'))
 $list=@($paths|Where-Object{Test-Path -LiteralPath $_}|ForEach-Object{Get-ChildItem -LiteralPath $_ -Directory|Where-Object{$_.Name -match '(?i)cobbleverse' -and (Test-Path -LiteralPath (Join-Path $_.FullName 'mods'))}})
 if($list.Count-eq 1){return $list[0].FullName}
 for($i=0;$i-lt $list.Count;$i++){Write-Host ('[{0}] {1}'-f ($i+1),$list[$i].FullName)}
 $choice=0;if([int]::TryParse((Read-Host '설치할 프로필 번호'),[ref]$choice)-and $choice-ge 1 -and $choice-le $list.Count){return $list[$choice-1].FullName}
 throw 'Modrinth COBBLEVERSE 프로필을 찾을 수 없습니다. -TargetPath로 지정하세요.'
}
function Download($url,$dest){
 $args=@{Uri=$url;OutFile=$dest;UseBasicParsing=$true;TimeoutSec=90;Headers=@{'User-Agent'='CobbleLeague-client-updater';Accept='application/octet-stream'}}
 Invoke-WebRequest @args|Out-Null
}
function LaunchId($root){
 $db=Join-Path (Split-Path (Split-Path $root -Parent) -Parent) 'app.db'
 if(-not(Test-Path -LiteralPath $db)){return [IO.Path]::GetFileName($root)}
 if(-not ('CobbleLeagueInstanceDb' -as [type])){
  Add-Type -TypeDefinition @"
using System;
using System.Text;
using System.Runtime.InteropServices;
public static class CobbleLeagueInstanceDb {
 [DllImport("winsqlite3",CallingConvention=CallingConvention.Cdecl)]static extern int sqlite3_open_v2(byte[] name,out IntPtr db,int flags,IntPtr vfs);
 [DllImport("winsqlite3",CallingConvention=CallingConvention.Cdecl)]static extern int sqlite3_prepare_v2(IntPtr db,byte[] sql,int n,out IntPtr stmt,IntPtr tail);
 [DllImport("winsqlite3",CallingConvention=CallingConvention.Cdecl)]static extern int sqlite3_bind_text(IntPtr stmt,int i,byte[] text,int n,IntPtr destructor);
 [DllImport("winsqlite3",CallingConvention=CallingConvention.Cdecl)]static extern int sqlite3_step(IntPtr stmt);
 [DllImport("winsqlite3",CallingConvention=CallingConvention.Cdecl)]static extern IntPtr sqlite3_column_text(IntPtr stmt,int col);
 [DllImport("winsqlite3",CallingConvention=CallingConvention.Cdecl)]static extern int sqlite3_column_bytes(IntPtr stmt,int col);
 [DllImport("winsqlite3",CallingConvention=CallingConvention.Cdecl)]static extern int sqlite3_finalize(IntPtr stmt);
 [DllImport("winsqlite3",CallingConvention=CallingConvention.Cdecl)]static extern int sqlite3_close(IntPtr db);
 static byte[] U(string s){return Encoding.UTF8.GetBytes(s+"\0");}
 public static string Find(string path,string leaf,string full){
  IntPtr db=IntPtr.Zero,stmt=IntPtr.Zero;
  try{
   if(sqlite3_open_v2(U(path),out db,1,IntPtr.Zero)!=0)throw new Exception("Could not read Modrinth instance database");
   if(sqlite3_prepare_v2(db,U("SELECT id FROM instances WHERE path = ?1 COLLATE NOCASE OR path = ?2 COLLATE NOCASE OR path = ?3 COLLATE NOCASE"),-1,out stmt,IntPtr.Zero)!=0)return "legacy:"+leaf;
   string[] values={leaf,full,full.Replace('\\','/')};
   for(int i=0;i<3;i++){byte[] b=U(values[i]);sqlite3_bind_text(stmt,i+1,b,b.Length-1,new IntPtr(-1));}
   string found=null;
   while(sqlite3_step(stmt)==100){if(found!=null)throw new Exception("Multiple Modrinth instances match this folder");int n=sqlite3_column_bytes(stmt,0);byte[] b=new byte[n];Marshal.Copy(sqlite3_column_text(stmt,0),b,0,n);found=Encoding.UTF8.GetString(b);}
   return found;
  }finally{if(stmt!=IntPtr.Zero)sqlite3_finalize(stmt);if(db!=IntPtr.Zero)sqlite3_close(db);}
 }
}
"@
 }
 $id=[CobbleLeagueInstanceDb]::Find($db,[IO.Path]::GetFileName($root),$root)
 if(-not $id){throw '모드린스 실행 ID를 찾을 수 없습니다. 프로필 경로를 확인해 주세요.'}
 if($id.StartsWith('legacy:')){return $id.Substring(7)};return $id
}
$lock=$null;$stage=$null;$stagedPaths=@()
try{
 if(-not $TargetPath){$TargetPath=PickProfile};$root=[IO.Path]::GetFullPath($TargetPath).TrimEnd('\');$modsRoot=Child $root 'mods'
 if(-not (Test-Path -LiteralPath $modsRoot -PathType Container)){throw '프로필의 mods 폴더가 없습니다.'}
 if((Test-Path -LiteralPath (Join-Path $root 'server.properties'))-or(Test-Path -LiteralPath (Join-Path $root 'fabric-server-launch.jar'))){throw '접속하는 플레이어 PC용입니다. 서버 폴더에는 사용하지 않습니다.'}
 foreach($path in @($root,$modsRoot)){if((Get-Item -LiteralPath $path).Attributes-band[IO.FileAttributes]::ReparsePoint){throw '공유·연결된 폴더는 변경하지 않습니다.'}}
 $instanceId=if($Launch -or $ResolveLaunchOnly){LaunchId $root}else{$null}
 if($ResolveLaunchOnly){Write-Output ('modrinth://launch/instance/'+[Uri]::EscapeDataString($instanceId));exit 0}
 if($HandoffToken){
  if($HandoffToken-notmatch '^[a-f0-9]{32}$' -or -not(GameRunning $root)){throw '게임 업데이트 인계 검증 실패'}
  [IO.File]::WriteAllText((Child $root ('.cobbleleague-updater/handoff-'+$HandoffToken+'.ready')),'ready')
 }
 $lock=[IO.File]::Open((Child $root '.cobbleleague-update.lock'),[IO.FileMode]::OpenOrCreate,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
 while(GameRunning $root){if(-not $WaitForExit){throw '게임을 완전히 종료한 뒤 실행하세요.'};Write-Host '게임이 종료되기를 기다립니다. 플레이 중인 게임은 강제로 종료하지 않습니다.';Start-Sleep -Seconds 5}
 $current=Mods $root
 $c=@($current|Where-Object{$_.meta -and $_.meta.id-eq 'cobblemon'})
 if($c.Count-ne 1 -or $c[0].meta.version-notmatch '^1\.7\.3(?:\+|$)'){throw 'Cobblemon 1.7.3이 있는 COBBLEVERSE 1.7.42용입니다.'}
 foreach($id in @('fabric-api','fabric-language-kotlin','cobblemon_quick_teams')){if(@($current|Where-Object{$_.meta -and $_.meta.id-eq $id}).Count-ne 1){throw ('필요한 모드가 없습니다: '+$id)}}
 $statePath=Child $root 'cobbleleague-auto-install.json'
 if($Restore){
  if(-not(Test-Path -LiteralPath $statePath)){throw '이 자동 설치기의 복구 기록이 없습니다.'}
  $state=ReadJson $statePath;$backup=Child $root $state.backup;$record=ReadJson (Child $backup 'record.json')
  if($record.restored){Write-Host '이미 복구했습니다.';exit 0}
  foreach($new in $record.installed){$dest=Child $modsRoot $new.filename;if((Hash $dest)-ne $new.sha256){throw '설치 후 파일이 바뀌어 자동 복구를 중단했습니다.'}}
  foreach($old in $record.originals){if((Hash (Child $backup $old.name))-ne $old.sha256){throw '백업 검증 실패'}}
  if($CheckOnly){Write-Host '복구 검사 통과';exit 0}
  foreach($old in $record.originals){
   $dest=Child $modsRoot $old.name
   if((Test-Path -LiteralPath $dest)-and $old.name-notin @($record.installed.filename)){throw '복구할 이름에 다른 파일이 있어 중단했습니다.'}
  }
  $rollback=Child $backup ('restore-'+[guid]::NewGuid().ToString('N'));$null=New-Item -ItemType Directory -Path $rollback
  $removed=@();$restored=@()
  try{
   foreach($new in $record.installed){Move-Item -LiteralPath (Child $modsRoot $new.filename) -Destination (Child $rollback $new.filename);$removed+=$new.filename}
   foreach($old in $record.originals){Copy-Item -LiteralPath (Child $backup $old.name) -Destination (Child $modsRoot $old.name);$restored+=$old.name}
  }catch{
   foreach($name in $restored){Remove-Item -LiteralPath (Child $modsRoot $name)}
   foreach($name in $removed){Move-Item -LiteralPath (Child $rollback $name) -Destination (Child $modsRoot $name)};throw
  }
  $record.restored=$true;$record|ConvertTo-Json -Depth 8|Set-Content -LiteralPath (Child $backup 'record.json') -Encoding UTF8
  Write-Host '두 애드온을 설치 전 상태로 복구했습니다. 다시 실행 배치를 누르면 최신본을 설치합니다.';exit 0
 }
 $baseline=if($ManifestPath){[IO.Path]::GetFullPath($ManifestPath)}else{Join-Path $PSScriptRoot 'client-release.json'}
 if(-not $BundleDirectory){$BundleDirectory=Join-Path $PSScriptRoot 'payload'}
 if($Repository-notmatch '^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$'){throw '저장소 이름이 올바르지 않습니다.'}
 $manifest=$null;$assets=@();$remote=$false
 if($ManifestPath){$manifest=ReadJson $baseline}
 else{
  $release=$null
  try{$release=Invoke-RestMethod -Uri ('https://api.github.com/repos/'+$Repository+'/releases/latest') -Headers @{'User-Agent'='CobbleLeague-client-updater';Accept='application/vnd.github+json'} -TimeoutSec 30}
  catch{if(Test-Path -LiteralPath $baseline){Write-Host '릴리스에 연결하지 못해 포함된 검증 버전을 확인합니다.';$manifest=ReadJson $baseline}else{throw}}
  if($release){
   $assets=@($release.assets);$asset=@($assets|Where-Object{$_.name-eq 'client-release.json'})
   if($asset.Count-ne 1){throw '최신 릴리스에 client-release.json이 없습니다. 배포자가 릴리스를 완성해야 합니다.'}
   $manifestTemp=Child $root ('.cobbleleague-release-'+[guid]::NewGuid().ToString('N')+'.tmp')
   try{
    Download $asset[0].url $manifestTemp
    if($asset[0].digest -and $asset[0].digest -ne ('sha256:'+(Hash $manifestTemp))){throw '릴리스 설명 파일 검증 실패'}
    $manifest=ReadJson $manifestTemp;$remote=$true
   }finally{if(Test-Path -LiteralPath $manifestTemp){Remove-Item -LiteralPath $manifestTemp}}
  }
 }
 if($manifest.schema-ne 1 -or $manifest.minecraft-ne '1.21.1' -or $manifest.cobblemon-ne '1.7.3'){throw '지원하지 않는 업데이트 형식 또는 모드팩 버전입니다.'}
 $expected=@('cobbleleague','cobblesynergy');$entries=@($manifest.mods)
 if($entries.Count-ne 2 -or @($entries|Where-Object{$_.id-notin $expected}).Count-gt 0 -or @($entries.id|Select-Object -Unique).Count-ne 2 -or @($entries.filename|Select-Object -Unique).Count-ne 2){throw '두 모드가 함께 지정된 릴리스만 설치합니다.'}
 $stage=Child $root ('.cobbleleague-stage-'+[guid]::NewGuid().ToString('N'));$null=New-Item -ItemType Directory -Path $stage
 foreach($entry in $entries){
  if([IO.Path]::GetFileName($entry.filename)-ne $entry.filename -or $entry.filename-notmatch '\.jar$' -or $entry.sha256-notmatch '^[0-9a-f]{64}$'){throw '잘못된 릴리스 파일 정보'}
  $dest=Child $stage $entry.filename;$stagedPaths+=$dest
  if($remote){
   $asset=@($assets|Where-Object{$_.name-eq $entry.filename})
   if($asset.Count-ne 1){throw ('릴리스 파일 누락: '+$entry.filename)}
   $url=[uri]$asset[0].url
   if($url.Scheme-ne 'https' -or $url.Host-ne 'api.github.com' -or -not $url.AbsolutePath.StartsWith('/repos/'+$Repository+'/releases/assets/',[StringComparison]::OrdinalIgnoreCase)){throw '지정한 저장소의 릴리스 파일만 다운로드합니다.'}
   Download $url.AbsoluteUri $dest
  }else{Copy-Item -LiteralPath (Child ([IO.Path]::GetFullPath($BundleDirectory)) $entry.filename) -Destination $dest}
  if((Hash $dest)-ne $entry.sha256){throw ('다운로드 파일 검증 실패: '+$entry.filename)}
  $meta=Meta $dest
  if(-not $meta -or $meta.id-ne $entry.id -or $meta.version-ne $entry.version){throw 'JAR의 실제 모드 정보가 릴리스와 다릅니다.'}
  $old=@($current|Where-Object{$_.meta -and $_.meta.id-eq $entry.id})
  foreach($o in $old){if([version](($o.meta.version-split '[+-]')[0])-gt [version](($entry.version-split '[+-]')[0])){throw '이미 더 높은 버전이 설치되어 있어 자동으로 낮추지 않습니다.'}}
  $existing=Child $modsRoot $entry.filename
  if((Test-Path -LiteralPath $existing)-and -not @($old|Where-Object{$_.path-eq $existing}).Count){throw '같은 이름의 다른 파일이 있어 중단했습니다.'}
 }
 $synergy=$entries|Where-Object{$_.id-eq 'cobblesynergy'};$core=$entries|Where-Object{$_.id-eq 'cobbleleague'}
 if((Meta (Child $stage $synergy.filename)).depends.cobbleleague-ne $core.version){throw '두 애드온의 버전 조합이 맞지 않습니다.'}
 $changed=@($entries|Where-Object{$e=$_;$old=@($current|Where-Object{$_.meta -and $_.meta.id-eq $e.id});$old.Count-ne 1 -or (Hash $old[0].path)-ne $e.sha256})
 if($CheckOnly){Write-Host ('검사 통과 · 변경할 애드온 '+$changed.Count+'개');exit 0}
 if($changed.Count-gt 0){
  if(GameRunning $root){throw '게임이 실행되어 업데이트를 중단했습니다.'}
  $stamp=(Get-Date -Format 'yyyyMMdd-HHmmss')+'-'+[guid]::NewGuid().ToString('N').Substring(0,8)
  $relative='cobbleleague-auto-backups\'+$stamp;$backup=Child $root $relative;$null=New-Item -ItemType Directory -Path $backup
  $originals=@($current|Where-Object{$_.meta -and $_.meta.id-in $expected})
  foreach($old in $originals){Copy-Item -LiteralPath $old.path -Destination (Child $backup $old.name)}
  $placed=@()
  try{
   foreach($old in $originals){Remove-Item -LiteralPath $old.path}
   foreach($entry in $entries){Move-Item -LiteralPath (Child $stage $entry.filename) -Destination (Child $modsRoot $entry.filename);$placed+=$entry.filename}
   $record=[pscustomobject]@{originals=@($originals|ForEach-Object{[pscustomobject]@{name=$_.name;sha256=(Hash (Child $backup $_.name))}});installed=$entries;restored=$false}
   $record|ConvertTo-Json -Depth 8|Set-Content -LiteralPath (Child $backup 'record.json') -Encoding UTF8
   @{backup=$relative;release=$manifest.release}|ConvertTo-Json|Set-Content -LiteralPath $statePath -Encoding UTF8
  }catch{
   foreach($name in $placed){Remove-Item -LiteralPath (Child $modsRoot $name)}
   foreach($old in $originals){Copy-Item -LiteralPath (Child $backup $old.name) -Destination $old.path -Force};throw
  }
  Write-Host ('최신 두 애드온 적용 완료 · '+$manifest.release)
 }else{Write-Host ('이미 같은 릴리스가 설치되어 있습니다 · '+$manifest.release)}
 if($InstallLauncher -or (Test-Path -LiteralPath (Child $root '.cobbleleague-updater'))){
  $homePath=Child $root '.cobbleleague-updater';$null=New-Item -ItemType Directory -Force -Path $homePath
  $autoConfig=Child $homePath 'auto-update.json'
  if(-not(Test-Path -LiteralPath $autoConfig)){@{enabled=$true;repository=$Repository}|ConvertTo-Json|Set-Content -LiteralPath $autoConfig -Encoding UTF8}
  $bundle=Child $homePath 'payload';$null=New-Item -ItemType Directory -Force -Path $bundle
  if([IO.Path]::GetFullPath($PSCommandPath)-ne (Child $homePath 'client-updater.ps1')){Copy-Item -LiteralPath $PSCommandPath -Destination (Child $homePath 'client-updater.ps1') -Force}
  $manifest|ConvertTo-Json -Depth 8|Set-Content -LiteralPath (Child $homePath 'client-release.json') -Encoding UTF8
  foreach($entry in $entries){Copy-Item -LiteralPath (Child $modsRoot $entry.filename) -Destination (Child $bundle $entry.filename) -Force}
  $bat="@echo off`r`npowershell.exe -NoProfile -ExecutionPolicy Bypass -File `"%~dp0client-updater.ps1`" -TargetPath `"%~dp0..`" -WaitForExit -Launch`r`nif errorlevel 1 pause`r`n"
  [IO.File]::WriteAllText((Child $homePath 'UPDATE-AND-PLAY.bat'),$bat,[Text.Encoding]::ASCII)
  if($InstallLauncher){
  $desktop=[Environment]::GetFolderPath('Desktop');$shortcut=Join-Path $desktop 'CobbleLeague-PLAY.bat'
  [IO.File]::WriteAllText($shortcut,('@echo off'+"`r`nchcp 65001 >nul`r`ncall `""+(Child $homePath 'UPDATE-AND-PLAY.bat')+"`"`r`n"),(New-Object Text.UTF8Encoding($false)))
  Write-Host '바탕화면 CobbleLeague-PLAY.bat로 실행하면 매번 릴리스를 확인합니다.'
  }
 }
 if($Launch){
  $uri='modrinth://launch/instance/'+[Uri]::EscapeDataString($instanceId)
  Start-Process $uri
 }
}catch{Write-Host ('업데이트 중단: '+$_.Exception.Message) -ForegroundColor Red;exit 1}
finally{
 foreach($path in $stagedPaths){if(Test-Path -LiteralPath $path){Remove-Item -LiteralPath $path -ErrorAction SilentlyContinue}}
 if($stage -and (Test-Path -LiteralPath $stage)){Remove-Item -LiteralPath $stage -ErrorAction SilentlyContinue}
 if($lock){$lock.Dispose()}
}
