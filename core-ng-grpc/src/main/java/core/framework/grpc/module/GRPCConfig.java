package core.framework.grpc.module;

import core.framework.internal.module.Config;
import core.framework.internal.module.ModuleContext;
import io.grpc.Server;

import static core.framework.util.Strings.format;

/**
 * @author charlie
 */
public class GRPCConfig extends Config {
    private ModuleContext context;
    private String name;
    private Integer port;
    private Server grpcServer;

    @Override
    protected void initialize(ModuleContext context, String name) {
        this.context = context;
        this.name = name;
    }

    private void port(Integer port) {
        if (this.port != null) throw new Error(format("grpc server port is already configured, name={}, port={}, previous={}", name, port, this.port));
        this.port = port;
    }
}
