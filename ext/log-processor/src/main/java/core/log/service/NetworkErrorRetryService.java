package core.log.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.ConnectException;
import java.net.SocketTimeoutException;

/**
 * @author rickeyhong
 */
public class NetworkErrorRetryService {
    private static final Logger LOGGER = LoggerFactory.getLogger(NetworkErrorRetryService.class);
    private final int defaultMaxRetry;

    public NetworkErrorRetryService(int defaultMaxRetry) {
        this.defaultMaxRetry = defaultMaxRetry;
    }

    public void run(Runnable action) {
        run(defaultMaxRetry, action);
    }

    public void run(int maxRetry, Runnable action) {
        run(0, maxRetry, action);
    }

    private void run(int retry, int maxRetry, Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            if (retry >= maxRetry) {
                throw e;
            }
            Throwable cause = e;
            while (cause != null) {
                if (cause instanceof ConnectException || cause instanceof SocketTimeoutException) {
                    LOGGER.warn("network error! trying rerun, retry: {}, maxRetry: {}", retry, maxRetry);
                    run(retry + 1, maxRetry, action);
                    return;
                }
                cause = cause.getCause();
            }
            throw e;
        }
    }
}
