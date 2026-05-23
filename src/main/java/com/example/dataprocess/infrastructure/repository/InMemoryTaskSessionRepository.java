package com.example.dataprocess.infrastructure.repository;

import com.example.dataprocess.domain.model.TaskSession;
import com.example.dataprocess.domain.repository.TaskSessionRepository;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于内存的任务会话仓储实现。
 */
@Repository
public class InMemoryTaskSessionRepository implements TaskSessionRepository {

    private final Map<String, TaskSession> store = new ConcurrentHashMap<>();

    @Override
    public TaskSession save(TaskSession session) {
        store.put(session.taskId(), session);
        return session;
    }

    @Override
    public TaskSession findByTaskId(String taskId) {
        TaskSession session = store.get(taskId);
        if (session == null) {
            throw new IllegalArgumentException("未找到任务会话: " + taskId);
        }
        return session;
    }
}
