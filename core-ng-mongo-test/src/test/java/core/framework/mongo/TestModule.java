package core.framework.mongo;

import core.framework.mongo.impl.TestMongoEntity;
import core.framework.mongo.impl.TestMongoView;
import core.framework.mongo.module.MongoConfig;
import core.framework.test.module.AbstractTestModule;

/**
 * @author neo
 */
public class TestModule extends AbstractTestModule {
    @Override
    protected void initialize() {
        MongoConfig mongo = config(MongoConfig.class);
        mongo.uri("mongodb://localhost:27017/test");
        mongo.collection(TestMongoEntity.class);
        mongo.view(TestMongoView.class);

        mongo = config(MongoConfig.class, "other");
        mongo.uri("mongodb://localhost:27018/test");
        mongo.collection(TestMongoEntity.class);
    }
}
