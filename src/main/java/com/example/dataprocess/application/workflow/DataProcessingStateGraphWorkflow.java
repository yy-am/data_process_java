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
 * 数据加工 StateGraph 工作流门面，负责启动和恢复同一条流程。
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
     * 提交任务并启动首次 StateGraph 执行。
     */
    public DataProcessingTaskResponse start(DataProcessingTaskRequest request) {
        /* 先把文件解析后的标准化输入沉淀成会话，后续节点统一从会话读取上下文。 */
        TaskSession session = taskSessionRepository.save(
                TaskSession.newSession(request.taskId(), request.inputType(), request.sourceHeaders(), request.sampleRows())
        );

        /* taskId 直接作为 threadId，确保 start 和 resume 都命中同一条图执行链。 */
        RunnableConfig config = RunnableConfig.builder()
                .threadId(session.taskId())
                .build();

        /* 首次执行从初始状态启动，直到命中中断点或自然完成。 */
        compiledGraph.stream(definition.toInitialState(session), config).blockLast();

        /* 把原生状态映射回业务状态，再持久化给接口层和后续排障使用。 */
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
     * 接收用户确认结果并恢复 StateGraph 执行。
     */
    public UserConfirmationResponse resume(UserConfirmationRequest request) {
        /* 先校验任务和图状态都存在，避免对不存在的线程做 update。 */
        taskSessionRepository.findByTaskId(request.taskId());
        graphStateRepository.findByTaskId(request.taskId());

        RunnableConfig config = RunnableConfig.builder()
                .threadId(request.taskId())
                .build();

        RunnableConfig updatedConfig;
        try {
            /* 将用户补充信息写入 checkpoint，然后从等待节点继续向后推进。 */
            updatedConfig = compiledGraph.updateState(config, definition.toResumeUpdate(request), null);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to update native StateGraph state for user confirmation.", ex);
        }

        /* resume 场景不再注入初始输入，直接基于更新后的线程状态续跑。 */
        compiledGraph.stream(null, updatedConfig).blockLast();

        /* 再次映射执行后的图状态，返回最终 DSL 和预览结果。 */
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
