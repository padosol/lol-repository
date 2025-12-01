package lol.mmrtr.lolrepository.domain.league.service;

import lol.mmrtr.lolrepository.domain.league.entity.League;
import lol.mmrtr.lolrepository.domain.league.entity.LeagueSummoner;
import lol.mmrtr.lolrepository.domain.league.entity.LeagueSummonerDetail;
import lol.mmrtr.lolrepository.domain.league.repository.LeagueRepository;
import lol.mmrtr.lolrepository.domain.league.repository.LeagueSummonerDetailJpaRepository;
import lol.mmrtr.lolrepository.domain.league.repository.LeagueSummonerRepository;
import lol.mmrtr.lolrepository.riot.dto.league.LeagueEntryDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class LeagueService {

    private final LeagueRepository leagueRepository;
    private final LeagueSummonerRepository leagueSummonerRepository;
    private final LeagueSummonerDetailJpaRepository leagueSummonerDetailJpaRepository;

    public void addAllLeague(String puuid, Set<LeagueEntryDTO> leagueEntryDTOS) {
        for (LeagueEntryDTO leagueEntryDTO : leagueEntryDTOS) {
            String leagueId = leagueEntryDTO.getLeagueId();
            League league = leagueRepository.findById(leagueId);
            if (league == null) {
                League newLeague = new League(
                        leagueEntryDTO.getLeagueId(),
                        leagueEntryDTO.getTier(),
                        leagueEntryDTO.getQueueType()
                );
                league = leagueRepository.save(newLeague);
            }

            LeagueSummoner leagueSummoner = leagueSummonerRepository.findBy(puuid, league.getLeagueId());
            if (leagueSummoner == null) {
                leagueSummoner = leagueSummonerRepository.save(LeagueSummoner.builder()
                        .puuid(puuid)
                        .leagueId(league.getLeagueId())
                        .build());
            }

            LeagueSummonerDetail leagueSummonerDetail = LeagueSummonerDetail.builder()
                    .leagueSummonerId(leagueSummoner.getId())
                    .leaguePoints(leagueEntryDTO.getLeaguePoints())
                    .rank(leagueEntryDTO.getRank())
                    .wins(leagueEntryDTO.getWins())
                    .losses(leagueEntryDTO.getLosses())
                    .veteran(leagueEntryDTO.isVeteran())
                    .inactive(leagueEntryDTO.isInactive())
                    .freshBlood(leagueEntryDTO.isFreshBlood())
                    .hotStreak(leagueEntryDTO.isHotStreak())
                    .build();

            leagueSummonerDetailJpaRepository.save(leagueSummonerDetail);

        }
    }

}
