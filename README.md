# CobbleLeague

Minecraft 1.21.1 / Fabric / Cobblemon 1.7.3 기반의 대여전, 덱 결정전, 라운드형 던전 모드입니다. 현재 본체 버전은 **0.7.5**, 통신 프로토콜은 **11**입니다.

## 구성

| 경로 | 내용 | 설치 위치 |
| --- | --- | --- |
| 저장소 루트 | CobbleLeague 0.7.5 본체와 클라이언트 UI | 서버와 참가자 클라이언트 |
| `server-addons/league-practice` | 연습장, 전체 파티 분석 전술 AI 1.1.0, 관장전 지원 | 서버 |
| `server-addons/cobble-synergy` | CobbleSynergy 0.3.1 타입 시너지 | 서버와 참가자 클라이언트 |
| `server-addons/league-battle-recorder` | 배틀 기록과 XLSX 내보내기 1.2.0 | 서버 |
| `installer` | Windows 친구용 설치·백업·복원 스크립트 | 배포 패키지 |
| `scripts/package_friends.py` | 빌드한 본체로 친구용 ZIP 생성 | 개발 환경 |
| `examples` | 15HP 던전 설정 예시 | 서버 설정 참고 |

## 주요 기능

- 상대를 보고 R키를 눌러 Quick Teams 저장 덱을 사용하는 대여전·덱 결정전을 신청합니다.
- F4 로비에 2–8명이 모여 전원 대기 완료하면 던전을 시작합니다. 참가자마다 독립 선택방이 배정됩니다.
- 눈앞의 후보 3마리 중 하나를 영입합니다. 파티가 가득 차면 교체 대상을 직접 선택하고, 현재 파티를 유지할 수도 있습니다. 물리 리롤 버튼과 타입·개체값 별점 표시를 제공합니다.
- 던전 임시 파티는 좌측 기본 파티 HUD에 표시됩니다. C키 기본 파티 화면에서 순서와 선봉을 바꿉니다. 도전 종료 시 원래 파티와 위치를 복구합니다.
- 예시 설정은 15HP 시작입니다. 일반전 패배 차감은 4라운드 관장전 이후 2칸, 8라운드 이후 3칸으로 증가합니다. 관장전 패배는 1칸입니다.
- 4·8라운드는 참가자 모두 같은 관장·파티를 상대로 각자의 방에서 레벨 50 싱글배틀을 진행합니다. 모든 전투와 전원의 보상 확인이 끝나면 다음 라운드가 함께 시작됩니다.
- 아이템 상점, 학습 가능한 기술 검색, 개체값·특성 육성, 분류별 뽑기 확률 강화를 제공합니다. 구매 전 변경 결과와 잔액을 확인합니다.
- 관장 승리 보상에 환상권, 전설 후보 공개, 돈, Z크리스탈, 고급 도구와 무료 기술 학습이 포함됩니다.

## 0.7.5 변경점

- 관장전은 **4라운드마다 계속** 나옵니다. 4라운드 4마리, 8라운드부터 6마리이며 모두 레벨 50 싱글입니다. 한 라운드에서는 모든 참가자가 같은 트레이너를 각자 방에서 만납니다.
- 12라운드는 이향·윤진·목호·카르네 중 한 명, 16라운드부터는 난천·성호·단델 중 한 명을 뽑습니다. 20·24라운드 이후도 최상위 챔피언 풀을 재사용합니다. 캐릭터별 테마 파티로 만든 이 던전의 난이도 분류입니다.
- 패배 HP 차감은 **1~4라운드 1 / 5~8라운드 2 / 9~12라운드 3 / 13~16라운드 4 / 17~20라운드 5…**이며, 관장전도 같은 수치를 적용합니다. 기본 HP는 15, 기본 최대 차감은 15입니다. 기존 서버의 `maxLossDamage: 3` 설정은 계속 증가를 원하면 `15`로 변경하세요.
- F4 장비 관리·상점의 **테라피스**에서 포켓몬과 테라타입을 선택하고 **3골드**로 변경합니다. 테라타입은 던전 임시 파티에만 적용되며, 같은 타입·잘못된 타입 요청에는 돈을 쓰지 않습니다. 던전 종료 시 원래 파티의 테라타입을 유지합니다.
- 통합 배포 조합은 CobbleLeague 0.7.5 + CobbleSynergy 0.3.1입니다.

## 0.7.4 변경점

- F4의 **장비·상점**에서 파티 6마리의 장착 도구를 한 번에 확인합니다. 개별 해제·전체 해제·두 포켓몬의 도구 맞교환, 선택 대상의 보관 도구 장착을 제공합니다. 해제한 도구는 해당 던전 보관함으로 돌아갑니다.
- F4 **경기 관전**에서 같은 던전의 진행 중 경기를 고릅니다. 2인 관장전에서도 먼저 전투를 마친 참가자가 상대 방의 관전석으로 이동할 수 있습니다. 자기 경기 중에는 관전할 수 없으며, 관전 종료·대상 경기 종료·다음 라운드·본인 경기 시작 시 복귀합니다.
- CobbleSynergy 0.3.0은 공개 관전 API를 사용합니다. 페어리의 경제 혜택은 제한 시간 명중률 방어와 미스트필드로 교체했습니다. 단계별 설명은 시너지 README와 U 도감에서 확인합니다.

## 플레이어 자동 업데이트

처음 한 번 최신 친구용 ZIP을 풀고 `설치.bat`로 사용할 Modrinth 프로필에 설치합니다. 코블리그와 시너지의 호환 버전, 업데이트 보조 파일이 함께 설치됩니다.

이후에는 **Modrinth의 원래 Play 버튼으로 실행해도** 메인 메뉴에서 GitHub의 최신 안정 릴리스를 확인합니다. 새 버전이 있으면 게임을 정상 종료하고 두 애드온을 함께 검증·백업·교체한 뒤 **같은 Modrinth 프로필로 자동 재실행**합니다. 월드나 서버에서 플레이하는 도중에는 이 재시작을 시작하지 않습니다. `.cobbleleague-updater/auto-update.json`의 `enabled`로 자동 확인을 끌 수 있습니다.

바탕화면 `CobbleLeague-PLAY.bat`는 게임 실행 전에 먼저 업데이트를 확인하는 별도 실행 경로입니다. JAR만 수동 복사한 경우에는 자동 업데이트 보조 파일이 없으므로 처음 한 번 친구용 설치기를 사용해야 합니다. 모드팩 본체 업데이트는 별도이며, 대상은 Minecraft 1.21.1 / Cobblemon 1.7.3입니다.

배포 채널은 **이 저장소의 통합 GitHub Release 하나**입니다. `client-release.json`과 두 JAR을 함께 배포하며, 파일 해시와 실제 모드 ID·버전·의존성 조합을 검증합니다. 임시 다운로드가 실패하면 기존 설치를 보존합니다. 백업과 되돌리기는 프로필 안에서 관리합니다. 서버 자동 업데이트 기능은 포함하지 않습니다.

릴리스 제작자는 본체와 `server-addons/cobble-synergy`를 빌드한 뒤 `python scripts/package_friends.py`를 실행합니다. `v0.7.5`처럼 본체 버전과 같은 태그를 올리면 `.github/workflows/release.yml`이 두 모드와 친구 ZIP을 빌드하고, 필요한 자산이 모두 올라온 후 릴리스를 공개합니다. 별도 Synergy 저장소 수정은 통합 사본에도 반영한 뒤 통합 릴리스로 배포합니다.

참조: [GitHub Releases API](https://docs.github.com/en/rest/releases/releases), [Modrinth 실행 링크 처리](https://github.com/modrinth/code/blob/main/packages/app-lib/src/api/handler.rs). 최신 Modrinth의 고유 인스턴스 ID는 로컬 인스턴스 DB에서 읽어 같은 프로필을 실행합니다.

## 0.7.3 변경점

- 관장전 승리 시 **돈 + 무작위 보상 2개**를 제시하고 하나만 지급합니다. 창을 다시 열어도 후보가 유지되며 선택하지 않은 보상은 받지 않습니다. 선택 전에는 보상 확인 완료를 누를 수 없습니다.
- F4의 **현재 대전만 무효 후 다시 시작**으로 멈춘 전투를 다시 시작할 수 있습니다. 관장전은 본인 전투만, PvP는 해당 전투의 두 참가자가 모두 동의하면 재시작합니다. 다른 방의 전투·라운드·HP·보상에는 영향을 주지 않습니다. `/league restart`로도 같은 요청을 보낼 수 있습니다.
- 전투 시작 후 5초부터 요청할 수 있습니다. 끝난 전투는 대상이 아니며, 무효 처리한 시도는 승패·돈·보상으로 정산하지 않습니다.
- 던전 참가자는 보상 선택 UI가 있는 0.7.5(프로토콜 10)으로 업데이트해야 합니다.

## 0.7.2 변경점

- 상점 44종: 풍선·허브·약점보험·탈출 도구·목스프레이·주사위·휘석·은밀망토·날씨 도구 등 1·2원 선택지 26종과 설명을 추가했습니다.
- 전투 도구의 소모 여부를 가격과 분리했습니다. 발동한 풍선·기합의띠 등은 소모하고, 일반 장비는 유지합니다.
- 리그가 관리하는 PvP·관장전에서 120틱 이상 끊긴 회수·소환 애니메이션 완료를 복구합니다. 활성 포켓몬의 개체가 사라지면 HP·포켓몬 식별자를 유지해 다시 소환합니다. 엔진의 행동 선택이나 턴 대기를 강제로 넘기지 않습니다.
- `/league surrender`는 애니메이션 대기 중에도 즉시 이번 대전을 종료합니다. 종료 시 서버 전투 등록·복사본 상태·클라이언트 전투 화면을 정리합니다.
- 복구 경로에 전투·포켓몬 ID, 개체 제거 여부, 빔 상태 진단 로그를 남깁니다. 사용자에게 발생한 최초 소환 실패의 원인은 기존 로그만으로 확정되지 않았습니다.

## 빌드

JDK 21을 설치하고 `JAVA_HOME`을 설정합니다. 최초 빌드에는 Gradle과 의존성 다운로드를 위한 네트워크 연결이 필요합니다.

Windows PowerShell에서 실행합니다.

```powershell
.\gradlew.bat --no-daemon test build
.\gradlew.bat -p server-addons/league-practice --no-daemon test build
.\gradlew.bat -p server-addons/league-battle-recorder --no-daemon test build
.\gradlew.bat -p server-addons/cobble-synergy "-Pcobbleleague_jar=../../build/libs/cobbleleague-0.7.5.jar" --no-daemon test build
```

Linux와 macOS에서는 같은 명령의 실행 파일을 `./gradlew`로 바꿉니다. 각 프로젝트의 `build/libs/`에 배포용 JAR과 소스 JAR이 생성됩니다. 설치할 때에는 소스 JAR을 제외한 모드 JAR을 사용합니다.

## 실행 환경과 설치

검증한 모드팩은 COBBLEVERSE 1.7.42입니다.

- Minecraft 1.21.1, Java 21, Fabric Loader 0.18.4 이상
- Cobblemon 1.7.3, Fabric API, Fabric Language Kotlin
- 저장 덱 기능을 사용할 클라이언트의 Cobblemon Quick Teams 1.5.5
- 관장전 서버의 이 저장소 `league-practice`와 Run & Bun Cobblemon Trainers AI 호환 설치본 0.15.4-beta
- Run & Bun의 선행 모드인 RCT API, Radical Cobblemon Trainers, Architectury
- 메가진화·Z기술·다이맥스 아이템과 프리셋을 사용할 서버·클라이언트의 Mega Showdown 호환 설치본

일반 참가자는 CobbleLeague 본체를 클라이언트에 설치합니다. 연습장 AI와 배틀 기록 애드온은 서버에 설치합니다. 서버와 클라이언트의 CobbleLeague 버전을 맞춥니다.

관장전은 Run & Bun 연결을, 연습장 NPC는 전체 파티 분석 전술 AI를 사용합니다. 전술 AI는 실제 파티의 능력치·특성·도구·기술을 바탕으로 두 턴의 공격과 교체를 비교합니다. 피해 예측과 지원하는 특수 효과에 근사 계산이 포함됩니다.

새 서버에서 사용할 때에는 `examples/cobbleleague-lobby.json`을 `config/cobbleleague-lobby.json`으로 복사해 15HP 설정을 적용할 수 있습니다. 기존 설정은 내용을 확인한 뒤 원하는 값을 반영합니다.

연습장 기본 팀 27개는 `server-addons/league-practice/config/league-practice/teams`에 있습니다. 해당 `teams` 폴더를 서버의 `config/league-practice/teams`로 복사합니다. 운영자는 게임에서 다음 순서로 위치와 버튼을 등록합니다.

```text
/leaguepractice setspawn
/leaguepractice bind beginner
/leaguepractice bind intermediate
/leaguepractice bind advanced
/leaguepractice bind master
/leaguepractice enable
/leaguepractice validate
```

각 `bind` 명령은 등록할 버튼을 바라본 상태에서 실행합니다. 배틀 기록 영역은 서버에서 생성되는 `config/league-battle-recorder.json`에 설정합니다.

## 친구용 설치 ZIP

본체 빌드 후 Python 3으로 패키지를 만듭니다.

```powershell
python scripts/package_friends.py
python tests/test_installer.py
```

`dist/CobbleLeague-friends-0.7.5.zip`이 생성됩니다. 받은 사람은 게임을 완전히 종료하고 ZIP을 압축 해제한 뒤 `설치.bat`를 실행해 Modrinth의 COBBLEVERSE 프로필을 선택합니다. `되돌리기.bat`로 설치 전 백업을 복원합니다.

## 검증

본체 단위 검사 97개, 연습장 단위 검사 57개와 기존 배틀 기록 검사를 각 프로젝트의 `test` 명령으로 실행할 수 있습니다. 설치 검사에는 Windows PowerShell 5.1이 필요합니다.

배포 전 실제 시험 서버에서 본체 68개 확인 항목과 연습장 전술 AI 20개 시나리오를 검증했습니다. 시험 서버의 6대6 파티 조합과 초기 상태, 전체 실전 승률은 별도의 평가 항목입니다.

## 라이선스

기존 CobbleLeague 배포의 MIT 라이선스 문서는 `LICENSE`에 있습니다. 외부 모드의 배포와 라이선스는 각 프로젝트의 안내를 따릅니다.
