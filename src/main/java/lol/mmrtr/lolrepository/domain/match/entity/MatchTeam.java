package lol.mmrtr.lolrepository.domain.match.entity;

import lol.mmrtr.lolrepository.riot.dto.match.MatchDto;
import lol.mmrtr.lolrepository.riot.dto.match.TeamDto;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MatchTeam {

    private int teamId;
    private String matchId;
    private	boolean win;
    private	boolean baronFirst;
    private	int baronKills;
    private	boolean championFirst;
    private	int championKills;
    private	boolean dragonFirst;
    private	int dragonKills;
    private	boolean inhibitorFirst;
    private	int inhibitorKills;
    private	boolean riftHeraldFirst;
    private	int riftHeraldKills;
    private	boolean towerFirst;
    private	int towerKills;
    private	int champion1Id;
    private	int pick1Turn;
    private	int champion2Id;
    private	int pick2Turn;
    private	int champion3Id;
    private	int pick3Turn;
    private	int champion4Id;
    private	int pick4Turn;
    private	int champion5Id;
    private	int pick5Turn;

    public MatchTeam(){}

    public MatchTeam(MatchDto matchDto, TeamDto teamDto) {
        this.teamId = teamDto.getTeamId();
        this.matchId = matchDto.getMetadata().getMatchId();
        this.win = teamDto.isWin();
        this.baronFirst = teamDto.getObjectives().getBaron().isFirst();
        this.baronKills = teamDto.getObjectives().getBaron().getKills();
        this.championFirst = teamDto.getObjectives().getChampion().isFirst();
        this.championKills = teamDto.getObjectives().getChampion().getKills();
        this.dragonFirst = teamDto.getObjectives().getDragon().isFirst();
        this.dragonKills = teamDto.getObjectives().getDragon().getKills();
        this.inhibitorFirst = teamDto.getObjectives().getInhibitor().isFirst();
        this.inhibitorKills = teamDto.getObjectives().getInhibitor().getKills();
        this.riftHeraldFirst = teamDto.getObjectives().getRiftHerald().isFirst();
        this.riftHeraldKills = teamDto.getObjectives().getRiftHerald().getKills();
        this.towerFirst = teamDto.getObjectives().getTower().isFirst();
        this.towerKills = teamDto.getObjectives().getTower().getKills();

        if(!teamDto.getBans().isEmpty()) {
            this.champion1Id = teamDto.getBans().get(0).getChampionId();
            this.pick1Turn = teamDto.getBans().get(0).getPickTurn();
            this.champion2Id = teamDto.getBans().get(1).getChampionId();
            this.pick2Turn = teamDto.getBans().get(1).getPickTurn();
            this.champion3Id = teamDto.getBans().get(2).getChampionId();
            this.pick3Turn = teamDto.getBans().get(2).getPickTurn();
            this.champion4Id = teamDto.getBans().get(3).getChampionId();
            this.pick4Turn = teamDto.getBans().get(3).getPickTurn();
            this.champion5Id = teamDto.getBans().get(4).getChampionId();
            this.pick5Turn = teamDto.getBans().get(4).getPickTurn();
        }

    }

}
