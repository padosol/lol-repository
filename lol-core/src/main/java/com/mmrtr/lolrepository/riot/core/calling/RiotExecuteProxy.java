package com.mmrtr.lolrepository.riot.core.calling;

import com.mmrtr.lolrepository.bucket.BucketService;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class RiotExecuteProxy implements RiotExecute{

    private final RiotExecute execute;
    private final BucketService bucketService;

    public RiotExecuteProxy(RiotExecute execute, BucketService bucketService) {
        this.execute = execute;
        this.bucketService = bucketService;
    }

    @Override
    public <T> CompletableFuture<T> execute(Class<T> clazz, URI uri) {
        CompletableFuture<T> result = execute.execute(clazz, uri);
//        log.info("URI: {}", uri);

        return result;

    }
}
