"""Package matched pairs or explicit offline development triples from built JARs."""
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
import argparse
import hashlib
import json
import re
import shutil

ROOT = Path(__file__).resolve().parents[1]


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--spire-jar', type=Path, help='Explicit built CobbleSpire JAR')
    parser.add_argument('--spire-license', type=Path)
    parser.add_argument('--channel', choices=['stable', 'development'], default='stable')
    args = parser.parse_args()
    version = re.search(r"^version\s*=\s*'([^']+)'", (ROOT/'build.gradle').read_text(), re.M).group(1)
    props = dict(line.split('=', 1) for line in (ROOT/'server-addons/cobble-synergy/gradle.properties').read_text().splitlines() if '=' in line and not line.startswith('#'))
    paths = [ROOT/'build/libs'/f'cobbleleague-{version}.jar', ROOT/'server-addons/cobble-synergy/build/libs'/f'cobblesynergy-{props["mod_version"]}.jar']
    if args.spire_jar:
        paths.append(args.spire_jar.resolve())
    expected = ['cobbleleague', 'cobblesynergy'] + (['cobblespire'] if args.spire_jar else [])
    mods, provenance = [], []
    for path, expected_id in zip(paths, expected):
        with ZipFile(path) as archive:
            meta = json.loads(archive.read('fabric.mod.json'))
            if meta['id'] != expected_id:
                raise ValueError(f'Wrong mod ID: {path.name}')
            if expected_id == 'cobbleleague' and meta['version'] != version:
                raise ValueError('Core JAR version differs from source version')
            if args.channel == 'stable' and '-' in meta['version'].split('+')[0]:
                raise ValueError('Prerelease JARs require --channel development (offline only)')
            if not re.fullmatch(r'[0-9]+(?:\.[0-9]+)*(?:-[0-9A-Za-z.-]+)?(?:\+[0-9A-Za-z.-]+)?', meta['version']):
                raise ValueError('Unsupported version')
            if expected_id == 'cobblesynergy':
                if meta['depends']['cobbleleague'] != version or archive.read('cobblesynergy/build.properties').decode().strip() != 'test=false':
                    raise ValueError('Synergy must be a matched production build')
            if expected_id == 'cobblespire':
                baseline = {'minecraft': '1.21.1', 'java': '>=21', 'fabricloader': '>=0.17.2', 'fabric-api': '*', 'fabric-language-kotlin': '*', 'cobblemon': '>=1.7.3 <1.8'}
                if meta['depends'] != baseline:
                    raise ValueError('CobbleSpire dependencies changed; review compatibility before packaging')
            if not re.fullmatch(r'[A-Za-z0-9_.+-]+\.jar', path.name):
                raise ValueError('Unsafe filename')
            mods.append(dict(id=meta['id'], version=meta['version'], filename=path.name, sha256=digest(path)))
            provenance.append(dict(id=meta['id'], filename=path.name, sha256=digest(path), depends=meta['depends'], source=meta.get('contact', {}).get('sources', 'CobbleLeague integrated source')))
    if len({m['filename'].lower() for m in mods}) != len(mods):
        raise ValueError('Duplicate filename')
    identity = hashlib.sha256(json.dumps(mods, sort_keys=True).encode()).hexdigest()[:12]
    release = version if args.channel == 'stable' else f'{version}-{args.channel}-{identity}'
    manifest = dict(schema=1, release=release, channel=args.channel, minecraft='1.21.1', cobblemon='1.7.3', mods=mods)
    dist = ROOT/'dist'
    package = dist/f'cobbleleague-friends-{release}'
    (package/'payload').mkdir(parents=True, exist_ok=True)
    for path in (ROOT/'installer').iterdir():
        if path.is_file():
            shutil.copy2(path, package/path.name)
    for path in paths:
        shutil.copy2(path, package/'payload'/path.name)
        shutil.copy2(path, dist/path.name)
    for path in (package/'payload').glob('*.jar'):
        if path.name not in {mod['filename'] for mod in mods}:
            path.unlink()
    (package/'client-release.json').write_text(json.dumps(manifest, indent=2), encoding='utf-8')
    shutil.copy2(package/'client-release.json', dist/'client-release.json')
    (package/'artifact-provenance.json').write_text(json.dumps(provenance, indent=2), encoding='utf-8')
    (package/'package.json').write_text(json.dumps(dict(name='CobbleLeague friends installer', version=version, clientVersion=version, validatedServerVersion=version, payload='payload/'+paths[0].name, sha256=mods[0]['sha256']), indent=2), encoding='utf-8')
    shutil.copy2(ROOT/'LICENSE', package/'LICENSE-CobbleLeague.txt')
    shutil.copy2(ROOT/'server-addons/cobble-synergy/UPSTREAM.md', package/'CobbleSynergy-UPSTREAM.md')
    if args.spire_jar:
        license_path = args.spire_license or args.spire_jar.resolve().parents[2]/'LICENSE'
        shutil.copy2(license_path, package/'LICENSE-CobbleSpire.txt')
    offline = ' -Offline' if args.channel == 'development' else ''
    for name, flags in [('설치.bat', '-InstallLauncher'), ('INSTALL.bat', '-InstallLauncher'), ('되돌리기.bat', '-Restore'), ('RESTORE.bat', '-Restore'), ('UPDATE-AND-PLAY.bat', '-WaitForExit -Launch')]:
        (package/name).write_bytes(('@echo off\r\npowershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0client-updater.ps1" '+flags+offline+'\r\nif errorlevel 1 pause\r\n').encode('ascii'))
    (package/'사용방법.txt').write_text(' + '.join(f'{m["id"]} {m["version"]}' for m in mods)+'\n\n게임을 종료하고 전체 ZIP을 풀어 설치.bat를 실행합니다.\n기존 사용자도 새 ZIP의 설치.bat를 한 번 실행해야 두 모드 전용 helper가 교체됩니다.\n개발 번들은 오프라인 고정 채널입니다. 새 개발 ZIP 설치로 갱신하며 GitHub latest를 사용하지 않습니다.\n안정 번들은 GitHub latest의 정식 릴리스만 확인합니다.\n바탕화면 CobbleLeague-PLAY.bat 또는 Modrinth Play로 같은 프로필을 실행합니다.\n실행 중인 게임은 강제로 종료하지 않으며 종료 후에만 파일을 바꿉니다.\n더 높은 설치 버전은 낮추지 않습니다. 복구는 게임 종료 후 되돌리기.bat입니다.\nconfig, world, saves와 다른 모드는 보존합니다.\n', encoding='utf-8-sig')
    output = dist/f'CobbleLeague-friends-{release}.zip'
    with ZipFile(output, 'w', ZIP_DEFLATED) as archive:
        for path in sorted(package.rglob('*')):
            if path.is_file():
                archive.write(path, path.relative_to(package).as_posix())
    with ZipFile(output) as archive:
        if archive.testzip() is not None:
            raise ValueError('Invalid ZIP')
    artifacts = [dist/p.name for p in paths] + [dist/'client-release.json', output]
    (dist/'SHA256SUMS.txt').write_text(''.join(digest(p)+'  '+p.name+'\n' for p in artifacts), encoding='utf-8')
    print(output)


if __name__ == '__main__':
    main()
