package com.mmrtr.lol.infra.riot.dto.match;


import com.mmrtr.lol.infra.riot.dto.error.ErrorDTO;
import com.mmrtr.lol.infra.riot.dto.match_timeline.TimelineDto;
import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
public class MatchDto extends ErrorDTO {

    private MetadataDto metadata;
    private InfoDto info;
    private TimelineDto timeline;
}
