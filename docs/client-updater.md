# 클라이언트 업데이터와 CobbleSpire

본체 0.8.2 / CobbleSynergy 0.4.2부터 `cobbleleague`, `cobblesynergy`, 선택적인 `cobblespire`를 같은 트랜잭션으로 설치한다. Minecraft 1.21.1, Cobblemon 1.7.3, Java 21, Fabric Loader 0.17.2 이상이 있는 COBBLEVERSE 1.7.42 클라이언트용이다. 실행 중인 게임은 정상 종료를 기다리고 서버 폴더에는 설치하지 않는다. Java/Loader 버전은 설치기의 대상 환경 계약이며 실행 중 런타임을 별도로 탐지하는 기능은 없다.

## 기존 사용자 전환

기존 두 모드 전용 helper와 게임 내 감지는 세 모드 manifest를 거부한다. 기존 `latest` 자산을 세 모드로 바꾸는 것만으로 기존 설치기를 자동 전환할 수 없다. 이번 전환은 **새 ZIP을 한 번 직접 실행하는 방식**이다.

1. 게임을 완전히 종료하고 새 ZIP 전체를 새 폴더에 압축 해제한다.
2. 새 폴더의 `설치.bat`를 실행해 기존 Modrinth COBBLEVERSE 프로필을 선택한다. ZIP의 JAR만 수동 복사하면 helper가 갱신되지 않는다.
3. 설치기가 JAR 검증·백업·교체 후 `.cobbleleague-updater/client-updater.ps1`, manifest, payload, 실행 배치를 갱신한다. 기존 `enabled:false`는 유지한다.
4. 이후 바탕화면 `CobbleLeague-PLAY.bat` 또는 Modrinth Play로 같은 프로필을 실행한다. 게임 내 감지는 새 본체에서 수행한다.
5. 게임 종료 후 ZIP의 `되돌리기.bat`는 마지막 JAR 트랜잭션 직전 상태를 복구한다. 처음 추가한 Spire도 제거하고 원래 없던 상태로 돌린다. helper와 채널 설정은 새 버전으로 유지된다. 자동 업데이트를 중지하려면 `auto-update.json`의 `enabled`를 false로 설정한다.

이전 설치기는 기존 두 모드 안정 manifest를 계속 사용할 수 있다. 자동 전환용 구버전 실행 코드 다운로드는 추가하지 않았다. 배포자는 기존 안정 manifest를 유지하면서 전환 ZIP을 별도로 안내할 수 있다. 서버/클라이언트 조합의 실제 게임 호환 확인과 운영 교체는 별도의 배포 단계다.

## 채널과 패키지 제작

기본 `python scripts/package_friends.py`는 본체/시너지 두 모드 안정 번들을 만듭니다. 정식 버전 `CobbleSpire`(`0.6.1` 등 `-dev` 없는 SemVer)가 준비된 통합 릴리스에서는 `--spire-jar <경로> --channel stable`로 세 모드 정식 번들(`CobbleLeague-friends-0.8.2.zip`)을 만들며, GitHub Actions `.github/workflows/release.yml`도 검증된 `cobblespire-0.6.1.jar`를 포함해 `v0.8.2` 정식 릴리스에 함께 게시합니다. 개발 JAR이 안정 번들에 들어가면 제작이 실패합니다.

현재 확인한 정식 CobbleSpire 산출물은 별도 `slaythecobblebus` 프로젝트의 `build/libs/cobblespire-0.6.1.jar`다. 이 파일 이름을 최신으로 간주해 자동 선택하지 않는다. 배포자가 검증한 JAR을 명시한다.

```powershell
.\gradlew.bat --no-daemon test build
.\gradlew.bat -p server-addons/cobble-synergy "-Pcobbleleague_jar=../../build/libs/cobbleleague-0.8.2.jar" --no-daemon test build
python scripts/package_friends.py --spire-jar ../slaythecobblebus/build/libs/cobblespire-0.6.1.jar --channel stable
python tests/test_client_updater.py
```

개발(`-dev`) 테스트용 번들은 `--channel development`로 만들며, 해당 배치는 `-Offline` / `offlineBundle:true`로 고정되어 GitHub `latest`를 조회하지 않습니다. 안정 정식 번들(`--channel stable`)은 GitHub `/releases/latest`의 정식 릴리스만 확인하며 각 모드의 다운그레이드는 허용하지 않습니다.

패키지는 JAR의 실제 Fabric ID/버전, 시너지의 본체 의존성·test=false, Spire의 검토된 의존성을 확인한다. `artifact-provenance.json`에 소스 저장소 URL, 의존성, SHA-256을 기록하고 MIT 라이선스를 함께 포함한다. 검토된 Spire 의존성이 달라지면 패키징이 실패하므로 새 조합을 먼저 검토한다. 외부 소스 프로젝트는 수정하지 않는다.

## Manifest와 검증

schema 1의 기존 두 모드 manifest도 계속 지원한다. 두 모드 manifest는 설치된 Spire를 삭제·백업·복구 대상으로 삼지 않는다. 세 모드 정식 예시는 생성된 `dist/client-release.json`에 있다.

```json
{
  "schema": 1,
  "release": "0.8.2",
  "channel": "stable",
  "minecraft": "1.21.1",
  "cobblemon": "1.7.3",
  "mods": [
    {"id":"cobbleleague","version":"0.8.2","filename":"cobbleleague-0.8.2.jar","sha256":"<64 lowercase hex>"},
    {"id":"cobblesynergy","version":"0.4.2","filename":"cobblesynergy-0.4.2.jar","sha256":"<64 lowercase hex>"},
    {"id":"cobblespire","version":"0.6.1","filename":"cobblespire-0.6.1.jar","sha256":"<64 lowercase hex>"}
  ]
}
```

`release`는 표시용이며 실제 변경 감지는 모드별 버전과 SHA-256으로 한다. Spire만 버전이 오르거나 같은 버전으로 JAR 내용이 바뀌어도 감지하며 설치 후 동일 해시에서는 재시작하지 않는다. 어느 모드든 이미 더 높은 버전이면 전체 계획을 거부하여 반복 재시작을 방지한다. prerelease 순서도 비교한다.

모든 파일을 staging에 받은 뒤 해시, 실제 메타데이터, 의존 버전, 중복 ID/파일명, 경로를 검사한다. 원래 JAR은 실제 모드 ID로 찾아 중복·변경된 파일명도 처리한다. 관계없는 모드와 `config`, `world`, `saves`는 보존한다. 설치/복구 도중 실패하면 해당 트랜잭션의 파일 상태를 원복한다. 설치 뒤 파일이 바뀌면 자동 복구는 중단한다.

`tests/test_client_updater.py`는 실제 Windows PowerShell helper를 폐기 가능한 `.test-tmp` 프로필에서 실행한다. 세 번째 파일 손상·의존성 오류, 배치 중간 실패, 복구 중간 실패, 프로세스 대기, 구 helper 전환을 포함한다. Kotlin 단위 테스트는 게임 내 선택 정책과 반복 루프 방지를 검사한다. 실제 Minecraft UI·Modrinth 재실행·서버 접속은 별도 수동 검증 항목이다.
