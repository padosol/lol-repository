package com.mmrtr.lol.domain.summoner.readmodel;

import java.util.List;

/**
 * lol-server duo 가 모집글·신청 생성 시점에 스냅샷으로 저장하는 Riot 계정 정보.
 * JSON 형태는 lol-server {@code RiotAccountStats} 와 같아서 그대로 역직렬화할 수 있다.
 * {@code gameName}·{@code tagLine} 은 매칭이 확정된 상대에게 보여 주는 Riot ID 다 (lol-server 는 summoner 를 읽지 않는다).
 */
public record DuoProfileReadModel(
        String gameName,
        String tagLine,
        TierInfo tierInfo,
        List<MostChampion> mostChampions,
        RecentGameSummary recentGameSummary
) {

    /** 색인된 소환사의 Riot ID (포트 조회 행). */
    public record RiotId(String gameName, String tagLine) {
    }

    /** 솔로 랭크 티어. 솔로 랭크 기록이 없으면 {@link #UNRANKED}. */
    public record TierInfo(String tier, String rank, int leaguePoints) {

        public static final TierInfo UNRANKED = new TierInfo("UNRANKED", "I", 0);
    }

    public record MostChampion(int championId, String championName, long playCount, long wins, long losses) {
    }

    /** 최근 솔로 랭크 게임 요약. {@code playedChampions} 는 최신 게임부터. */
    public record RecentGameSummary(int wins, int losses, List<PlayedChampion> playedChampions) {

        public static RecentGameSummary from(List<RecentGame> games) {
            int wins = (int) games.stream().filter(RecentGame::win).count();
            List<PlayedChampion> playedChampions = games.stream()
                    .map(game -> new PlayedChampion(game.championId(), game.championName()))
                    .toList();
            return new RecentGameSummary(wins, games.size() - wins, playedChampions);
        }
    }

    public record PlayedChampion(int championId, String championName) {
    }

    /** 최근 게임 한 판에서 대상 소환사의 결과 (포트 조회 행). */
    public record RecentGame(boolean win, int championId, String championName) {
    }
}
