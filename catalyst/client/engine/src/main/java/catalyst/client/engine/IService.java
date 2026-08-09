package catalyst.client.engine;

public interface IService {
    default void start()          {}
    default void stop()           {}
    default void update()         {}
    default void update(float dt) {}
    default void postUpdate()     {}  // called after all update(dt), before buffer swap
    default int  executionOrder() { return 0; }
}
