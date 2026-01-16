---
name: test-run
allowed-tools:
  - Bash(./gradlew test *)
  - Bash(./gradlew clean *)
  - Bash(./gradlew :module:*)
description: 테스트 실행 및 결과 확인 유틸리티
---

## Context

- 프로젝트 루트: !`pwd`
- 최근 테스트 결과 요약: !`./gradlew test --info 2>&1 | tail -15`

## 사용법

### 전체 테스트
```
/test-run all
```

### 특정 테스트 클래스
```
/test-run SummonerServiceTest
/test-run ChampionControllerTest
```

### 특정 모듈
```
/test-run module:core        # 도메인 모듈
/test-run module:api         # API 모듈
/test-run module:postgresql  # 영속성 모듈
/test-run module:redis       # 캐시 모듈
/test-run module:client      # 클라이언트 모듈
```

### 커버리지 확인
```
/test-run coverage
```

## 모듈별 Gradle 명령

| 모듈 | 명령어 |
|------|--------|
| core | `./gradlew :module:core:lol-server-domain:test` |
| api | `./gradlew :module:infra:api:test` |
| postgresql | `./gradlew :module:infra:persistence:postgresql:test` |
| redis | `./gradlew :module:infra:persistence:redis:test` |
| client | `./gradlew :module:infra:client:lol-repository:test` |
| all | `./gradlew test` |
| coverage | `./gradlew test jacocoTestReport` |

## Your task

사용자의 요청에 따라 적절한 테스트 명령을 실행합니다:

1. **요청 파싱**
   - `all`: 전체 테스트 실행
   - `module:{name}`: 해당 모듈 테스트만 실행
   - `{TestClassName}`: 특정 테스트 클래스 실행
   - `coverage`: 커버리지 리포트 생성

2. **명령 실행**
   - 해당하는 Gradle 명령 실행
   - 실패 시 상세 로그 출력

3. **결과 요약**
   - 성공/실패 테스트 수
   - 실패한 테스트 목록 (있는 경우)
   - 실행 시간

## 실행 예시

```bash
# 전체 테스트
./gradlew test

# 특정 클래스 테스트
./gradlew test --tests "SummonerServiceTest"

# 모듈별 테스트
./gradlew :module:core:lol-server-domain:test

# 커버리지 리포트
./gradlew test jacocoTestReport
```
