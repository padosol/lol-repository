package lol.mmrtr.lolrepository.dto.match;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class PerksDto {

    private PerkStatsDto statPerks;
    private List<PerkStyleDto> styles;

}
