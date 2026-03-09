-- 집계 테이블 (Materialized View 대상) TRUNCATE
TRUNCATE TABLE IF EXISTS champion_stats_agg;
TRUNCATE TABLE IF EXISTS champion_bans_agg;
TRUNCATE TABLE IF EXISTS match_count_agg;
TRUNCATE TABLE IF EXISTS champion_rune_stats_agg;
TRUNCATE TABLE IF EXISTS champion_spell_stats_agg;
TRUNCATE TABLE IF EXISTS champion_skill_build_stats_agg;
TRUNCATE TABLE IF EXISTS champion_start_item_stats_agg;
TRUNCATE TABLE IF EXISTS champion_item_stats_agg;
TRUNCATE TABLE IF EXISTS champion_item_build_stats_agg;
TRUNCATE TABLE IF EXISTS champion_matchup_stats_agg;

-- 팩트 테이블 TRUNCATE
TRUNCATE TABLE IF EXISTS match_participant_local;
TRUNCATE TABLE IF EXISTS match_ban_local;
TRUNCATE TABLE IF EXISTS match_skill_build_local;
TRUNCATE TABLE IF EXISTS match_start_item_build_local;
TRUNCATE TABLE IF EXISTS match_item_build_local;
TRUNCATE TABLE IF EXISTS match_final_item_local;
TRUNCATE TABLE IF EXISTS match_matchup_local;
-- legendary_items는 참조 데이터이므로 TRUNCATE하지 않음
