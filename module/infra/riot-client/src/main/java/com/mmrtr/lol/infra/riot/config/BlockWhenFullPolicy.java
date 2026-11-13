package com.mmrtr.lol.infra.riot.config;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 스레드와 큐가 모두 차면 부른 스레드가 큐에 자리가 날 때까지 기다린다. {@code CallerRunsPolicy} 와 달리 부른 스레드가
 * 직접 실행하지 않으므로 동시에 도는 작업 수가 풀 크기를 넘지 않는다. 실행기가 멈췄으면 기다리지 않고 거부한다.
 */
public final class BlockWhenFullPolicy implements RejectedExecutionHandler {

    @Override
    public void rejectedExecution(Runnable task, ThreadPoolExecutor executor) {
        if (executor.isShutdown()) {
            throw new RejectedExecutionException("executor is shut down");
        }
        try {
            executor.getQueue().put(task);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RejectedExecutionException("interrupted while waiting for queue space", e);
        }
    }
}
