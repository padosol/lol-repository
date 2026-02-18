-- match_summoner: 개별 참가자 티어
ALTER TABLE match_summoner ADD COLUMN tier VARCHAR(20);
ALTER TABLE match_summoner ADD COLUMN tier_rank VARCHAR(5);
ALTER TABLE match_summoner ADD COLUMN absolute_points INTEGER;

-- match: 평균 티어
ALTER TABLE match ADD COLUMN average_tier INTEGER;
