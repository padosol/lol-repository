-- Match 관련 모든 테이블 일괄 TRUNCATE (개발/테스트 환경 전용)
-- CASCADE 옵션으로 FK 참조까지 안전하게 처리
TRUNCATE TABLE
    match,
    match_participant,
    match_participant_challenges,
    match_team,
    match_ban,
    participant_frame,
    building_events,
    champion_special_kill_event,
    game_end_event,
    item_event,
    kill_event,
    level_up_event,
    skill_level_up_event,
    ward_event,
    turret_plate_destroyed_event
CASCADE;
