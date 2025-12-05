package com.mmrtr.lolrepository.domain.league.service;

import com.mmrtr.lolrepository.domain.league.entity.League;
import com.mmrtr.lolrepository.domain.league.entity.LeagueSummoner;
import com.mmrtr.lolrepository.domain.league.entity.LeagueSummonerHistory;
import com.mmrtr.lolrepository.domain.league.repository.LeagueRepository;
import com.mmrtr.lolrepository.domain.league.repository.LeagueSummonerHistoryRepository;
import com.mmrtr.lolrepository.domain.league.repository.LeagueSummonerRepository;
import com.mmrtr.lolrepository.riot.dto.league.LeagueEntryDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class LeagueService {

    private final LeagueRepository leagueRepository;
    private final LeagueSummonerRepository leagueSummonerRepository;
    private final LeagueSummonerHistoryRepository leagueSummonerHistoryRepository;

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

            LeagueSummoner leagueSummoner = leagueSummonerRepository
                    .findAllByPuuidAndQueueOptional(puuid, league.getQueue())
                    .orElse(LeagueSummoner.of(puuid, league, leagueEntryDTO));

            // 기존 리그정보 history 등록
            LeagueSummonerHistory leagueSummonerHistory = LeagueSummonerHistory.create(leagueSummoner);
            leagueSummonerHistoryRepository.save(leagueSummonerHistory);

            // 리그 정보 변경
            // 최초 리그 정보가 없을 때 LeagueSummoner.of() 로 세팅 후 changeLeague 로 한번더 로직이 사용됨.
            // 줄이는 방법 모색해야함.
            leagueSummoner.changeLeague(league, leagueEntryDTO);
            leagueSummonerRepository.save(leagueSummoner);
        }
    }

}
