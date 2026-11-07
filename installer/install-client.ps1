[CmdletBinding()]
param(
    [ValidateSet('Install','Restore')][string]$Action = 'Install',
    [string]$TargetPath,
    [switch]$CheckOnly,
    [switch]$NonInteractive
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$packageRoot = [IO.Path]::GetFullPath($PSScriptRoot)

function Read-Json([string]$Path) {
    return ([IO.File]::ReadAllText($Path, [Text.Encoding]::UTF8) | ConvertFrom-Json)
}
function Write-Json([string]$Path, $Value) {
    $temp = $Path + '.tmp-' + [guid]::NewGuid().ToString('N')
    try {
        [IO.File]::WriteAllText($temp, ($Value | ConvertTo-Json -Depth 8), (New-Object Text.UTF8Encoding($true)))
        if ([IO.File]::Exists($Path)) { [IO.File]::Replace($temp, $Path, [NullString]::Value) }
        else { [IO.File]::Move($temp, $Path) }
    } finally { if (Test-Path -LiteralPath $temp) { Remove-Item -LiteralPath $temp } }
}
function Child-Path([string]$Root, [string]$Relative) {
    $full = [IO.Path]::GetFullPath((Join-Path $Root $Relative))
    $prefix = $Root.TrimEnd('\') + '\'
    if (-not $full.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase)) { throw '설치 경로가 선택한 폴더 밖을 가리킵니다.' }
    return $full
}
function File-Sha256([string]$Path) {
    $stream = [IO.File]::OpenRead($Path)
    $algorithm = [Security.Cryptography.SHA256]::Create()
    try { return ([BitConverter]::ToString($algorithm.ComputeHash($stream))).Replace('-','').ToLowerInvariant() }
    finally { $stream.Dispose(); $algorithm.Dispose() }
}
function Jar-Metadata([string]$Path) {
    $zip = $null; $reader = $null
    try {
        $zip = [IO.Compression.ZipFile]::OpenRead($Path)
        $entry = $zip.GetEntry('fabric.mod.json')
        if ($null -eq $entry) { return $null }
        $reader = New-Object IO.StreamReader($entry.Open(), [Text.Encoding]::UTF8)
        return ($reader.ReadToEnd() | ConvertFrom-Json)
    } catch { return $null }
    finally { if ($reader) { $reader.Dispose() }; if ($zip) { $zip.Dispose() } }
}
function Mods-In([string]$Root) {
    $mods = Child-Path $Root 'mods'
    if (-not (Test-Path -LiteralPath $mods -PathType Container)) { throw '선택한 폴더에 mods 폴더가 없습니다. Modrinth 프로필의 최상위 폴더를 선택하세요.' }
    if ((Get-Item -LiteralPath $mods).Attributes -band [IO.FileAttributes]::ReparsePoint) { throw '공유 또는 연결된 mods 폴더는 자동 변경하지 않습니다. 개별 프로필의 mods 폴더를 사용해 주세요.' }
    return @(Get-ChildItem -LiteralPath $mods -Filter '*.jar' -File | ForEach-Object {
        $meta = Jar-Metadata $_.FullName
        [pscustomobject]@{ File = $_; Meta = $meta }
    })
}
function Pick-Profile {
    $roots = @((Join-Path $env:APPDATA 'ModrinthApp\profiles'), (Join-Path $env:APPDATA 'com.modrinth.theseus\profiles'))
    $candidates = @($roots | Select-Object -Unique | ForEach-Object {
        if (Test-Path -LiteralPath $_ -PathType Container) {
            Get-ChildItem -LiteralPath $_ -Directory | Where-Object { $_.Name -match '(?i)cobbleverse' -and (Test-Path -LiteralPath (Join-Path $_.FullName 'mods')) }
        }
    } | Sort-Object FullName -Unique)
    if ($NonInteractive) { throw '자동 검사에는 -TargetPath를 지정해야 합니다.' }
    Write-Host ''
    Write-Host '설치할 COBBLEVERSE 1.7.42 프로필을 골라 주세요.' -ForegroundColor Cyan
    for ($i=0; $i -lt $candidates.Count; $i++) { Write-Host ('[{0}] {1}' -f ($i+1), $candidates[$i].FullName) }
    if ($candidates.Count -gt 0) {
        Write-Host '[0] 다른 폴더 선택'
        $choice = Read-Host '번호'
        $number = 0
        if ([int]::TryParse($choice, [ref]$number) -and $number -ge 1 -and $number -le $candidates.Count) { return $candidates[$number-1].FullName }
        if ($choice -ne '0') { throw '올바른 번호를 입력한 뒤 다시 실행해 주세요.' }
    }
    Add-Type -AssemblyName System.Windows.Forms
    $dialog = New-Object Windows.Forms.FolderBrowserDialog
    $dialog.Description = 'Modrinth에서 프로필 → 폴더 열기로 확인한 COBBLEVERSE 1.7.42 폴더를 선택하세요. mods 폴더의 한 단계 위입니다.'
    $dialog.ShowNewFolderButton = $false
    try {
        if ($dialog.ShowDialog() -ne [Windows.Forms.DialogResult]::OK) { throw '폴더 선택을 취소했습니다. 변경한 파일은 없습니다.' }
        return $dialog.SelectedPath
    } finally { $dialog.Dispose() }
}
function Check-Closed([string]$Root) {
    $needle = $Root.Replace('/','\')
    $running = @(Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'" | Where-Object {
        $_.CommandLine -and $_.CommandLine.Replace('/','\').IndexOf($needle,[StringComparison]::OrdinalIgnoreCase) -ge 0
    })
    if ($running.Count -gt 0) { throw '이 프로필의 마인크래프트가 실행 중입니다. 게임을 완전히 종료한 뒤 다시 실행해 주세요. 실행 중인 게임은 자동으로 종료하지 않습니다.' }
}
function Check-Version($Mods) {
    $cobblemon = @($Mods | Where-Object { $_.Meta -and $_.Meta.id -eq 'cobblemon' })
    if ($cobblemon.Count -ne 1 -or $cobblemon[0].Meta.version -notmatch '^1\.7\.3(?:\+|$)') { throw 'Cobblemon 1.7.3용입니다. Modrinth에서 COBBLEVERSE 1.7.42를 설치한 프로필을 선택해 주세요. 1.8 프로필에는 설치하지 않습니다.' }
    $api = @($Mods | Where-Object { $_.Meta -and $_.Meta.id -eq 'fabric-api' })
    $kotlin = @($Mods | Where-Object { $_.Meta -and $_.Meta.id -eq 'fabric-language-kotlin' })
    $teams = @($Mods | Where-Object { $_.Meta -and $_.Meta.id -eq 'cobblemon_quick_teams' })
    if ($api.Count -ne 1 -or $kotlin.Count -ne 1) { throw 'Fabric API와 Fabric Language Kotlin이 필요합니다. 기존 코블버스 프로필을 선택해 주세요.' }
    $apiVersion = [version](($api[0].Meta.version -split '\+')[0])
    $kotlinVersion = [version](($kotlin[0].Meta.version -split '\+')[0])
    if ($apiVersion -lt [version]'0.116.6' -or $kotlinVersion -lt [version]'1.13.6') { throw '이 프로필의 Fabric API 또는 Kotlin 버전이 지원 범위보다 낮습니다. COBBLEVERSE 1.7.42를 사용해 주세요.' }
    if ($teams.Count -ne 1 -or $teams[0].Meta.version -ne '1.5.5') { throw '등록 덱 기능은 Quick Teams 1.5.5 기준입니다. 해당 모드가 들어 있는 COBBLEVERSE 1.7.42 프로필을 선택해 주세요.' }
}

try {
    Write-Host 'CobbleLeague · 친구 PC용 설치' -ForegroundColor Cyan
    Write-Host '게임을 종료한 상태에서 사용하세요. 서버 파일과 월드는 변경하지 않습니다.'
    if (-not $TargetPath) { $TargetPath = Pick-Profile }
    $target = [IO.Path]::GetFullPath($TargetPath.Trim().Trim('"')).TrimEnd('\')
    if (-not (Test-Path -LiteralPath $target -PathType Container)) { throw '프로필 폴더를 찾을 수 없습니다.' }
    if ((Get-Item -LiteralPath $target).Attributes -band [IO.FileAttributes]::ReparsePoint) { throw '연결 폴더 대신 실제 프로필 폴더를 선택해 주세요.' }
    if ((Test-Path -LiteralPath (Join-Path $target 'server.properties')) -or (Test-Path -LiteralPath (Join-Path $target 'fabric-server-launch.jar'))) {
        throw '이 설치파일은 접속하는 친구 PC용입니다. 서버 폴더에는 설치하지 않습니다. Modrinth의 코블버스 프로필 폴더를 선택하세요.'
    }
    $mods = Mods-In $target
    Check-Closed $target
    $statePath = Child-Path $target 'cobbleleague-client-install.json'
    $modsRoot = Child-Path $target 'mods'

    if ($Action -eq 'Restore') {
        if (-not (Test-Path -LiteralPath $statePath)) { throw '이 설치기로 설치한 기록이 없습니다.' }
        $state = Read-Json $statePath
        $backup = Child-Path $target $state.backup
        $record = Read-Json (Child-Path $backup 'record.json')
        if ($record.restored) { Write-Host '이미 설치 전 상태로 되돌렸습니다.'; exit 0 }
        if ([IO.Path]::GetFileName($record.installedName) -ne $record.installedName) { throw '설치 기록의 파일 이름이 올바르지 않습니다.' }
        $installed = Child-Path $modsRoot $record.installedName
        if (-not (Test-Path -LiteralPath $installed) -or (File-Sha256 $installed) -ne $record.installedSha256) { throw '설치 후 파일이 변경되어 자동 복원을 중단했습니다. 현재 파일은 유지합니다.' }
        $current = @($mods | Where-Object { $_.Meta -and $_.Meta.id -eq 'cobbleleague' })
        if ($current.Count -ne 1 -or $current[0].File.Name -ne $record.installedName) { throw '다른 CobbleLeague 파일이 추가되어 자동 복원을 중단했습니다.' }
        foreach ($old in @($record.originals)) {
            if ([IO.Path]::GetFileName($old.name) -ne $old.name) { throw '백업 파일 이름 오류' }
            $file = Child-Path $backup ('originals\'+$old.name)
            if ((File-Sha256 $file) -ne $old.sha256) { throw '백업 파일 검증에 실패했습니다.' }
        }
        foreach ($old in @($record.originals)) {
            $oldDestination = Child-Path $modsRoot $old.name
            if ($oldDestination -ne $installed -and (Test-Path -LiteralPath $oldDestination)) { throw '복원할 파일 이름에 다른 파일이 생겨 중단했습니다. 현재 파일은 유지합니다.' }
        }
        if ($CheckOnly) { Write-Host '복원 가능 상태입니다. 파일을 변경하지 않았습니다.'; exit 0 }
        # All sources and destinations were checked within the selected profile above.
        Move-Item -LiteralPath $installed -Destination (Child-Path $backup 'removed-installed.jar')
        try {
            foreach ($old in @($record.originals)) { Copy-Item -LiteralPath (Child-Path $backup ('originals\'+$old.name)) -Destination (Child-Path $modsRoot $old.name) }
        } catch {
            foreach ($old in @($record.originals)) {
                $partial = Child-Path $modsRoot $old.name
                if (Test-Path -LiteralPath $partial) { Remove-Item -LiteralPath $partial }
            }
            Move-Item -LiteralPath (Child-Path $backup 'removed-installed.jar') -Destination $installed
            throw
        }
        $record.restored = $true; Write-Json (Child-Path $backup 'record.json') $record
        Write-Host '설치 전 CobbleLeague 상태로 되돌렸습니다. 다른 모드는 유지했습니다.' -ForegroundColor Green
        exit 0
    }

    Check-Version $mods
    $manifest = Read-Json (Join-Path $packageRoot 'package.json')
    $source = Child-Path $packageRoot $manifest.payload
    if ((File-Sha256 $source) -ne $manifest.sha256) { throw '배포 파일이 손상됐습니다. ZIP을 다시 받아 압축을 풀어 주세요.' }
    $payloadMeta = Jar-Metadata $source
    if ($payloadMeta.id -ne 'cobbleleague' -or $payloadMeta.version -ne $manifest.clientVersion) { throw '배포 애드온 정보가 맞지 않습니다.' }
    Write-Host ('선택한 프로필: '+$target)
    if ($CheckOnly) { Write-Host '호환 버전과 배포 파일 검사가 통과했습니다. 파일을 변경하지 않았습니다.'; exit 0 }
    $filename = [IO.Path]::GetFileName($source)
    $destination = Child-Path $modsRoot $filename
    $oldMods = @($mods | Where-Object { $_.Meta -and $_.Meta.id -eq 'cobbleleague' })
    if ($oldMods.Count -eq 1 -and $oldMods[0].File.Name -eq $filename -and (File-Sha256 $oldMods[0].File.FullName) -eq $manifest.sha256) {
        Write-Host '같은 설치 파일이 이미 적용돼 있습니다. 추가로 변경하지 않았습니다.' -ForegroundColor Green
        exit 0
    }
    if ((Test-Path -LiteralPath $destination) -and -not @($oldMods | Where-Object { $_.File.FullName -eq $destination }).Count) { throw '설치 위치에 다른 파일이 있어 덮어쓰지 않았습니다.' }
    $stamp = (Get-Date -Format 'yyyyMMdd-HHmmss')+'-'+([guid]::NewGuid().ToString('N').Substring(0,8))
    $backupRelative = 'cobbleleague-installer-backups\'+$stamp
    $backup = Child-Path $target $backupRelative
    $null = New-Item -ItemType Directory -Path (Child-Path $backup 'originals') -Force
    $originals = @($oldMods | ForEach-Object { [pscustomobject]@{ name=$_.File.Name; sha256=(File-Sha256 $_.File.FullName) } })
    foreach ($old in $oldMods) { Copy-Item -LiteralPath $old.File.FullName -Destination (Child-Path $backup ('originals\'+$old.File.Name)) }
    $record = [pscustomobject]@{ installedName=$filename; installedSha256=$manifest.sha256; originals=$originals; restored=$false }
    Write-Json (Child-Path $backup 'record.json') $record
    $stage = Child-Path $modsRoot ('.cobbleleague-'+[guid]::NewGuid().ToString('N')+'.tmp')
    $placed = $false
    try {
        Copy-Item -LiteralPath $source -Destination $stage
        if ((File-Sha256 $stage) -ne $manifest.sha256) { throw '복사 검증 실패' }
        foreach ($old in $oldMods) { Remove-Item -LiteralPath $old.File.FullName }
        Move-Item -LiteralPath $stage -Destination $destination
        $placed = $true
        Write-Json $statePath ([pscustomobject]@{ version=$manifest.clientVersion; backup=$backupRelative })
    } catch {
        if (Test-Path -LiteralPath $stage) { Remove-Item -LiteralPath $stage }
        if ($placed -and (Test-Path -LiteralPath $destination)) { Remove-Item -LiteralPath $destination }
        foreach ($old in $originals) { Copy-Item -LiteralPath (Child-Path $backup ('originals\'+$old.name)) -Destination (Child-Path $modsRoot $old.name) -Force }
        throw
    }
    Write-Host ''
    Write-Host '설치 완료! Modrinth에서 선택한 코블버스 프로필을 실행하세요.' -ForegroundColor Green
    Write-Host '새 리그 대전은 서버에도 같은 애드온이 설치되어 있어야 나타납니다.'
    Write-Host ('이전 파일 백업: '+$backup)
    exit 0
} catch {
    Write-Host ''
    Write-Host ('설치 중단: '+$_.Exception.Message) -ForegroundColor Red
    exit 1
}
