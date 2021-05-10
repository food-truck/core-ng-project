package core.log.service;

import core.framework.inject.Inject;
import core.framework.log.LogAppender;
import core.framework.log.message.ActionLogMessage;
import core.framework.log.message.StatMessage;

/**
 * @author neo
 */
public class ElasticSearchAppender implements LogAppender {
    @Inject
    ActionService actionService;
    @Inject
    StatService statService;

    @Override
    public void append(ActionLogMessage message) {
        actionService.index(message);
    }

    @Override
    public void append(StatMessage message) {
        statService.index(message);
    }
}
