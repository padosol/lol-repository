package com.mmrtr.lol.controller.gamedata.response;

import com.mmrtr.lol.domain.gamedata.domain.QueueInfo;

public record QueueInfoResponse(
        long queueId,
        String queueName
) {
    public static QueueInfoResponse of(QueueInfo queueInfo) {
        return new QueueInfoResponse(queueInfo.queueId(), queueInfo.queueName());
    }
}
