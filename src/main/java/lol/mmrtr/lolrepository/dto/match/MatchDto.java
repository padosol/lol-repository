package lol.mmrtr.lolrepository.dto.match;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class MatchDto{

    private MetadataDto metadata;
    private InfoDto info;

}