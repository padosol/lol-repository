# VT 전후 비교 테스트 가이드

## 목적

Virtual Thread 적용 전후를 같은 코드베이스에서 비교한다.

- `Baseline`: JDK 21 + VT 비활성
- `Candidate`: JDK 21 + VT 활성

이 서비스는 RIOT API rate limit이 강한 병목이라 처리량 증가보다 다음 항목을 본다.

- 플랫폼 스레드 수 감소
- 대기 중 스레드 점유 감소
- CPU/메모리 사용량 유지 또는 개선
- HTTP/RabbitMQ 오류율 유지
- HikariCP 경합 악화 여부
- pinned thread 발생 여부

## 토글

현재 비교용 토글은 아래 두 개다.

- `LOL_VT_ENABLED`
  - Spring `spring.threads.virtual.enabled`에 연결
  - Servlet 요청 처리 VT on/off
- `LOL_VT_EXECUTORS_ENABLED`
  - `requestExecutor`, `matchFindExecutor`, `riotApiExecutor`, Rabbit listener executor VT on/off
  - 미지정 시 `LOL_VT_ENABLED` 값을 따라감

`timelineSaveExecutor`는 항상 플랫폼 스레드 풀을 유지한다.

## 사전 준비

1. JDK 21 설치
2. 인프라 실행

```bash
export PROMETHEUS_SCRAPE_HOST_IP="$(hostname -I | awk '{print $1}')"
docker compose -f docker/docker-compose.yml up -d
docker compose -f docker/monitoring/docker-compose.monitoring.yml up -d
```

WSL 우분투에서는 `host.docker.internal` 대신 `PROMETHEUS_SCRAPE_HOST_IP`에
현재 WSL 배포판 IP를 넣어 `wsl-app-host:8111`로 수집한다.
WSL 재시작이나 네트워크 변경 후에는 IP가 바뀔 수 있으므로 값을 다시 갱신해야 한다.

3. Actuator 확인

- Prometheus: `http://localhost:9090`
- Grafana: `http://localhost:3010`
- App health: `http://localhost:8080/actuator/health`
- Thread dump: `http://localhost:8080/actuator/threaddump`
- Prometheus targets: `http://localhost:9090/targets`

## 실행 방법

### 1. Baseline 실행

```bash
LOL_VT_ENABLED=false \
LOL_VT_EXECUTORS_ENABLED=false \
./run-local.sh
```

### 2. Candidate 실행

```bash
LOL_VT_ENABLED=true \
LOL_VT_EXECUTORS_ENABLED=true \
JVM_DIAGNOSTIC_OPTS="-Djdk.tracePinnedThreads=short" \
./run-local.sh
```

반복 실행 시 재빌드를 건너뛰려면:

```bash
SKIP_BUILD=true LOL_VT_ENABLED=false LOL_VT_EXECUTORS_ENABLED=false ./run-local.sh
SKIP_BUILD=true LOL_VT_ENABLED=true LOL_VT_EXECUTORS_ENABLED=true JVM_DIAGNOSTIC_OPTS="-Djdk.tracePinnedThreads=short" ./run-local.sh
```

## 측정 항목

### 필수 메트릭

- `jvm_threads_live_threads`
- `jvm_threads_peak_threads`
- `jvm_memory_used_bytes`
- `process_cpu_usage`
- `system_cpu_usage`
- `hikaricp_connections_active`
- `hikaricp_connections_pending`
- `http_server_requests_seconds`

### 추가 확인

- RabbitMQ queue depth
- RabbitMQ ack/nack 수
- `/actuator/threaddump`
- 애플리케이션 로그의 소요시간
  - `SummonerRenewalService`
  - `MatchListener`
  - `TimeLineService`
- `jdk.tracePinnedThreads` 출력

## 비교 시나리오

### 시나리오 A: HTTP 요청

동일한 소환사/관전 API를 고정 RPS로 5~10분 호출한다.

성공 기준:

- p95/p99 응답시간 유지
- 에러율 증가 없음
- live thread 수 감소

### 시나리오 B: RabbitMQ 소비

동일 수량의 메시지를 `SUMMONER`, `RENEWAL_MATCH_FIND`, `MATCH_ID` 큐에 넣고 drain 시간을 비교한다.

성공 기준:

- queue backlog 감소 속도 유지
- nack/requeue 증가 없음
- 플랫폼 스레드 수 감소

### 시나리오 C: 혼합 부하

HTTP와 RabbitMQ 부하를 동시에 준다.

성공 기준:

- Hikari pending connection 급증 없음
- CPU 급등 없음
- pinned thread가 없거나 제한적

## 권장 수집 절차

1. 워밍업 3분
2. 본 측정 10분
3. 실행당 아래 자료 저장
   - Grafana 스크린샷
   - Prometheus 메트릭 값
   - `/actuator/threaddump` 3회
   - pinned thread 로그
4. Baseline/Candidate 각각 3회 반복
5. 평균값과 p95 기준으로 비교

## 해석 기준

다음이면 VT 적용이 유효하다고 본다.

- 처리량이 유지되거나 유사함
- 에러율 차이가 거의 없음
- live/peak thread 수가 의미 있게 감소함
- CPU와 메모리가 유지되거나 소폭 개선됨
- Hikari pending connection이 늘지 않음

다음이면 재검토가 필요하다.

- pinned thread 로그가 지속적으로 발생
- Hikari pending connection 급증
- HTTP p95/p99 악화
- RabbitMQ nack/requeue 증가
- queue drain 속도 저하
