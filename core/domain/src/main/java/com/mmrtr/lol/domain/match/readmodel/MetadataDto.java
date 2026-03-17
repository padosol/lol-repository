package com.mmrtr.lol.domain.match.readmodel;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class MetadataDto {

    private String dataVersion;
    private String matchId;
    private List<String> participants;

}
