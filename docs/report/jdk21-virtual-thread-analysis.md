# JDK 21 + Virtual Thread 도입 타당성 분석

> 작성일: 2026-03-13

## 1. 현재 동시성 아키텍처

LOL Repository는 Java 17 + Spring Boot 3.3.1 기반으로, RIOT API 호출과 DB 배치 작업을
**CompletableFuture + 4개 커스텀 ThreadPoolTaskExecutor** 및 **RabbitMQ 리스너 26개 컨슈머 스레드**로 처리하고 있다.

### 스레드 풀 구성

| 구성 요소 | 스레드 수 | 용도 |
|-----------|----------|------|
| `requestExecutor` | core=20, max=40, queue=100 | 일반 RIOT API 호출 |
| `riotApiExecutor` | core=20, max=40, queue=40 | MatchListener용 API 호출 |
| `matchFindExecutor` | core=5, max=10, queue=20 | 매치 ID 검색 |
| `timelineSaveExecutor` | core=10, max=10, queue=0 (CallerRunsPolicy) | DB 병렬 벌크 저장 |
| RabbitMQ 컨슈머 | 1 + 5 + 20 = 26 | 메시지 처리 |
| Tomcat | ~200 (기본값) | HTTP 요청 |
| **추정 총 플랫폼 스레드** | **~300-400** | |

### 주요 블로킹 패턴

- `.join()` 15곳+ — CompletableFuture 결과 대기
- `globalApiRateLimiter.acquire(1)` — Redisson RateLimiter 블로킹
- `RLock.tryLock()` — 분산 락 대기
- `RetryInterceptor` — `Thread.sleep(2000)` 2초 backoff
- 동기 JDBC 배치 작업 — pgjdbc 블로킹 I/O

---

## 2. Virtual Thread 이점 분석

### HIGH 이점

| 패턴 | 현재 상태 | Virtual Thread 적용 시 |
|------|----------|----------------------|
| **RIOT API 호출** (RiotApiService 11개 메서드) | `supplyAsync()` + executor로 플랫폼 스레드 점유하며 HTTP I/O 대기 | JDK 21의 `java.net.http.HttpClient`는 VT 친화적. carrier thread가 I/O 대기 중 해제됨 |
| **MatchListener** (20 컨슈머) | 각각 rate limiter 블로킹 + HTTP + `.join()` | 20개 플랫폼 스레드 → VT로 전환, carrier thread 재활용 가능 |
| **RetryInterceptor** 2초 backoff | `Thread.sleep(2000)`으로 플랫폼 스레드 낭비 | VT의 `Thread.sleep()`은 carrier에서 즉시 unmount |

### MODERATE 이점

| 패턴 | 비고 |
|------|------|
| **SummonerRenewalService** 복합 Future 체인 | `.join()` 5곳에서 블로킹하지만 컨슈머 1개라 절대적 이점은 작음 |
| **Tomcat 요청 스레드** | `SummonerService`, `SpectatorService` 등에서 `.join()` 호출 시 carrier 해제 |

### 이점 없음 / 위험

| 패턴 | 이유 |
|------|------|
| **TimeLineService 병렬 벌크 저장** (10개 Future) | pgjdbc의 `synchronized` 블록 → carrier thread **핀닝** 발생, 오히려 악화 가능 |
| **MatchService.addAllMatch()** | 단일 `@Transactional`, 순차 실행 → 병렬성 없음 |
| **SummonerRankingScheduler** | 단일 스레드 스케줄 작업 → 이점 없음 |
| **RedisLockHandler.acquireLock()** | `tryLock(0, ...)` 이미 논블로킹 |

---

## 3. 핵심 호환성 이슈

### 3.1 pgjdbc `synchronized` 핀닝 (위험도: HIGH)

- PostgreSQL JDBC 드라이버가 `PgConnection`, `QueryExecutorImpl` 등에서 `synchronized` 사용
- VT가 `synchronized` 블록 안에서 I/O 블로킹 시 carrier thread 핀닝
- **`timelineSaveExecutor`를 VT로 전환하면 안 됨**

### 3.2 HikariCP 커넥션 풀 고갈 (위험도: MODERATE)

- 현재 max 15개 커넥션
- VT는 무제한 생성 가능 → 커넥션 풀 경합 폭증 위험
- DB 접근 경로에 `Semaphore` 등 동시성 제한 필요

### 3.3 Redisson/Netty 핀닝 (위험도: MODERATE)

- Redisson 3.46.0은 대부분 `ReentrantLock` 전환 완료
- 단, Netty 내부 `synchronized` 잔존 → `acquire(1)` 블로킹 시 짧은 핀닝 가능
- `-Djdk.tracePinnedThreads=short`로 테스트 필요

### 3.4 Spring Boot 3.3.1 호환성 (위험도: LOW)

- Spring Boot 3.2+에서 `spring.threads.virtual.enabled=true` 공식 지원
- **Spring Boot 업그레이드 불필요** — 현재 3.3.1로 충분

---

## 4. 최종 결론

### JDK 21 업그레이드: 강력 추천 (VT 무관)

- 더 나은 GC 성능 (G1/ZGC 개선)
- Pattern matching, Record patterns, Sequenced Collections 등 언어 기능
- 2029년+까지 LTS 지원

### Virtual Thread 적용: 제한적 이점 (리소스 효율성 개선)

> **이 애플리케이션의 처리량은 스레드 풀이 아닌 RIOT API Rate Limiter(20 req/sec)에 의해 제한된다.**
> Virtual Thread는 이 병목을 해결하지 못한다.

**실질적 이점:**
- ~80-100개 플랫폼 스레드가 I/O 대기 중 낭비되는 것 방지 (리소스 효율성)
- 스레드 풀 사이징 튜닝 부담 감소
- Rate Limit이 향후 증가할 경우 스레드 풀 병목 없이 자연스럽게 확장

**한계:**
- 현재 워크로드에서 **처리량(throughput) 향상은 없음** — rate limiter가 병목
- DB 작업 경로에는 적용 불가 (pgjdbc 핀닝)
- Redisson 블로킹 호출에서 핀닝 가능성 존재

---

## 5. 권장 마이그레이션 단계

### Phase 0: JDK 21 업그레이드 (Virtual Thread 없이)

| 파일 | 변경 내용 |
|------|----------|
| `build.gradle` | `JavaLanguageVersion.of(17)` → `of(21)` |
| `docker/Dockerfile` | `eclipse-temurin:17-jdk/jre` → `21-jdk/jre`, JVM 핀닝 진단 플래그 추가 |
| `.github/workflows/ci.yml` | `java-version: 17` → `21` |

검증: `./gradlew build` 성공, `./gradlew :app:bootRun` 정상 기동

### Phase 1: Tomcat Virtual Thread 활성화

| 파일 | 변경 내용 |
|------|----------|
| `app/src/main/resources/application.yml` | `spring.threads.virtual.enabled: true` 추가 |

효과: API 엔드포인트(SummonerService 등)의 `.join()` 호출이 carrier thread 미점유

### Phase 2: RIOT API Executor를 Virtual Thread로 전환

| 파일 | 변경 내용 |
|------|----------|
| `infra/riot-client/.../config/AsyncConfig.java` | `requestExecutor`, `matchFindExecutor`, `riotApiExecutor` 3개를 `Executors.newVirtualThreadPerTaskExecutor()`로 교체. **`timelineSaveExecutor`는 기존 유지** (DB 핀닝 방지) |

### Phase 3: RabbitMQ 리스너 Virtual Thread 전환

| 파일 | 변경 내용 |
|------|----------|
| `infra/rabbitmq/.../config/RabbitMqConfig.java` | 리스너 컨테이너 팩토리에 VT task executor 설정 |

모니터링: `-Djdk.tracePinnedThreads=short`로 핀닝 확인

### Phase 4 (보류): StructuredTaskScope 적용

JDK 25 LTS에서 정식 출시 시 CompletableFuture 체인 단순화 검토

---

## 6. Before/After 예상 스레드 리소스

| 구성 요소 | Before (플랫폼 스레드) | After (VT 적용) |
|-----------|----------------------|-----------------|
| `requestExecutor` | 20~40개 | 0개 (VT executor) |
| `riotApiExecutor` | 20~40개 | 0개 (VT executor) |
| `matchFindExecutor` | 5~10개 | 0개 (VT executor) |
| `timelineSaveExecutor` | 10개 | 10개 유지 (DB 핀닝) |
| RabbitMQ 컨슈머 | 26개 | 0개 (VT 전환) |
| Tomcat | ~200개 | 0개 (VT 전환) |
| **합계** | **~280-330개** | **~10개 + carrier(CPU 코어 수)** |

---

## 7. 검증 및 모니터링 방법

### 검증 체크리스트

1. **Phase 0**: `./gradlew build` 성공, `./gradlew :app:bootRun` 정상 기동
2. **Phase 1-3**: `-Djdk.tracePinnedThreads=short` JVM 플래그로 핀닝 모니터링
3. **스레드 덤프 비교**: 업그레이드 전후 `jstack` 또는 Actuator `/threaddump`로 플랫폼 스레드 수 비교
4. **부하 테스트**: RabbitMQ에 메시지 대량 투입 후 처리량/응답시간 비교
5. **Hikari 모니터링**: Actuator의 HikariCP 메트릭으로 커넥션 풀 사용량 확인

### 모니터링 도구

1. **VisualVM / JConsole** (로컬 개발): 실시간 스레드 수, 이름, CPU 그래프 확인
2. **Actuator 메트릭** (간편 비교):
   - `GET /actuator/metrics/jvm.threads.live` — 활성 스레드 수
   - `GET /actuator/metrics/jvm.threads.peak` — 피크 스레드 수
3. **jstack 스레드 덤프** (직관적 비교):
   - Before: `Request Thread-*`, `Riot API Thread-*`, `MatchFind Thread-*` 등 이름으로 각 풀 확인
   - After: 해당 이름 사라지고 `VirtualThread` 형태로 전환 확인
4. **Prometheus + Grafana** (운영 환경): `jvm_threads_live_threads` 메트릭 시계열 대시보드
5. **핀닝 모니터링**: `-Djdk.tracePinnedThreads=short` JVM 플래그로 핀닝 스택트레이스 출력

### 모니터링 환경 실행

```bash
# 1. 인프라 실행 (기존)
docker compose -f docker/docker-compose.yml up -d

# 2. 모니터링 실행
export PROMETHEUS_SCRAPE_HOST_IP="$(hostname -I | awk '{print $1}')"
docker compose -f docker/monitoring/docker-compose.monitoring.yml up -d

# 3. 앱 실행
./gradlew :app:bootRun -Dspring.profiles.active=local

# 4. 대시보드 확인
# Grafana: http://localhost:3010 (admin/admin)
# Prometheus: http://localhost:9090
# Targets: http://localhost:9090/targets
```

WSL 우분투에서는 `host.docker.internal` 대신 `PROMETHEUS_SCRAPE_HOST_IP`에
현재 WSL 배포판 IP를 넣고, Prometheus가 `wsl-app-host:8111/actuator/prometheus`를 읽는다.
WSL 재시작이나 네트워크 변경 후에는 IP가 바뀔 수 있으므로 값을 다시 갱신해야 한다.
