package lol.mmrtr.lolrepository.riot.core.calling;

import java.net.URI;
import java.util.concurrent.CompletableFuture;

public interface RiotExecute {

    <T> CompletableFuture<T> execute(Class<T> clazz, URI uri);
}
