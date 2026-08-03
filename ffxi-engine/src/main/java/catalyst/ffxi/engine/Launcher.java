package catalyst.ffxi.engine;

import io.micronaut.context.ApplicationContext;
import io.micronaut.runtime.Micronaut;

public final class Launcher {
    private Launcher() {}

    public static void run(Class<?> primarySource, String[] args) {
        try (ApplicationContext ctx = Micronaut.build(args)
                .mainClass(primarySource)
                .banner(false)
                .start()) {
            ctx.getBean(Engine.class).run();
        }
    }
}
