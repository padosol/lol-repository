-- =====================================================
-- Timeline Domain DDL
-- =====================================================
-- 매치 타임라인 이벤트 정보를 저장하는 테이블
-- =====================================================

-- -----------------------------------------------------
-- Table: time_line_event
-- Description: 타임라인 이벤트 프레임
-- -----------------------------------------------------
CREATE TABLE time_line_event (
    match_id    VARCHAR(255)    NOT NULL,
    timestamp   INTEGER         NOT NULL,

    CONSTRAINT pk_time_line_event PRIMARY KEY (match_id, timestamp)
);

COMMENT ON TABLE time_line_event IS '타임라인 이벤트 프레임 테이블';
COMMENT ON COLUMN time_line_event.match_id IS '매치 ID';
COMMENT ON COLUMN time_line_event.timestamp IS '타임스탬프 (밀리초)';


-- -----------------------------------------------------
-- Table: participant_frame
-- Description: 참가자 프레임 정보 (특정 시점의 상태)
-- -----------------------------------------------------
CREATE TABLE participant_frame (
    match_id                            VARCHAR(255)    NOT NULL,
    timestamp                           INTEGER         NOT NULL,
    participant_id                      INTEGER         NOT NULL,

    -- Embedded: ChampionStatsValue
    ability_haste                       INTEGER         NOT NULL,
    ability_power                       INTEGER         NOT NULL,
    armor                               INTEGER         NOT NULL,
    armor_pen                           INTEGER         NOT NULL,
    armor_pen_percent                   INTEGER         NOT NULL,
    attack_damage                       INTEGER         NOT NULL,
    attack_speed                        INTEGER         NOT NULL,
    bonus_armor_pen_percent             INTEGER         NOT NULL,
    bonus_magic_pen_percent             INTEGER         NOT NULL,
    cc_reduction                        INTEGER         NOT NULL,
    cooldown_reduction                  INTEGER         NOT NULL,
    health                              INTEGER         NOT NULL,
    health_max                          INTEGER         NOT NULL,
    health_regen                        INTEGER         NOT NULL,
    lifesteal                           INTEGER         NOT NULL,
    magic_pen                           INTEGER         NOT NULL,
    magic_pen_percent                   INTEGER         NOT NULL,
    magic_resist                        INTEGER         NOT NULL,
    movement_speed                      INTEGER         NOT NULL,
    omnivamp                            INTEGER         NOT NULL,
    physical_vamp                       INTEGER         NOT NULL,
    power                               INTEGER         NOT NULL,
    power_max                           INTEGER         NOT NULL,
    power_regen                         INTEGER         NOT NULL,
    spell_vamp                          INTEGER         NOT NULL,

    -- 기본 프레임 정보
    current_gold                        INTEGER         NOT NULL,
    gold_per_second                     INTEGER         NOT NULL,
    jungle_minions_killed               INTEGER         NOT NULL,
    level                               INTEGER         NOT NULL,
    minions_killed                      INTEGER         NOT NULL,
    time_enemy_spent_controlled         INTEGER         NOT NULL,
    total_gold                          INTEGER         NOT NULL,
    xp                                  INTEGER         NOT NULL,

    -- Embedded: DamageStatsValue
    magic_damage_done                   INTEGER         NOT NULL,
    magic_damage_done_to_champions      INTEGER         NOT NULL,
    magic_damage_taken                  INTEGER         NOT NULL,
    physical_damage_done                INTEGER         NOT NULL,
    physical_damage_done_to_champions   INTEGER         NOT NULL,
    physical_damage_taken               INTEGER         NOT NULL,
    total_damage_done                   INTEGER         NOT NULL,
    total_damage_done_to_champions      INTEGER         NOT NULL,
    total_damage_taken                  INTEGER         NOT NULL,
    true_damage_done                    INTEGER         NOT NULL,
    true_damage_done_to_champions       INTEGER         NOT NULL,
    true_damage_taken                   INTEGER         NOT NULL,

    -- Embedded: PositionValue
    x                                   INTEGER         NOT NULL,
    y                                   INTEGER         NOT NULL,

    CONSTRAINT pk_participant_frame PRIMARY KEY (match_id, timestamp, participant_id)
);

CREATE INDEX idx_participant_frame_match_id ON participant_frame (match_id);

COMMENT ON TABLE participant_frame IS '참가자 프레임 정보 테이블 (특정 시점의 상태)';
COMMENT ON COLUMN participant_frame.match_id IS '매치 ID';
COMMENT ON COLUMN participant_frame.timestamp IS '타임스탬프 (밀리초)';
COMMENT ON COLUMN participant_frame.participant_id IS '참가자 ID (1-10)';
COMMENT ON COLUMN participant_frame.current_gold IS '현재 보유 골드';
COMMENT ON COLUMN participant_frame.total_gold IS '총 획득 골드';
COMMENT ON COLUMN participant_frame.level IS '챔피언 레벨';
COMMENT ON COLUMN participant_frame.xp IS '경험치';
COMMENT ON COLUMN participant_frame.x IS 'X 좌표';
COMMENT ON COLUMN participant_frame.y IS 'Y 좌표';


-- -----------------------------------------------------
-- Table: game_events
-- Description: 게임 이벤트 (게임 시작/종료)
-- -----------------------------------------------------
CREATE TABLE game_events (
    game_event_id       BIGINT          NOT NULL GENERATED BY DEFAULT AS IDENTITY,
    match_id            VARCHAR(255),
    timeline_timestamp  INTEGER,
    timestamp           BIGINT          NOT NULL,
    game_id             BIGINT          NOT NULL,
    real_timestamp      BIGINT          NOT NULL,
    winning_team        INTEGER         NOT NULL,
    type                VARCHAR(255),

    CONSTRAINT pk_game_events PRIMARY KEY (game_event_id)
);

CREATE INDEX idx_game_events_match_id_timestamp ON game_events (match_id, timeline_timestamp);

COMMENT ON TABLE game_events IS '게임 이벤트 테이블 (게임 시작/종료)';
COMMENT ON COLUMN game_events.game_event_id IS '게임 이벤트 고유 식별자';
COMMENT ON COLUMN game_events.match_id IS '매치 ID';
COMMENT ON COLUMN game_events.timeline_timestamp IS '타임라인 타임스탬프';
COMMENT ON COLUMN game_events.timestamp IS '이벤트 타임스탬프';
COMMENT ON COLUMN game_events.game_id IS '게임 ID';
COMMENT ON COLUMN game_events.real_timestamp IS '실제 타임스탬프 (epoch)';
COMMENT ON COLUMN game_events.winning_team IS '승리 팀 ID';
COMMENT ON COLUMN game_events.type IS '이벤트 타입';


-- -----------------------------------------------------
-- Table: building_events
-- Description: 건물 파괴 이벤트
-- -----------------------------------------------------
CREATE TABLE building_events (
    building_event_id           BIGINT          NOT NULL GENERATED BY DEFAULT AS IDENTITY,
    match_id                    VARCHAR(255),
    timeline_timestamp          INTEGER,
    assisting_participant_ids   VARCHAR(255),
    bounty                      INTEGER         NOT NULL,
    building_type               VARCHAR(255),
    killer_id                   INTEGER         NOT NULL,
    lane_type                   VARCHAR(255),
    x                           INTEGER         NOT NULL,
    y                           INTEGER         NOT NULL,
    team_id                     INTEGER         NOT NULL,
    timestamp                   BIGINT          NOT NULL,
    tower_type                  VARCHAR(255),
    type                        VARCHAR(255),

    CONSTRAINT pk_building_events PRIMARY KEY (building_event_id)
);

CREATE INDEX idx_building_events_match_id_timestamp ON building_events (match_id, timeline_timestamp);

COMMENT ON TABLE building_events IS '건물 파괴 이벤트 테이블';
COMMENT ON COLUMN building_events.building_event_id IS '건물 이벤트 고유 식별자';
COMMENT ON COLUMN building_events.match_id IS '매치 ID';
COMMENT ON COLUMN building_events.timeline_timestamp IS '타임라인 타임스탬프';
COMMENT ON COLUMN building_events.building_type IS '건물 타입 (TOWER_BUILDING, INHIBITOR_BUILDING)';
COMMENT ON COLUMN building_events.tower_type IS '타워 타입 (OUTER_TURRET, INNER_TURRET, BASE_TURRET, NEXUS_TURRET)';
COMMENT ON COLUMN building_events.lane_type IS '라인 타입 (TOP_LANE, MID_LANE, BOT_LANE)';
COMMENT ON COLUMN building_events.killer_id IS '파괴자 참가자 ID';
COMMENT ON COLUMN building_events.team_id IS '건물 소속 팀 ID';


-- -----------------------------------------------------
-- Table: kill_events
-- Description: 챔피언 킬 이벤트
-- -----------------------------------------------------
CREATE TABLE kill_events (
    kill_event_id               BIGINT          NOT NULL GENERATED BY DEFAULT AS IDENTITY,
    match_id                    VARCHAR(255),
    timeline_timestamp          INTEGER,
    assisting_participant_ids   VARCHAR(255),
    bounty                      INTEGER         NOT NULL,
    kill_streak_length          INTEGER         NOT NULL,
    killer_id                   INTEGER         NOT NULL,
    x                           INTEGER         NOT NULL,
    y                           INTEGER         NOT NULL,
    shutdown_bounty             INTEGER         NOT NULL,
    victim_id                   INTEGER         NOT NULL,
    timestamp                   BIGINT          NOT NULL,
    type                        VARCHAR(255),

    CONSTRAINT pk_kill_events PRIMARY KEY (kill_event_id)
);

CREATE INDEX idx_kill_events_match_id_timestamp ON kill_events (match_id, timeline_timestamp);
CREATE INDEX idx_kill_events_killer_id ON kill_events (killer_id);
CREATE INDEX idx_kill_events_victim_id ON kill_events (victim_id);

COMMENT ON TABLE kill_events IS '챔피언 킬 이벤트 테이블';
COMMENT ON COLUMN kill_events.kill_event_id IS '킬 이벤트 고유 식별자';
COMMENT ON COLUMN kill_events.match_id IS '매치 ID';
COMMENT ON COLUMN kill_events.killer_id IS '킬러 참가자 ID';
COMMENT ON COLUMN kill_events.victim_id IS '피해자 참가자 ID';
COMMENT ON COLUMN kill_events.bounty IS '현상금';
COMMENT ON COLUMN kill_events.shutdown_bounty IS '셧다운 현상금';
COMMENT ON COLUMN kill_events.kill_streak_length IS '연속 킬 수';
COMMENT ON COLUMN kill_events.assisting_participant_ids IS '어시스트 참가자 ID 목록 (쉼표 구분)';


-- -----------------------------------------------------
-- Table: item_events
-- Description: 아이템 이벤트 (구매/판매/철거)
-- -----------------------------------------------------
CREATE TABLE item_events (
    item_event_id       BIGINT          NOT NULL GENERATED BY DEFAULT AS IDENTITY,
    match_id            VARCHAR(255),
    timeline_timestamp  INTEGER,
    item_id             INTEGER         NOT NULL,
    participant_id      INTEGER         NOT NULL,
    timestamp           BIGINT          NOT NULL,
    type                VARCHAR(255),
    after_id            INTEGER         NOT NULL,
    before_id           INTEGER         NOT NULL,
    gold_gain           INTEGER         NOT NULL,

    CONSTRAINT pk_item_events PRIMARY KEY (item_event_id)
);

CREATE INDEX idx_item_events_match_id_timestamp ON item_events (match_id, timeline_timestamp);
CREATE INDEX idx_item_events_participant_id ON item_events (participant_id);

COMMENT ON TABLE item_events IS '아이템 이벤트 테이블 (구매/판매/철거)';
COMMENT ON COLUMN item_events.item_event_id IS '아이템 이벤트 고유 식별자';
COMMENT ON COLUMN item_events.match_id IS '매치 ID';
COMMENT ON COLUMN item_events.item_id IS '아이템 ID';
COMMENT ON COLUMN item_events.participant_id IS '참가자 ID';
COMMENT ON COLUMN item_events.type IS '이벤트 타입 (ITEM_PURCHASED, ITEM_SOLD, ITEM_DESTROYED, ITEM_UNDO)';
COMMENT ON COLUMN item_events.after_id IS '변환 후 아이템 ID';
COMMENT ON COLUMN item_events.before_id IS '변환 전 아이템 ID';
COMMENT ON COLUMN item_events.gold_gain IS '획득/손실 골드';


-- -----------------------------------------------------
-- Table: skill_events
-- Description: 스킬 레벨업 이벤트
-- -----------------------------------------------------
CREATE TABLE skill_events (
    skill_event_id      BIGINT          NOT NULL GENERATED BY DEFAULT AS IDENTITY,
    match_id            VARCHAR(255),
    timeline_timestamp  INTEGER,
    skill_slot          INTEGER         NOT NULL,
    participant_id      INTEGER         NOT NULL,
    level_up_type       VARCHAR(255),
    timestamp           BIGINT          NOT NULL,
    type                VARCHAR(255),

    CONSTRAINT pk_skill_events PRIMARY KEY (skill_event_id)
);

CREATE INDEX idx_skill_events_match_id_timestamp ON skill_events (match_id, timeline_timestamp);
CREATE INDEX idx_skill_events_participant_id ON skill_events (participant_id);

COMMENT ON TABLE skill_events IS '스킬 레벨업 이벤트 테이블';
COMMENT ON COLUMN skill_events.skill_event_id IS '스킬 이벤트 고유 식별자';
COMMENT ON COLUMN skill_events.match_id IS '매치 ID';
COMMENT ON COLUMN skill_events.skill_slot IS '스킬 슬롯 (1: Q, 2: W, 3: E, 4: R)';
COMMENT ON COLUMN skill_events.participant_id IS '참가자 ID';
COMMENT ON COLUMN skill_events.level_up_type IS '레벨업 타입 (NORMAL, EVOLVE)';
COMMENT ON COLUMN skill_events.type IS '이벤트 타입';


-- -----------------------------------------------------
-- Table: ward_events
-- Description: 와드 이벤트 (설치/파괴)
-- -----------------------------------------------------
CREATE TABLE ward_events (
    ward_event_id       BIGINT          NOT NULL GENERATED BY DEFAULT AS IDENTITY,
    match_id            VARCHAR(255),
    timeline_timestamp  INTEGER,
    participant_id      INTEGER         NOT NULL,
    ward_type           VARCHAR(255),
    timestamp           BIGINT          NOT NULL,
    type                VARCHAR(255),

    CONSTRAINT pk_ward_events PRIMARY KEY (ward_event_id)
);

CREATE INDEX idx_ward_events_match_id_timestamp ON ward_events (match_id, timeline_timestamp);
CREATE INDEX idx_ward_events_participant_id ON ward_events (participant_id);

COMMENT ON TABLE ward_events IS '와드 이벤트 테이블 (설치/파괴)';
COMMENT ON COLUMN ward_events.ward_event_id IS '와드 이벤트 고유 식별자';
COMMENT ON COLUMN ward_events.match_id IS '매치 ID';
COMMENT ON COLUMN ward_events.participant_id IS '참가자 ID';
COMMENT ON COLUMN ward_events.ward_type IS '와드 타입 (YELLOW_TRINKET, CONTROL_WARD, SIGHT_WARD, BLUE_TRINKET)';
COMMENT ON COLUMN ward_events.type IS '이벤트 타입 (WARD_PLACED, WARD_KILL)';


-- -----------------------------------------------------
-- Table: champion_special_kill_event
-- Description: 특수 킬 이벤트 (멀티킬, 첫 번째 피 등)
-- -----------------------------------------------------
CREATE TABLE champion_special_kill_event (
    champion_special_kill_event_id  BIGINT          NOT NULL GENERATED BY DEFAULT AS IDENTITY,
    match_id                        VARCHAR(255),
    timeline_timestamp              INTEGER,
    kill_type                       VARCHAR(255),
    killer_id                       INTEGER         NOT NULL,
    multi_kill_length               INTEGER         NOT NULL,
    x                               INTEGER         NOT NULL,
    y                               INTEGER         NOT NULL,
    timestamp                       BIGINT          NOT NULL,
    type                            VARCHAR(255),

    CONSTRAINT pk_champion_special_kill_event PRIMARY KEY (champion_special_kill_event_id)
);

CREATE INDEX idx_champion_special_kill_match_timestamp ON champion_special_kill_event (match_id, timeline_timestamp);

COMMENT ON TABLE champion_special_kill_event IS '특수 킬 이벤트 테이블 (멀티킬, 첫 번째 피 등)';
COMMENT ON COLUMN champion_special_kill_event.champion_special_kill_event_id IS '특수 킬 이벤트 고유 식별자';
COMMENT ON COLUMN champion_special_kill_event.match_id IS '매치 ID';
COMMENT ON COLUMN champion_special_kill_event.kill_type IS '킬 타입 (KILL_FIRST_BLOOD, KILL_MULTI, KILL_ACE)';
COMMENT ON COLUMN champion_special_kill_event.killer_id IS '킬러 참가자 ID';
COMMENT ON COLUMN champion_special_kill_event.multi_kill_length IS '멀티킬 횟수 (2: 더블킬, 3: 트리플킬, 4: 쿼드라킬, 5: 펜타킬)';


-- -----------------------------------------------------
-- Table: level_events
-- Description: 레벨업 이벤트
-- -----------------------------------------------------
CREATE TABLE level_events (
    level_event_id      BIGINT          NOT NULL GENERATED BY DEFAULT AS IDENTITY,
    match_id            VARCHAR(255),
    timeline_timestamp  INTEGER,
    level               INTEGER         NOT NULL,
    participant_id      INTEGER         NOT NULL,
    timestamp           BIGINT          NOT NULL,
    type                VARCHAR(255),

    CONSTRAINT pk_level_events PRIMARY KEY (level_event_id)
);

CREATE INDEX idx_level_events_match_id_timestamp ON level_events (match_id, timeline_timestamp);
CREATE INDEX idx_level_events_participant_id ON level_events (participant_id);

COMMENT ON TABLE level_events IS '레벨업 이벤트 테이블';
COMMENT ON COLUMN level_events.level_event_id IS '레벨 이벤트 고유 식별자';
COMMENT ON COLUMN level_events.match_id IS '매치 ID';
COMMENT ON COLUMN level_events.level IS '달성 레벨';
COMMENT ON COLUMN level_events.participant_id IS '참가자 ID';
COMMENT ON COLUMN level_events.type IS '이벤트 타입';


-- -----------------------------------------------------
-- Table: turret_plate_destroyed_event
-- Description: 포탑 플레이트 파괴 이벤트
-- -----------------------------------------------------
CREATE TABLE turret_plate_destroyed_event (
    turret_plate_destroyed_event_id BIGINT          NOT NULL GENERATED BY DEFAULT AS IDENTITY,
    match_id                        VARCHAR(255),
    timeline_timestamp              INTEGER,
    killer_id                       INTEGER         NOT NULL,
    lane_type                       VARCHAR(255),
    x                               INTEGER         NOT NULL,
    y                               INTEGER         NOT NULL,
    team_id                         INTEGER         NOT NULL,
    timestamp                       BIGINT          NOT NULL,
    type                            VARCHAR(255),

    CONSTRAINT pk_turret_plate_destroyed_event PRIMARY KEY (turret_plate_destroyed_event_id)
);

CREATE INDEX idx_turret_plate_match_timestamp ON turret_plate_destroyed_event (match_id, timeline_timestamp);

COMMENT ON TABLE turret_plate_destroyed_event IS '포탑 플레이트 파괴 이벤트 테이블';
COMMENT ON COLUMN turret_plate_destroyed_event.turret_plate_destroyed_event_id IS '포탑 플레이트 이벤트 고유 식별자';
COMMENT ON COLUMN turret_plate_destroyed_event.match_id IS '매치 ID';
COMMENT ON COLUMN turret_plate_destroyed_event.killer_id IS '파괴자 참가자 ID';
COMMENT ON COLUMN turret_plate_destroyed_event.lane_type IS '라인 타입 (TOP_LANE, MID_LANE, BOT_LANE)';
COMMENT ON COLUMN turret_plate_destroyed_event.team_id IS '포탑 소속 팀 ID';
