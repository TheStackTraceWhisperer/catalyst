package catalyst.client.engine.services.window;

import catalyst.client.engine.IService;
import jakarta.inject.Singleton;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;

import java.nio.IntBuffer;

@Slf4j
@Singleton
@RequiredArgsConstructor
public final class WindowService implements IService {

    private final WindowProperties props;

    @Getter
    private long handle = 0L;

    @Override
    public int executionOrder() { return Integer.MIN_VALUE + 1; }

    @Override
    public void start() {
        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_CLIENT_API, GLFW.GLFW_OPENGL_API);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 4);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 6);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, GLFW.GLFW_TRUE);
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);

        handle = GLFW.glfwCreateWindow(props.getWidth(), props.getHeight(), props.getTitle(), 0L, 0L);
        if (handle == 0L) throw new IllegalStateException("Failed to create GLFW window (OpenGL 4.6 core)");

        GLFW.glfwMakeContextCurrent(handle);
        if (GLFW.glfwGetCurrentContext() != handle) {
            GLFW.glfwDestroyWindow(handle);
            handle = 0L;
            throw new IllegalStateException("Failed to make OpenGL context current");
        }

        GL.createCapabilities();
        GLFW.glfwSwapInterval(1);
        GL30.glViewport(0, 0, props.getWidth(), props.getHeight());

        try {
            log.info("OpenGL version: {}", GL11.glGetString(GL11.GL_VERSION));
            log.info("OpenGL renderer: {}", GL11.glGetString(GL11.GL_RENDERER));
            log.info("OpenGL vendor: {}", GL11.glGetString(GL11.GL_VENDOR));
        } catch (Throwable t) {
            log.warn("Failed to query OpenGL version info", t);
        }

        GLFW.glfwShowWindow(handle);
        log.info("Window created: {}x{} '{}' handle={}", props.getWidth(), props.getHeight(), props.getTitle(), handle);
    }

    @Override
    public void update() {
        GLFW.glfwPollEvents();
    }

    @Override
    public void stop() {
        if (handle != 0L) {
            GLFW.glfwDestroyWindow(handle);
            log.info("Window destroyed: handle={}", handle);
            handle = 0L;
        }
    }

    public void swapBuffers() {
        if (handle != 0L) GLFW.glfwSwapBuffers(handle);
    }

    public void pollEvents() {
        GLFW.glfwPollEvents();
    }

    public int getWidth() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer w = stack.mallocInt(1), h = stack.mallocInt(1);
            GLFW.glfwGetWindowSize(handle, w, h);
            return w.get(0);
        }
    }

    public int getHeight() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer w = stack.mallocInt(1), h = stack.mallocInt(1);
            GLFW.glfwGetWindowSize(handle, w, h);
            return h.get(0);
        }
    }
}
