package com.mmrtr.lol.domain.league.service.usecase;

import com.mmrtr.lol.domain.league.domain.SummonerRanking;
import com.mmrtr.lol.domain.league.repository.SummonerRankingRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CalculateSummonerRankingUseCase {

    private final SummonerRankingRepositoryPort summonerRankingRepositoryPort;

    @Transactional
    public void execute(List<SummonerRanking> rankings) {
        if (rankings.isEmpty()) {
            log.warn("저장할 랭킹 데이터가 없습니다.");
            return;
        }

        summonerRankingRepositoryPort.saveAll(rankings);
        log.info("소환사 랭킹 {} 건 저장 완료", rankings.size());
    }
}
