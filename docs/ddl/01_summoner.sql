-- =====================================================
-- Summoner Domain DDL
-- =====================================================
-- 소환사 정보를 저장하는 테이블
-- =====================================================

-- -----------------------------------------------------
-- Table: summoner
-- Description: 소환사 기본 정보
-- -----------------------------------------------------
CREATE TABLE summoner (
    puuid               VARCHAR(255)    NOT NULL,
    profile_icon_id     INTEGER         NOT NULL,
    summoner_level      BIGINT          NOT NULL,
    game_name           VARCHAR(255),
    tag_line            VARCHAR(255),
    region              VARCHAR(255),
    search_name         VARCHAR(255),
    revision_date       TIMESTAMP,
    revision_click_date TIMESTAMP,

    CONSTRAINT pk_summoner PRIMARY KEY (puuid)
);

-- Index for search optimization
CREATE INDEX idx_summoner_search_name ON summoner (search_name);
CREATE INDEX idx_summoner_region ON summoner (region);

COMMENT ON TABLE summoner IS '소환사 기본 정보 테이블';
COMMENT ON COLUMN summoner.puuid IS '플레이어 고유 식별자 (PUUID)';
COMMENT ON COLUMN summoner.profile_icon_id IS '프로필 아이콘 ID';
COMMENT ON COLUMN summoner.summoner_level IS '소환사 레벨';
COMMENT ON COLUMN summoner.game_name IS '게임 내 이름';
COMMENT ON COLUMN summoner.tag_line IS '태그라인 (#뒤의 식별자)';
COMMENT ON COLUMN summoner.region IS '서버 지역';
COMMENT ON COLUMN summoner.search_name IS '검색용 이름 (gameName#tagLine, 소문자, 공백제거)';
COMMENT ON COLUMN summoner.revision_date IS '정보 갱신 일시';
COMMENT ON COLUMN summoner.revision_click_date IS '갱신 버튼 클릭 일시';
