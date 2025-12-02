package lol.mmrtr.lolrepository.riot.dto.champion;

import lol.mmrtr.lolrepository.riot.dto.error.ErrorDTO;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ChampionInfo extends ErrorDTO {

    private int maxNewPlayerLevel;
    private List<Integer> freeChampionIdsForNewPlayers;
    private List<Integer> freeChampionIds;

}
