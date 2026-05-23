package com.example.dataprocess.infrastructure.service;

import com.example.dataprocess.domain.model.InputSnapshot;
import com.example.dataprocess.domain.model.TaskSession;
import org.springframework.stereotype.Service;

/**
 * 输入快照服务。
 */
@Service
public class InputSnapshotService {

    /**
     * 基于任务会话构建标准化输入快照。
     */
    public InputSnapshot build(TaskSession session) {
        return new InputSnapshot(
                session.taskId(),
                session.inputType(),
                session.sourceHeaders(),
                session.sampleRows()
        );
    }
}
