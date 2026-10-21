package com.mmrtr.lol.infra.redis.adapter;

import com.mmrtr.lol.domain.spectator.application.port.ActiveGameCachePort;
import com.mmrtr.lol.domain.spectator.readmodel.ActiveGameReadModel;
import com.mmrtr.lol.domain.spectator.readmodel.ActiveGameReadModel.Participant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 관전 캐시 (spectator 컨텍스트). TTL 은 lol-server {@code SpectatorRedisAdapter} 와 같고, 키는 공유하지 않는다
 * (lol-server 는 {@code spectator:active_game:*} 에 {@code @class} 타입 정보가 든 JSON 을 쓴다).
 * lol-server 의 게임 메타 키는 두지 않는다 — 지울 참여자 목록은 캐시된 게임에 들어 있다.
 */
@Component
@RequiredArgsConstructor
public class ActiveGameCacheRedisAdapter implements ActiveGameCachePort {

    static final String GAME_KEY_PREFIX = "spectator:v1:active-game:";
    static final String NOT_IN_GAME_KEY_PREFIX = "spectator:v1:not-in-game:";
    static final Duration GAME_TTL = Duration.ofMinutes(30);
    static final Duration NOT_IN_GAME_TTL = Duration.ofSeconds(30);
    private static final String NOT_IN_GAME_VALUE = "NOT_IN_GAME";

    private final JsonValueCache cache;

    @Override
    public Optional<ActiveGameReadModel> findGame(String platformId, String puuid) {
        return cache.get(gameKey(platformId, puuid), ActiveGameReadModel.class);
    }

    @Override
    public void saveGame(String platformId, ActiveGameReadModel game) {
        for (String puuid : participantPuuids(game)) {
            cache.set(gameKey(platformId, puuid), game, GAME_TTL);
        }
    }

    @Override
    public void evictGame(String platformId, ActiveGameReadModel game) {
        cache.delete(participantPuuids(game).stream().map(puuid -> gameKey(platformId, puuid)).toList());
    }

    @Override
    public boolean isNotInGame(String platformId, String puuid) {
        return cache.exists(notInGameKey(platformId, puuid));
    }

    @Override
    public void saveNotInGame(String platformId, String puuid) {
        cache.set(notInGameKey(platformId, puuid), NOT_IN_GAME_VALUE, NOT_IN_GAME_TTL);
    }

    private List<String> participantPuuids(ActiveGameReadModel game) {
        if (game.participants() == null) {
            return List.of();
        }
        return game.participants().stream().map(Participant::puuid).filter(Objects::nonNull).toList();
    }

    private String gameKey(String platformId, String puuid) {
        return GAME_KEY_PREFIX + platformId + ":" + puuid;
    }

    private String notInGameKey(String platformId, String puuid) {
        return NOT_IN_GAME_KEY_PREFIX + platformId + ":" + puuid;
    }
}
