-- =====================================================
-- 05: PostgreSQL → ClickHouse 데이터 적재 (수동 실행)
-- =====================================================
-- 01_pg_source_tables.sql 실행 후 사용하세요.
-- 증분 로드: WHERE 절에 patch_version 필터를 추가하여 특정 패치만 로드 가능
--   예) AND m.patch_version = '15.1'
-- =====================================================

-- match_participant_local 적재
INSERT INTO match_participant_local
SELECT
    m.match_id,
    mp.champion_id,
    mp.team_position,
    mp.team_id,
    mp.win,
    m.queue_id,
    m.platform_id,
    m.patch_version,
    assumeNotNull(mp.tier) AS tier,
    assumeNotNull(mp.primary_style_id) AS primary_style_id,
    assumeNotNull(mp.primary_perk0) AS primary_perk0,
    assumeNotNull(mp.primary_perk1) AS primary_perk1,
    assumeNotNull(mp.primary_perk2) AS primary_perk2,
    assumeNotNull(mp.primary_perk3) AS primary_perk3,
    assumeNotNull(mp.sub_style_id) AS sub_style_id,
    assumeNotNull(mp.sub_perk0) AS sub_perk0,
    assumeNotNull(mp.sub_perk1) AS sub_perk1,
    assumeNotNull(mp.stat_perk_defense) AS stat_perk_defense,
    assumeNotNull(mp.stat_perk_flex) AS stat_perk_flex,
    assumeNotNull(mp.stat_perk_offense) AS stat_perk_offense,
    mp.summoner1id,
    mp.summoner2id
FROM pg_match AS m
INNER JOIN pg_match_participant AS mp ON m.match_id = mp.match_id
WHERE m.queue_id = 420
  AND mp.tier IS NOT NULL
  AND m.patch_version IS NOT NULL
  AND mp.team_position != ''
  AND m.match_id NOT IN (SELECT DISTINCT match_id FROM match_participant_local);

-- match_matchup_local 적재 (같은 매치, 같은 라인, 다른 팀 셀프조인)
INSERT INTO match_matchup_local
SELECT
    p1.match_id,
    p1.champion_id,
    p2.champion_id AS opponent_champion_id,
    p1.team_position,
    p1.win,
    p1.queue_id,
    p1.platform_id,
    p1.patch_version,
    p1.tier
FROM match_participant_local AS p1
INNER JOIN match_participant_local AS p2
    ON  p1.match_id      = p2.match_id
    AND p1.team_position  = p2.team_position
    AND p1.team_id       != p2.team_id
WHERE p1.queue_id = 420
  AND p1.team_position != ''
  AND p1.match_id NOT IN (SELECT DISTINCT match_id FROM match_matchup_local);

-- match_ban_local 적재
INSERT INTO match_ban_local
SELECT
    m.match_id,
    mb.champion_id,
    mb.team_id,
    mb.pick_turn,
    m.queue_id,
    m.platform_id,
    m.patch_version,
    assumeNotNull(mp.tier) AS tier
FROM pg_match AS m
INNER JOIN pg_match_ban AS mb ON m.match_id = mb.match_id
INNER JOIN pg_match_participant AS mp
    ON mb.match_id = mp.match_id
   AND mp.participant_id = mb.pick_turn
WHERE m.queue_id = 420
  AND mp.tier IS NOT NULL
  AND m.patch_version IS NOT NULL
  AND m.match_id NOT IN (SELECT DISTINCT match_id FROM match_ban_local);

-- match_skill_build_local 적재
INSERT INTO match_skill_build_local
SELECT
    sub.match_id,
    sub.champion_id,
    sub.team_position,
    sub.win,
    sub.queue_id,
    sub.platform_id,
    sub.patch_version,
    sub.tier,
    sub.skill_build
FROM (
    SELECT
        ordered.match_id,
        ordered.champion_id,
        ordered.team_position,
        ordered.win,
        ordered.queue_id,
        ordered.platform_id,
        ordered.patch_version,
        ordered.tier,
        ordered.participant_id,
        arrayStringConcat(
            arraySlice(
                groupArray(toString(ordered.skill_slot)),
                1, 15
            ),
            ','
        ) AS skill_build
    FROM (
        SELECT
            sk.match_id AS match_id,
            sk.participant_id AS participant_id,
            sk.skill_slot AS skill_slot,
            sk.timestamp AS timestamp,
            mp.champion_id AS champion_id,
            mp.team_position AS team_position,
            mp.win AS win,
            m.queue_id AS queue_id,
            m.platform_id AS platform_id,
            m.patch_version AS patch_version,
            assumeNotNull(mp.tier) AS tier
        FROM pg_skill_events_flat AS sk
        INNER JOIN pg_match_participant AS mp
            ON sk.match_id = mp.match_id
           AND sk.participant_id = mp.participant_id
        INNER JOIN pg_match AS m
            ON sk.match_id = m.match_id
        WHERE m.queue_id = 420
          AND mp.tier IS NOT NULL
          AND m.patch_version IS NOT NULL
          AND mp.team_position != ''
          AND sk.match_id NOT IN (SELECT DISTINCT match_id FROM match_skill_build_local)
        ORDER BY sk.match_id, sk.participant_id, sk.timestamp ASC
    ) AS ordered
    GROUP BY
        ordered.match_id, ordered.champion_id, ordered.team_position,
        ordered.win, ordered.queue_id, ordered.platform_id, ordered.patch_version,
        ordered.tier, ordered.participant_id
    HAVING count(*) >= 15
) AS sub;

-- match_start_item_build_local 적재 (ITEM_UNDO 처리 포함)
INSERT INTO match_start_item_build_local
SELECT
    agg.match_id, agg.champion_id, agg.team_position, agg.win,
    agg.queue_id, agg.platform_id, agg.patch_version, agg.tier,
    agg.start_items
FROM (
    SELECT
        net.match_id, net.champion_id, net.team_position, net.win,
        net.queue_id, net.platform_id, net.patch_version, net.tier,
        net.participant_id,
        arrayStringConcat(
            arraySort(
                arrayFlatten(
                    groupArray(
                        arrayWithConstant(
                            assumeNotNull(toUInt32(net.net_count)),
                            toString(net.effective_item_id)
                        )
                    )
                )
            ),
            ','
        ) AS start_items
    FROM (
        SELECT
            ie.match_id AS match_id,
            ie.participant_id AS participant_id,
            CASE
                WHEN ie.type = 'ITEM_PURCHASED' THEN ie.item_id
                WHEN ie.type = 'ITEM_UNDO'      THEN ie.before_id
            END AS effective_item_id,
            sum(CASE
                WHEN ie.type = 'ITEM_PURCHASED' THEN 1
                WHEN ie.type = 'ITEM_UNDO'      THEN -1
            END) AS net_count,
            any(mp.champion_id)              AS champion_id,
            any(mp.team_position)            AS team_position,
            any(mp.win)                      AS win,
            any(m.queue_id)                  AS queue_id,
            any(m.platform_id)               AS platform_id,
            any(m.patch_version)             AS patch_version,
            any(assumeNotNull(mp.tier))      AS tier
        FROM pg_item_event AS ie
        INNER JOIN pg_match_participant AS mp
            ON ie.match_id = mp.match_id AND ie.participant_id = mp.participant_id
        INNER JOIN pg_match AS m
            ON ie.match_id = m.match_id
        WHERE ie.timestamp <= 60000
          AND ie.type IN ('ITEM_PURCHASED', 'ITEM_UNDO')
          AND ie.item_id NOT IN (3340, 3363, 3364)    -- 장신구 제외
          AND m.queue_id = 420
          AND mp.tier IS NOT NULL
          AND m.patch_version IS NOT NULL
          AND mp.team_position != ''
          AND ie.match_id NOT IN (SELECT DISTINCT match_id FROM match_start_item_build_local)
        GROUP BY ie.match_id, ie.participant_id,
                 CASE
                     WHEN ie.type = 'ITEM_PURCHASED' THEN ie.item_id
                     WHEN ie.type = 'ITEM_UNDO'      THEN ie.before_id
                 END
        HAVING net_count > 0
    ) AS net
    GROUP BY net.match_id, net.participant_id,
             net.champion_id, net.team_position, net.win,
             net.queue_id, net.platform_id, net.patch_version, net.tier
    HAVING start_items != ''
) AS agg;

-- legendary_items 참조 데이터 적재 (전체 교체)
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

-- match_item_build_local 적재 (match_final_item_local에서 3코어 빌드 순서 추출)
-- 1) item_order <= 3인 행만 대상
-- 2) groupArray + arraySort로 item_order순 정렬 → arrayStringConcat
-- 3) 3코어 미만 게임은 HAVING count(*) = 3으로 제외
INSERT INTO match_item_build_local
SELECT
    match_id, champion_id, team_position, win,
    queue_id, platform_id, patch_version, tier,
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
  AND queue_id = 420
  AND team_position != ''
  AND match_id NOT IN (SELECT DISTINCT match_id FROM match_item_build_local)
GROUP BY match_id, champion_id, team_position,
         win, queue_id, platform_id, patch_version, tier
HAVING count(*) = 3;

-- match_final_item_local 적재 (전설급 아이템만, item_event JOIN으로 구매 순서 결정)
-- 1) item0-item5에서 전설급 아이템 추출 (최종 빌드 기준)
-- 2) pg_item_event ITEM_PURCHASED와 JOIN → 각 아이템의 MIN(timestamp) 획득
-- 3) ROW_NUMBER()로 코어 순서 부여
INSERT INTO match_final_item_local
SELECT
    ranked.match_id, ranked.champion_id, ranked.team_position, ranked.win,
    ranked.queue_id, ranked.platform_id, ranked.patch_version, ranked.tier,
    ranked.item_id, ranked.item_order
FROM (
    SELECT
        joined.match_id, joined.champion_id, joined.team_position, joined.win,
        joined.queue_id, joined.platform_id, joined.patch_version, joined.tier,
        joined.item_id,
        row_number() OVER (
            PARTITION BY joined.match_id, joined.participant_id
            ORDER BY joined.purchase_ts ASC
        ) AS item_order
    FROM (
        SELECT
            fi.match_id, fi.participant_id,
            fi.champion_id, fi.team_position, fi.win,
            fi.queue_id, fi.platform_id, fi.patch_version, fi.tier,
            fi.item_id,
            min(ie.timestamp) AS purchase_ts
        FROM (
            SELECT
                m.match_id, mp.participant_id,
                mp.champion_id, mp.team_position, mp.win,
                m.queue_id, m.platform_id, m.patch_version,
                assumeNotNull(mp.tier) AS tier,
                arrayJoin(
                    arrayFilter(x -> x != 0, [mp.item0, mp.item1, mp.item2, mp.item3, mp.item4, mp.item5])
                ) AS item_id
            FROM pg_match AS m
            INNER JOIN pg_match_participant AS mp ON m.match_id = mp.match_id
            WHERE m.queue_id = 420
              AND mp.tier IS NOT NULL
              AND m.patch_version IS NOT NULL
              AND mp.team_position != ''
              AND m.match_id NOT IN (SELECT DISTINCT match_id FROM match_final_item_local)
        ) AS fi
        INNER JOIN pg_item_event AS ie
            ON fi.match_id = ie.match_id
           AND fi.participant_id = ie.participant_id
           AND fi.item_id = ie.item_id
           AND ie.type = 'ITEM_PURCHASED'
        WHERE fi.item_id IN (SELECT item_id FROM legendary_items)
        GROUP BY fi.match_id, fi.participant_id,
                 fi.champion_id, fi.team_position, fi.win,
                 fi.queue_id, fi.platform_id, fi.patch_version, fi.tier,
                 fi.item_id
    ) AS joined
) AS ranked;
