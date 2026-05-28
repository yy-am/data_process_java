package com.example.dataprocess.agent.repository;

import com.example.dataprocess.agent.model.DataProcessingAgentState;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory state repository for early agent testing.
 */
@Repository
public class InMemoryDataProcessingAgentStateRepository implements DataProcessingAgentStateRepository {

    private final Map<String, DataProcessingAgentState> store = new ConcurrentHashMap<>();

    @Override
    public Optional<DataProcessingAgentState> findByTaskId(String taskId) {
        return Optional.ofNullable(store.get(taskId));
    }

    @Override
    public DataProcessingAgentState save(DataProcessingAgentState state) {
        store.put(state.taskId(), state);
        return state;
    }
}
