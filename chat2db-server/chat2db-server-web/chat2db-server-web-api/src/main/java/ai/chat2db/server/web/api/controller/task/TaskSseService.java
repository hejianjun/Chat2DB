package ai.chat2db.server.web.api.controller.task;

import ai.chat2db.server.domain.api.enums.TaskStatusEnum;
import ai.chat2db.server.domain.api.model.Task;
import ai.chat2db.server.domain.api.service.TaskEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
@Slf4j
public class TaskSseService implements TaskEventPublisher {

    private static final long TIMEOUT = 30 * 60 * 1000L;

    private final Map<Long, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Task task) {
        SseEmitter emitter = new SseEmitter(TIMEOUT);
        emitters.computeIfAbsent(task.getId(), key -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> removeEmitter(task.getId(), emitter));
        emitter.onTimeout(() -> removeEmitter(task.getId(), emitter));
        emitter.onError(error -> removeEmitter(task.getId(), emitter));
        send(emitter, task);
        if (TaskStatusEnum.FINISH.name().equals(task.getTaskStatus()) || TaskStatusEnum.ERROR.name().equals(task.getTaskStatus())) {
            emitter.complete();
        }
        return emitter;
    }

    @Override
    public void publish(Task task) {
        if (task == null || task.getId() == null) {
            return;
        }
        List<SseEmitter> taskEmitters = emitters.get(task.getId());
        if (taskEmitters == null || taskEmitters.isEmpty()) {
            return;
        }
        taskEmitters.forEach(emitter -> send(emitter, task));
        if (TaskStatusEnum.FINISH.name().equals(task.getTaskStatus()) || TaskStatusEnum.ERROR.name().equals(task.getTaskStatus())) {
            taskEmitters.forEach(SseEmitter::complete);
            emitters.remove(task.getId());
        }
    }

    private void send(SseEmitter emitter, Task task) {
        try {
            emitter.send(SseEmitter.event().name("task").data(task));
        } catch (IOException | IllegalStateException e) {
            log.warn("Failed to send task event, taskId={}", task.getId(), e);
            removeEmitter(task.getId(), emitter);
        }
    }

    private void removeEmitter(Long taskId, SseEmitter emitter) {
        List<SseEmitter> taskEmitters = emitters.get(taskId);
        if (taskEmitters == null) {
            return;
        }
        taskEmitters.remove(emitter);
        if (taskEmitters.isEmpty()) {
            emitters.remove(taskId);
        }
    }
}
