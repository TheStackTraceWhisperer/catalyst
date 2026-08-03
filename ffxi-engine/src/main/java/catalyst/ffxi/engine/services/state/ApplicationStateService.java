package catalyst.ffxi.engine.services.state;

import catalyst.ffxi.engine.IService;
import jakarta.inject.Named;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.util.Stack;
import java.util.function.Supplier;

@Slf4j
@Singleton
public class ApplicationStateService implements IService {

    private final Provider<ApplicationState> initialStateProvider;
    private final Stack<ApplicationState> stack = new Stack<>();

    public ApplicationStateService(@Named("initial") Provider<ApplicationState> initialStateProvider) {
        this.initialStateProvider = initialStateProvider;
    }

    @Override
    public int executionOrder() { return 100; }

    @Override
    public void start() {
        pushState(initialStateProvider::get);
    }

    @Override
    public void stop() {
        log.info("Stopping ApplicationStateService — popping all states");
        while (!stack.isEmpty()) popState();
    }

    @Override
    public void update(float dt) {
        if (!stack.isEmpty()) stack.peek().onUpdate(dt);
    }

    public boolean isEmpty() { return stack.isEmpty(); }

    public ApplicationState peek() { return stack.isEmpty() ? null : stack.peek(); }

    public void pushState(Supplier<? extends ApplicationState> supplier) {
        if (supplier == null) { log.warn("pushState called with null supplier"); return; }
        if (!stack.isEmpty()) {
            stack.peek().onSuspend();
        }
        ApplicationState next = supplier.get();
        log.debug("Pushing state: {}", next.getClass().getSimpleName());
        stack.push(next);
        next.onEnter();
    }

    public void popState() {
        if (stack.isEmpty()) { log.warn("popState on empty stack"); return; }
        ApplicationState top = stack.pop();
        log.debug("Popping state: {}", top.getClass().getSimpleName());
        top.onExit();
        if (!stack.isEmpty()) {
            stack.peek().onResume();
        }
    }

    public void changeState(Supplier<? extends ApplicationState> supplier) {
        if (supplier == null) { log.warn("changeState called with null supplier"); return; }
        if (!stack.isEmpty()) {
            ApplicationState old = stack.pop();
            log.debug("Changing state from: {}", old.getClass().getSimpleName());
            old.onExit();
        }
        ApplicationState next = supplier.get();
        log.debug("Changing state to: {}", next.getClass().getSimpleName());
        stack.push(next);
        next.onEnter();
    }
}
