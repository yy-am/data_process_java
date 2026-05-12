package com.example.dataprocess.application.workflow.node;

import com.example.dataprocess.application.state.DataProcessingGraphState;
import com.example.dataprocess.domain.model.InputSnapshot;
import com.example.dataprocess.infrastructure.tool.InputSnapshotTool;
import org.springframework.stereotype.Component;

/**
 * 构建输入快照节点。
 */
@Component
public class BuildInputSnapshotNode {

    private final InputSnapshotTool inputSnapshotTool;

    public BuildInputSnapshotNode(InputSnapshotTool inputSnapshotTool) {
        this.inputSnapshotTool = inputSnapshotTool;
    }

    /**
     * 基于当前任务会话生成标准输入快照。
     */
    public InputSnapshot execute(DataProcessingGraphState state) {
        return inputSnapshotTool.loadInputSnapshot(state.toTaskSession());
    }
}
