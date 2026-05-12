package com.example.dataprocess.application.workflow;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.example.dataprocess.application.state.DataProcessingGraphState;
import com.example.dataprocess.application.state.DataProcessingGraphStateRepository;
import com.example.dataprocess.domain.model.TaskSession;
import com.example.dataprocess.domain.repository.TaskSessionRepository;
import com.example.dataprocess.interfaces.restful.request.DataProcessingTaskRequest;
import com.example.dataprocess.interfaces.restful.request.UserConfirmationRequest;
import com.example.dataprocess.interfaces.restful.response.DataProcessingTaskResponse;
import com.example.dataprocess.interfaces.restful.response.UserConfirmationResponse;
import org.springframework.stereotype.Service;

/**
 * 数据加工状态图工作流门面，负责启动和恢复同一条流程。
 */
@Service
public class DataProcessingStateGraphWorkflow {

    private final CompiledGraph compiledGraph;
    private final DataProcessingStateGraphDefinition definition;
    private final TaskSessionRepository taskSessionRepository;
    private final DataProcessingGraphStateRepository graphStateRepository;

    public DataProcessingStateGraphWorkflow(
            CompiledGraph compiledGraph,
            DataProcessingStateGraphDefinition definition,
            TaskSessionRepository taskSessionRepository,
            DataProcessingGraphStateRepository graphStateRepository
    ) {
        this.compiledGraph = compiledGraph;
        this.definition = definition;
        this.taskSessionRepository = taskSessionRepository;
        this.graphStateRepository = graphStateRepository;
    }

    /**
     * 提交任务并启动首次状态图执行。
     */
    public DataProcessingTaskResponse start(DataProcessingTaskRequest request) {
        TaskSession session = taskSessionRepository.save(
                TaskSession.newSession(request.taskId(), request.sourceHeaders(), request.sampleRows())
        );

        RunnableConfig config = RunnableConfig.builder()
                .threadId(session.taskId())
                .build();
        compiledGraph.stream(definition.toInitialState(session), config).blockLast();

        DataProcessingGraphState completedState = graphStateRepository.save(
                definition.fromNativeState(compiledGraph.getState(config).state().data())
        );
        taskSessionRepository.save(completedState.toTaskSession());

        return new DataProcessingTaskResponse(
                completedState.workflowStage(),
                completedState.templateRecognitionResult(),
                completedState.userConfirmationItems()
        );
    }

    /**
     * 接收用户确认结果并恢复状态图执行。
     */
    public UserConfirmationResponse resume(UserConfirmationRequest request) {
        taskSessionRepository.findByTaskId(request.taskId());
        graphStateRepository.findByTaskId(request.taskId());

        RunnableConfig config = RunnableConfig.builder()
                .threadId(request.taskId())
                .build();
        RunnableConfig updatedConfig;
        try {
            updatedConfig = compiledGraph.updateState(config, definition.toResumeUpdate(request), null);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to update native StateGraph state for user confirmation.", ex);
        }

        compiledGraph.stream(null, updatedConfig).blockLast();

        DataProcessingGraphState completedState = graphStateRepository.save(
                definition.fromNativeState(compiledGraph.getState(updatedConfig).state().data())
        );
        taskSessionRepository.save(completedState.toTaskSession());

        return new UserConfirmationResponse(
                completedState.workflowStage(),
                completedState.finalDsl(),
                completedState.transformedPreviewRows()
        );
    }
}
