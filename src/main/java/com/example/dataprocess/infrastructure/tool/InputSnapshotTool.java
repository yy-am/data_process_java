package com.example.dataprocess.infrastructure.tool;

import com.example.dataprocess.domain.model.InputSnapshot;
import com.example.dataprocess.domain.model.TaskSession;
import org.springframework.stereotype.Component;

/**
 * 输入快照工具。
 */
@Component
public class InputSnapshotTool {

    public InputSnapshot loadInputSnapshot(TaskSession session) {
        return new InputSnapshot(
                session.taskId(),
                "table-import",
                session.sourceHeaders(),
                session.sampleRows()
        );
    }
}
