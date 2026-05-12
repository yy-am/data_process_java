package com.example.dataprocess.domain.repository;

import com.example.dataprocess.domain.model.TaskSession;

/**
 * 任务会话仓储接口。
 */
public interface TaskSessionRepository {

    TaskSession save(TaskSession session);

    TaskSession findByTaskId(String taskId);
}
