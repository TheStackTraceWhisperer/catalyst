package catalyst.ffxi.engine;

import catalyst.ffxi.common.concurrency.TaskScheduler;
import catalyst.ffxi.engine.services.state.ApplicationStateService;
import catalyst.ffxi.engine.services.time.FrameTimeService;
import catalyst.ffxi.engine.services.window.WindowService;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Comparator;
import java.util.List;

@Slf4j
@Singleton
@RequiredArgsConstructor
public final class Engine implements Runnable {

    private enum EngineState { NEW, INITIALIZED, SHUTDOWN }

    private final ApplicationLoopPolicy loopPolicy;
    private final ApplicationStateService stateService;
    private final List<IService> services;
    private final WindowService windowService;
    private final FrameTimeService frameTime;
    private final TaskScheduler taskScheduler;

    private EngineState state = EngineState.NEW;
    private int frames = 0;

    public void init() {
        if (state != EngineState.NEW) {
            log.warn("Engine already initialized");
            return;
        }
        log.info("Initializing FFXI Engine");
        services.sort(Comparator.comparingInt(IService::executionOrder));
        for (IService service : services) {
            log.debug("Starting service: {}", service.getClass().getSimpleName());
            service.start();
        }
        state = EngineState.INITIALIZED;
        log.info("Engine initialized successfully");
    }

    public void tick() {
        if (state != EngineState.INITIALIZED) {
            throw new IllegalStateException("Cannot tick uninitialized engine");
        }
        windowService.pollEvents();
        taskScheduler.processForegroundTasks();
        for (IService service : services) service.update();
        float dt = frameTime.getDeltaTimeSeconds();
        for (IService service : services) service.update(dt);
        for (IService service : services) service.postUpdate();
        windowService.swapBuffers();
        frames++;
    }

    private void mainLoop() {
        while (loopPolicy.continueRunning(frames, windowService.getHandle())
               && !stateService.isEmpty()) {
            tick();
        }
    }

    public void shutdown() {
        if (state == EngineState.SHUTDOWN) return;
        log.info("Shutting down FFXI Engine");
        List<IService> reversed = services.stream()
            .sorted(Comparator.comparingInt(IService::executionOrder).reversed())
            .toList();
        for (IService service : reversed) {
            log.debug("Stopping service: {}", service.getClass().getSimpleName());
            service.stop();
        }
        state = EngineState.SHUTDOWN;
    }

    @Override
    public void run() {
        try {
            init();
            mainLoop();
        } finally {
            shutdown();
        }
    }
}
