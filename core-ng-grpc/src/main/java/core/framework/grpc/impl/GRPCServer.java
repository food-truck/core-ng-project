package core.framework.grpc.impl;

import core.framework.util.StopWatch;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.TimeUnit;

import static core.framework.log.Markers.errorCode;

/**
 * @author charlie
 */
public class GRPCServer {
    private final Logger logger = LoggerFactory.getLogger(GRPCServer.class);
    private final Server server;

    public GRPCServer(ServerBuilder<?> serverBuilder) {
        server = serverBuilder.build();
    }

    public void start() {
        var watch = new StopWatch();
        try {
            server.start();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            logger.info("grpc server started, port={}, elapsed={}", server.getPort(), watch.elapsed());
        }
    }

    public void shutdown() {
        if (server != null) {
            logger.info("shutting down grpc server");
            server.shutdown();
        }
    }

    public void awaitRequestCompletion(long timeoutMs) throws InterruptedException {
        if (server != null) {
            boolean success = server.awaitTermination(timeoutMs, TimeUnit.MILLISECONDS);
            if (!success) {
                logger.warn(errorCode("FAILED_TO_STOP"), "failed to wait for active grpc requests to complete");
                server.shutdownNow();
            } else {
                logger.info("active grpc requests complete");
            }
        }
    }

    public void awaitTermination() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
            logger.info("grpc server stopped");
        }
    }
}
