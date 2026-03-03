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
public class PerkValue {

    @Comment("방어 룬 스탯")
    private int statPerkDefense;
    @Comment("유연 룬 스탯")
    private int statPerkFlex;
    @Comment("공격 룬 스탯")
    private int statPerkOffense;

    @Comment("주 룬 스타일 ID")
    private int primaryStyleId;
    @Comment("주 룬 키스톤")
    private int primaryPerk0;
    @Comment("주 룬 1")
    private int primaryPerk1;
    @Comment("주 룬 2")
    private int primaryPerk2;
    @Comment("주 룬 3")
    private int primaryPerk3;

    @Comment("보조 룬 스타일 ID")
    private int subStyleId;
    @Comment("보조 룬 0")
    private int subPerk0;
    @Comment("보조 룬 1")
    private int subPerk1;

    public PerkValue(ParticipantDto participantDto) {
        if (participantDto.getPerks() == null) return;

        this.statPerkDefense = participantDto.getPerks().getStatPerks().getDefense();
        this.statPerkFlex = participantDto.getPerks().getStatPerks().getFlex();
        this.statPerkOffense = participantDto.getPerks().getStatPerks().getOffense();

        List<PerkStyleDto> styles = participantDto.getPerks().getStyles();
        if (styles == null) return;

        for (PerkStyleDto style : styles) {
            String description = style.getDescription();
            List<PerkStyleSelectionDto> selections = style.getSelections();

            if ("primaryStyle".equalsIgnoreCase(description)) {
                this.primaryStyleId = style.getStyle();
                if (selections != null) {
                    if (selections.size() > 0) this.primaryPerk0 = selections.get(0).getPerk();
                    if (selections.size() > 1) this.primaryPerk1 = selections.get(1).getPerk();
                    if (selections.size() > 2) this.primaryPerk2 = selections.get(2).getPerk();
                    if (selections.size() > 3) this.primaryPerk3 = selections.get(3).getPerk();
                }
            } else if ("subStyle".equalsIgnoreCase(description)) {
                this.subStyleId = style.getStyle();
                if (selections != null) {
                    if (selections.size() > 0) this.subPerk0 = selections.get(0).getPerk();
                    if (selections.size() > 1) this.subPerk1 = selections.get(1).getPerk();
                }
            }
        }
    }
}
