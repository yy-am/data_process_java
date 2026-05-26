package com.example.dataprocess.application.workflow;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.CompileConfig;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.checkpoint.config.SaverConfig;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.strategy.AppendStrategy;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import com.example.dataprocess.application.state.DataProcessingGraphState;
import com.example.dataprocess.application.workflow.node.ApplyUserConfirmationNode;
import com.example.dataprocess.application.workflow.node.BuildInputSnapshotNode;
import com.example.dataprocess.application.workflow.node.BuildUserConfirmationRequestNode;
import com.example.dataprocess.application.workflow.node.RuleDraftingNode;
import com.example.dataprocess.application.workflow.node.TemplateRecognitionNode;
import com.example.dataprocess.domain.model.FinalDsl;
import com.example.dataprocess.domain.model.InputSnapshot;
import com.example.dataprocess.domain.model.ProcessingRule;
import com.example.dataprocess.domain.model.TaskSession;
import com.example.dataprocess.domain.model.TemplateRecognitionResult;
import com.example.dataprocess.domain.model.UserConfirmationItems;
import com.example.dataprocess.domain.model.UserConfirmationPreparationResult;
import com.example.dataprocess.domain.model.UserConfirmationResult;
import com.example.dataprocess.domain.model.VagueBindingRecoResult;
import com.example.dataprocess.domain.model.WorkflowStage;
import com.example.dataprocess.interfaces.restful.request.UserConfirmationRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncEdgeAction.edge_async;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;

/**
 * 数据加工一期 StateGraph 定义。
 *
 * <p>当前只覆盖 DSL 生成前后的主流程：
 * 解析后的样本数据进入输入快照，完成模板识别，必要时生成结构化确认请求，
 * 等待用户提交固定 JSON 结构的确认结果，最后生成 DSL。</p>
 */
@Component
public class DataProcessingStateGraphDefinition {

    private static final String TASK_ID = "task_id";
    private static final String INPUT_TYPE = "input_type";
    private static final String SOURCE_HEADERS = "source_headers";
    private static final String SAMPLE_ROWS = "sample_rows";
    private static final String INPUT_SNAPSHOT = "input_snapshot";
    private static final String TEMPLATE_RECOGNITION_RESULT = "template_recognition_result";
    private static final String PROCESSING_RULE = "processing_rule";
    private static final String VAGUE_BINDING_RECO_RESULT = "vague_binding_reco_result";
    private static final String USER_CONFIRMATION_ITEMS = "user_confirmation_items";
    private static final String USER_CONFIRMATION_REQUEST = "user_confirmation_request";
    private static final String USER_CONFIRMATION_RESULT = "user_confirmation_result";
    private static final String FINAL_DSL = "final_dsl";
    private static final String WORKFLOW_STAGE = "workflow_stage";
    private static final String CURRENT_NODE = "current_node";
    private static final String RETRY_COUNT = "retry_count";
    private static final String ERROR_MESSAGES = "error_messages";
    private static final String TRACE_LOGS = "trace_logs";
    private static final String NEXT_NODE = "next_node";

    private static final String BUILD_INPUT_SNAPSHOT_NODE = "build_input_snapshot";
    private static final String TEMPLATE_RECOGNITION_NODE = "template_recognition";
    private static final String BUILD_USER_CONFIRMATION_REQUEST_NODE = "build_user_confirmation_request";
    private static final String NEED_USER_CONFIRMATION_ROUTER = "need_user_confirmation_router";
    private static final String WAIT_USER_CONFIRMATION_NODE = "wait_user_confirmation";
    private static final String APPLY_USER_CONFIRMATION_NODE = "apply_user_confirmation";
    private static final String RULE_DRAFTING_NODE = "rule_drafting";
    private static final String COMPLETE_NODE = "complete";

    private final ObjectMapper objectMapper;
    private final BuildInputSnapshotNode buildInputSnapshotNode;
    private final TemplateRecognitionNode templateRecognitionNode;
    private final BuildUserConfirmationRequestNode buildUserConfirmationRequestNode;
    private final ApplyUserConfirmationNode applyUserConfirmationNode;
    private final RuleDraftingNode ruleDraftingNode;

    public DataProcessingStateGraphDefinition(
            ObjectMapper objectMapper,
            BuildInputSnapshotNode buildInputSnapshotNode,
            TemplateRecognitionNode templateRecognitionNode,
            BuildUserConfirmationRequestNode buildUserConfirmationRequestNode,
            ApplyUserConfirmationNode applyUserConfirmationNode,
            RuleDraftingNode ruleDraftingNode
    ) {
        this.objectMapper = objectMapper;
        this.buildInputSnapshotNode = buildInputSnapshotNode;
        this.templateRecognitionNode = templateRecognitionNode;
        this.buildUserConfirmationRequestNode = buildUserConfirmationRequestNode;
        this.applyUserConfirmationNode = applyUserConfirmationNode;
        this.ruleDraftingNode = ruleDraftingNode;
    }

    /**
     * 为图状态字段声明合并策略。
     *
     * <p>大多数字段采用替换策略，错误和轨迹日志采用追加策略。</p>
     */
    public KeyStrategyFactory keyStrategyFactory() {
        return () -> {
            HashMap<String, KeyStrategy> strategies = new HashMap<>();
            strategies.put(TASK_ID, new ReplaceStrategy());
            strategies.put(INPUT_TYPE, new ReplaceStrategy());
            strategies.put(SOURCE_HEADERS, new ReplaceStrategy());
            strategies.put(SAMPLE_ROWS, new ReplaceStrategy());
            strategies.put(INPUT_SNAPSHOT, new ReplaceStrategy());
            strategies.put(TEMPLATE_RECOGNITION_RESULT, new ReplaceStrategy());
            strategies.put(PROCESSING_RULE, new ReplaceStrategy());
            strategies.put(VAGUE_BINDING_RECO_RESULT, new ReplaceStrategy());
            strategies.put(USER_CONFIRMATION_ITEMS, new ReplaceStrategy());
            strategies.put(USER_CONFIRMATION_REQUEST, new ReplaceStrategy());
            strategies.put(USER_CONFIRMATION_RESULT, new ReplaceStrategy());
            strategies.put(FINAL_DSL, new ReplaceStrategy());
            strategies.put(WORKFLOW_STAGE, new ReplaceStrategy());
            strategies.put(CURRENT_NODE, new ReplaceStrategy());
            strategies.put(RETRY_COUNT, new ReplaceStrategy());
            strategies.put(ERROR_MESSAGES, new AppendStrategy());
            strategies.put(TRACE_LOGS, new AppendStrategy());
            strategies.put(NEXT_NODE, new ReplaceStrategy());
            return strategies;
        };
    }

    /**
     * 构建并编译一期数据加工状态图。
     */
    public CompiledGraph build() throws GraphStateException {
        StateGraph workflow = new StateGraph(keyStrategyFactory())
                .addNode(BUILD_INPUT_SNAPSHOT_NODE, node_async(state -> {
                    DataProcessingGraphState current = fromNativeState(state.data());
                    InputSnapshot inputSnapshot = buildInputSnapshotNode.execute(current);
                    return Map.of(
                            INPUT_SNAPSHOT, inputSnapshot,
                            WORKFLOW_STAGE, WorkflowStage.INPUT_SNAPSHOT_BUILT.name(),
                            CURRENT_NODE, BUILD_INPUT_SNAPSHOT_NODE,
                            TRACE_LOGS, List.of("Built input snapshot.")
                    );
                }))
                .addNode(TEMPLATE_RECOGNITION_NODE, node_async(state -> {
                    DataProcessingGraphState current = fromNativeState(state.data());
                    TemplateRecognitionResult result = templateRecognitionNode.execute(current);
                    return Map.of(
                            TEMPLATE_RECOGNITION_RESULT, result,
                            WORKFLOW_STAGE, WorkflowStage.TEMPLATE_RECOGNIZED.name(),
                            CURRENT_NODE, TEMPLATE_RECOGNITION_NODE,
                            NEXT_NODE, BUILD_USER_CONFIRMATION_REQUEST_NODE,
                            TRACE_LOGS, List.of("Finished template recognition.")
                    );
                }))
                .addNode(BUILD_USER_CONFIRMATION_REQUEST_NODE, node_async(state -> {
                    DataProcessingGraphState current = fromNativeState(state.data());
                    UserConfirmationPreparationResult result = buildUserConfirmationRequestNode.execute(current);
                    UserConfirmationItems items = result.userConfirmationItems();
                    return Map.of(
                            PROCESSING_RULE, result.processingRule(),
                            USER_CONFIRMATION_ITEMS, items,
                            VAGUE_BINDING_RECO_RESULT, result.vagueBindingRecoResult(),
                            WORKFLOW_STAGE, hasUserConfirmationItems(items)
                                    ? WorkflowStage.USER_CONFIRMATION_REQUIRED.name()
                                    : WorkflowStage.TEMPLATE_RECOGNIZED.name(),
                            CURRENT_NODE, BUILD_USER_CONFIRMATION_REQUEST_NODE,
                            NEXT_NODE, NEED_USER_CONFIRMATION_ROUTER,
                            TRACE_LOGS, List.of("Built structured user confirmation request.")
                    );
                }))
                .addNode(NEED_USER_CONFIRMATION_ROUTER, node_async(state -> {
                    DataProcessingGraphState current = fromNativeState(state.data());
                    String nextNode = resolveNextNodeAfterConfirmationItems(current);
                    return Map.of(
                            CURRENT_NODE, NEED_USER_CONFIRMATION_ROUTER,
                            NEXT_NODE, nextNode,
                            TRACE_LOGS, List.of("Resolved user confirmation routing.")
                    );
                }))
                .addNode(WAIT_USER_CONFIRMATION_NODE, node_async(state -> Map.of(
                        CURRENT_NODE, WAIT_USER_CONFIRMATION_NODE,
                        WORKFLOW_STAGE, WorkflowStage.USER_CONFIRMATION_REQUIRED.name(),
                        NEXT_NODE, APPLY_USER_CONFIRMATION_NODE,
                        TRACE_LOGS, List.of("Waiting for structured user confirmation.")
                )))
                .addNode(APPLY_USER_CONFIRMATION_NODE, node_async(state -> {
                    DataProcessingGraphState current = fromNativeState(state.data());
                    UserConfirmationResult result = applyUserConfirmationNode.execute(current);
                    return Map.of(
                            USER_CONFIRMATION_RESULT, result,
                            WORKFLOW_STAGE, WorkflowStage.USER_CONFIRMED.name(),
                            CURRENT_NODE, APPLY_USER_CONFIRMATION_NODE,
                            NEXT_NODE, RULE_DRAFTING_NODE,
                            TRACE_LOGS, List.of("Applied structured user confirmation.")
                    );
                }))
                .addNode(RULE_DRAFTING_NODE, node_async(state -> {
                    DataProcessingGraphState current = fromNativeState(state.data());
                    FinalDsl finalDsl = ruleDraftingNode.execute(current);
                    return Map.of(
                            FINAL_DSL, finalDsl,
                            WORKFLOW_STAGE, WorkflowStage.DSL_DRAFTED.name(),
                            CURRENT_NODE, RULE_DRAFTING_NODE,
                            NEXT_NODE, COMPLETE_NODE,
                            TRACE_LOGS, List.of("Drafted final DSL.")
                    );
                }))
                .addNode(COMPLETE_NODE, node_async(state -> Map.of(
                        WORKFLOW_STAGE, WorkflowStage.COMPLETED.name(),
                        CURRENT_NODE, COMPLETE_NODE,
                        TRACE_LOGS, List.of("Workflow completed.")
                )));

        workflow.addEdge(START, BUILD_INPUT_SNAPSHOT_NODE);
        workflow.addEdge(BUILD_INPUT_SNAPSHOT_NODE, TEMPLATE_RECOGNITION_NODE);
        workflow.addEdge(TEMPLATE_RECOGNITION_NODE, BUILD_USER_CONFIRMATION_REQUEST_NODE);
        workflow.addEdge(BUILD_USER_CONFIRMATION_REQUEST_NODE, NEED_USER_CONFIRMATION_ROUTER);
        workflow.addConditionalEdges(
                NEED_USER_CONFIRMATION_ROUTER,
                edge_async(state -> (String) state.value(NEXT_NODE).orElse(RULE_DRAFTING_NODE)),
                Map.of(
                        WAIT_USER_CONFIRMATION_NODE, WAIT_USER_CONFIRMATION_NODE,
                        RULE_DRAFTING_NODE, RULE_DRAFTING_NODE
                )
        );
        workflow.addEdge(WAIT_USER_CONFIRMATION_NODE, APPLY_USER_CONFIRMATION_NODE);
        workflow.addEdge(APPLY_USER_CONFIRMATION_NODE, RULE_DRAFTING_NODE);
        workflow.addEdge(RULE_DRAFTING_NODE, COMPLETE_NODE);
        workflow.addEdge(COMPLETE_NODE, END);

        return workflow.compile(CompileConfig.builder()
                .saverConfig(SaverConfig.builder().register(new MemorySaver()).build())
                .interruptBefore(WAIT_USER_CONFIRMATION_NODE)
                .build());
    }

    /**
     * 生成首次启动时的初始图状态。
     */
    public Map<String, Object> toInitialState(TaskSession session) {
        return Map.of(
                TASK_ID, session.taskId(),
                INPUT_TYPE, session.inputType(),
                SOURCE_HEADERS, session.sourceHeaders(),
                SAMPLE_ROWS, session.sampleRows(),
                WORKFLOW_STAGE, WorkflowStage.RECEIVED.name(),
                CURRENT_NODE, "START",
                RETRY_COUNT, 0,
                ERROR_MESSAGES, new ArrayList<String>(),
                TRACE_LOGS, new ArrayList<String>()
        );
    }

    /**
     * 生成用户确认后恢复执行时的状态更新片段。
     */
    public Map<String, Object> toResumeUpdate(UserConfirmationRequest request) {
        return Map.of(
                USER_CONFIRMATION_REQUEST, request,
                WORKFLOW_STAGE, WorkflowStage.USER_CONFIRMATION_REQUIRED.name(),
                CURRENT_NODE, WAIT_USER_CONFIRMATION_NODE
        );
    }

    /**
     * 将原生图状态映射为业务侧状态对象。
     */
    public DataProcessingGraphState fromNativeState(Map<String, Object> state) {
        return new DataProcessingGraphState(
                asString(state.get(TASK_ID)),
                asString(state.getOrDefault(INPUT_TYPE, "excel-import")),
                convertList(state.get(SOURCE_HEADERS), new TypeReference<>() {
                }),
                convertList(state.get(SAMPLE_ROWS), new TypeReference<>() {
                }),
                convertNullable(state.get(INPUT_SNAPSHOT), InputSnapshot.class),
                convertNullable(state.get(TEMPLATE_RECOGNITION_RESULT), TemplateRecognitionResult.class),
                convertNullable(state.get(PROCESSING_RULE), ProcessingRule.class),
                convertNullable(state.get(VAGUE_BINDING_RECO_RESULT), VagueBindingRecoResult.class),
                convertNullable(state.get(USER_CONFIRMATION_ITEMS), UserConfirmationItems.class),
                convertNullable(state.get(USER_CONFIRMATION_REQUEST), UserConfirmationRequest.class),
                convertNullable(state.get(USER_CONFIRMATION_RESULT), UserConfirmationResult.class),
                convertNullable(state.get(FINAL_DSL), FinalDsl.class),
                WorkflowStage.valueOf(asString(state.getOrDefault(WORKFLOW_STAGE, WorkflowStage.RECEIVED.name()))),
                asString(state.getOrDefault(CURRENT_NODE, "START")),
                ((Number) state.getOrDefault(RETRY_COUNT, 0)).intValue(),
                convertList(state.get(ERROR_MESSAGES), new TypeReference<>() {
                }),
                convertList(state.get(TRACE_LOGS), new TypeReference<>() {
                })
        );
    }

    /**
     * 根据已生成的确认项判断是否进入结构化用户确认分支。
     */
    private String resolveNextNodeAfterConfirmationItems(DataProcessingGraphState state) {
        if (hasUserConfirmationItems(state.userConfirmationItems())) {
            return WAIT_USER_CONFIRMATION_NODE;
        }
        return RULE_DRAFTING_NODE;
    }

    /**
     * 判断当前确认项集合中是否至少包含一项待确认内容。
     */
    private boolean hasUserConfirmationItems(UserConfirmationItems items) {
        return items != null
                && (!items.mappingConfirmations().isEmpty()
                || !items.optionConfirmations().isEmpty()
                || !items.inputConfirmations().isEmpty());
    }

    /**
     * 将任意值转换为字符串。
     */
    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 将可空值映射为目标类型。
     */
    private <T> T convertNullable(Object value, Class<T> type) {
        return value == null ? null : objectMapper.convertValue(value, type);
    }

    /**
     * 将列表值映射为目标列表类型。
     */
    private <T> List<T> convertList(Object value, TypeReference<List<T>> typeReference) {
        if (value == null) {
            return List.of();
        }
        return objectMapper.convertValue(value, typeReference);
    }
}
