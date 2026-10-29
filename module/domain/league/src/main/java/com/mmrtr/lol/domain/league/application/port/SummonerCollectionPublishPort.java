package com.mmrtr.lol.domain.league.application.port;

import java.util.List;

public interface SummonerCollectionPublishPort {

    /** 사용자가 요청한 갱신. USER 우선순위로 처리된다. */
    void publishForRenewal(List<String> puuids, String platformName);

    /** 티어 수집 등 사용자 요청이 아닌 갱신. BACKGROUND 우선순위로 처리된다. */
    void publishForCollection(List<String> puuids, String platformName);
}
