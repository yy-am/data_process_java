package com.example.dataprocess.agent.tool;

import com.example.dataprocess.agent.model.AgentWorkflowStage;
import com.example.dataprocess.agent.model.DataProcessingAgentState;
import com.example.dataprocess.agent.repository.DataProcessingAgentStateRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Tool facade for agent state persistence.
 */
@Component
public class AgentStateTool {

    private final DataProcessingAgentStateRepository repository;

    public AgentStateTool(DataProcessingAgentStateRepository repository) {
        this.repository = repository;
    }

    public Optional<DataProcessingAgentState> loadTaskState(String taskId) {
        return repository.findByTaskId(taskId);
    }

    public DataProcessingAgentState saveTaskState(DataProcessingAgentState state) {
        return repository.save(state);
    }

    public DataProcessingAgentState markTaskFailed(
            DataProcessingAgentState state,
            String errorCode,
            String message
    ) {
        return repository.save(state
                .withStage(AgentWorkflowStage.FAILED)
                .addError(errorCode + ": " + message));
    }
}
