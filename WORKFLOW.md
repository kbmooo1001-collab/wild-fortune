# 필요한 파일과 검증 범위

이 문서는 파일 위치나 작업 절차가 필요할 때 해당 행·항목만 읽는다.
Java 경로는 `src/main/java/dev/wildfortune/`, 테스트는 `src/gametest/java/dev/wildfortune/` 기준이다.

| 작업 | 먼저 볼 파일 | 필요한 확인 |
| --- | --- | --- |
| 설명·사용법 질문 | 해당 설정이나 문서의 관련 부분 | 소스 변경·빌드·배포 없음 |
| 문서·Git 정리 | 변경한 문서, Git 상태·diff | `git diff --check`, 변경 범위 확인 |
| 실행용 확률·수치 조정 | 실제 인스턴스의 `config/wildfortune.json` | JSON 문법·허용 범위·다른 값 보존 확인. 재빌드 없음 |
| 내장 기본값·설정 형식 | `config/WildFortuneConfig.java`, 설정 예제, `config/WildFortuneConfigGameTest.java` | 생략값·자동 생성·기존 값 보존 확인. 확률 변경이면 관련 확률 테스트도 갱신 |
| 몬스터 추첨·체력·생성 조건 | `monster/MonsterMutations.java`, 필요한 생성 Mixin, `MonsterMutationsGameTest.java` | 해당 코드·테스트 수정 후 `build` 한 번 |
| 장비 추첨·종류 제한 | `gear/GearAffix.java`, `gear/GearAffixes.java`, 관련 장비 테스트 | 허용 종류·상쇄·저장 보존 확인 후 `build` 한 번 |
| 장비 획득 경로 | `gear/GearAcquisition.java`, `config/GearAcquisitionGameTest.java` | 새 경로·미리보기·재추첨 방지 확인 후 `build` 한 번 |
| 확률표·명령 | 해당 `*ChanceTable.java`, `config/ConfigCommands.java`, 관련 명령 테스트 | 확률 합·현재 설정 반영·정렬·권한 확인 후 `build` 한 번 |
| 표시 문구·번역 리소스만 수정 | 해당 언어 JSON, 필요하면 `LocalizationGameTest.java` | JSON·번역 키·포맷 인자 확인. 필요할 때 JAR 갱신, 전체 테스트는 영향에 따라 판단 |
| 프리즘·거리·벽 가림 코드 | 해당 `mixin/client/` 파일, 필요하면 `gear/PrismaticNames.java` | 컴파일·정확한 연결 서명·관련 테스트. 서버 테스트가 화면을 검증했다고 보고하지 않음 |
| 실제 공격·채굴·장착 동작 | 요청된 동작의 `gear/` 코드·Mixin·관련 테스트 | 직접 영향받는 동작을 검증. 공통 수치·장착 변경은 회귀 범위 확대 |
| 배포 ZIP 요청 | 버전, 현재 JAR, 직전 검증 결과, 설치 안내 | 아래 배포 절차. 같은 코드의 성공한 검증을 불필요하게 반복하지 않음 |

## 명령과 결과 읽기

- Java·Mixin·의존성 등 실행 코드 변경의 기본 검증:
  `powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\dev.ps1 build --offline --no-daemon`
  이 명령이 컴파일·JAR 생성·서버 GameTest를 포함한다. 성공 뒤 코드가 더 바뀌지 않으면 같은 검증을 다시 실행하지 않는다.
- 게임 리소스나 배포 메타데이터만 바뀌고 필요한 검증을 마쳤다면 JAR 갱신에 `powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\dev.ps1 jar sourcesJar --offline --no-daemon`을 사용할 수 있다. 실행 코드 변경의 테스트를 건너뛰는 용도로 쓰지 않는다.
- 게임 실행이 필요한 경우: `powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\dev.ps1 runClient --offline --no-daemon`. 게임 내부 조작이나 추가 테스트 없이 실행한다.
- `dev.ps1`은 `.tools/`의 JDK 25와 Gradle 캐시를 사용한다. 새 환경 준비가 필요한 경우에만 README의 개발 환경 부분을 읽는다.
- 긴 명령 출력은 `verification/`의 작업별 로그로 보관하고 종료 코드·성공/실패 요약만 먼저 확인한다. 실패하면 해당 오류와 주변 부분만 읽고 원인을 따라 범위를 넓힌다.
- 이전 결과는 같은 소스·설정·의존성에만 재사용한다. 관련 변경·실패·새로운 우려가 있으면 필요한 검증을 다시 한다. 예전 통과 기록을 새로 실행한 결과처럼 보고하지 않는다.

## 배포 요청이 있을 때만

1. 버전과 변경분을 확인한다. 현재 코드의 검증이 끝났고 JAR가 최신이면 빌드·테스트를 재실행하지 않는다.
2. `release/Wild-Fortune-버전/mods/`에 모드 JAR와 호환 Fabric API 원본을 넣는다. `-sources.jar`는 넣지 않는다.
3. 짧은 설치 안내와 API 라이선스를 포함한다. 자동 생성되는 `config/wildfortune.json`, 월드, 로그, 캐시는 넣지 않는다.
4. ZIP을 한 번 생성하고 내부 파일·모드 버전·파일 무결성을 확인한다. 기존 버전 ZIP을 임의로 삭제하거나 덮어쓰지 않는다.
5. 로컬 ZIP 생성과 GitHub Release 게시는 별개다. 커밋·푸시·외부 배포는 요청받은 범위에서만 수행한다.

검증 결과는 종료 코드·실행한 범위·제한 사항만 간결하게 보고한다. 수정과 배포가 모두 요청되었으면 중간 버전을 여러 번 포장하지 않는다.
