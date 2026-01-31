package com.mmrtr.lol.domain.league.service.usecase;

import com.mmrtr.lol.domain.league.domain.TierCutoff;
import com.mmrtr.lol.domain.league.repository.TierCutoffRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SaveTierCutoffUseCase {

    private final TierCutoffRepositoryPort tierCutoffRepositoryPort;

    @Transactional
    public void execute(List<TierCutoff> cutoffs) {
        if (cutoffs.isEmpty()) {
            log.warn("저장할 티어 커트라인 데이터가 없습니다.");
            return;
        }

        tierCutoffRepositoryPort.saveAll(cutoffs);
        log.info("티어 커트라인 {} 건 저장 완료", cutoffs.size());
    }
}
