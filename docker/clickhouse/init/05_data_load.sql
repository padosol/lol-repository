-- =====================================================
-- 05: PostgreSQL → ClickHouse 데이터 적재 (수동 실행)
-- =====================================================
-- 01_pg_source_tables.sql 실행 후 사용하세요.
-- 반복 실행 가능: 워터마크 기반 증분 로딩으로 매번 새 데이터만 처리합니다.
--
-- [2-Stage 로딩 전략]
-- Stage 1: match PK 워터마크로 배치 범위 결정 (LIMIT으로 배치 크기 제어)
-- Stage 2: PostgreSQL View를 통해 match PK 범위로 관련 데이터 조회 (LIMIT 없음, 정합성 보장)
--   - platform_id, queue_id 필터 push-down → idx_match_platform_queue_season_patch 활용
--   - 수집 대상: 솔로랭크(420), 자유랭크(440), 칼바람(450), 아레나(1700)
--
-- [벌크 로드 시 MV 관리]
-- 초기 적재나 대량 재처리 시, DETACH/ATTACH 블록의 주석을 해제하세요.
-- =====================================================

-- =====================================================
-- 세션 설정 (벌크 로드 최적화)
-- =====================================================
SET max_memory_usage = 4000000000;            -- 4GB (스테이징이 디스크 기반이므로 낮춤)
SET max_insert_block_size = 1048576;          -- 1M rows/block (MV 트리거 횟수 감소)
SET min_insert_block_size_rows = 1048576;
SET optimize_on_insert = 0;                   -- 벌크 로드 시 dedup 지연

-- =====================================================
-- [선택] 벌크 로드 시 Materialized View DETACH
-- =====================================================
-- DETACH TABLE mv_champion_stats;
-- DETACH TABLE mv_champion_bans;
-- DETACH TABLE mv_match_count;
-- DETACH TABLE mv_champion_rune_stats;
-- DETACH TABLE mv_champion_spell_stats;
-- DETACH TABLE mv_champion_skill_build_stats;
-- DETACH TABLE mv_champion_start_item_stats;
-- DETACH TABLE mv_champion_item_build_stats;
-- DETACH TABLE mv_champion_item_stats;
-- DETACH TABLE mv_champion_matchup_stats;

-- =====================================================
-- 워터마크 읽기 (match PK 단일 워터마크)
-- =====================================================
DROP TABLE IF EXISTS _watermarks;
CREATE TABLE _watermarks ENGINE = Memory AS
SELECT source_table, last_id
FROM etl_watermarks FINAL;

-- =====================================================
-- Stage 1: Match 데이터 + 배치 범위 결정
-- =====================================================
-- 모든 queue_id를 포함합니다 (워터마크가 정확히 전진하려면 id 범위를 건너뛸 수 없음).
-- queue_id 필터는 Fact 테이블 INSERT 시 로컬에서 적용합니다.
DROP TABLE IF EXISTS _stg_matches;
CREATE TABLE _stg_matches ENGINE = MergeTree ORDER BY id AS
SELECT id, match_id, queue_id, platform_id, season, patch_version
FROM pg_match
WHERE id > (SELECT coalesce(max(last_id), 0) FROM _watermarks WHERE source_table = 'match')
ORDER BY id
LIMIT 10000;

-- 배치 범위 결정 (Stage 2에서 사용)
DROP TABLE IF EXISTS _batch_range;
CREATE TABLE _batch_range ENGINE = Memory AS
SELECT
    (SELECT coalesce(max(last_id), 0) FROM _watermarks WHERE source_table = 'match') AS batch_min_id,
    (SELECT max(id) FROM _stg_matches) AS batch_max_id;

-- =====================================================
-- Stage 2a: Participant 데이터 (View 기반, match PK 범위 + 인덱스 필터)
-- =====================================================
DROP TABLE IF EXISTS _stg_participants_raw;
CREATE TABLE _stg_participants_raw ENGINE = MergeTree ORDER BY (match_id, participant_id) AS
SELECT
    match_id, queue_id, platform_id, season, patch_version,
    participant_id, champion_id, team_position, team_id, win, tier,
    primary_style_id, primary_perk0, primary_perk1, primary_perk2, primary_perk3,
    sub_style_id, sub_perk0, sub_perk1,
    stat_perk_defense, stat_perk_flex, stat_perk_offense,
    summoner1id, summoner2id,
    item0, item1, item2, item3, item4, item5
FROM pg_v_match_participant
WHERE match_pk > (SELECT batch_min_id FROM _batch_range)
  AND match_pk <= (SELECT batch_max_id FROM _batch_range)
  AND platform_id = 'KR'
  AND queue_id IN (420, 440, 450, 1700);

-- 랭크용 필터 (tier/position 필수)
DROP TABLE IF EXISTS _stg_participants;
CREATE TABLE _stg_participants ENGINE = MergeTree ORDER BY (match_id, participant_id) AS
SELECT
    match_id, queue_id, platform_id, season, patch_version,
    participant_id, champion_id,
    assumeNotNull(team_position) AS team_position,
    team_id, win,
    assumeNotNull(tier) AS tier,
    assumeNotNull(primary_style_id) AS primary_style_id,
    assumeNotNull(primary_perk0) AS primary_perk0,
    assumeNotNull(primary_perk1) AS primary_perk1,
    assumeNotNull(primary_perk2) AS primary_perk2,
    assumeNotNull(primary_perk3) AS primary_perk3,
    assumeNotNull(sub_style_id) AS sub_style_id,
    assumeNotNull(sub_perk0) AS sub_perk0,
    assumeNotNull(sub_perk1) AS sub_perk1,
    assumeNotNull(stat_perk_defense) AS stat_perk_defense,
    assumeNotNull(stat_perk_flex) AS stat_perk_flex,
    assumeNotNull(stat_perk_offense) AS stat_perk_offense,
    summoner1id, summoner2id,
    item0, item1, item2, item3, item4, item5
FROM _stg_participants_raw
WHERE tier IS NOT NULL
  AND team_position IS NOT NULL AND team_position != ''
  AND queue_id IN (420, 440);

-- =====================================================
-- Stage 2b: Ban 데이터 (View 기반, match PK 범위)
-- =====================================================
DROP TABLE IF EXISTS _stg_bans;
CREATE TABLE _stg_bans ENGINE = MergeTree ORDER BY (match_id, pick_turn) AS
SELECT
    match_id, queue_id, platform_id, season, patch_version,
    team_id, champion_id, pick_turn
FROM pg_v_match_ban
WHERE match_pk > (SELECT batch_min_id FROM _batch_range)
  AND match_pk <= (SELECT batch_max_id FROM _batch_range)
  AND platform_id = 'KR'
  AND queue_id IN (420, 440, 1700);

-- =====================================================
-- Stage 2c: Skill 이벤트 (View 기반, match PK 범위)
-- =====================================================
DROP TABLE IF EXISTS _stg_skill_events_raw;
CREATE TABLE _stg_skill_events_raw ENGINE = MergeTree ORDER BY (match_id, participant_id) AS
SELECT
    match_id, queue_id, platform_id, season, patch_version,
    participant_id, skill_slot, level_up_type, timestamp
FROM pg_v_match_skill_event
WHERE match_pk > (SELECT batch_min_id FROM _batch_range)
  AND match_pk <= (SELECT batch_max_id FROM _batch_range)
  AND platform_id = 'KR'
  AND queue_id IN (420, 440);

DROP TABLE IF EXISTS _stg_skill_events;
CREATE TABLE _stg_skill_events ENGINE = MergeTree ORDER BY (match_id, participant_id) AS
SELECT match_id, participant_id, skill_slot, timestamp
FROM _stg_skill_events_raw
WHERE level_up_type = 'NORMAL';

-- =====================================================
-- Stage 2d: Item 이벤트 (View 기반, match PK 범위)
-- =====================================================
DROP TABLE IF EXISTS _stg_item_events_raw;
CREATE TABLE _stg_item_events_raw ENGINE = MergeTree ORDER BY (match_id, participant_id) AS
SELECT
    match_id, queue_id, platform_id, season, patch_version,
    type, item_id, participant_id, timestamp, before_id
FROM pg_v_match_item_event
WHERE match_pk > (SELECT batch_min_id FROM _batch_range)
  AND match_pk <= (SELECT batch_max_id FROM _batch_range)
  AND platform_id = 'KR'
  AND queue_id IN (420, 440);

DROP TABLE IF EXISTS _stg_item_events;
CREATE TABLE _stg_item_events ENGINE = MergeTree ORDER BY (match_id, participant_id) AS
SELECT match_id, type, item_id, participant_id, timestamp, before_id
FROM _stg_item_events_raw
WHERE type IN ('ITEM_PURCHASED', 'ITEM_UNDO');

-- =====================================================
-- legendary_items 참조 데이터 적재 (전체 교체)
-- =====================================================
TRUNCATE TABLE IF EXISTS legendary_items;

INSERT INTO legendary_items (item_id, item_name) VALUES
(2501, '지배자의 피갑옷'),
(2502, '끝없는 절망'),
(2503, '어둠불꽃 횃불'),
(2504, '케이닉 루컨'),
(2510, '황혼과 새벽'),
(2512, '악마사냥꾼의 화살'),
(2517, '끝없는 갈망'),
(2520, '요새파괴자'),
(2522, '실체화 장비'),
(2523, '마법광학 장치 C44'),
(2525, '원형질 안전벨트'),
(3003, '대천사의 지팡이'),
(3004, '마나무네'),
(3026, '수호 천사'),
(3032, '윤 탈 야생화살'),
(3033, '필멸자의 운명'),
(3036, '도미닉 경의 인사'),
(3046, '유령 무희'),
(3053, '스테락의 도전'),
(3065, '정령의 형상'),
(3068, '태양불꽃 방패'),
(3071, '칠흑의 양날 도끼'),
(3072, '피바라기'),
(3073, '실험적 마공학판'),
(3074, '굶주린 히드라'),
(3078, '삼위일체'),
(3083, '워모그의 갑옷'),
(3084, '강철심장'),
(3085, '루난의 허리케인'),
(3087, '스태틱의 단검'),
(3091, '마법사의 최후'),
(3094, '고속 연사포'),
(3097, '폭풍갈퀴'),
(3100, '리치베인'),
(3102, '밴시의 장막'),
(3110, '얼어붙은 심장'),
(3115, '내셔의 이빨'),
(3116, '라일라이의 수정홀'),
(3118, '악의'),
(3124, '구인수의 격노검'),
(3135, '공허의 지팡이'),
(3137, '무덤꽃'),
(3139, '헤르메스의 시미터'),
(3142, '요우무의 유령검'),
(3143, '란두인의 예언'),
(3146, '마법공학 총검'),
(3152, '마법공학 로켓 벨트'),
(3153, '몰락한 왕의 검'),
(3156, '맬모셔스의 아귀'),
(3157, '존야의 모래시계'),
(3161, '쇼진의 창'),
(3165, '모렐로노미콘'),
(3179, '그림자 검'),
(3181, '선체파괴자'),
(3302, '경계'),
(3508, '정수 약탈자'),
(3742, '망자의 갑옷'),
(3748, '거대한 히드라'),
(3814, '밤의 끝자락'),
(4401, '대자연의 힘'),
(4628, '지평선의 초점'),
(4629, '우주의 추진력'),
(4633, '균열 생성기'),
(4645, '그림자불꽃'),
(4646, '폭풍 쇄도'),
(6333, '죽음의 무도'),
(6609, '화공 펑크 사슬검'),
(6610, '갈라진 하늘'),
(6621, '새벽심장'),
(6631, '발걸음 분쇄기'),
(6653, '리안드리의 고통'),
(6655, '루덴의 메아리'),
(6657, '영겁의 지팡이'),
(6662, '얼어붙은 건틀릿'),
(6664, '공허한 광휘'),
(6665, '해신 작쇼'),
(6672, '크라켄 학살자'),
(6673, '불멸의 철갑궁'),
(6675, '나보리 명멸검'),
(6676, '징수의 총'),
(6692, '월식'),
(6694, '세릴다의 원한'),
(6695, '독사의 송곳니'),
(6696, '원칙의 원형낫'),
(6697, '오만'),
(6698, '불경한 히드라'),
(6699, '벼락폭풍검'),
(6701, '기회'),
(8010, '핏빛 저주'),
(8020, '심연의 가면'),
(322065, '슈렐리아의 군가'),
(323002, '개척자'),
(323003, '대천사의 지팡이'),
(323004, '마나무네'),
(323075, '가시 갑옷'),
(323107, '구원'),
(323109, '기사의 맹세'),
(323110, '얼어붙은 심장'),
(323190, '강철의 솔라리 펜던트'),
(323222, '미카엘의 축복'),
(323504, '불타는 향로'),
(324005, '제국의 명령'),
(326616, '흐르는 물의 지팡이'),
(326617, '월석 재생기'),
(326620, '헬리아의 메아리'),
(326621, '새벽심장'),
(326657, '영겁의 지팡이'),
(328020, '심연의 가면'),
(667666, '징수의 총');

-- =====================================================
-- [솔로랭크 + 자유랭크] 1. match_participant_local 적재
-- =====================================================
-- stg에 이미 match 메타 포함 → match JOIN 불필요
INSERT INTO match_participant_local
SELECT
    match_id, champion_id, team_position, team_id, win,
    queue_id, platform_id, season, patch_version, tier,
    primary_style_id, primary_perk0, primary_perk1, primary_perk2, primary_perk3,
    sub_style_id, sub_perk0, sub_perk1,
    stat_perk_defense, stat_perk_flex, stat_perk_offense,
    summoner1id, summoner2id
FROM _stg_participants
WHERE patch_version IS NOT NULL;

-- =====================================================
-- [솔로랭크 + 자유랭크] 2. match_matchup_local 적재 (셀프조인)
-- =====================================================
INSERT INTO match_matchup_local
SELECT
    p1.match_id,
    p1.champion_id,
    p2.champion_id AS opponent_champion_id,
    p1.team_position,
    p1.win,
    p1.queue_id,
    p1.platform_id,
    p1.season,
    p1.patch_version,
    p1.tier
FROM _stg_participants AS p1
INNER JOIN _stg_participants AS p2
    ON  p1.match_id      = p2.match_id
    AND p1.team_position  = p2.team_position
    AND p1.team_id       != p2.team_id
WHERE p1.team_position != ''
  AND p1.patch_version IS NOT NULL;

-- =====================================================
-- [솔로랭크 + 자유랭크] 3. match_ban_local 적재
-- =====================================================
INSERT INTO match_ban_local
SELECT
    b.match_id,
    b.champion_id,
    b.team_id,
    b.pick_turn,
    b.queue_id,
    b.platform_id,
    b.season,
    b.patch_version,
    p.tier
FROM _stg_bans AS b
INNER JOIN _stg_participants AS p
    ON b.match_id = p.match_id
   AND p.participant_id = b.pick_turn
WHERE b.queue_id IN (420, 440)
  AND b.patch_version IS NOT NULL;

-- =====================================================
-- [솔로랭크 + 자유랭크] 4. match_skill_build_local 적재
-- =====================================================
INSERT INTO match_skill_build_local
SELECT
    sub.match_id, sub.champion_id, sub.team_position, sub.win,
    sub.queue_id, sub.platform_id, sub.season, sub.patch_version,
    sub.tier, sub.skill_build
FROM (
    SELECT
        o.match_id, o.champion_id, o.team_position, o.win,
        o.queue_id, o.platform_id, o.season, o.patch_version,
        o.tier, o.participant_id,
        arrayStringConcat(
            arraySlice(
                groupArray(toString(o.skill_slot)),
                1, 15
            ),
            ','
        ) AS skill_build
    FROM (
        SELECT
            sk.match_id,
            sk.participant_id,
            sk.skill_slot,
            sk.timestamp,
            p.champion_id,
            p.team_position,
            p.win,
            p.queue_id,
            p.platform_id,
            p.season,
            p.patch_version,
            p.tier
        FROM _stg_skill_events AS sk
        INNER JOIN _stg_participants AS p
            ON sk.match_id = p.match_id
           AND sk.participant_id = p.participant_id
        WHERE p.patch_version IS NOT NULL
        ORDER BY sk.match_id, sk.participant_id, sk.timestamp ASC
    ) AS o
    GROUP BY
        o.match_id, o.champion_id, o.team_position,
        o.win, o.queue_id, o.platform_id, o.season, o.patch_version,
        o.tier, o.participant_id
    HAVING count(*) >= 15
) AS sub;

-- =====================================================
-- [솔로랭크 + 자유랭크] 5. match_start_item_build_local 적재
-- =====================================================
INSERT INTO match_start_item_build_local
SELECT
    agg.match_id, agg.champion_id, agg.team_position, agg.win,
    agg.queue_id, agg.platform_id, agg.season, agg.patch_version, agg.tier,
    agg.start_items
FROM (
    SELECT
        net.match_id, net.champion_id, net.team_position, net.win,
        net.queue_id, net.platform_id, net.season, net.patch_version, net.tier,
        net.participant_id,
        arrayStringConcat(
            arraySort(
                arrayFlatten(
                    groupArray(
                        arrayWithConstant(
                            toUInt32(net.net_count),
                            toString(net.effective_item_id)
                        )
                    )
                )
            ),
            ','
        ) AS start_items
    FROM (
        SELECT
            ie.match_id,
            ie.participant_id,
            CASE
                WHEN ie.type = 'ITEM_PURCHASED' THEN ie.item_id
                WHEN ie.type = 'ITEM_UNDO'      THEN ie.before_id
            END AS effective_item_id,
            sum(CASE
                WHEN ie.type = 'ITEM_PURCHASED' THEN 1
                WHEN ie.type = 'ITEM_UNDO'      THEN -1
            END) AS net_count,
            any(p.champion_id)     AS champion_id,
            any(p.team_position)   AS team_position,
            any(p.win)             AS win,
            any(p.queue_id)        AS queue_id,
            any(p.platform_id)     AS platform_id,
            any(p.season)          AS season,
            any(p.patch_version)   AS patch_version,
            any(p.tier)            AS tier
        FROM _stg_item_events AS ie
        INNER JOIN _stg_participants AS p
            ON ie.match_id = p.match_id
           AND ie.participant_id = p.participant_id
        WHERE ie.timestamp <= 60000
          AND ie.type IN ('ITEM_PURCHASED', 'ITEM_UNDO')
          AND ie.item_id NOT IN (3340, 3363, 3364)
          AND p.patch_version IS NOT NULL
        GROUP BY ie.match_id, ie.participant_id,
            CASE
                WHEN ie.type = 'ITEM_PURCHASED' THEN ie.item_id
                WHEN ie.type = 'ITEM_UNDO'      THEN ie.before_id
            END
        HAVING net_count > 0
    ) AS net
    GROUP BY net.match_id, net.participant_id,
             net.champion_id, net.team_position, net.win,
             net.queue_id, net.platform_id, net.season, net.patch_version, net.tier
    HAVING start_items != ''
) AS agg;

-- =====================================================
-- [솔로랭크 + 자유랭크] 6. match_final_item_local 적재
-- =====================================================
INSERT INTO match_final_item_local
SELECT
    ranked.match_id, ranked.champion_id, ranked.team_position, ranked.win,
    ranked.queue_id, ranked.platform_id, ranked.season, ranked.patch_version, ranked.tier,
    ranked.item_id, ranked.item_order
FROM (
    SELECT
        joined.match_id, joined.champion_id, joined.team_position, joined.win,
        joined.queue_id, joined.platform_id, joined.season, joined.patch_version, joined.tier,
        joined.item_id,
        toUInt8(row_number() OVER (
            PARTITION BY joined.match_id, joined.participant_id
            ORDER BY joined.purchase_ts ASC
        )) AS item_order
    FROM (
        SELECT
            fi.match_id, fi.participant_id,
            fi.champion_id, fi.team_position, fi.win,
            fi.queue_id, fi.platform_id, fi.season, fi.patch_version, fi.tier,
            fi.item_id,
            min(ie.timestamp) AS purchase_ts
        FROM (
            SELECT
                p.match_id, p.participant_id,
                p.champion_id, p.team_position, p.win,
                p.queue_id, p.platform_id, p.season, p.patch_version, p.tier,
                arrayJoin(
                    arrayFilter(x -> x != 0, [p.item0, p.item1, p.item2, p.item3, p.item4, p.item5])
                ) AS item_id
            FROM _stg_participants AS p
        ) AS fi
        INNER JOIN _stg_item_events AS ie
            ON fi.match_id = ie.match_id
           AND fi.participant_id = ie.participant_id
           AND fi.item_id = ie.item_id
        WHERE fi.item_id IN (SELECT item_id FROM legendary_items)
          AND ie.type = 'ITEM_PURCHASED'
          AND fi.patch_version IS NOT NULL
        GROUP BY fi.match_id, fi.participant_id,
                 fi.champion_id, fi.team_position, fi.win,
                 fi.queue_id, fi.platform_id, fi.season, fi.patch_version, fi.tier,
                 fi.item_id
    ) AS joined
) AS ranked;

-- =====================================================
-- [솔로랭크 + 자유랭크] 7. match_item_build_local 적재 (3코어 빌드 순서)
-- =====================================================
INSERT INTO match_item_build_local
SELECT
    match_id, champion_id, team_position, win,
    queue_id, platform_id, season, patch_version, tier,
    arrayStringConcat(
        arrayMap(
            x -> toString(x.1),
            arraySort(
                x -> x.2,
                groupArray(tuple(item_id, item_order))
            )
        ),
        ','
    ) AS item_build
FROM match_final_item_local
WHERE item_order <= 3
  AND queue_id IN (420, 440)
  AND team_position != ''
  AND match_id IN (SELECT match_id FROM _stg_matches WHERE queue_id IN (420, 440))
GROUP BY match_id, champion_id, team_position,
         win, queue_id, platform_id, season, patch_version, tier
HAVING count(*) = 3;

-- =====================================================
-- [칼바람] aram_participant_local 적재 (queue_id=450)
-- =====================================================
INSERT INTO aram_participant_local
SELECT
    match_id, champion_id, team_id, win,
    queue_id, platform_id, season, patch_version,
    assumeNotNull(tier) AS tier,
    assumeNotNull(primary_style_id) AS primary_style_id,
    assumeNotNull(primary_perk0) AS primary_perk0,
    assumeNotNull(primary_perk1) AS primary_perk1,
    assumeNotNull(primary_perk2) AS primary_perk2,
    assumeNotNull(primary_perk3) AS primary_perk3,
    assumeNotNull(sub_style_id) AS sub_style_id,
    assumeNotNull(sub_perk0) AS sub_perk0,
    assumeNotNull(sub_perk1) AS sub_perk1,
    assumeNotNull(stat_perk_defense) AS stat_perk_defense,
    assumeNotNull(stat_perk_flex) AS stat_perk_flex,
    assumeNotNull(stat_perk_offense) AS stat_perk_offense,
    summoner1id, summoner2id
FROM _stg_participants_raw
WHERE queue_id = 450
  AND tier IS NOT NULL
  AND patch_version IS NOT NULL;

-- =====================================================
-- [아레나] arena_participant_local 적재 (queue_id=1700)
-- =====================================================
INSERT INTO arena_participant_local
SELECT
    match_id, champion_id, team_id, win,
    queue_id, platform_id, season, patch_version,
    assumeNotNull(tier) AS tier,
    assumeNotNull(primary_style_id) AS primary_style_id,
    assumeNotNull(primary_perk0) AS primary_perk0,
    assumeNotNull(primary_perk1) AS primary_perk1,
    assumeNotNull(primary_perk2) AS primary_perk2,
    assumeNotNull(primary_perk3) AS primary_perk3,
    assumeNotNull(sub_style_id) AS sub_style_id,
    assumeNotNull(sub_perk0) AS sub_perk0,
    assumeNotNull(sub_perk1) AS sub_perk1,
    assumeNotNull(stat_perk_defense) AS stat_perk_defense,
    assumeNotNull(stat_perk_flex) AS stat_perk_flex,
    assumeNotNull(stat_perk_offense) AS stat_perk_offense,
    summoner1id, summoner2id
FROM _stg_participants_raw
WHERE queue_id = 1700
  AND tier IS NOT NULL
  AND patch_version IS NOT NULL;

-- =====================================================
-- 워터마크 갱신 (match PK 단일 워터마크)
-- =====================================================
INSERT INTO etl_watermarks (source_table, last_id, updated_at)
SELECT 'match', batch_max_id, now() FROM _batch_range
WHERE (SELECT count() FROM _stg_matches) > 0;

OPTIMIZE TABLE etl_watermarks FINAL;

-- =====================================================
-- 스테이징 테이블 정리
-- =====================================================
DROP TABLE IF EXISTS _stg_item_events;
DROP TABLE IF EXISTS _stg_item_events_raw;
DROP TABLE IF EXISTS _stg_skill_events;
DROP TABLE IF EXISTS _stg_skill_events_raw;
DROP TABLE IF EXISTS _stg_bans;
DROP TABLE IF EXISTS _stg_participants;
DROP TABLE IF EXISTS _stg_participants_raw;
DROP TABLE IF EXISTS _stg_matches;
DROP TABLE IF EXISTS _batch_range;
DROP TABLE IF EXISTS _watermarks;

-- =====================================================
-- [선택] 벌크 로드 시 Materialized View ATTACH + 집계 백필
-- =====================================================
-- ATTACH TABLE mv_champion_stats;
-- ATTACH TABLE mv_champion_bans;
-- ATTACH TABLE mv_match_count;
-- ATTACH TABLE mv_champion_rune_stats;
-- ATTACH TABLE mv_champion_spell_stats;
-- ATTACH TABLE mv_champion_skill_build_stats;
-- ATTACH TABLE mv_champion_start_item_stats;
-- ATTACH TABLE mv_champion_item_build_stats;
-- ATTACH TABLE mv_champion_item_stats;
-- ATTACH TABLE mv_champion_matchup_stats;
--
-- INSERT INTO champion_stats_agg
-- SELECT patch_version, platform_id, tier, champion_id, team_position,
--        count() AS games, sum(win) AS wins
-- FROM match_participant_local
-- WHERE queue_id IN (420, 440) AND team_position != ''
-- GROUP BY patch_version, platform_id, tier, champion_id, team_position;
--
-- INSERT INTO champion_bans_agg
-- SELECT patch_version, platform_id, tier, champion_id, count() AS bans
-- FROM match_ban_local
-- WHERE queue_id IN (420, 440) AND champion_id > 0
-- GROUP BY patch_version, platform_id, tier, champion_id;
--
-- INSERT INTO match_count_agg
-- SELECT patch_version, platform_id, tier, team_position, count() AS participant_rows
-- FROM match_participant_local
-- WHERE queue_id IN (420, 440) AND team_position != ''
-- GROUP BY patch_version, platform_id, tier, team_position;
--
-- INSERT INTO champion_rune_stats_agg
-- SELECT patch_version, platform_id, tier, champion_id, team_position,
--        primary_style_id, primary_perk0, primary_perk1, primary_perk2, primary_perk3,
--        sub_style_id, sub_perk0, sub_perk1,
--        stat_perk_defense, stat_perk_flex, stat_perk_offense,
--        count() AS games, sum(win) AS wins
-- FROM match_participant_local
-- WHERE queue_id IN (420, 440) AND team_position != ''
-- GROUP BY patch_version, platform_id, tier, champion_id, team_position,
--          primary_style_id, primary_perk0, primary_perk1, primary_perk2, primary_perk3,
--          sub_style_id, sub_perk0, sub_perk1,
--          stat_perk_defense, stat_perk_flex, stat_perk_offense;
--
-- INSERT INTO champion_spell_stats_agg
-- SELECT patch_version, platform_id, tier, champion_id, team_position,
--        summoner1id, summoner2id, count() AS games, sum(win) AS wins
-- FROM match_participant_local
-- WHERE queue_id IN (420, 440) AND team_position != ''
-- GROUP BY patch_version, platform_id, tier, champion_id, team_position,
--          summoner1id, summoner2id;
--
-- INSERT INTO champion_skill_build_stats_agg
-- SELECT patch_version, platform_id, tier, champion_id, team_position,
--        skill_build, count() AS games, sum(win) AS wins
-- FROM match_skill_build_local
-- WHERE queue_id IN (420, 440) AND team_position != ''
-- GROUP BY patch_version, platform_id, tier, champion_id, team_position, skill_build;
--
-- INSERT INTO champion_start_item_stats_agg
-- SELECT patch_version, platform_id, tier, champion_id, team_position,
--        start_items, count() AS games, sum(win) AS wins
-- FROM match_start_item_build_local
-- WHERE queue_id IN (420, 440) AND team_position != ''
-- GROUP BY patch_version, platform_id, tier, champion_id, team_position, start_items;
--
-- INSERT INTO champion_item_build_stats_agg
-- SELECT patch_version, platform_id, tier, champion_id, team_position,
--        item_build, count() AS games, sum(win) AS wins
-- FROM match_item_build_local
-- WHERE queue_id IN (420, 440) AND team_position != ''
-- GROUP BY patch_version, platform_id, tier, champion_id, team_position, item_build;
--
-- INSERT INTO champion_item_stats_agg
-- SELECT patch_version, platform_id, tier, champion_id, team_position,
--        item_id, item_order, count() AS games, sum(win) AS wins
-- FROM match_final_item_local
-- WHERE queue_id IN (420, 440) AND team_position != ''
-- GROUP BY patch_version, platform_id, tier, champion_id, team_position, item_id, item_order;
--
-- INSERT INTO champion_matchup_stats_agg
-- SELECT patch_version, platform_id, tier, champion_id, team_position,
--        opponent_champion_id, count() AS games, sum(win) AS wins
-- FROM match_matchup_local
-- WHERE queue_id IN (420, 440) AND team_position != ''
-- GROUP BY patch_version, platform_id, tier, champion_id, team_position, opponent_champion_id;
