package com.example.dataprocess.application.state;

/**
 * 图状态仓储接口，负责保存和读取工作流运行时状态。
 */
public interface DataProcessingGraphStateRepository {

    DataProcessingGraphState save(DataProcessingGraphState state);

    DataProcessingGraphState findByTaskId(String taskId);
}
