package com.mmrtr.lol.infra.riot.dto.match_timeline;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.mmrtr.lol.infra.riot.dto.error.ErrorDTO;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
@ToString
public class TimelineDto extends ErrorDTO {
    private MetadataTimeLineDto metadata;
    private InfoTimeLineDto info;
}
