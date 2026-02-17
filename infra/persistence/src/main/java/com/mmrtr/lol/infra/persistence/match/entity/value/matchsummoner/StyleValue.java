package com.mmrtr.lol.infra.persistence.match.entity.value.matchsummoner;


import com.mmrtr.lol.infra.riot.dto.match.ParticipantDto;
import com.mmrtr.lol.infra.riot.dto.match.PerkStyleDto;
import com.mmrtr.lol.infra.riot.dto.match.PerkStyleSelectionDto;
import jakarta.persistence.Embeddable;
import lombok.*;
import org.hibernate.annotations.Comment;

import java.util.List;

@Getter
@Setter
@Embeddable
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StyleValue {

    @Comment("주 룬 ID")
    private int primaryRuneId;
    @Comment("주 룬 세부 ID 목록")
    private String primaryRuneIds;

    @Comment("보조 룬 ID")
    private int secondaryRuneId;
    @Comment("보조 룬 세부 ID 목록")
    private String secondaryRuneIds;

    public StyleValue(ParticipantDto participantDto) {

        List<PerkStyleDto> styles = participantDto.getPerks().getStyles();

        StringBuffer sb = new StringBuffer();
        for (PerkStyleDto style : styles) {
            sb.setLength(0);

            String description = style.getDescription();

            if(description.equalsIgnoreCase("primaryStyle")) {
                this.primaryRuneId = style.getStyle();
                List<PerkStyleSelectionDto> selections = style.getSelections();
                for (PerkStyleSelectionDto selection : selections) {
                    if(sb.length() != 0) {
                        sb.append(",");
                    }
                    int perk = selection.getPerk();
                    sb.append(perk);
                }

                this.primaryRuneIds = sb.toString();
            }


             if(description.equalsIgnoreCase("subStyle")) {
                 this.secondaryRuneId = style.getStyle();
                 List<PerkStyleSelectionDto> selections = style.getSelections();
                 for (PerkStyleSelectionDto selection : selections) {
                     if(sb.length() != 0) {
                         sb.append(",");
                     }
                     int perk = selection.getPerk();
                     sb.append(perk);
                 }

                 this.secondaryRuneIds = sb.toString();

             }
        }


    }
}
