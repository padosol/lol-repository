# 주요 클래스 레퍼런스

### app 모듈
| 클래스 | 패키지 | 설명 |
|--------|--------|------|
| LolRepositoryApplication | com.mmrtr.lol | Spring Boot 메인 애플리케이션 진입점 |

### core/domain 모듈

#### Summoner 도메인
| 클래스 | 패키지 | 설명 |
|--------|--------|------|
| Summoner | domain.summoner.domain | 소환사 도메인 객체 (PUUID, 게임ID, 리그 정보 포함) |
| SummonerService | domain.summoner.service | 소환사 조회 오케스트레이션 서비스 |
| SaveSummonerDataUseCase | domain.summoner.service.usecase | 소환사 데이터 저장 UseCase |
| SummonerRepositoryPort | domain.summoner.repository | 소환사 저장소 포트 인터페이스 |
| SummonerApiPort | domain.summoner.port | RIOT API 소환사 조회 포트 |

#### League 도메인
| 클래스 | 패키지 | 설명 |
|--------|--------|------|
| League | domain.league.domain | 리그 도메인 객체 |
| LeagueSummoner | domain.league.domain | 소환사별 리그 정보 |
| SummonerRanking | domain.league.domain | 지역별 소환사 랭킹 |
| TierCutoff | domain.league.domain | 티어별 LP 커트라인 |
| CalculateSummonerRankingUseCase | domain.league.service.usecase | 랭킹 계산 UseCase |

#### Match 도메인
| 클래스 | 패키지 | 설명 |
|--------|--------|------|
| Match | domain.match.domain | 매치 도메인 객체 |
| MatchTimeline | domain.match.domain | 매치 타임라인 이벤트 |
| MatchApiPort | domain.match.port | RIOT API 매치 조회 포트 |

#### Champion 도메인
| 클래스 | 패키지 | 설명 |
|--------|--------|------|
| ChampionRotation | domain.champion.domain | 무료 챔피언 로테이션 |
| ChampionRotateService | domain.champion.service | 챔피언 로테이션 조회 서비스 |

#### Spectator 도메인
| 클래스 | 패키지 | 설명 |
|--------|--------|------|
| ActiveGame | domain.spectator.domain | 활성 게임 정보 |
| GameParticipant | domain.spectator.domain | 게임 참가자 정보 |
| SpectatorService | domain.spectator.service | 활성 게임 조회 서비스 |

### core/enum 모듈
| 클래스 | 패키지 | 설명 |
|--------|--------|------|
| Platform | common.type | 게임 지역/플랫폼 (KR, NA 등) |
| Tier | common.type | 랭크 티어 (CHALLENGER ~ IRON) |
| Division | common.type | 티어 내 구간 (I ~ IV) |
| Queue | common.type | 랭킹 큐 타입 (솔로랭크, 자유랭크) |
| EventType | common.type | 매치 타임라인 이벤트 타입 |

### infra/api 모듈
| 클래스 | 패키지 | 설명 |
|--------|--------|------|
| SummonerController | controller.summoner | 소환사 조회 API |
| ChampionRotateController | controller.champion | 챔피언 로테이션 API |
| SpectatorController | controller.spectator | 활성 게임 조회 API |
| AdminRankingController | controller.admin | 랭킹 계산 트리거 API |
| CoreExceptionAdvice | controller | 전역 예외 처리 (@RestControllerAdvice) |

### infra/persistence 모듈
| 클래스 | 패키지 | 설명 |
|--------|--------|------|
| SummonerEntity | persistence.summoner.entity | 소환사 JPA 엔티티 |
| SummonerRepositoryImpl | persistence.summoner.repository | SummonerRepositoryPort 구현체 |
| MatchEntity | persistence.match.entity | 매치 JPA 엔티티 |
| MatchService | persistence.match.service | 매치 배치 저장 서비스 |
| LeagueEntity | persistence.league.entity | 리그 JPA 엔티티 |
| LeagueRepositoryImpl | persistence.league.repository | LeagueRepositoryPort 구현체 (분산 락 포함) |

### infra/redis 모듈
| 클래스 | 패키지 | 설명 |
|--------|--------|------|
| RedisConfig | redis.config | Redis 클라이언트 및 RedisTemplate 설정 |
| RedissonConfig | redis.config | Redisson 분산 락 클라이언트 설정 |
| RedisLockHandler | redis.service | Redis 기반 분산 락 처리 (Lua 스크립트) |

### infra/rabbitmq 모듈
| 클래스 | 패키지 | 설명 |
|--------|--------|------|
| RabbitMqConfig | rabbitmq.config | 큐/Exchange/Binding 및 리스너 설정 |
| SummonerRenewalListener | rabbitmq.listener | 소환사 갱신 메시지 리스너 |
| MatchListener | rabbitmq.listener | 매치 ID 메시지 리스너 (배치 처리) |
| MessageSender | rabbitmq.service | RabbitTemplate 메시지 발송 서비스 |

### infra/riot-client 모듈
| 클래스 | 패키지 | 설명 |
|--------|--------|------|
| RiotApiService | riot.service | RIOT API 호출 핵심 서비스 (비동기) |
| SummonerApiAdapter | riot.adapter | SummonerApiPort 구현체 |
| MatchApiAdapter | riot.adapter | MatchApiPort 구현체 |
| SpectatorApiAdapter | riot.adapter | SpectatorApiPort 구현체 |
| RetryInterceptor | riot.interceptor | 429/5xx 자동 재시도 인터셉터 |
| RateLimitInterceptor | riot.interceptor | Redisson 기반 Rate Limiting |

### support 모듈
| 클래스 | 패키지 | 설명 |
|--------|--------|------|
| CoreException | support.error | 커스텀 예외 클래스 |
| ErrorType | support.error | 에러 타입 enum (HTTP 상태, 메시지 매핑) |
| ErrorMessage | support.error | 에러 응답 DTO |
