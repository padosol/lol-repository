package com.mmrtr.lol.infra.riot.service;

/**
 * Riot 호출 한 번의 시각과 받은 바이트. {@link RiotCallRecorder} 가 호출 동안 그 스레드에 두고,
 * {@link RiotCallTimingInterceptor} 가 응답 헤더를 받은 시각·본문을 다 받은 시각·받은 바이트를 채운다.
 * RestClient 는 부른 스레드에서 보내고 본문까지 읽으므로 같은 스레드에서 찾을 수 있다.
 */
final class RiotCallTiming {

    private static final ThreadLocal<RiotCallTiming> CURRENT = new ThreadLocal<>();

    private final long startNanos;
    private long headersNanos = -1;
    private long bodyEndNanos = -1;
    private long bytes;

    private RiotCallTiming(long startNanos) {
        this.startNanos = startNanos;
    }

    /** 새 기록을 이 스레드에 둔다. 호출이 끝나면 {@link #end()} 로 지운다. */
    static RiotCallTiming begin(long nowNanos) {
        RiotCallTiming timing = new RiotCallTiming(nowNanos);
        CURRENT.set(timing);
        return timing;
    }

    static void end() {
        CURRENT.remove();
    }

    /** 이 스레드에서 진행 중인 기록. {@link RiotCallRecorder} 밖에서 보낸 요청이면 null. */
    static RiotCallTiming current() {
        return CURRENT.get();
    }

    void headersReceived(long nowNanos) {
        if (headersNanos < 0) {
            headersNanos = nowNanos;
        }
    }

    void bodyRead(long count) {
        bytes += count;
    }

    void bodyEnded(long nowNanos) {
        if (bodyEndNanos < 0) {
            bodyEndNanos = nowNanos;
        }
    }

    boolean hasHeaders() {
        return headersNanos >= 0;
    }

    /** 보냄 → 끝(본문 변환까지). */
    long totalNanos(long endNanos) {
        return endNanos - startNanos;
    }

    /** 보냄 → 응답 헤더. */
    long waitNanos() {
        return headersNanos - startNanos;
    }

    /** 응답 헤더 → 본문 끝. 본문 끝을 보지 못했으면 호출이 끝난 시각까지다. */
    long downloadNanos(long endNanos) {
        return (bodyEndNanos >= 0 ? bodyEndNanos : endNanos) - headersNanos;
    }

    long bytes() {
        return bytes;
    }
}
