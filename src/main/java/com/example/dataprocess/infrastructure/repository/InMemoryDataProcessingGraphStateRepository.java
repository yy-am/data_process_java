package com.example.dataprocess.infrastructure.repository;

import com.example.dataprocess.application.state.DataProcessingGraphState;
import com.example.dataprocess.application.state.DataProcessingGraphStateRepository;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于内存的图状态仓储实现。
 */
@Repository
public class InMemoryDataProcessingGraphStateRepository implements DataProcessingGraphStateRepository {

    private final Map<String, DataProcessingGraphState> store = new ConcurrentHashMap<>();

    @Override
    public DataProcessingGraphState save(DataProcessingGraphState state) {
        store.put(state.taskId(), state);
        return state;
    }

    @Override
    public DataProcessingGraphState findByTaskId(String taskId) {
        DataProcessingGraphState state = store.get(taskId);
        if (state == null) {
            throw new IllegalArgumentException("Graph state not found: " + taskId);
        }
        return state;
    }
}
