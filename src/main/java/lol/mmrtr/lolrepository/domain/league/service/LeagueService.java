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
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class LeagueService {

    private final LeagueRepository leagueRepository;
    private final LeagueSummonerRepository leagueSummonerRepository;
    private final LeagueSummonerDetailJpaRepository leagueSummonerDetailJpaRepository;

    @Transactional
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

            // 리그는 솔로랭크와 자유랭크로 나눌 수 있다.
            // 유저는 솔로랭크 1개 자유랭크 1개를 가질 수 있다.
            LeagueSummoner leagueSummoner = leagueSummonerRepository.findAllByPuuid(puuid, league.getQueue());
            if (leagueSummoner == null) {
                leagueSummoner = leagueSummonerRepository.save(LeagueSummoner.builder()
                        .puuid(puuid)
                        .leagueId(league.getLeagueId())
                        .build());
            }

            // 리그 아이디가 다르면 업데이트 함
            if (!leagueSummoner.getLeagueId().equals(league.getLeagueId())) {
                leagueSummoner.changeLeague(league);
                leagueSummonerRepository.save(leagueSummoner);
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
