package core.log;

/**
 * @author rickeyhong
 */
public interface LogIndexRouter {
    String route(String indexName);

    LogIndexRouter DEFAULT_ROUTER = indexName -> indexName;

    record FixedLogIndexRouter(String fixedIndexName) implements LogIndexRouter {
        @Override
        public String route(String indexName) {
            return fixedIndexName;
        }
    }
}