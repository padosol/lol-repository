package com.mmrtr.lol.domain.gamedata.application.port;

import com.mmrtr.lol.domain.gamedata.domain.QueueInfo;
import java.util.List;

public interface QueuePersistencePort {

    List<QueueInfo> findAll();

    List<QueueInfo> findAllTabs();
}
