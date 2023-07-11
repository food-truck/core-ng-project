package core.framework.module;

import com.sun.management.OperatingSystemMXBean;
import core.framework.internal.asm.DynamicInstanceBuilder;
import core.framework.internal.json.JSONMapper;
import core.framework.internal.log.ActionLog;
import core.framework.internal.log.LogManager;
import core.framework.internal.module.ModuleContext;
import core.framework.internal.module.StartupHook;
import core.framework.internal.validate.Validator;
import core.framework.log.Markers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.util.Optional;

/**
 * @author neo
 */
public abstract class App extends Module {
    private final LogManager logManager = new LogManager();
    private final Logger logger = LoggerFactory.getLogger(App.class);

    public final void start() {
        ActionLog actionLog = logManager.begin("=== startup begin ===", null);
        boolean failed = false;
        try {
            logContext(actionLog);
            configure();
            invokeHook("configure");
            context.probe.check();    // readiness probe only needs to run on actual startup, not on test
            logger.info("execute startup tasks");
            context.startupHook.initialize();
            context.prepareHook.invoke(StartupHook.class, "initialize");

            context.startupHook.start();
            context.prepareHook.invoke(StartupHook.class, "start");
            cleanup();
            logger.info("startup completed, elapsed={}", actionLog.elapsed());
        } catch (Throwable e) {
            logger.error(Markers.errorCode("FAILED_TO_START"), "app failed to start, error={}", e.getMessage(), e);
            failed = true;
        } finally {
            logManager.end("=== startup end ===");
        }
        if (failed) {
            System.exit(1);
        }
    }

    void logContext(ActionLog actionLog) {
        actionLog.action("app:start");
    }

    public final void configure() {
        logger.info("initialize framework");
        Runtime runtime = Runtime.getRuntime();
        logger.info("availableProcessors={}, maxMemory={}", runtime.availableProcessors(), ((OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean()).getTotalMemorySize());
        logger.info("jvmArgs={}", String.join(" ", ManagementFactory.getRuntimeMXBean().getInputArguments()));

        context = new ModuleContext(logManager);
        runtime.addShutdownHook(new Thread(context.shutdownHook, "shutdown"));

        logger.info("initialize application");
        Optional.ofNullable(Thread.currentThread().getContextClassLoader().getResource("plugin.properties")).ifPresent(ignore -> plugin());
        initialize();
        invokeHook("initialize");
        context.validate();
    }

    private void invokeHook(String method) {
        context.prepareHook.invoke(getClass(), method);
        context.prepareHook.invoke(App.class, method);
    }

    private void cleanup() {
        // free static objects not used anymore
        Validator.cleanup();
        JSONMapper.cleanup();
        context.prepareHook.cleanup();
        context.pluginManager.cleanup();
        if (!context.httpServer.siteManager.webDirectory.localEnv) {    // for local env, it may rebuild html template at runtime
            DynamicInstanceBuilder.cleanup();
        }
    }
}
