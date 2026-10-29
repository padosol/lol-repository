-- 챔피언 통계 증분 적재 워터마크 가드. 설계 5.2 · 11.
--
-- observed_at : observed_until 을 기록한 시각. 범위 [exported_until, observed_until) 는 이 시각부터
--               lol.stats-export.min-observe-age(기본 15분)가 지나야 export 한다. 그 아래 ID 를 받은 트랜잭션이
--               커밋(또는 롤백)될 시간을 보장해, 수동 실행이 틱 직후에 와도 미커밋 행을 건너뛰지 않게 한다.
--               세션 타임존과 무관하게 비교하려고 TIMESTAMPTZ 로 둔다.
-- export_epoch: 같은 범위를 새 JobInstance 로 다시 export 하게 하는 운영용 카운터 (identifying JobParameter).
--               막힌 범위 복구: UPDATE stats_export_watermark SET export_epoch = export_epoch + 1 WHERE fact_type = '<code>'
ALTER TABLE stats_export_watermark
    ADD COLUMN IF NOT EXISTS observed_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS export_epoch INTEGER     NOT NULL DEFAULT 0;

COMMENT ON COLUMN stats_export_watermark.observed_at IS 'observed_until 을 기록한 시각. 이 시각부터 min-observe-age 가 지나야 범위를 export 한다';
COMMENT ON COLUMN stats_export_watermark.export_epoch IS '범위를 새 JobInstance 로 다시 export 할 때 올리는 운영 카운터 (JobParameter exportEpoch)';
-- V3 의 예시("1621")가 실제 저장 형식과 달라 바로잡는다 (적용된 V3 는 수정하지 않는다)
COMMENT ON COLUMN stats_export_watermark.range_patches IS '진행 중 범위의 대상 패치 (예: 16.21, 여러 개면 최신순 쉼표 구분 16.21,16.20). 첫 시도 때 고정, 성공 시 NULL';
