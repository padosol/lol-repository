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

## 주요 기능
1. 메시지 큐 처리
   - Kafka Consumer를 통한 메시지 처리
   - Redis Pub/Sub을 통한 실시간 데이터 처리

2. RIOT API 연동
   - API 호출 및 데이터 수집
   - Rate Limit 관리 및 최적화

3. 데이터 관리
   - 데이터베이스 입력 및 관리
   - 캐시 데이터 처리

## 시스템 아키텍처
```
Main Server → Message Queue (Kafka/Redis) → LOL Repository → RIOT API → Database
```

## 프로세스 흐름
1. Redis Pub/Sub
   - 긴급하거나 실시간성이 필요한 데이터 우선 처리
   - 빠른 응답이 필요한 작업 처리

2. Kafka Consumer
   - RIOT API Rate Limit을 고려한 단계적 데이터 처리
   - 대용량 데이터 처리 및 배치 작업 수행

## 프로젝트 설정 및 실행

### 필수 요구사항
- JDK 11 이상
- Gradle 7.x 이상
- Redis
- Kafka
- MySQL/PostgreSQL