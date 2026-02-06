package com.mmrtr.lol.controller.spectator.response;

import com.mmrtr.lol.domain.spectator.domain.GameParticipant;

public record ParticipantResponse(
        String riotId,
        String puuid,
        long championId,
        long teamId,
        long spell1Id,
        long spell2Id,
        boolean bot,
        int lastSelectedSkinIndex,
        long profileIconId,
        PerksResponse perks
) {
    public static ParticipantResponse of(GameParticipant participant) {
        return new ParticipantResponse(
                participant.getRiotId(),
                participant.getPuuid(),
                participant.getChampionId(),
                participant.getTeamId(),
                participant.getSpell1Id(),
                participant.getSpell2Id(),
                participant.isBot(),
                participant.getLastSelectedSkinIndex(),
                participant.getProfileIconId(),
                PerksResponse.of(participant.getPerks())
        );
    }
}
