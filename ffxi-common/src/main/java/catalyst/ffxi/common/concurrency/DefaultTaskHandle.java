package catalyst.ffxi.common.concurrency;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

@RequiredArgsConstructor
public class DefaultTaskHandle<T> implements TaskHandle<T> {

    @Getter
    private final UUID id = UUID.randomUUID();

    @Getter
    private volatile TaskStatus status = TaskStatus.PENDING;

    @Getter
    private final CompletableFuture<T> future = new CompletableFuture<>();

    private final Object statusLock = new Object();
    private volatile Future<?> rawFuture;
    private volatile T result;
    private volatile Throwable error;

    public void setRawFuture(Future<?> rawFuture) {
        this.rawFuture = rawFuture;
    }

    public void setRunning() {
        synchronized (statusLock) {
            if (this.status == TaskStatus.PENDING) {
                this.status = TaskStatus.RUNNING;
            }
        }
    }

    public boolean setSuccess(T result) {
        synchronized (statusLock) {
            if (this.status == TaskStatus.PENDING || this.status == TaskStatus.RUNNING) {
                this.result = result;
                this.status = TaskStatus.SUCCEEDED;
                this.future.complete(result);
                return true;
            }
            return false;
        }
    }

    public boolean setFailure(Throwable error) {
        synchronized (statusLock) {
            if (this.status == TaskStatus.PENDING || this.status == TaskStatus.RUNNING) {
                this.error = error;
                this.status = TaskStatus.FAILED;
                this.future.completeExceptionally(error);
                return true;
            }
            return false;
        }
    }

    public boolean setTimedOut() {
        synchronized (statusLock) {
            if (this.status == TaskStatus.PENDING || this.status == TaskStatus.RUNNING) {
                this.status = TaskStatus.TIMED_OUT;
                this.future.completeExceptionally(new java.util.concurrent.TimeoutException("Task timed out"));
                if (rawFuture != null) {
                    rawFuture.cancel(true);
                }
                return true;
            }
            return false;
        }
    }

    @Override
    public Throwable getError() {
        return error;
    }

    @Override
    public T getResult() {
        return result;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        synchronized (statusLock) {
            if (status == TaskStatus.PENDING || status == TaskStatus.RUNNING) {
                status = TaskStatus.CANCELLED;
                future.cancel(mayInterruptIfRunning);
                if (rawFuture != null) {
                    return rawFuture.cancel(mayInterruptIfRunning);
                }
                return true;
            }
            return false;
        }
    }
}
