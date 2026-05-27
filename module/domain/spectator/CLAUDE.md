# module:domain:spectator

활성 게임(관전) 조회. sink 컨텍스트.

## 책임 / 구조

`com.mmrtr.lol.domain.spectator` 하위:
- `domain/` — 도메인 모델 (인프라 무지 — `ArchitectureTest` 강제)
- `application/port/` — Outbound Port (Repository/Api/Publish)
- `application/usecase/` — UseCase (Inbound Port)

## 어댑터 위치

이 컨텍스트의 port 를 구현/소비하는 어댑터는 `module:infra:*` 에 있다: riot-client(SpectatorApiAdapter)·api(컨트롤러).

## 규칙

- 도메인은 인프라 타입(@Entity·RestClient·RedisTemplate·AMQP)을 import 하지 않는다.
- 타 컨텍스트 참조는 그 컨텍스트의 `application`(port·usecase)·도메인 모델만 사용한다.
- 검증: `./gradlew :module:domain:spectator:test` (ArchUnit) / `./gradlew :module:domain:spectator:compileJava`.
