# Spec: Virtual-thread Task Scheduler Kernel

## Purpose

Provide a shared scheduler kernel in `ffxi-common` for background work that keeps render-thread operations safe and removes ad-hoc executor usage in feature code.

## Reference

- `ffxi-xim-analysis/ffxi-viewer/TaskScheduler` (virtual-thread background + foreground callback queue)

## Design

- Add `TaskSchedulerService` to `ffxi-common` as the shared scheduler implementation.
- Use `Executors.newVirtualThreadPerTaskExecutor()` for background tasks.
- Maintain a thread-safe foreground queue (`Runnable`) for main-thread callbacks.
- Drain foreground callbacks during the engine update tick.

## Proposed API

```java
public interface TaskScheduler {
    <T> TaskHandle<T> submit(Callable<T> task);
    <T> TaskHandle<T> submit(
        Callable<T> task,
        Consumer<T> onSuccess,
        Consumer<Throwable> onError
    );
    void runOnMainThread(Runnable action);
    void processForegroundTasks();
}
```

## Threading Rules

- Background tasks must never call OpenGL/ImGui APIs directly.
- UI/render mutations must run through `runOnMainThread(...)`.
- `processForegroundTasks()` runs on engine thread once per frame.

## Integration Points

- `Engine` invokes foreground queue processing every frame.
- Client services/states use scheduler API instead of creating local executors.
- Server may adopt the same abstraction for long-running background orchestration where relevant.

## Milestone 3 Done Criteria

- [ ] `TaskSchedulerService` exists in `ffxi-common`.
- [ ] Virtual-thread executor is used for background tasks.
- [ ] Foreground callback queue is processed in engine loop.
- [ ] At least one existing feature flow is migrated to scheduler API.
