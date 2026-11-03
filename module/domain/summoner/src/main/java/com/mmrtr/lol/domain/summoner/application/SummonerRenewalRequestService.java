package com.mmrtr.lol.domain.summoner.application;

import com.mmrtr.lol.common.type.Platform;
import com.mmrtr.lol.domain.league.application.port.SummonerCollectionPublishPort;
import com.mmrtr.lol.domain.summoner.application.port.RenewalRecordPort;
import com.mmrtr.lol.domain.summoner.application.port.SummonerQueryPort;
import com.mmrtr.lol.domain.summoner.application.port.SummonerRenewalStatePort;
import com.mmrtr.lol.domain.summoner.application.usecase.SummonerRenewalUseCase;
import com.mmrtr.lol.domain.summoner.domain.RenewalStatus;
import com.mmrtr.lol.domain.summoner.domain.Summoner;
import com.mmrtr.lol.domain.summoner.readmodel.SummonerRenewalReadModel;
import com.mmrtr.lol.support.error.CoreException;
import com.mmrtr.lol.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 갱신 접수와 상태 조회. 판단 순서: 소환사·플랫폼 확인 → 갱신 레코드가 진행 중이면 SUCCESS →
 * 클릭 쿨다운(10초)이면 FAILED → Riot 호출 2분 이내면 FAILED → 레코드를 열고 renewalId 와 함께 발행.
 *
 * <p>진행 중은 갱신 레코드로만 판단한다 (기한이 지난 PROGRESS 는 FAILED 로 보므로 진행 중이 아니다).
 * 상태 조회도 레코드를 {@code statusAt(now)} 로 읽고, 레코드가 없으면 SUCCESS, FAILED 면 화면 문구를 준다.
 *
 * <p>메시지에는 플랫폼 이름(KR, NA …)을 싣는다 — 갱신 컨슈머가 이름으로 읽는다.
 *
 * <p>갱신 실행은 rabbitmq 모듈의 {@code SummonerRenewalService} 가 한다 (빈 이름이 겹치지 않게 Request 를 붙였다).
 */
@Service
@RequiredArgsConstructor
public class SummonerRenewalRequestService implements SummonerRenewalUseCase {

    private final SummonerQueryPort summonerQueryPort;
    private final SummonerRenewalStatePort summonerRenewalStatePort;
    private final SummonerCollectionPublishPort summonerCollectionPublishPort;
    private final RenewalRecordPort renewalRecordPort;

    @Override
    public SummonerRenewalReadModel requestRenewal(String puuid) {
        Summoner summoner = summonerQueryPort.findSummoner(puuid)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND_PUUID));
        Platform platform = Platform.valueOfPlatformId(summoner.getPlatformId());
        if (platform == null) {
            // 쿨다운을 걸기 전에 거절한다
            throw new CoreException(ErrorType.UNKNOWN_SUMMONER_PLATFORM);
        }

        if (isRenewing(puuid)) {
            return SummonerRenewalReadModel.of(puuid, RenewalStatus.SUCCESS);
        }
        if (!summonerRenewalStatePort.tryStartClickCooldown(puuid)) {
            return SummonerRenewalReadModel.of(puuid, RenewalStatus.FAILED);
        }
        if (!summoner.isRenewableAt(LocalDateTime.now())) {
            return SummonerRenewalReadModel.of(puuid, RenewalStatus.FAILED);
        }

        String renewalId = UUID.randomUUID().toString();
        renewalRecordPort.open(puuid, renewalId, platform.name());
        summonerCollectionPublishPort.publishForRenewal(puuid, platform.name(), renewalId);
        return SummonerRenewalReadModel.of(puuid, RenewalStatus.SUCCESS);
    }

    @Override
    public SummonerRenewalReadModel getRenewalStatus(String puuid) {
        RenewalStatus status = renewalRecordPort.find(puuid)
                .map(record -> record.statusAt(System.currentTimeMillis()))
                .orElse(RenewalStatus.SUCCESS);
        if (status == RenewalStatus.FAILED) {
            return SummonerRenewalReadModel.retryLater(puuid);
        }
        return SummonerRenewalReadModel.of(puuid, status);
    }

    private boolean isRenewing(String puuid) {
        return renewalRecordPort.find(puuid)
                .map(record -> record.statusAt(System.currentTimeMillis()))
                .map(status -> status == RenewalStatus.PROGRESS)
                .orElse(false);
    }
}
