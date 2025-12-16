package com.mmrtr.lol.riot.interceptor;

import com.mmrtr.lol.riot.exception.RiotClientException;
import com.mmrtr.lol.support.error.CoreException;
import com.mmrtr.lol.support.error.ErrorType;
import org.springframework.boot.logging.LogLevel;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.retry.support.RetryTemplate;

import java.io.IOException;

public class RetryInterceptor implements ClientHttpRequestInterceptor {

    private final RetryTemplate retryTemplate;

    public RetryInterceptor(RetryTemplate retryTemplate) {
        this.retryTemplate = retryTemplate;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        return retryTemplate.execute(context -> {
            try {
                ClientHttpResponse response = execution.execute(request, body);

                // 4xx, 5xx 오류 코드에 대해서도 재시도를 원한다면 여기서 예외를 던져야 합니다.
                if (response.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                    throw new RiotClientException(response.getStatusCode(), "요청이 너무 많습니다.", LogLevel.WARN);
                }

                // 예시: 5xx 서버 오류 코드가 오면 예외를 던져 재시도를 유발합니다.
                if (response.getStatusCode().is5xxServerError()) {
                    throw new CoreException(ErrorType.DEFAULT_ERROR, "예상치 못한 서버에러가 발생했습니다.");
                }

                return response;
            } catch (IOException e) {
                throw new CoreException(ErrorType.DEFAULT_ERROR, "예상치 못한 서버에러가 발생했습니다.");
            }
        });
    }
}
