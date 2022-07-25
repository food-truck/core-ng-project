## Change Log In Wonder

### 1.3.0 ()
* corresponds to upstream version **7.10.6**
* bean validator: remove calling of BeanClassNameValidator, since we can’t coordinate all teams prevent them to use the same class name
* search: support extendSearch() for extra search cases in Wonder
* search: support checkProbe in SearchConfig. Once set `checkProbe = false`, it will skip es probe check when app starts
* log-processor: add `sys.elasticsearch.checkProbe` property to enable SearchConfig.checkProbe()
* log-processor: add a new log appender `ConsoleAppenderExtenssion` to index trace log into elasticsearch
* log-processor: expand action index name as `action-{app}-{date}` once the field `app` is not blank in action document
* log-processor: support `app.index.lifecycle.name` for linking lifecycle policy to index template 

### 1.2.3 (12/13/2021)

## corresponds to upstream version **7.9.3**.
* monitor: improve kube pod monitor error message for "Unschedulable" condition
* action: fixed webserviceClient/messagePublisher/executor only pass trace header when trace = cascade
* action: redesigned maxProcessTime behavior, use http client timeout and shutdown time out as benchmark
  > for executor task actions, use SHUTDOWN_TIMEOUT as maxProcessTime
  > for kafka listener, use SHUTDOWN_TIMEOUT as maxProcessTime for each message handling (just for warning purpose), ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG is still 30min
  > for http handler, use request timeout header or HTTPConfig.maxProcessTime(), (default to 30s)
  > for scheduler job, use 10s, scheduler job should be fast, generally just sending kafka message
* db: updated "too many db operations" check
  > if within maxProcessTime, it logs warning with error code TOO_MANY_DB_OPERATIONS, otherwise throws error
  > not break if critical workload generates massive db operations, and still protect from infinite loop by mistake
* db: added stats.db_queries to track how many db queries (batch operation counts as 1 perf_stats.db operation)
  > log-processor / kibana.json is updated with new diagram

### 1.2.2 (12/13/2021)

* corresponds to upstream version **7.9.2**.
* db: validate timestamp param must be after 1970-01-01 00:00:01
  > with insert ignore, out of range timestamp param will be converted to "0000-00-00 00:00:00" into db, and will trigger "Zero date value prohibited" error on read
  > refer to https://dev.mysql.com/doc/refman/8.0/en/insert.html
  > Data conversions that would trigger errors abort the statement if IGNORE is not specified. With IGNORE, invalid values are adjusted to the closest values and inserted; warnings are produced but the statement does not abort.
  > current we only check > 0, make trade off between validating TIMESTAMP column type and keeping compatible with DATETIME column type
  > most likely the values we deal with from external systems are lesser (e.g. nodejs default year is 1900, it converts 0 into 1900/01/01 00:00:00)
  > if it passes timestamp after 2038-01-19 03:14:07 (Instant.ofEpochSecond(Integer.MAX_VALUE)), it will still trigger this issue on MySQL
  > so on application level, if you can not ensure the range of input value, write your own utils to check before assigning
* jre: 17.0.1 released, published "neowu/jre:17.0.1"
* app: added external dependency checking before startup
  > it currently checks kafka, redis/cache, mongo, es to be ready (not db, generally we use managed db service created before kube cluster)
  > in kube env, during node upgrading or provision, app pods usually start faster than kafka/redis/other stateful set (e.g. one common issue we see is that scheduler job failed to send kafka message)
  > by this way, app pods will wait until external dependencies ready, it will fail to start if not ready in 30s
  > log kafka appender still treat log-kafka as optional
  > for es, it checks http://es:9200/_cluster/health?local=true
* http: Request.hostName() renamed to Request.hostname() to keep consistent with other places  !!! breaking change but easy to fix
* action: replaced ActionLogContext.trace() to ActionLogContext.triggerTrace(boolean cascade)
  > for audit context, we may not want to trace all correlated actions, with this way we can tweak the scope of tracing
* app: startupHooks introduced 2 stages (initialize and start), removed lazy init for kafka producer / elasticsearch / mongo
  > since client initialize() will be called during startup, it removes lazy init to simplify
  > if you want to call Mongo/ElasticSearch directly (e.g. local arbitrary main method), call initialize() before using
* log-processor: removed elasticsearch appender support
  > in prod env, we use null log appender for log-processor, since log-processor is stable, and not necessary to double the amount of action logs in log-es
  > if anything wrong happened, error output of log-processor is good enough for troubleshooting

### 1.2.1 (12/13/2021)

* corresponds to upstream version **7.9.1**.
* site: StaticDirectoryController will normalize path before serving the requested file, to prevent controller serving files outside content directory
  > it's not recommended serving static directory or file directly through java webapp,
  > better use CDN + static bucket or put Nginx in front of java process to server /static
* db: use useAffectedRows=true on mysql connection, return boolean for update/delete/upsert !!! pls read this carefully
  > refer to https://dev.mysql.com/doc/connector-j/8.0/en/connector-j-connp-props-connection.html#cj-conn-prop_useAffectedRows
  > the MySQL affected rows means the row actually changed,
  > e.g. sql like update table set column = 'value' where id = 'id', the affected row = 0 means either id not found or column value is set to its current values (no row affected)
  > so if you want to use update with optimistic lock or determine whether id exists, you can make one column always be changed, like set updatedTime = now()
* db: added Repository.upsert() and Repository.batchUpsert()
  > upsert is using "insert on duplicate key update", a common use case is data sync
  > !!! due to HSQL doesn't support MySQL useAffectedRows=true behavior, so upsert always return true
  > we may create HSQL upsert impl to support unit test if there is need in future (get by id then create or update approach)
* db: Repository update and delete operations return boolean to indicate whether updated
  > normally we expect a valid PK when updating by PK, if there is no row updated, framework will log warning,
  > and the boolean result is used by app code to determine whether break by throwing error
* db: removed DBConfig.batchSize() configuration
  > with recent MySQL server and jdbc driver, it is already auto split batch according to max_allowed_packet
  > refer to com.mysql.cj.AbstractPreparedQuery.computeBatchSize
  > and MySQL prefers large batch as the default max_allowed_packet value is getting larger
* db: mysql jdbc driver updated to 8.0.27
* redis: added Redis.list().trim(key, maxSize)
  > to make it easier to manage fixed length list in redis

### 1.2.0 (12/13/2021)

* corresponds to upstream version **7.9.0**.
* jdk: updated to JDK 17
  > for local env, it's easier to use intellij builtin way to download SDK, or go to https://adoptium.net/
  > adoptium (renamed from adoptopenjdk) doesn't provide JRE docker image anymore, you should build for yourself (or use JDK one if you don't mind image size)
  > refer to docker/jre folder, here it has slimmed jre image for generic core-ng app
* message: make Message.get() more tolerable, won't fail but log as error if key is missing or language is null
  > use first language defined in site().message() if language is null
  > return key and log error if message key is missing,
  > with integration test context, still throw error if key is missing, to make message unit test easier to write
* http: update undertow to 2.2.12
* actionLog: added ActionLogContext.trace() to trigger trace log
  > e.g. to integrate with external services, we want to track all the request/response for critical actions
  > recommended way is to use log-processor forward all action log messages to application kafka
  > then to create audit-service, consume the action log messages, save trace to designated location (Cloud Storage Service)
* action: removed ActionLogContext.remainingProcessTime(), and httpClient retryInterceptor won't consider actionLog.remainingProcessTimeInNano
  > it's not feasible to adapt time left before making external call (most likely http call with timeout),
  > due to http call is out of control (e.g. take long to create connection with external site), or external sdk/client not managed by framework
  > so it's better to careful plan in advance for sync chained http calls
  > maxProcessTime mechanism will be mainly used for measurement/visibility purpose (alert if one action took too long, close to maxProcessTime)

### 1.1.3 (12/13/2021)

* es: support ElasticSearch API keys

> refer to https://www.elastic.co/guide/en/elasticsearch/client/java-rest/current/_other_authentication_methods.html#_elasticsearch_api_keys

* log-processor: add ElasticSearch API keys optional config
* monitor: add ElasticSearch API keys optional config

### 1.1.2 (12/13/2021)

* corresponds to upstream version **7.8.2**.
* java: target to Java 16,
  > since all projects are on java 16 for long time, this should not be issue, will update to java 17 LTS, once adoptopenjdk released java 17 build
* kafka: update client to 3.0.0
* es: update to 7.15.0
* db: added "boolean partialUpdate(T entity, String where, Object... params)" on Repository, to support updating with optimistic lock
  > to clarify, Repository.update() must be used carefully, since it's update all columns to bean fields, regardless it's null
  > in actual project, common use cases generally are like to update few columns with id or optimistic lock, so always prefer partialUpdate over update
  > for accumulated update (like set amount = amount + ?), it's still better use Database.execute() + plain sql
* db: updated Repository.batchInsertIgnore to return boolean[], to tell exactly whether each entity was inserted successfully

### 1.1.1 (12/13/2021)

* corresponds to upstream version **7.8.1**.
* db: batchInsert returns Optional<long[]> for auto incremental PK
* db: update mysql driver to 8.0.26
* httpClient: support client ssl auth
* site: removed Session.timeout(Duration), it proved not useful, for app level remember me, it's better handle in app level with custom token handling
* redis: support password auth for redis/cache
* ws: added WebContext.responseCookie to allow WS to assign cookie to response

### 1.1.0 (12/13/2021)

* corresponds to upstream version **7.8.0**.
* es: update to 7.14.0
* log-processor: kibana 7.14 added duration human precise formatter, updated all time fields of index pattern
  > must update kibana/es to 7.14 to use this version of log-processor
* api: always publish /_sys/api, for internal api change monitoring
* api: added /_sys/api/message to publish message definition, for future message change monitoring
* error: refactored ErrorResponse and AJAXErrorResponse, !!! changed ErrorResponse json field from "error_code" to "errorCode"
  > !!! for consistency, it breaks contract, it's transparent if both client/server upgrade to same framework version, ErrorResponse only be used when client is from coreng
  > if client uses old version of framework, RemoteServiceException will not have errorCode
  > ErrorResponse is renamed to InternalErrorResponse, AJAXErrorResponse is renamed to ErrorResponse
* api: changed system property "sys.publishAPI.allowCIDR" to "sys.api.allowCIDR", !!! update sys.properties if this is used

### 1.0.5 (12/13/2021)

* corresponds to upstream version **7.7.5**.
* mongo: updated driver to 4.3.0
* action: added ActionLogContext.remainingProcessTime() for max time left for current action, to control external calling timeout or future get with timeout
* http: update undertow to 2.2.9

### 1.0.4 (12/13/2021)

* corresponds to upstream version **7.7.4**.
* site: added Session.timeout(Duration), to allow application set different timeout
  > e.g. use cases are like longer mobile app session expiration time, or "remember me" feature
* http: disabled okHTTP builtin retryOnConnectionFailure, use RetryInterceptor to log all connection failures explicitly, more failure case handling

### 1.0.3 (12/13/2021)

* corresponds to upstream version **7.7.3**.
* log: fixed error when third-party lib calls slf4f logger with empty var args (Azure SDK)
* executor: updated way to print canceled tasks during shutdown (with JDK16, it cannot access private field of internal JDK classes)
* log-processor: add first version of action flow diagram, pass actionId to visualize entire action flow with all related _id and correlation_id
  > e.g. https://localhost:8443/diagram/action?actionId=7A356AA3B1A5C6740794

### 1.0.2 (12/13/2021)

* corresponds to upstream version **7.7.2**.
* monitor: fixed overflowed vmRSS value, use long instead of int
* api: added "app" in APIDefinitionResponse
* monitor: api config json schema changed !!!
  > changed from map to List<ServiceURL>, to simplify config. it requires the latest framework, refers to above
    ```json
        {
          "api": {
            "services": ["https://website", "https://backoffice"]
          } 
        }
    ```

### 1.0.1 (12/13/2021)

* corresponds to upstream version **7.7.1**.
* log-processor/kibana: added http server/client dashboard and visualizations (http / dns / conn / reties / delays)
* http: added "action.stats.http_delay" to track time between http request start to action start (time spent on HTTPIOHandler)
  added action.stats.request_body_length/action.stat.response_body_length to track
* httpClient: track request/response body length thru perf_stats.http.write_entries/read_entries
* db: fixed insertIgnore reports write_entries wrong, mysql 8.0 return SUCCESS_NO_INFO (-2) if insert succeeds

### 1.0.0 (12/13/2021)

* corresponds to upstream version **7.7.0**.
* api: replaced /_sys/api, to expose more structured api info
  > one purpose is to create api monitoring, to alert if api breaks backward compatibility
  > !!! for ts client code generator, refer to https://github.com/neowu/frontend-demo-project/blob/master/website-frontend/webpack/api.js
* redis: support pop with multiple items
  > !!! only be supported since redis 6.2, use latest redis docker image if you use this feature
  > pop without count still uses old protocol, so it's optional to upgrade redis
* monitor: support to monitor api changes, alert config json schema changed !!!
  > refer to ext/monitor/src/test/resources/monitor.json, ext/monitor/src/test/resources/alert.json for example config
  > !!! alert config -> channels changed, to support one channel with multiple matchers, e.g.
  ```json 
    "notifications": [
        {"channel": "backendWarnChannel", "matcher": {"severity": "WARN", "indices": ["trace", "stat"]}},
        {"channel": "backendErrorChannel", "matcher": {"severity": "ERROR", "indices": ["trace", "stat"]}},
        {"channel": "frontendWarnChannel", "matcher": {"severity": "WARN", "indices": ["event"]}},
        {"channel": "frontendWarnChannel", "matcher": {"severity": "WARN", "errorCodes": ["API_CHANGED"], "indices": ["stat"]}},
        {"channel": "frontendErrorChannel", "matcher": {"severity": "ERROR", "indices": ["event"]}},
        {"channel": "frontendErrorChannel", "matcher": {"severity": "ERROR", "errorCodes": ["API_CHANGED"], "indices": ["stat"]}},
        {"channel": "additionalErrorCodeChannel", "matcher": {"apps": ["product-service"], "errorCodes": ["PRODUCT_ERROR"]}}
    ]
  ```
* log-processor: updated kibana objects to be compatible with kibana 7.12.0, rebuild objects with kibana object builder
  > refer to core-ng-demo-project/kibana-generator

* es: update to 7.13.0, updated ElasticSearch.putIndexTemplate impl to use new PutComposableIndexTemplateRequest
  > !!! refer to https://www.elastic.co/guide/en/elasticsearch/reference/current/index-templates.html
  > must update index template format to match new API, refer to ext/log-processor/src/main/resources/index/action-index-template.json as example
