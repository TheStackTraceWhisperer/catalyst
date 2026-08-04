package catalyst.ffxi.common.concurrency;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface TaskHandle<T> {
    UUID getId();
    TaskStatus getStatus();
    CompletableFuture<T> getFuture();
    Throwable getError();
    T getResult();
    boolean cancel(boolean mayInterruptIfRunning);
}
