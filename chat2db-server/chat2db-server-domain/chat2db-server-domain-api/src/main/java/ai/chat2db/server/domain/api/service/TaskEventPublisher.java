package ai.chat2db.server.domain.api.service;

import ai.chat2db.server.domain.api.model.Task;

public interface TaskEventPublisher {

    void publish(Task task);
}
