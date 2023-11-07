package core.framework.grpc.module;

import core.framework.grpc.impl.GRPCServer;
import core.framework.internal.module.Config;
import core.framework.internal.module.ModuleContext;
import core.framework.internal.module.ShutdownHook;
import io.grpc.Channel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.ServerBuilder;
import io.grpc.ServerServiceDefinition;
import io.grpc.protobuf.services.HealthStatusManager;
import io.grpc.protobuf.services.ProtoReflectionService;
import io.grpc.stub.AbstractBlockingStub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Function;

import static core.framework.util.Strings.format;

/**
 * @author charlie
 */
public class GRPCConfig extends Config {
    private final Logger logger = LoggerFactory.getLogger(GRPCConfig.class);
    private ModuleContext context;
    private String name;
    private Integer port;
    private List<ServerServiceDefinition> serviceDefinitions;
    private GRPCServer grpcServer;
    private boolean serviceAdded;
    private boolean clientAdded;

    @Override
    protected void initialize(ModuleContext context, String name) {
        this.context = context;
        this.name = name;

        if (serviceAdded) {
            var serverBuilder = ServerBuilder
                .forPort(port)
                .addServices(serviceDefinitions)
                .addService(new HealthStatusManager().getHealthService())
                .addService(ProtoReflectionService.newInstance());
            grpcServer = new GRPCServer(serverBuilder);
            context.startupHook.start.add(() -> grpcServer.start());
            context.shutdownHook.add(ShutdownHook.STAGE_0, timeout -> grpcServer.shutdown());
            context.shutdownHook.add(ShutdownHook.STAGE_1, grpcServer::awaitRequestCompletion);
            context.shutdownHook.add(ShutdownHook.STAGE_8, timeout -> grpcServer.awaitTermination());
        }
    }

    @Override
    protected void validate() {
        if (port == null) throw new Error("grpc port must be configured, name=" + name);
        if (!serviceAdded && !clientAdded)
            throw new Error("grpc is configured but no service/client added, please remove unnecessary config, name=" + name);
    }

    private void port(Integer port) {
        if (this.port != null)
            throw new Error(format("grpc server port is already configured, name={}, port={}, previous={}", name, port, this.port));
        this.port = port;
    }

    public <T extends ServerServiceDefinition> void service(Class<T> serviceInterface, T service) {
        logger.info("create grpc service, interface={}", serviceInterface.getCanonicalName());
        serviceDefinitions.add(service);
        serviceAdded = true;
    }

    public <T extends AbstractBlockingStub<T>> T client(Class<T> serviceInterface, Function<Channel, T> newBlockingStub, String serviceURL) {
        logger.info("create grpc client, interface={}, serviceURL={}", serviceInterface.getCanonicalName(), serviceURL);
        T client = newBlockingStub.apply(defaultChannelBuilder(serviceURL).build());
        context.beanFactory.bind(serviceInterface, null, client);
        clientAdded = true;
        return client;
    }

    private ManagedChannelBuilder<?> defaultChannelBuilder(String serviceUrl) {
        return ManagedChannelBuilder
            .forTarget(serviceUrl)
            .defaultLoadBalancingPolicy("pick_first")
            .usePlaintext();
    }
}
