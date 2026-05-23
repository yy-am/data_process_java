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
 * 数据加工 StateGraph 工作流门面。
 *
 * <p>当前一期只负责驱动“样本解析后进入模板识别、结构化确认、DSL 生成”这条链路，
 * 不负责重新获取全量 Excel，也不负责数据加工执行和导出。</p>
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
        // 一期任务会话只保留表头和少量样本数据，不持有全量 Excel 内容。
        TaskSession session = taskSessionRepository.save(
                TaskSession.newSession(
                        request.taskId(),
                        request.inputType(),
                        request.sourceHeaders(),
                        request.sampleRows()
                )
        );

        // taskId 直接作为 threadId，确保 start 和 resume 命中同一条流程实例。
        RunnableConfig config = RunnableConfig.builder()
                .threadId(session.taskId())
                .build();

        // 首次执行从初始状态启动，直到命中等待确认节点或自然完成。
        compiledGraph.stream(definition.toInitialState(session), config).blockLast();

        // 将最新图状态回写到图状态仓储和任务会话仓储，保证后续 resume 可继续使用。
        DataProcessingGraphState completedState = graphStateRepository.save(
                definition.fromNativeState(compiledGraph.getState(config).state().data())
        );
        taskSessionRepository.save(completedState.toTaskSession());

        return new DataProcessingTaskResponse(
                completedState.workflowStage(),
                completedState.templateRecognitionResult(),
                completedState.userConfirmationItems(),
                completedState.finalDsl()
        );
    }

    /**
     * 接收用户确认结果并恢复 StateGraph 执行。
     */
    public UserConfirmationResponse resume(UserConfirmationRequest request) {
        // 显式校验任务和图状态都存在，避免对不存在的流程做更新。
        taskSessionRepository.findByTaskId(request.taskId());
        graphStateRepository.findByTaskId(request.taskId());

        RunnableConfig config = RunnableConfig.builder()
                .threadId(request.taskId())
                .build();

        RunnableConfig updatedConfig;
        try {
            // 把用户提交的结构化确认结果写入 checkpoint，然后从等待确认节点继续推进。
            updatedConfig = compiledGraph.updateState(config, definition.toResumeUpdate(request), null);
        } catch (Exception ex) {
            throw new IllegalStateException("写入用户确认结果并恢复 StateGraph 失败。", ex);
        }

        // resume 场景不再注入初始输入，直接基于更新后的线程状态继续执行。
        compiledGraph.stream(null, updatedConfig).blockLast();

        // 当前阶段只回传流程状态和 DSL，不再包含执行结果或导出结果。
        DataProcessingGraphState completedState = graphStateRepository.save(
                definition.fromNativeState(compiledGraph.getState(updatedConfig).state().data())
        );
        taskSessionRepository.save(completedState.toTaskSession());

        return new UserConfirmationResponse(
                completedState.workflowStage(),
                completedState.finalDsl()
        );
    }
}
