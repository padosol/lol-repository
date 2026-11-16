package com.mmrtr.lol.infra.riot.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class AsyncConfigTest {

    @Test
    @DisplayName("갱신 매치 저장 실행기는 5 스레드·큐 20 이고, 꽉 차면 부른 쪽이 기다린다")
    void matchSaveExecutor_capsAtFiveWithBoundedQueue() {
        // given
        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) new AsyncConfig().matchSaveExecutor();
        try {
            // then
            assertThat(executor.getCorePoolSize()).isEqualTo(5);
            assertThat(executor.getMaxPoolSize()).isEqualTo(5);
            assertThat(executor.getQueueCapacity()).isEqualTo(20);
            assertThat(executor.getThreadPoolExecutor().getRejectedExecutionHandler())
                    .isInstanceOf(BlockWhenFullPolicy.class);
        } finally {
            executor.shutdown();
        }
    }

    @Test
    @DisplayName("타임라인 저장 실행기는 꽉 차면 부른 스레드가 실행한다 (거부하지 않는다)")
    void timelineSaveExecutor_callerRunsWhenFull() {
        // given
        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) new AsyncConfig().timelineSaveExecutor();
        try {
            // then
            assertThat(executor.getThreadPoolExecutor().getRejectedExecutionHandler())
                    .isInstanceOf(ThreadPoolExecutor.CallerRunsPolicy.class);
        } finally {
            executor.shutdown();
        }
    }

    @Test
    @DisplayName("큐가 차면 부른 스레드는 직접 실행하지 않고 자리가 날 때까지 기다린다")
    void blockWhenFull_waitsForQueueSpace() throws Exception {
        // given: 스레드 1개는 막혀 있고 큐 1칸도 찼다
        ThreadPoolExecutor pool = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1), new BlockWhenFullPolicy());
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean ranOnCaller = new AtomicBoolean();
        AtomicBoolean ran = new AtomicBoolean();
        try {
            pool.execute(() -> await(release));
            pool.execute(() -> { });

            // when
            CompletableFuture<Void> third = CompletableFuture.runAsync(() -> {
                Thread submitter = Thread.currentThread();
                pool.execute(() -> {
                    ranOnCaller.set(Thread.currentThread() == submitter);
                    ran.set(true);
                });
            });

            // then
            Thread.sleep(200);
            assertThat(third).isNotDone();
            release.countDown();
            third.get(5, TimeUnit.SECONDS);
            pool.shutdown();
            assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            assertThat(ran).isTrue();
            assertThat(ranOnCaller).isFalse();
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("멈춘 실행기면 기다리지 않고 거부한다")
    void blockWhenFull_shutdownRejects() {
        // given
        ThreadPoolExecutor pool = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1));
        pool.shutdown();

        // when & then
        assertThatThrownBy(() -> new BlockWhenFullPolicy().rejectedExecution(() -> { }, pool))
                .isInstanceOf(RejectedExecutionException.class);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
