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
import com.example.dataprocess.application.workflow.node.BuildInputSnapshotNode;
import com.example.dataprocess.application.workflow.node.ConfirmationQuestionSkillNode;
import com.example.dataprocess.application.workflow.node.DslTransformationNode;
import com.example.dataprocess.application.workflow.node.DslValidationNode;
import com.example.dataprocess.application.workflow.node.RuleDraftingSkillNode;
import com.example.dataprocess.application.workflow.node.TemplateRecognitionSkillNode;
import com.example.dataprocess.domain.model.FinalDsl;
import com.example.dataprocess.domain.model.InputSnapshot;
import com.example.dataprocess.domain.model.TaskSession;
import com.example.dataprocess.domain.model.TemplateRecognitionResult;
import com.example.dataprocess.domain.model.UserConfirmationItems;
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
 * 数据加工 StateGraph 定义类，负责装配节点、边和中断恢复规则。
 */
@Component
public class DataProcessingStateGraphDefinition {

    private static final String TASK_ID = "task_id";
    private static final String INPUT_TYPE = "input_type";
    private static final String SOURCE_HEADERS = "source_headers";
    private static final String SAMPLE_ROWS = "sample_rows";
    private static final String INPUT_SNAPSHOT = "input_snapshot";
    private static final String TEMPLATE_RECOGNITION_RESULT = "template_recognition_result";
    private static final String USER_CONFIRMATION_ITEMS = "user_confirmation_items";
    private static final String USER_CONFIRMATION_REQUEST = "user_confirmation_request";
    private static final String FINAL_DSL = "final_dsl";
    private static final String TRANSFORMED_PREVIEW_ROWS = "transformed_preview_rows";
    private static final String WORKFLOW_STAGE = "workflow_stage";
    private static final String CURRENT_NODE = "current_node";
    private static final String RETRY_COUNT = "retry_count";
    private static final String ERROR_MESSAGES = "error_messages";
    private static final String TRACE_LOGS = "trace_logs";
    private static final String NEXT_NODE = "next_node";

    private static final String BUILD_INPUT_SNAPSHOT_NODE = "build_input_snapshot";
    private static final String TEMPLATE_RECOGNITION_NODE = "template_recognition";
    private static final String NEED_USER_CONFIRMATION_ROUTER = "need_user_confirmation_router";
    private static final String CONFIRMATION_QUESTION_NODE = "confirmation_question";
    private static final String WAIT_USER_CONFIRMATION_NODE = "wait_user_confirmation";
    private static final String RULE_DRAFTING_NODE = "rule_drafting";
    private static final String DSL_VALIDATION_NODE = "dsl_validation";
    private static final String DSL_TRANSFORMATION_NODE = "dsl_transformation";
    private static final String COMPLETE_NODE = "complete";

    private final ObjectMapper objectMapper;
    private final BuildInputSnapshotNode buildInputSnapshotNode;
    private final TemplateRecognitionSkillNode templateRecognitionSkillNode;
    private final ConfirmationQuestionSkillNode confirmationQuestionSkillNode;
    private final RuleDraftingSkillNode ruleDraftingSkillNode;
    private final DslValidationNode dslValidationNode;
    private final DslTransformationNode dslTransformationNode;

    public DataProcessingStateGraphDefinition(
            ObjectMapper objectMapper,
            BuildInputSnapshotNode buildInputSnapshotNode,
            TemplateRecognitionSkillNode templateRecognitionSkillNode,
            ConfirmationQuestionSkillNode confirmationQuestionSkillNode,
            RuleDraftingSkillNode ruleDraftingSkillNode,
            DslValidationNode dslValidationNode,
            DslTransformationNode dslTransformationNode
    ) {
        this.objectMapper = objectMapper;
        this.buildInputSnapshotNode = buildInputSnapshotNode;
        this.templateRecognitionSkillNode = templateRecognitionSkillNode;
        this.confirmationQuestionSkillNode = confirmationQuestionSkillNode;
        this.ruleDraftingSkillNode = ruleDraftingSkillNode;
        this.dslValidationNode = dslValidationNode;
        this.dslTransformationNode = dslTransformationNode;
    }

    /**
     * 为 StateGraph 的每个状态字段声明合并策略。
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
            strategies.put(USER_CONFIRMATION_ITEMS, new ReplaceStrategy());
            strategies.put(USER_CONFIRMATION_REQUEST, new ReplaceStrategy());
            strategies.put(FINAL_DSL, new ReplaceStrategy());
            strategies.put(TRANSFORMED_PREVIEW_ROWS, new ReplaceStrategy());
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
     * 构建并编译整张数据加工状态图。
     */
    public CompiledGraph build() throws GraphStateException {
        StateGraph workflow = new StateGraph(keyStrategyFactory())
                .addNode(BUILD_INPUT_SNAPSHOT_NODE, node_async(state -> {
                    /* 首节点先把原始输入归一化，给后续模板识别和工具调用提供统一视图。 */
                    DataProcessingGraphState current = fromNativeState(state.data());
                    DataProcessingGraphState next = current.withInputSnapshot(buildInputSnapshotNode.execute(current));
                    return Map.of(
                            INPUT_SNAPSHOT, next.inputSnapshot(),
                            WORKFLOW_STAGE, next.workflowStage().name(),
                            CURRENT_NODE, BUILD_INPUT_SNAPSHOT_NODE,
                            TRACE_LOGS, List.of("Built input snapshot.")
                    );
                }))
                .addNode(TEMPLATE_RECOGNITION_NODE, node_async(state -> {
                    /* 模板识别节点负责判定模板，并写入是否需要人工确认的信号。 */
                    DataProcessingGraphState current = fromNativeState(state.data());
                    TemplateRecognitionResult result = templateRecognitionSkillNode.execute(current);
                    return Map.of(
                            TEMPLATE_RECOGNITION_RESULT, result,
                            WORKFLOW_STAGE, Boolean.TRUE.equals(result.needUserConfirm())
                                    ? WorkflowStage.USER_CONFIRMATION_REQUIRED.name()
                                    : WorkflowStage.TEMPLATE_RECOGNIZED.name(),
                            CURRENT_NODE, TEMPLATE_RECOGNITION_NODE,
                            NEXT_NODE, NEED_USER_CONFIRMATION_ROUTER,
                            TRACE_LOGS, List.of("Template recognition finished.")
                    );
                }))
                .addNode(NEED_USER_CONFIRMATION_ROUTER, node_async(state -> {
                    /* 单独的路由节点只负责决定下一跳，避免业务节点承担分支控制。 */
                    DataProcessingGraphState current = fromNativeState(state.data());
                    String nextNode = resolveNextNodeAfterRecognition(current);
                    return Map.of(
                            CURRENT_NODE, NEED_USER_CONFIRMATION_ROUTER,
                            NEXT_NODE, nextNode,
                            TRACE_LOGS, List.of("Evaluated need-user-confirmation routing.")
                    );
                }))
                .addNode(CONFIRMATION_QUESTION_NODE, node_async(state -> {
                    /* 识别有歧义时，生成一份前端可直接渲染的确认问题集合。 */
                    DataProcessingGraphState current = fromNativeState(state.data());
                    UserConfirmationItems items = confirmationQuestionSkillNode.execute(current);
                    return Map.of(
                            USER_CONFIRMATION_ITEMS, items,
                            WORKFLOW_STAGE, WorkflowStage.USER_CONFIRMATION_REQUIRED.name(),
                            CURRENT_NODE, CONFIRMATION_QUESTION_NODE,
                            NEXT_NODE, WAIT_USER_CONFIRMATION_NODE,
                            TRACE_LOGS, List.of("Generated user confirmation package.")
                    );
                }))
                .addNode(WAIT_USER_CONFIRMATION_NODE, node_async(state -> Map.of(
                        /* 该节点只负责形成稳定中断点，真正的用户输入由 resume 接口补回状态。 */
                        CURRENT_NODE, WAIT_USER_CONFIRMATION_NODE,
                        WORKFLOW_STAGE, WorkflowStage.USER_CONFIRMATION_REQUIRED.name(),
                        NEXT_NODE, RULE_DRAFTING_NODE,
                        TRACE_LOGS, List.of("Waiting for user confirmation.")
                )))
                .addNode(RULE_DRAFTING_NODE, node_async(state -> {
                    /* 规则起草节点结合模板、输入快照和用户确认结果输出 DSL 草案。 */
                    DataProcessingGraphState current = fromNativeState(state.data());
                    FinalDsl finalDsl = ruleDraftingSkillNode.execute(current);
                    return Map.of(
                            FINAL_DSL, finalDsl,
                            WORKFLOW_STAGE, WorkflowStage.DSL_DRAFTED.name(),
                            CURRENT_NODE, RULE_DRAFTING_NODE,
                            NEXT_NODE, DSL_VALIDATION_NODE,
                            TRACE_LOGS, List.of("Drafted final DSL.")
                    );
                }))
                .addNode(DSL_VALIDATION_NODE, node_async(state -> {
                    /* 校验失败则回退到起草节点重试，成功后再进入转换预览节点。 */
                    DataProcessingGraphState current = fromNativeState(state.data());
                    boolean valid = dslValidationNode.isValid(current);
                    String nextNode = valid ? DSL_TRANSFORMATION_NODE : RULE_DRAFTING_NODE;
                    return Map.of(
                            WORKFLOW_STAGE, valid ? WorkflowStage.DSL_VALIDATED.name() : WorkflowStage.DSL_DRAFTED.name(),
                            CURRENT_NODE, DSL_VALIDATION_NODE,
                            NEXT_NODE, nextNode,
                            TRACE_LOGS, List.of(valid ? "Validated DSL." : "DSL validation requires retry.")
                    );
                }))
                .addNode(DSL_TRANSFORMATION_NODE, node_async(state -> {
                    /* DSL 通过校验后先做预览转换，避免直接输出不可验证的最终结果。 */
                    DataProcessingGraphState current = fromNativeState(state.data());
                    var previewRows = dslTransformationNode.execute(current);
                    return Map.of(
                            TRANSFORMED_PREVIEW_ROWS, previewRows,
                            WORKFLOW_STAGE, WorkflowStage.TRANSFORMED.name(),
                            CURRENT_NODE, DSL_TRANSFORMATION_NODE,
                            NEXT_NODE, COMPLETE_NODE,
                            TRACE_LOGS, List.of("Built transformed preview rows.")
                    );
                }))
                .addNode(COMPLETE_NODE, node_async(state -> Map.of(
                        /* 结束节点只负责收口状态，最终响应由 workflow 门面层统一组装。 */
                        WORKFLOW_STAGE, WorkflowStage.COMPLETED.name(),
                        CURRENT_NODE, COMPLETE_NODE,
                        TRACE_LOGS, List.of("Workflow completed.")
                )));

        workflow.addEdge(START, BUILD_INPUT_SNAPSHOT_NODE);
        workflow.addEdge(BUILD_INPUT_SNAPSHOT_NODE, TEMPLATE_RECOGNITION_NODE);
        workflow.addEdge(TEMPLATE_RECOGNITION_NODE, NEED_USER_CONFIRMATION_ROUTER);
        workflow.addConditionalEdges(NEED_USER_CONFIRMATION_ROUTER,
                edge_async(state -> (String) state.value(NEXT_NODE).orElse(RULE_DRAFTING_NODE)),
                Map.of(
                        CONFIRMATION_QUESTION_NODE, CONFIRMATION_QUESTION_NODE,
                        RULE_DRAFTING_NODE, RULE_DRAFTING_NODE
                ));
        workflow.addEdge(CONFIRMATION_QUESTION_NODE, WAIT_USER_CONFIRMATION_NODE);
        workflow.addEdge(WAIT_USER_CONFIRMATION_NODE, RULE_DRAFTING_NODE);
        workflow.addEdge(RULE_DRAFTING_NODE, DSL_VALIDATION_NODE);
        workflow.addConditionalEdges(DSL_VALIDATION_NODE,
                edge_async(state -> (String) state.value(NEXT_NODE).orElse(RULE_DRAFTING_NODE)),
                Map.of(
                        DSL_TRANSFORMATION_NODE, DSL_TRANSFORMATION_NODE,
                        RULE_DRAFTING_NODE, RULE_DRAFTING_NODE
                ));
        workflow.addEdge(DSL_TRANSFORMATION_NODE, COMPLETE_NODE);
        workflow.addEdge(COMPLETE_NODE, END);

        return workflow.compile(CompileConfig.builder()
                .saverConfig(SaverConfig.builder().register(new MemorySaver()).build())
                /* 在等待用户确认前中断，让前端先拿到题包，再通过 confirm 接口恢复执行。 */
                .interruptBefore(WAIT_USER_CONFIRMATION_NODE)
                .build());
    }

    /**
     * 生成首次启动状态图时的初始状态。
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
     * 生成用户确认后恢复执行的状态更新片段。
     */
    public Map<String, Object> toResumeUpdate(UserConfirmationRequest request) {
        return Map.of(
                USER_CONFIRMATION_REQUEST, request,
                WORKFLOW_STAGE, WorkflowStage.USER_CONFIRMED.name(),
                CURRENT_NODE, WAIT_USER_CONFIRMATION_NODE
        );
    }

    /**
     * 将原生图状态映射为业务侧图状态对象。
     */
    public DataProcessingGraphState fromNativeState(Map<String, Object> state) {
        return new DataProcessingGraphState(
                asString(state.get(TASK_ID)),
                asString(state.getOrDefault(INPUT_TYPE, "table-import")),
                convertList(state.get(SOURCE_HEADERS), new TypeReference<>() {
                }),
                convertList(state.get(SAMPLE_ROWS), new TypeReference<>() {
                }),
                convertNullable(state.get(INPUT_SNAPSHOT), InputSnapshot.class),
                convertNullable(state.get(TEMPLATE_RECOGNITION_RESULT), TemplateRecognitionResult.class),
                convertNullable(state.get(USER_CONFIRMATION_ITEMS), UserConfirmationItems.class),
                convertNullable(state.get(USER_CONFIRMATION_REQUEST), UserConfirmationRequest.class),
                convertNullable(state.get(FINAL_DSL), FinalDsl.class),
                convertList(state.get(TRANSFORMED_PREVIEW_ROWS), new TypeReference<>() {
                }),
                WorkflowStage.valueOf(asString(state.getOrDefault(WORKFLOW_STAGE, WorkflowStage.RECEIVED.name()))),
                asString(state.getOrDefault(CURRENT_NODE, "START")),
                ((Number) state.getOrDefault(RETRY_COUNT, 0)).intValue(),
                convertList(state.get(ERROR_MESSAGES), new TypeReference<>() {
                }),
                convertList(state.get(TRACE_LOGS), new TypeReference<>() {
                })
        );
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private <T> T convertNullable(Object value, Class<T> type) {
        return value == null ? null : objectMapper.convertValue(value, type);
    }

    private <T> List<T> convertList(Object value, TypeReference<List<T>> typeReference) {
        if (value == null) {
            return List.of();
        }
        return objectMapper.convertValue(value, typeReference);
    }

    private String resolveNextNodeAfterRecognition(DataProcessingGraphState state) {
        if (state.templateRecognitionResult() != null
                && Boolean.TRUE.equals(state.templateRecognitionResult().needUserConfirm())) {
            return CONFIRMATION_QUESTION_NODE;
        }
        return RULE_DRAFTING_NODE;
    }
}
