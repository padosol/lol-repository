package com.mmrtr.lolrepository.riot.dto.match_timeline;

import com.mmrtr.lolrepository.riot.dto.error.ErrorDTO;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TimelineDto extends ErrorDTO {

    private MetadataTimeLineDto metadata;
    private InfoTimeLineDto info;

}
