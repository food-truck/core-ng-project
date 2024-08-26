package core.log.service;

import core.framework.search.SearchException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.UncheckedIOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author rickeyhong
 */
class NetworkErrorRetryServiceTest {
    NetworkErrorRetryService networkErrorRetryService;

    @BeforeEach
    void init() {
        networkErrorRetryService = new NetworkErrorRetryService(1);
    }

    @Test
    void run() {
        var retry = new AtomicInteger(0);
        Assertions.assertDoesNotThrow(() -> networkErrorRetryService.run(3, () -> {
            var index = retry.getAndIncrement();
            if (index == 1) {
                throw new UncheckedIOException(new ConnectException("can't connect"));
            } else if (index == 2) {
                throw new UncheckedIOException(new SocketTimeoutException("timeout"));
            } else if (index == 3) {
                throw new SearchException("timeout", new SocketTimeoutException("timeout"));
            }
        }));

        retry.set(0);
        Assertions.assertThrows(UncheckedIOException.class, () -> networkErrorRetryService.run(3, () -> {
            throw new UncheckedIOException(new ConnectException("can't connect"));
        }));
    }
}
