package com.mmrtr.lol.domain.match.readmodel.timeline;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
@ToString
public class TimelineDto {
    private MetadataTimeLineDto metadata;
    private InfoTimeLineDto info;
}
