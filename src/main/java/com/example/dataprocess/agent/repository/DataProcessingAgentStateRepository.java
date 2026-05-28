package com.example.dataprocess.agent.repository;

import com.example.dataprocess.agent.model.DataProcessingAgentState;

import java.util.Optional;

/**
 * Agent state repository.
 */
public interface DataProcessingAgentStateRepository {

    Optional<DataProcessingAgentState> findByTaskId(String taskId);

    DataProcessingAgentState save(DataProcessingAgentState state);
}
