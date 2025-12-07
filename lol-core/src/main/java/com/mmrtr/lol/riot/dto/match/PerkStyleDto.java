package com.mmrtr.lol.riot.dto.match;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class PerkStyleDto {

    private	String description;
    private List<PerkStyleSelectionDto> selections;
    private	int style;
}
