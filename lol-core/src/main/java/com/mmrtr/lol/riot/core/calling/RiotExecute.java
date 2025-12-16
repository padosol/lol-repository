package com.mmrtr.lol.riot.core.calling;

import java.net.URI;
import java.util.concurrent.CompletableFuture;

public interface RiotExecute {

    <T> CompletableFuture<T> execute(Class<T> clazz, URI uri);
}
