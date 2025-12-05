package com.mmrtr.lolrepository.riot.dto.spectator;

import lombok.Getter;

import java.util.List;

@Getter
public class CurrentGameParticipant {

    private	long championId;
    private	Perks perks;
    private	long profileIconId;
    private String riotId;
    private	boolean bot;
    private	long teamId;
    private	String summonerId;
    private	String puuid;
    private	long spell1Id;
    private	long spell2Id;
    private List<GameCustomizationObject> gameCustomizationObjects;
}
