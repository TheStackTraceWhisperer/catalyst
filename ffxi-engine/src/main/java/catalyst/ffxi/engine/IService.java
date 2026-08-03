package catalyst.ffxi.engine;

public interface IService {
    default void start()          {}
    default void stop()           {}
    default void update()         {}
    default void update(float dt) {}
    default int  executionOrder() { return 0; }
}
