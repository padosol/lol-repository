# LOL Repository

## 프로젝트 소개
이 프로젝트는 League of Legends(LoL) 데이터 처리를 위한 컨슈머 서비스입니다. 메인 서비스의 부하 분산을 위해 설계되었으며, 
데이터베이스 작업과 RIOT API 통신을 전담하고 있습니다.

## 기술 스택
- Java
- Spring Boot
- Gradle
- Spring Data JPA
- Kafka
- Redis

## 시스템 구조
┌─────────────┐    ┌──────────────────┐    ┌────────────────┐    ┌──────────┐
│ Main Server │ -> │   Message Queue   │ -> │ LOL Repository │ -> │ RIOT API │
└─────────────┘    │ (Kafka & Redis)  │    └────────────────┘    └──────────┘
                   └──────────────────┘            │                    │
                                                  │                    │
                                                  ▼                    ▼
                                           ┌──────────────┐    ┌──────────────┐
                                           │    Cache     │    │   Database   │
                                           │   (Redis)    │    │   (MySQL)    │
                                           └──────────────┘    └──────────────┘

## 데이터 처리 프로세스
1. 실시간 데이터 처리 (Redis Pub/Sub)
   ┌────────────┐    ┌─────────────┐    ┌────────────┐
   │ Main Server │ -> │ Redis Queue │ -> │ 즉시 처리  │
   └────────────┘    └─────────────┘    └────────────┘

2. 일반 데이터 처리 (Kafka)
   ┌────────────┐    ┌─────────────┐    ┌────────────────┐
   │ Main Server │ -> │ Kafka Queue │ -> │ Rate Limit 처리 │
   └────────────┘    └─────────────┘    └────────────────┘

## 주요 기능

### 1. 메시지 큐 처리
Redis Pub/Sub (실시간)     Kafka Consumer (비동기)
     │                           │
     ▼                           ▼
[긴급 데이터 처리]        [일반 데이터 처리]
     │                           │
     └───────────┐     ┌────────┘
                 ▼     ▼
            [데이터베이스 저장]

### 2. RIOT API 연동
┌─────────────────┐
│   API 요청      │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  Rate Limiting  │◄─── Token Bucket Algorithm
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│   API 응답      │
└─────────────────┘

## 필수 요구사항
- JDK 11 이상
- Gradle 7.x 이상
- Redis
- Kafka
- MySQL/PostgreSQL