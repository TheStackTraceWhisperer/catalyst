package catalyst.ffxi.engine.services.state;

public interface ApplicationState {
    void onEnter();
    void onUpdate(float dt);
    void onExit();
    default void onResume()  {}
    default void onSuspend() {}
}
