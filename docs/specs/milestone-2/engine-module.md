# Spec: `ffxi-engine` Module

## Purpose

Provide the shared client engine kernel as a reusable Maven module. All client GLFW/OpenGL/ImGui lifecycle, the main loop, and the application state machine live here. The `ffxi-client` module depends on `ffxi-engine` and contributes only application-specific state implementations and configuration.

## Blueprint

Directly inspired by `reference/october/engine`. Patterns are adopted, not code.

## Module Structure

```
ffxi-engine/
  src/main/java/catalyst/ffxi/engine/
    Launcher.java
    Engine.java
    IService.java
    ApplicationLoopPolicy.java
    DefaultEngineConfiguration.java
    services/
      glfw/    GlfwContextService.java
      window/  WindowService.java, WindowProperties.java
      imgui/   ImGuiService.java
      time/    FrameTimeService.java
      state/   ApplicationState.java, ApplicationStateService.java
  src/main/resources/
    application.yml
```

## Key Classes

### `IService`

```java
public interface IService {
    default void start()          {}
    default void stop()           {}
    default void update()         {}    // pre-frame, no dt
    default void update(float dt) {}    // per-frame with delta time
    default int executionOrder()  { return 0; }
}
```

Services are collected by Micronaut as `List<IService>`, sorted by `executionOrder()`, and ticked each frame.

Execution order conventions:

| Range | Meaning |
|---|---|
| `Integer.MIN_VALUE` | GLFW init (must be first) |
| `Integer.MIN_VALUE + 1` | Window creation |
| `0` (default) | General services |
| `100` | Application state service (must be last) |

### `Engine`

`@Singleton`. Owns the main loop. Injected with `List<IService>` and `ApplicationLoopPolicy`.

```
init()      → sort services by order → start() each
mainLoop()  → while(loopPolicy && stateStack not empty): tick()
tick()      → pollEvents, update() all, update(dt) all, swapBuffers
shutdown()  → stop() each in reverse order
run()       → init → mainLoop → shutdown (in finally)
```

### `Launcher`

Static entry point. Starts Micronaut, retrieves `Engine`, calls `engine.run()`.

```java
public class Launcher {
    public static void run(Class<?> primarySource, String[] args) {
        try (ApplicationContext ctx = Micronaut.build(args)
                .mainClass(primarySource)
                .banner(false)
                .start()) {
            ctx.getBean(Engine.class).run();
        }
    }
}
```

### `ApplicationLoopPolicy`

Strategy interface for loop continuation. `DefaultEngineConfiguration` provides `@Requires(missingBeans=...)` fallback to `ApplicationLoopPolicy.standard()` (continues until GLFW window close).

### `ApplicationState`

```java
public interface ApplicationState {
    void onEnter();
    void onUpdate(float dt);
    void onExit();
    default void onResume()  {}
    default void onSuspend() {}
}
```

### `ApplicationStateService`

`@Singleton`. Stack-based FSM. `executionOrder() = 100` (runs last).

- `pushState(Supplier<ApplicationState>)` — suspends current, enters new
- `popState()` — exits current, resumes previous
- `changeState(Supplier<ApplicationState>)` — exits current, enters new (no stack growth)
- `isEmpty()` — signals engine to stop main loop

States are `@Prototype` beans. Callers use `BeanProvider<T>::get` as the supplier so Micronaut creates a fresh instance per transition.

### `GlfwContextService`

`@Singleton`, `executionOrder = Integer.MIN_VALUE`. Calls `glfwInit()` on `start()`, `glfwTerminate()` on `stop()`. Registers a SLF4J-backed error callback.

### `WindowService`

`@Singleton`, `executionOrder = Integer.MIN_VALUE + 1`. Creates an **OpenGL 4.6 core profile** GLFW window using properties from `WindowProperties`.

```java
@ConfigurationProperties("engine.window")
public interface WindowProperties {
    @Bindable(defaultValue = "1280") int getWidth();
    @Bindable(defaultValue = "720")  int getHeight();
    @Bindable(defaultValue = "FFXI Client") String getTitle();
}
```

### `ImGuiService`

`@Singleton`. Wraps `ImGuiImplGlfw` + `ImGuiImplGl3`. `start()` initialises the Dear ImGui context. `update()` calls `newFrame()`. `stop()` disposes.

### `FrameTimeService`

`@Singleton`. Tracks delta time between frames using `glfwGetTime()`.

## Dependencies (`ffxi-engine/pom.xml`)

```xml
micronaut-inject, micronaut-runtime, micronaut-context
lwjgl, lwjgl-glfw, lwjgl-opengl (+ linux natives)
imgui-java-binding, imgui-java-lwjgl3, imgui-java-natives-linux
lombok
slf4j-api, logback-classic
```

## Milestone 2 Done Criteria

- [ ] Module builds independently
- [ ] `Launcher.run()` boots Micronaut and enters the engine loop
- [ ] All services start/stop in correct order
- [ ] GLFW window opens with OpenGL 4.6 core context
- [ ] ImGui renders correctly in the engine loop
- [ ] `ApplicationStateService` transitions work: push/pop/change
- [ ] Empty state stack causes engine loop to exit cleanly
