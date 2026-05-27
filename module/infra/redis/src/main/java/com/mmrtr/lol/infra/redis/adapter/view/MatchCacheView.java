package com.mmrtr.lol.infra.redis.adapter.view;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * lol-server 의 {@code GameReadModel} JSON 형태를 그대로 미러링한 캐시 쓰기 전용 view.
 * lol-server 는 이 JSON 을 변환 없이 GameReadModel 로 역직렬화한다. 필드명은 GameReadModel 트리와 1:1 일치해야 한다.
 */
@Getter
@Builder
public class MatchCacheView {
    private GameInfoView gameInfoData;
    private List<ParticipantView> participantData;
    private TeamView teamInfoData;
}
