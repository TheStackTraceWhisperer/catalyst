package catalyst.ffxi.engine.services.glfw;

import catalyst.ffxi.engine.IService;
import jakarta.inject.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;

@Slf4j
@Singleton
public class GlfwContextService implements IService {

    @Getter
    private boolean initialized = false;
    private GLFWErrorCallback errorCallback;

    @Override
    public int executionOrder() { return Integer.MIN_VALUE; }

    @Override
    public void start() {
        errorCallback = GLFWErrorCallback.create((error, description) ->
            log.error("[GLFW] code={} description={}", error, GLFWErrorCallback.getDescription(description)));
        errorCallback.set();

        if (!GLFW.glfwInit()) {
            freeCallback();
            throw new IllegalStateException("Unable to initialize GLFW");
        }
        initialized = true;
        log.info("GLFW initialized");
    }

    @Override
    public void stop() {
        if (!initialized) return;
        GLFW.glfwTerminate();
        freeCallback();
        initialized = false;
        log.info("GLFW terminated");
    }

    private void freeCallback() {
        GLFWErrorCallback prev = GLFW.glfwSetErrorCallback(null);
        if (prev != null) prev.free();
        errorCallback = null;
    }
}
