package lol.mmrtr.lolrepository.message;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
public class SummonerMessage {

    private String id;
    private String accountId;
    private String puuid;

    private int profileIconId;
    private long revisionDate;
    private long summonerLevel;

    private String gameName;
    private String tagLine;

    private String region;

    private LocalDateTime revisionClickDate;

    private Set<LeagueSummonerMessage> leagueSummoners = new HashSet<>();

    public SummonerMessage(){};
}
