package com.mmrtr.lol.support.riot;

/**
 * Riot 호출 허가가 거절됐다. 잠정 예외다: 호출하는 쪽마다 거절 처리를 정할 때까지 지금의 "예약 실패" 처리로 보낸다.
 */
public class RiotPermitDeniedException extends RuntimeException {

    private final String platform;
    private final RiotCallPriority priority;

    public RiotPermitDeniedException(String platform, RiotCallPriority priority) {
        super("Riot 호출 허가 거절: platform=" + platform + ", priority=" + priority);
        this.platform = platform;
        this.priority = priority;
    }

    public String getPlatform() {
        return platform;
    }

    public RiotCallPriority getPriority() {
        return priority;
    }
}
