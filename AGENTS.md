# Wild Fortune 작업 지침

- 먼저 `STATUS.md`와 `git status --short`를 확인하고, 이번 작업과 관련된 파일·변경분만 읽는다. 긴 대화나 README의 과거 기록을 매번 재탐색하지 않는다.
- 구현은 `src/main/`, 실제 서버 테스트는 `src/gametest/`, 설정 예제는 `config/wildfortune.example.json`에 있다.
- 현재 기능·버전·확률·검증 범위는 `STATUS.md`를 기준으로 확인한다. 기본값을 바꾸면 파서의 생략값, 설정 예제, 확률표 테스트도 함께 맞춘다.
- 기존 설정 파일과 장비·몬스터의 저장된 추첨 결과를 보존한다. 이미 평범한 장비도 재추첨하지 않는다.
- 문서·Git 정리만 하는 작업은 빌드·게임 실행·ZIP 재생성을 하지 않는다. 설정값만 바꾸는 작업은 재빌드가 필요 없으며, 사용자 요청에 따라 reload 또는 재시작으로 적용한다.
- 코드 변경은 필요한 범위에서 검증한다. `build`는 서버 GameTest도 실행하므로 통과 후 변경이나 실패가 없으면 반복하지 않는다. 큰 로그는 파일로 남기고 결과·실패 원인만 읽는다.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\dev.ps1 build --offline --no-daemon
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\dev.ps1 runClient --offline --no-daemon
```

- `dev.ps1`은 로컬 `.tools/`의 JDK 25와 Gradle 캐시를 사용한다. 새 환경 준비는 README의 개발 환경 안내를 따른다.
- 코드 수정 후 게임을 켤 때는 수정된 모드가 적용된 게임만 실행한다. 이미 켜져 있다면 정상 저장·종료 후 재실행하며, 사용자가 재시작하지 말라고 한 경우 그 지시를 따른다.
- 게임 내부 조작·플레이·렌더링 테스트·검증용 Java 에이전트 연결은 하지 않는다. 서버 GameTest는 사용할 수 있다. 미검증 사항은 확인했다고 보고하지 않는다.
- 배포 ZIP은 배포 요청을 받은 때에 만든다. `mods/`의 모드·Fabric API, 간단한 설치 안내, API 라이선스만 포함한다. 설정 파일은 첫 실행에 자동 생성하므로 ZIP에 넣지 않는다.
- `build/`, `run/`, `.tools/`, `.downloads/`, `release/`, `verification/` 및 과거 설치 묶음은 추적하지 않는다. 실제 테스트 소스와 Gradle Wrapper JAR는 추적한다.
- 의미 있는 기능·버전 변경 후 `STATUS.md`를 짧게 갱신한다. 커밋·원격 연결·업로드는 사용자가 요청한 범위에서 수행한다. 새 작업 브랜치를 만들 때는 `codex/` 접두사를 사용한다.
