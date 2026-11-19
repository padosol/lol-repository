# league 재절단 — 헥사고날 1단계

> **[결정]** 사용자가 정함 · **[판단]** 설계자가 정함(확인 필요) · 기준 develop `97d0ae1`

## 요약

league 테이블은 summoner가 쓰고 있으니 summoner로 옮기고, 남은 league는 `ranking`으로 바꾼다. 이와 함께 잘못 놓인 로직 4개를 제자리로 옮기고, ArchUnit으로 그 상태를 고정한다. 동작은 바꾸지 않는다.

## 왜

- league 테이블(`league`, `league_summoner`)에 쓰는 곳은 summoner의 `SaveSummonerDataUseCase` 하나뿐이다. league 모듈이 실제로 가진 것은 랭킹·커트라인 테이블뿐이다.
- 그래서 랭킹 계산은 summoner와 match를 직접 써야 하고, 그 때문에 infra에 머물러 있다. 지금 구조에서 league로 옮기면 summoner와 순환 의존이 생긴다.

## 바뀌는 것 (PR 5개, 순서대로)

| PR | 내용 | 크기 |
|---|---|---|
| 1 | 평균 티어 계산을 persistence `MatchService`에서 match 도메인 `MatchTierSnapshot`으로 옮긴다 | S~M |
| 2 | 갱신 단계 3개(계정·리그·소환사)가 `RiotApiService` 대신 `SummonerApiPort`를 쓰게 한다 | M |
| 3 | league 재절단: `League`·`LeagueSummoner`와 리그 포트는 summoner로, 나머지는 `ranking` 모듈로 옮긴다 | M |
| 4 | 랭킹 계산(infra `SummonerRankingCalculationService`)을 ranking application으로 옮긴다. 스케줄러는 호출만 한다 | M |
| 5 | ArchUnit 가드: 컨텍스트끼리는 port.in·domain·readmodel로만 의존하고, infra는 UseCase를 구현하지 않는다 | S |

**모듈 의존:** 지금은 match → league, summoner → league이다. 바뀐 뒤에는 match → summoner, ranking → summoner·match가 된다. 순환은 없다.

## 바뀌지 않는 것

- API 응답, Redis 키, 테이블(마이그레이션 없음)
- 평균 티어 값. #146의 `tier_bucket`이 이 값에 의존한다.
- Riot 호출 횟수·우선순위, 갱신 실패 분류
- 어댑터 위치(`module/infra/*`)

## 이번에 하지 않는 것 [결정]

- 어댑터를 컨텍스트 안으로 옮기기, support 모듈, riot-dto, infra 삭제. 모두 다음 스펙에서 한다.
- 배경 매치 적재의 ack. MP-187에서 다룬다.

## 확인해 주실 판단 [판단]

1. 모듈과 패키지 이름을 `league`에서 `ranking`으로 바꾼다.
2. 호출하는 곳이 없는 `CalculateSummonerRankingUseCase`·`SaveTierCutoffUseCase`는 지운다.
3. `SummonerCollectionPublishPort`를 둘로 나눈다. 지금 한 포트에 쓰는 쪽이 다른 두 발행이 섞여 있다.
   - 갱신 요청 발행: summoner가 쓴다.
   - 티어 수집 발행: ranking이 쓴다.
4. duo 프로필·spectator의 persistence 교차 조회 2곳은 이번에 고치지 않고 ArchUnit 기준선에만 기록한다.
5. 평균 티어는 지금 규칙을 그대로 옮긴다. challenges가 없는 참가자도 평균에 포함하고, `(int)`로 소수점을 버린다.

---

새 입구 이름, 패키지 위치, 테스트 목록 같은 세부는 실행 계획에 적는다. 근거와 줄 번호는 이 문서의 이전 판(`320d07e`)에 있다.
