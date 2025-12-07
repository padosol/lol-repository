package com.mmrtr.lol.riot.dto.match_timeline;

import com.mmrtr.lol.riot.dto.error.ErrorDTO;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TimelineDto extends ErrorDTO {

    private MetadataTimeLineDto metadata;
    private InfoTimeLineDto info;

}
