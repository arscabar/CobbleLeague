"""Package an already built CobbleLeague JAR with the Windows profile installer."""
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
import hashlib
import json
import re
import shutil

ROOT = Path(__file__).resolve().parents[1]
match = re.search(r"^version\s*=\s*'([^']+)'", (ROOT / 'build.gradle').read_text(encoding='utf-8'), re.MULTILINE)
if not match:
    raise SystemExit('Cannot read the CobbleLeague version from build.gradle')
version = match.group(1)
jar = ROOT / 'build/libs' / f'cobbleleague-{version}.jar'
if not jar.is_file():
    raise SystemExit('Build CobbleLeague before packaging: gradlew.bat test build')
with ZipFile(jar) as archive:
    metadata = json.loads(archive.read('fabric.mod.json'))
    if metadata['id'] != 'cobbleleague' or metadata['version'] != version:
        raise SystemExit('Built mod metadata does not match build.gradle')
package = ROOT / 'dist' / f'cobbleleague-friends-{version}'
(package / 'payload').mkdir(parents=True, exist_ok=True)
for entry in (ROOT / 'installer').iterdir():
    if entry.is_file():
        shutil.copy2(entry, package / entry.name)
shutil.copy2(jar, package / 'payload' / jar.name)
shutil.copy2(ROOT / 'LICENSE', package / 'LICENSE-CobbleLeague.txt')
manifest = {
    'name': 'CobbleLeague friends installer',
    'version': version,
    'clientVersion': version,
    'validatedServerVersion': version,
    'payload': f'payload/{jar.name}',
    'sha256': hashlib.sha256(jar.read_bytes()).hexdigest(),
}
(package / 'package.json').write_text(json.dumps(manifest, indent=2), encoding='utf-8')
(package / '사용방법.txt').write_text(
    f'CobbleLeague {version}\n\n'
    '게임을 완전히 종료하고 ZIP 전체를 압축 해제한 뒤 설치.bat를 실행하세요.\n'
    'Modrinth의 COBBLEVERSE 1.7.42 프로필을 선택한 뒤 다시 실행합니다.\n'
    '복구할 때는 게임 종료 후 되돌리기.bat를 실행하세요.\n'
    '던전은 F4, 기본 파티 순서와 선봉은 C키로 관리합니다.\n'
    '관장전과 보상 확인은 모든 참가자가 완료하면 함께 진행됩니다.\n',
    encoding='utf-8-sig',
)
output = ROOT / 'dist' / f'CobbleLeague-friends-{version}.zip'
with ZipFile(output, 'w', ZIP_DEFLATED) as archive:
    for entry in sorted(package.rglob('*')):
        if entry.is_file():
            archive.write(entry, entry.relative_to(package).as_posix())
with ZipFile(output) as archive:
    if archive.testzip() is not None:
        raise SystemExit('ZIP integrity check failed')
print(output)
