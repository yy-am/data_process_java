package com.example.dataprocess.infrastructure.tool;

import com.example.dataprocess.domain.model.InputSnapshot;
import com.example.dataprocess.domain.model.TaskSession;
import com.example.dataprocess.infrastructure.runtime.SkillExecutionStateHolder;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/**
 * 输入快照工具，负责向模型暴露当前任务的标准化输入视图。
 */
@Component
public class InputSnapshotTool {

    private final SkillExecutionStateHolder stateHolder;

    public InputSnapshotTool(SkillExecutionStateHolder stateHolder) {
        this.stateHolder = stateHolder;
    }

    @Tool(name = "inputSnapshotTool", description = "Load the normalized input snapshot for the current data processing task.")
    public InputSnapshot loadCurrentInputSnapshot() {
        return loadInputSnapshot(stateHolder.getRequiredCurrentState().toTaskSession());
    }

    public InputSnapshot loadInputSnapshot(TaskSession session) {
        return new InputSnapshot(
                session.taskId(),
                session.inputType(),
                session.sourceHeaders(),
                session.sampleRows()
        );
    }
}
