package catalyst.ffxi.engine;

import org.lwjgl.glfw.GLFW;

import java.time.Duration;

public interface ApplicationLoopPolicy {
    boolean continueRunning(int frames, long windowHandle);

    static ApplicationLoopPolicy standard() {
        return (f, h) -> !GLFW.glfwWindowShouldClose(h);
    }

    static ApplicationLoopPolicy frames(int maxFrames) {
        return (f, h) -> f < maxFrames;
    }

    static ApplicationLoopPolicy skip() {
        return frames(0);
    }

    static ApplicationLoopPolicy timed(Duration duration) {
        final long limitNanos = duration.toNanos();
        final long start = System.nanoTime();
        if (limitNanos == 0L) return (f, h) -> false;
        return (f, h) -> !GLFW.glfwWindowShouldClose(h) && (System.nanoTime() - start) < limitNanos;
    }
}
