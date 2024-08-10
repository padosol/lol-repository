package lol.mmrtr.lolrepository.riot.dto.match_timeline;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class MetadataTimeLineDto {
    private String dataVersion;
    private String matchId;
    private List<String> participants;
}
