package com.mmrtr.lol.domain.summoner.application;

import com.mmrtr.lol.common.type.Platform;
import com.mmrtr.lol.domain.league.application.port.SummonerCollectionPublishPort;
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
import java.util.List;

/**
 * lol-server {@code SummonerService#renewalSummonerInfo(puuid)}·{@code renewalSummonerStatus} 의 이전. 판단 순서가 같다:
 * 소환사·플랫폼 확인 → 진행 중이면 SUCCESS → 클릭 쿨다운(10초)이면 FAILED → Riot 호출 2분 이내면 FAILED → 진행 표시 후 발행.
 *
 * <p>메시지에는 플랫폼 이름(KR, NA …)을 싣는다 — 갱신 컨슈머가 이름으로 읽는다. lol-server 는 저장된 platform_id(NA1 …)를
 * 보내 KR 외 지역의 갱신이 컨슈머에서 버려졌다.
 *
 * <p>갱신 실행은 rabbitmq 모듈의 {@code SummonerRenewalService} 가 한다 (빈 이름이 겹치지 않게 Request 를 붙였다).
 */
@Service
@RequiredArgsConstructor
public class SummonerRenewalRequestService implements SummonerRenewalUseCase {

    private final SummonerQueryPort summonerQueryPort;
    private final SummonerRenewalStatePort summonerRenewalStatePort;
    private final SummonerCollectionPublishPort summonerCollectionPublishPort;

    @Override
    public SummonerRenewalReadModel requestRenewal(String puuid) {
        Summoner summoner = summonerQueryPort.findSummoner(puuid)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND_PUUID));
        Platform platform = Platform.valueOfPlatformId(summoner.getPlatformId());
        if (platform == null) {
            // 쿨다운을 걸기 전에 거절한다
            throw new CoreException(ErrorType.UNKNOWN_SUMMONER_PLATFORM);
        }

        if (summonerRenewalStatePort.isRenewing(puuid)) {
            return SummonerRenewalReadModel.of(puuid, RenewalStatus.SUCCESS);
        }
        if (!summonerRenewalStatePort.tryStartClickCooldown(puuid)) {
            return SummonerRenewalReadModel.of(puuid, RenewalStatus.FAILED);
        }
        if (!summoner.isRenewableAt(LocalDateTime.now())) {
            return SummonerRenewalReadModel.of(puuid, RenewalStatus.FAILED);
        }

        summonerRenewalStatePort.markRenewing(puuid);
        summonerCollectionPublishPort.publishForRenewal(List.of(puuid), platform.name());
        return SummonerRenewalReadModel.of(puuid, RenewalStatus.SUCCESS);
    }

    @Override
    public SummonerRenewalReadModel getRenewalStatus(String puuid) {
        RenewalStatus status = summonerRenewalStatePort.isRenewing(puuid) ? RenewalStatus.PROGRESS : RenewalStatus.SUCCESS;
        return SummonerRenewalReadModel.of(puuid, status);
    }
}
