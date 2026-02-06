package com.mmrtr.lol.infra.riot.adapter;

import com.mmrtr.lol.common.type.Platform;
import com.mmrtr.lol.domain.spectator.domain.ActiveGame;
import com.mmrtr.lol.domain.spectator.domain.BannedChampion;
import com.mmrtr.lol.domain.spectator.domain.GameParticipant;
import com.mmrtr.lol.domain.spectator.domain.GamePerks;
import com.mmrtr.lol.domain.spectator.service.port.SpectatorApiPort;
import com.mmrtr.lol.infra.riot.dto.spectator.BannedChampionVO;
import com.mmrtr.lol.infra.riot.dto.spectator.CurrentGameInfoVO;
import com.mmrtr.lol.infra.riot.dto.spectator.ParticipantVO;
import com.mmrtr.lol.infra.riot.dto.spectator.PerksVO;
import com.mmrtr.lol.infra.riot.exception.RiotClientException;
import com.mmrtr.lol.infra.riot.service.RiotApiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class SpectatorApiAdapter implements SpectatorApiPort {

    private final RiotApiService riotApiService;

    @Override
    public CompletableFuture<Optional<ActiveGame>> fetchActiveGameByPuuid(
            String puuid, String platformName, Executor executor) {
        Platform platform = Platform.valueOfName(platformName);

        return riotApiService.getActiveGameByPuuid(puuid, platform, executor)
                .thenApply(vo -> Optional.of(toDomain(vo)))
                .exceptionally(ex -> {
                    Throwable cause = ex.getCause();
                    if (cause instanceof RiotClientException riotEx
                            && riotEx.getStatus().value() == 404) {
                        log.debug("Active game not found for puuid: {}", puuid);
                        return Optional.empty();
                    }
                    throw new RuntimeException(ex);
                });
    }

    private ActiveGame toDomain(CurrentGameInfoVO vo) {
        return ActiveGame.builder()
                .gameId(vo.gameId())
                .gameType(vo.gameType())
                .gameMode(vo.gameMode())
                .mapId(vo.mapId())
                .gameStartTime(vo.gameStartTime())
                .gameLength(vo.gameLength())
                .platformId(vo.platformId())
                .gameQueueConfigId(vo.gameQueueConfigId())
                .encryptionKey(vo.observers() != null ? vo.observers().encryptionKey() : null)
                .participants(toParticipantDomains(vo.participants()))
                .bannedChampions(toBannedChampionDomains(vo.bannedChampions()))
                .build();
    }

    private List<GameParticipant> toParticipantDomains(List<ParticipantVO> participants) {
        if (participants == null) {
            return Collections.emptyList();
        }
        return participants.stream()
                .map(this::toParticipantDomain)
                .collect(Collectors.toList());
    }

    private GameParticipant toParticipantDomain(ParticipantVO vo) {
        return GameParticipant.builder()
                .riotId(vo.riotId())
                .puuid(vo.puuid())
                .championId(vo.championId())
                .teamId(vo.teamId())
                .spell1Id(vo.spell1Id())
                .spell2Id(vo.spell2Id())
                .bot(vo.bot())
                .lastSelectedSkinIndex(vo.lastSelectedSkinIndex())
                .profileIconId(vo.profileIconId())
                .perks(toPerksDomain(vo.perks()))
                .build();
    }

    private GamePerks toPerksDomain(PerksVO vo) {
        if (vo == null) {
            return null;
        }
        return GamePerks.builder()
                .perkStyle(vo.perkStyle())
                .perkSubStyle(vo.perkSubStyle())
                .perkIds(vo.perkIds())
                .build();
    }

    private List<BannedChampion> toBannedChampionDomains(List<BannedChampionVO> bannedChampions) {
        if (bannedChampions == null) {
            return Collections.emptyList();
        }
        return bannedChampions.stream()
                .map(this::toBannedChampionDomain)
                .collect(Collectors.toList());
    }

    private BannedChampion toBannedChampionDomain(BannedChampionVO vo) {
        return BannedChampion.builder()
                .championId(vo.championId())
                .teamId(vo.teamId())
                .pickTurn(vo.pickTurn())
                .build();
    }
}
