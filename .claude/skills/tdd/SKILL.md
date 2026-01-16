---
name: tdd
allowed-tools:
  - Read
  - Glob
  - Grep
  - Bash(./gradlew test *)
  - Write
  - Edit
  - Task
  - Skill
description: TDD 전체 사이클 오케스트레이터 (Red → Green → Refactor)
---

## Context

- 프로젝트 아키텍처: 헥사고날 (Ports & Adapters)
- 테스트 프레임워크: JUnit 5 + Mockito (BDD) + AssertJ
- 현재 브랜치: !`git branch --show-current`
- 최근 테스트 상태: !`./gradlew test --info 2>&1 | tail -10`

## 사용법

### 전체 TDD 사이클
```
/tdd <대상클래스>.<메서드명> [테스트유형]
```

### 예시
```
/tdd SummonerService.deleteSummoner domain
/tdd MatchController.getMatchHistory restdocs
/tdd LeaguePersistenceAdapter.findByPuuid adapter
```

### 테스트 유형
| 유형 | 설명 | 테스트 위치 |
|------|------|------------|
| `domain` | 도메인 서비스 (기본값) | `module/core/.../application/` |
| `restdocs` | RestDocs 컨트롤러 | `module/infra/api/.../docs/` |
| `adapter` | 어댑터 통합 테스트 | `module/infra/persistence/.../` |

## TDD 사이클

```
┌─────────────────────────────────────────────────────────────┐
│                     TDD Cycle                               │
│                                                             │
│    ┌───────┐      ┌───────┐      ┌──────────┐              │
│    │  RED  │ ───> │ GREEN │ ───> │ REFACTOR │ ─┐           │
│    └───────┘      └───────┘      └──────────┘  │           │
│        ^                                        │           │
│        └────────────────────────────────────────┘           │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### 1. Red 단계 (/tdd-red)
- 실패하는 테스트 먼저 작성
- 테스트 실행하여 실패 확인
- 컴파일 에러 또는 assertion 실패

### 2. Green 단계 (/tdd-green)
- 최소한의 구현으로 테스트 통과
- 과도한 구현 금지
- 테스트가 요구하는 것만 구현

### 3. Refactor 단계 (/tdd-refactor)
- 코드 품질 개선
- 테스트 통과 상태 유지
- 중복 제거, 명확한 이름, 구조 개선

## Your task

사용자의 요청을 분석하여 TDD 사이클을 진행합니다:

### 파라미터가 있는 경우 (새 기능 개발)
1. **Red 단계 실행**
   - `/tdd-red <대상>.<메서드> <유형>` 스킬 호출
   - 실패하는 테스트 작성 및 확인

2. **Green 단계 실행**
   - `/tdd-green` 스킬 호출
   - 최소 구현으로 테스트 통과

3. **Refactor 단계 실행**
   - `/tdd-refactor` 스킬 호출
   - 코드 품질 개선

4. **사이클 완료 보고**
   - 생성된 테스트 요약
   - 구현된 코드 요약
   - 최종 테스트 결과

### 파라미터가 없는 경우 (상태 확인)
- 현재 테스트 상태 출력
- 실패한 테스트가 있으면 Green 단계 권장
- 모두 통과하면 다음 기능 개발 권장

## 헥사고날 아키텍처 가이드

### 도메인 서비스 개발
```
1. Port 인터페이스 정의 (out port)
2. Service에서 Port 사용
3. Adapter에서 Port 구현
```

### 컨트롤러 개발
```
1. UseCase 인터페이스 정의 (in port)
2. Service에서 UseCase 구현
3. Controller에서 UseCase 호출
```

## 유용한 명령어

```bash
# 전체 테스트
./gradlew test

# 특정 테스트 클래스
./gradlew test --tests "SummonerServiceTest"

# 특정 테스트 메서드
./gradlew test --tests "SummonerServiceTest.deleteSummoner*"

# 커버리지 리포트
./gradlew test jacocoTestReport
```

## Ralph Loop 연계

TDD 사이클을 반복 실행하려면:
```
/ralph-loop "테스트 커버리지 80% 달성" --maxiterations 15
```
