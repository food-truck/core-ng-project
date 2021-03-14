package core.framework.internal.kafka;

/**
 * @author mort
 */
public class SASLConfig {
    public static final String MECHANISM = "PLAIN"; // only support PLAIN for now, https://kafka.apache.org/documentation/#security_overview
    private final String jaas;

    public SASLConfig(String jaas) {
        this.jaas = jaas;
    }

    public String jaas() {
        return jaas;
    }
}
