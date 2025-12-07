package com.mmrtr.lol.riot.core.calling;

import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class RiotExecuteProxy implements RiotExecute{

    private final RiotExecute execute;

    public RiotExecuteProxy(RiotExecute execute) {
        this.execute = execute;
    }

    @Override
    public <T> CompletableFuture<T> execute(Class<T> clazz, URI uri) {
        CompletableFuture<T> result = execute.execute(clazz, uri);
//        log.info("URI: {}", uri);

        return result;

    }
}
