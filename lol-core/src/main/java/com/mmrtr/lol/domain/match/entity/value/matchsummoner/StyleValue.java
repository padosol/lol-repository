package com.mmrtr.lol.domain.match.entity.value.matchsummoner;


import jakarta.persistence.Embeddable;
import com.mmrtr.lol.riot.dto.match.ParticipantDto;
import com.mmrtr.lol.riot.dto.match.PerkStyleDto;
import com.mmrtr.lol.riot.dto.match.PerkStyleSelectionDto;
import lombok.*;

import java.util.List;


@Getter
@Setter
@Embeddable
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StyleValue {

    private int primaryRuneId;
    private String primaryRuneIds;

    private int secondaryRuneId;
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
