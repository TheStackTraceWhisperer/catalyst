package catalyst.client.engine.services.time;

import catalyst.client.engine.IService;
import jakarta.inject.Singleton;
import org.lwjgl.glfw.GLFW;

@Singleton
public class FrameTimeService implements IService {

    private double lastTime = 0.0;
    private float  deltaTime = 0.0f;

    @Override
    public int executionOrder() { return Integer.MIN_VALUE + 3; }

    @Override
    public void start() {
        lastTime = GLFW.glfwGetTime();
    }

    @Override
    public void update() {
        double now = GLFW.glfwGetTime();
        deltaTime = (float) (now - lastTime);
        lastTime = now;
    }

    public float getDeltaTimeSeconds() { return deltaTime; }
}
