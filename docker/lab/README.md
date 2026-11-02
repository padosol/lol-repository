# Riot API 실험실

실제 Riot 서버 대신 가짜 서버(`:tools:riot-mock`)로 호출을 보내고, 호출량·429·큐 적체를 Grafana 로 본다.
앱은 `lab` 프로파일로 뜨며, 평소 local DB·Redis·RabbitMQ 와 섞이지 않도록 격리를 강제한다.

## 개발 모드 절차

1. `bash docker/lab/lab.sh up` — 가짜 서버 jar 를 빌드하고 인프라·관측 스택을 띄운다. `lol_lab` DB 와 vhost `lab` 도 만든다.
2. `bash docker/lab/lab.sh app` — 앱을 `local,lab` 프로파일로 띄운다 (포트 8111). 로그에 "Riot 요청을 가짜 서버" WARN 이 한 줄 나온다.
3. `http://localhost:3000` — 실험실 대시보드가 홈이다.
4. 부하는 k6 로 건다: `docker compose -p lol-repository -f docker/docker-compose.yml -f docker/lab/compose.yml run --rm k6 run /scripts/<파일>`.
5. 끝나면 `bash docker/lab/lab.sh down`. 컨테이너와 볼륨은 남는다.

## 명령

| 명령 | 동작 |
|---|---|
| `up` | `riot-mock` bootJar 빌드, 7개 서비스 기동, `lol_lab` 생성, vhost `lab` 생성, `rabbitmq_prometheus` 활성화 |
| `down` | 7개 서비스를 `stop` 한다. `compose down` 이 아니므로 컨테이너·볼륨(기존 postgres·rabbitmq·redis 데이터)이 그대로다 |
| `app` | `bootRun -Dspring.profiles.active=local,lab` |
| `urls` | Grafana·Prometheus·RabbitMQ·riot-mock 주소 출력 |
| `limit <한도>` | 가짜 서버의 application 한도를 바꾼다. 예: `limit 100:10` |
| `reset` | `lol_lab`·`lol_lab_snapshot` 을 지우고 `lol_lab` 재생성, Redis DB 1 비우기, vhost `lab` 재생성, 가짜 서버 epoch 0 |

`lab.sh` 를 `source` 하면 `require_lab_app`, `mock_put_limits <json>`, `mock_reset [epoch]` 도 쓸 수 있다 (k6 스크립트용).

## 포트

| 포트 | 대상 | 비고 |
|---|---|---|
| 3000 | Grafana | 로그인 없음 |
| 9090 | Prometheus | 1초 수집, 보존 2일, remote write 수신 |
| 8099 | riot-mock | 조작 API `/admin/*`, 지표 `/actuator/prometheus` |
| 9121 | redis_exporter | |
| 8111 | 앱 | `/actuator/prometheus` 를 Prometheus 가 `host.docker.internal` 로 수집 |
| 15672 | RabbitMQ 관리 화면 | |
| 15692 | RabbitMQ 지표 | compose 네트워크 안에서만 연다 (호스트에 공개하지 않는다). 확인은 Prometheus 질의로 한다 |

기본 서비스(postgres·rabbitmq·redis)의 설정을 `docker/lab/compose.yml` 에서 바꾸면 compose 가 기존 컨테이너를 재생성해 데이터가 사라질 수 있다. 그래서 15692 를 공개하지 않는다.

## 확인된 동작

- 앱이 뜨면 Prometheus `lol-app`·`riot-mock`·`rabbitmq`·`redis` 4개 job 이 모두 `up` 이다.
- 대시보드는 Grafana 홈이다 (`/api/dashboards/home` 이 실험실 대시보드로 보낸다).
- 요청이 한 번도 없으면 패널 2·3 은 비어 있다. 패널 1 은 0 이다.
- `RIOT_API_KEY` 가 환경에 있으면 `LabIsolationGuard` 가 `riot.api-key` 를 이름만 대고 기동을 막는다. 값은 로그에 나오지 않는다.
- `dev,lab` 로 띄우면 lab 값이 dev 값을 덮지만, dev 의 Redis 호스트(`lol-redis`)는 lab 에 없어 그대로 남는다. 도커 밖에서는 해석되지 않아 기동이 실패한다.

## 이미지 버전

| 이미지 | 버전 |
|---|---|
| `prom/prometheus` | v3.15.1 |
| `grafana/grafana` | 13.2.5 |
| `oliver006/redis_exporter` | v1.96.0 |
| `grafana/k6` | 2.3.0 |
| `eclipse-temurin` (riot-mock) | 21-jre |
| `postgres` / `rabbitmq` / `redis` | 기본 `docker/docker-compose.yml` 그대로 |

## `.env` 키 충돌

`bootRun` 은 루트 `.env` 의 모든 줄을 환경변수로 넣고, 이것이 yml 값을 덮는다. lab 은 격리 값이 어긋나면 기동에 실패한다.

| 키 | lab 에서의 영향 | 대처 |
|---|---|---|
| `RIOT_API_KEY` | `riot.api-key` 를 덮어 가드가 기동을 막는다 (실제 키가 가짜 서버로 갈 뻔함) | lab 을 돌릴 때 `.env` 에서 주석 처리하거나, 이 키가 없는 체크아웃에서 돌린다 |
| `DB_HOST`, `DB_PORT` | lab URL 에 그대로 쓰인다 | lab postgres(localhost:5432)를 가리켜야 한다 |
| `DB_NAME` | 무시된다 (URL 에 `lol_lab` 고정) | 없음 |
| `DB_USERNAME`, `DB_PASSWORD` | lab 기본값(postgres/1234)을 덮는다 | lab postgres 계정과 같아야 한다 |
| `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD` | local 과 같이 쓰인다 | vhost `lab` 에 권한이 있어야 한다 (`up` 이 guest 에 준다) |
| `SPRING_PROFILES_ACTIVE` | `bootRun` 의 `-D` 가 이긴다 | 없음 |
| `PROMETHEUS_SCRAPE_HOST_IP` | 무관 | 없음 |

`.env` 에 `SPRING_DATASOURCE_URL`, `SPRING_DATA_REDIS_DATABASE`, `SPRING_RABBITMQ_VIRTUAL_HOST` 가 있어도 가드가 기동을 막는다.
가드 메시지에는 어긋난 속성 이름과 기대값만 나오고 실제 값은 나오지 않는다.
