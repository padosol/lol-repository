package com.mmrtr.lol.infra.persistence.match.entity.value.matchsummoner;

import com.mmrtr.lol.infra.riot.dto.match.ParticipantDto;
import jakarta.persistence.Embeddable;
import lombok.*;
import org.hibernate.annotations.Comment;

@Getter
@Setter
@Embeddable
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArenaValue {

    @Comment("최종 순위 (아레나)")
    private int placement;
    @Comment("서브팀 ID (아레나)")
    private int playerSubteamId;
    @Comment("서브팀 순위 (아레나)")
    private int subteamPlacement;

    @Comment("플레이어 증강 1")
    private int playerAugment1;
    @Comment("플레이어 증강 2")
    private int playerAugment2;
    @Comment("플레이어 증강 3")
    private int playerAugment3;
    @Comment("플레이어 증강 4")
    private int playerAugment4;
    @Comment("플레이어 증강 5")
    private int playerAugment5;
    @Comment("플레이어 증강 6")
    private int playerAugment6;

    public ArenaValue(ParticipantDto participantDto) {
        this.placement = participantDto.getPlacement();
        this.playerSubteamId = participantDto.getPlayerSubteamId();
        this.subteamPlacement = participantDto.getSubteamPlacement();
        this.playerAugment1 = participantDto.getPlayerAugment1();
        this.playerAugment2 = participantDto.getPlayerAugment2();
        this.playerAugment3 = participantDto.getPlayerAugment3();
        this.playerAugment4 = participantDto.getPlayerAugment4();
        this.playerAugment5 = participantDto.getPlayerAugment5();
        this.playerAugment6 = participantDto.getPlayerAugment6();
    }
}
