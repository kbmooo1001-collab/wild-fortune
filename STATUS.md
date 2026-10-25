# 현재 상태 — Wild Fortune 0.9.3

기준일: 2026-10-25. 로컬 Git의 첫 기준 버전이며 원격 저장소는 연결하지 않았다.

## 환경과 검증

- Minecraft Java 26.3 / Fabric Loader 0.20.1 / Fabric API 0.165.0+26.3.
- Java 25 / Gradle 9.8.2 / Loom 1.19.3. 버전은 `gradle.properties`에 고정한다.
- 최근 서버 GameTest **58개 통과**. 0.9.3 클라이언트 로딩 확인 완료.
- 벽 가림은 클라이언트 렌더링 코드와 대상 메서드 서명을 확인했다. 별도의 게임 내부 자동 렌더링·화면 검증은 완료하지 않았다.
- 빌드는 `AGENTS.md`의 `dev.ps1 build` 명령을 사용한다. 문서·Git 정리만으로 재실행하지 않는다.

## 현재 규칙

- 몬스터: MONSTER 분류의 새 NATURAL·STRUCTURE 생성에 신속·저항·힘·최대 체력 증가를 각각 독립 **35%**로 추첨한다. 기본적으로 위더·엔더 드래곤은 제외한다.
- 최대 체력은 **+25%**이며 새 개체의 현재 체력도 채운다. 저장·재로드로 중첩하거나 회복하지 않는다. 기존 투명화 변이는 한 번 체력 변이로 전환한다.
- 효과 개수에 따라 변이한 / 강화된 / 흉포한 / 재앙의 이름을 사용한다. 한국어·영어와 등급별 색상·굵기·붉은 프리즘을 지원한다.
- 몬스터 이름은 일반 **15칸**, 투명화 상태 **6칸**까지 표시하며 벽 투과 보조 표시를 생략한다. 사용자 지정 이름은 보존한다.
- 장비: 모든 획득 경로에서 미처리 장비를 한 번 추첨한다. 긍정 15종·부정 15종이 각각 독립 **30%**이며 종류별 5계열, 최종 최대 5개 효과다.
- 같은 계열의 긍정·부정 동시 성공은 제거한다. 무효과 결과도 회색 평범한 이름으로 확정하고 재추첨하지 않는다.
- 단일 효과는 개별 이름, 복수 효과는 긍정·부정 개수 조합 이름이다. 전설적인은 푸른빛·흰빛, 재앙의는 붉은빛 프리즘을 사용한다.

## 설정·명령·배포

- 스키마 **3**. 첫 실행에 게임 폴더의 `config/wildfortune.json`을 위 기본값으로 자동 생성한다. 기존 파일·사용자 편집값은 보존한다.
- 개발용 실제 설정은 `run/config/wildfortune.json`이며 Git에서 제외한다. 추적하는 전체 예제는 `config/wildfortune.example.json`이다.
- `/wildfortune chance`: 장비 최종 이름별 확률. 단일 효과는 합산하며 확률 내림차순이다.
- `/wildfortune chance monsters`: 몬스터 이름별 최종 확률 내림차순. 두 확률표는 명령 권한 없이 사용할 수 있다.
- `/wildfortune reload`: 관리자 전용. 새 추첨부터 적용하며 기존 결과·저장 수치는 유지한다.
- 모드 JAR: `build/libs/wild-fortune-0.9.3.jar`. 소스 열람용 `-sources.jar`는 설치하지 않는다.
- 배포 ZIP: `release/Wild-Fortune-0.9.3.zip`. 모드·Fabric API·설치 안내·API 라이선스를 포함하며 별도 config는 넣지 않는다. 생성 파일은 Git에 보관하지 않는다.
- 자체 코드의 공개 배포 라이선스는 아직 정하지 않았다. Fabric API 원본과 라이선스는 배포물에 보존한다.

## 주요 파일

- `config/WildFortuneConfig.java`, `config/ConfigCommands.java`: 설정 기본값·검증·자동 생성·명령어.
- `monster/MonsterMutations.java`, `monster/MonsterChanceTable.java`: 몬스터 변이와 확률표.
- `gear/`: 장비 효과·추첨·획득·확률·실제 능력치.
- `mixin/`, `mixin/client/`: 게임 동작 연결과 이름·프리즘·벽 가림 렌더링.
- 위 Java 경로는 `src/main/java/dev/wildfortune/` 기준이다. 실제 테스트는 `src/gametest/java/dev/wildfortune/`에 있다.
