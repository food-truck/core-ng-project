package core.framework.grpc.impl;

import core.framework.util.Lists;
import io.grpc.BindableService;

import java.util.List;

/**
 * @author charlie
 */
public class GRPCServerConfig {
    public Integer port;
    public List<BindableService> services = Lists.newArrayList();
}
