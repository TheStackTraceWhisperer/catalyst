package catalyst.ffxi.common.concurrency;

import java.util.concurrent.Callable;
import java.util.function.Consumer;

public interface TaskScheduler {
    <T> TaskHandle<T> submit(Callable<T> task);
    
    <T> TaskHandle<T> submit(
        Callable<T> task,
        Consumer<T> onSuccess,
        Consumer<Throwable> onError
    );

    <T> TaskHandle<T> submit(
        Callable<T> task,
        long timeoutMs,
        Consumer<T> onSuccess,
        Consumer<Throwable> onError
    );
    
    void runOnMainThread(Runnable action);
    
    void processForegroundTasks();
}
