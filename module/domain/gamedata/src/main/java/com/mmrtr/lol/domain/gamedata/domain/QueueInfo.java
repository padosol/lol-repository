package com.mmrtr.lol.domain.gamedata.domain;

/**
 * 큐 정보.
 *
 * @param tab 전적 화면 탭으로 노출하는 큐인지
 */
public record QueueInfo(
        Long queueId,
        String queueName,
        boolean tab
) {
}
