package com.mmrtr.lol.infra.riot.interceptor;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.util.concurrent.Semaphore;

public class ConcurrencyInterceptor implements ClientHttpRequestInterceptor {

    private final Semaphore permits;

    public ConcurrencyInterceptor(int maxConcurrency) {
        this.permits = new Semaphore(maxConcurrency);
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
            ClientHttpRequestExecution execution) throws IOException {
        try {
            permits.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for concurrency permit", e);
        }
        try {
            return execution.execute(request, body);
        } finally {
            permits.release();
        }
    }
}
