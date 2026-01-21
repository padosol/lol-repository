package com.mmrtr.lol.infra.riot.dto.match;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ObjectivesDto {
    private ObjectiveDto baron;
    private ObjectiveDto champion;
    private ObjectiveDto dragon;
    private ObjectiveDto inhibitor;
    private ObjectiveDto riftHerald;
    private ObjectiveDto tower;
}
