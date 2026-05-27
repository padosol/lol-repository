package com.mmrtr.lol.domain.match.readmodel;

import com.mmrtr.lol.domain.match.readmodel.timeline.TimelineDto;
import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
public class MatchDto {

    private MetadataDto metadata;
    private InfoDto info;
    private TimelineDto timeline;
}
