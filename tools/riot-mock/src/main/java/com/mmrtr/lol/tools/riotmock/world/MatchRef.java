package com.mmrtr.lol.tools.riotmock.world;

/**
 * 매치 ID 하나가 가리키는 게임. 시작 시각은 ID 만으로 정해진다.
 *
 * @param kind        PERSONAL(개인 기본 기록) / SHARED(그룹 공유 기록) / EPOCH(재방문 소환사의 새 게임)
 * @param owner       PERSONAL·EPOCH 는 소환사 번호 n, SHARED 는 그룹 번호 g
 * @param epoch       EPOCH 일 때만 의미가 있다(그 밖에는 0)
 * @param index       PERSONAL·SHARED 는 기록 번호 k, EPOCH 는 j
 * @param startMillis 게임 시작 시각(ms)
 */
public record MatchRef(Kind kind, int owner, int epoch, int index, long startMillis) {

    public enum Kind { PERSONAL, SHARED, EPOCH }

    public long endMillis() {
        return startMillis + LabIds.GAME_MILLIS;
    }

    public String matchId() {
        return switch (kind) {
            case PERSONAL -> LabIds.personalMatchId(owner, index);
            case SHARED -> LabIds.sharedMatchId(owner, index);
            case EPOCH -> LabIds.epochMatchId(epoch, owner, index);
        };
    }
}
