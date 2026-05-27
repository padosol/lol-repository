package com.mmrtr.lol.domain.league.application.port;

import java.util.List;

public interface SummonerCollectionPublishPort {

    void publishForRenewal(List<String> puuids, String platformName);
}
