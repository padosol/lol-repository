-- CONCURRENTLY 는 트랜잭션 밖에서만 돌아 Flyway 가 다른 문장과 한 파일에 두면 실패시킨다 — 이 문장만 둔다.
-- 배포 전에 운영 DB 에 같은 문장을 먼저 실행해 두면 기동 때는 IF NOT EXISTS 로 바로 넘어간다.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_match_participant_puuid_champion_name
    ON match_participant (puuid, champion_name);
