-- 챔피언 통계 증분 적재(PG -> GCS -> BigQuery) 워터마크. 설계 5.1.
--
-- [불변식 - 설계 5.2] 워터마크는 ID 단조 증가를 전제로 한다.
--   * match / match_ban / match_participant 의 ID 는 INSERT 시점에 DB IDENTITY 로 발급되어야 한다.
--   * 시퀀스 CACHE > 1 또는 Hibernate pooled optimizer 로 바꾸면 안 된다.
--   * 워터마크보다 작은 ID 로 행을 복사해 넣는 데이터 이전(원래 ID 를 유지한 INSERT ... SELECT 등)도 금지다.
-- 이를 어기면 exported_until 아래로 들어온 행이 영구 누락된다.
CREATE TABLE IF NOT EXISTS stats_export_watermark (
    fact_type        VARCHAR(64)  PRIMARY KEY,
    exported_until   BIGINT       NOT NULL,
    observed_until   BIGINT       NOT NULL,
    range_patches    VARCHAR(100),
    last_run_id      VARCHAR(128),
    last_success_at  TIMESTAMP,
    updated_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE stats_export_watermark IS '통계 증분 적재 워터마크 (fact 별 1행)';
COMMENT ON COLUMN stats_export_watermark.fact_type IS 'match / match-ban / match-participant-build';
COMMENT ON COLUMN stats_export_watermark.exported_until IS '[.., exported_until) BigQuery 적재 완료';
COMMENT ON COLUMN stats_export_watermark.observed_until IS '직전 틱에서 관측한 MAX(id)+1 = 이번 틱 export 상한';
COMMENT ON COLUMN stats_export_watermark.range_patches IS '진행 중 범위의 대상 패치 (예: 1621). 첫 시도 때 고정, 성공 시 NULL';
