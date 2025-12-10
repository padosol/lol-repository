package com.mmrtr.lol.riot.config;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

public class RateLimitHttpResponse implements ClientHttpResponse {

    private final HttpStatus statusCode;
    private final String statusText;
    private final byte[] body;

    public RateLimitHttpResponse(byte[] body, HttpStatus statusCode) {
        this.body = body;
        this.statusCode = statusCode;
        this.statusText = statusCode.getReasonPhrase();
    }

    @Override
    public HttpStatus getStatusCode() throws IOException {
        return statusCode;
    }

    @Override
    public String getStatusText() throws IOException {
        return statusText;
    }

    @Override
    public void close() {
    }

    @Override
    public InputStream getBody() throws IOException {
        return new ByteArrayInputStream(body);
    }

    @Override
    public HttpHeaders getHeaders() {
        return new HttpHeaders();
    }
}
