package com.example.dataprocess.agent.tool;

import com.example.dataprocess.agent.model.AgentConfirmationDecision;
import com.example.dataprocess.agent.model.AgentConfirmationItem;
import com.example.dataprocess.agent.model.AgentSqlGenerationContext;
import com.example.dataprocess.agent.model.AgentUserConfirmationRequest;
import com.example.dataprocess.agent.model.AgentWorkflowStage;
import com.example.dataprocess.agent.model.DataProcessingAgentResponse;
import com.example.dataprocess.agent.model.DataProcessingAgentState;
import com.example.dataprocess.agent.model.FieldBindingPlan;
import com.example.dataprocess.agent.model.ParsedExcelSummary;
import com.example.dataprocess.agent.model.RenderedProcessingSql;
import com.example.dataprocess.agent.model.StandardRequiredFields;
import com.example.dataprocess.agent.model.TemplateBundle;
import com.example.dataprocess.agent.model.ValueSetMetadata;
import com.example.dataprocess.domain.model.ProcessingPlanDsl;
import com.example.dataprocess.domain.model.ProcessingRule;
import com.example.dataprocess.domain.model.TemplateRecognitionResult;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Spring AI method tools exposed to ReactAgent.
 *
 * <p>Only the methods annotated with {@link Tool} are visible to the model. The older fine-grained methods
 * are intentionally kept as normal public methods for deterministic Java reuse, but they are no longer exposed
 * as model-callable tools.</p>
 */
@Component
public class DataProcessingAgentToolMethods {

    private final AgentStateTool stateTool;
    private final ParsedExcelFileTool parsedExcelFileTool;
    private final TemplateRuleTool templateRuleTool;
    private final RequiredFieldTool requiredFieldTool;
    private final ValueSetTool valueSetTool;
    private final FieldBindingValidationTool fieldBindingValidationTool;
    private final ConfirmationTool confirmationTool;
    private final ProcessingPlanSqlTool processingPlanSqlTool;

    public DataProcessingAgentToolMethods(
            AgentStateTool stateTool,
            ParsedExcelFileTool parsedExcelFileTool,
            TemplateRuleTool templateRuleTool,
            RequiredFieldTool requiredFieldTool,
            ValueSetTool valueSetTool,
            FieldBindingValidationTool fieldBindingValidationTool,
            ConfirmationTool confirmationTool,
            ProcessingPlanSqlTool processingPlanSqlTool
    ) {
        this.stateTool = stateTool;
        this.parsedExcelFileTool = parsedExcelFileTool;
        this.templateRuleTool = templateRuleTool;
        this.requiredFieldTool = requiredFieldTool;
        this.valueSetTool = valueSetTool;
        this.fieldBindingValidationTool = fieldBindingValidationTool;
        this.confirmationTool = confirmationTool;
        this.processingPlanSqlTool = processingPlanSqlTool;
    }

    /**
     * 新增暴露给模型的合并工具：加载或初始化任务，读取并保存解析后的 Excel 摘要，
     * 同时返回当前阶段和下一步动作提示。
     */
    @Tool(name = "prepare_task_context", description = "准备数据加工任务上下文：加载或初始化任务状态，读取并保存解析后的 Excel 摘要，返回当前阶段和下一步动作。")
    public Map<String, Object> prepareTaskContext(
            @ToolParam(description = "任务编号") String taskId,
            @ToolParam(description = "已解析 Excel 文件引用") String parsedFileRef
    ) {
        DataProcessingAgentState state = stateTool.loadTaskState(taskId)
                .orElseGet(() -> stateTool.saveTaskState(
                        DataProcessingAgentState.initial(taskId, parsedFileRef)
                                .addTrace("初始化 Agent 任务状态。")
                ));

        if (state.parsedExcelSummary() == null) {
            ParsedExcelSummary summary = parsedExcelFileTool.readParsedExcelSummary(state.parsedFileRef());
            state = stateTool.saveTaskState(state.withParsedExcelSummary(summary)
                    .withStage(AgentWorkflowStage.TASK_CONTEXT_READY)
                    .addTrace("读取并保存解析文件摘要。"));
        } else if (state.stage() == AgentWorkflowStage.RECEIVED) {
            state = stateTool.saveTaskState(state
                    .withStage(AgentWorkflowStage.TASK_CONTEXT_READY)
                    .addTrace("解析文件摘要已存在，任务上下文已就绪。"));
        }

        Map<String, Object> result = baseContext(state);
        result.put("nextAction", nextAction(state));
        result.put("agentResponse", toResponse(state));
        return result;
    }

    /**
     * 新增暴露给模型的工具：加载模板目录。该工具必须独立保留，因为模型需要先读取目录再判断模板。
     */
    @Tool(name = "load_template_catalog", description = "加载模板目录 Markdown 原文，供 Agent 识别最匹配的预置模板。")
    public String loadTemplateCatalog() {
        return templateRuleTool.loadTemplateCatalog();
    }

    /**
     * 新增暴露给模型的合并工具：接收模型识别出的模板结果，完成校验、保存、模板上下文加载、
     * 必填字段加载和值集元数据加载，并返回字段绑定所需上下文。
     */
    @Tool(name = "accept_template_recognition", description = "接收并校验模板识别结果，保存状态，加载模板、加工规则、必填字段和值集元数据。")
    public Map<String, Object> acceptTemplateRecognition(
            @ToolParam(description = "任务编号") String taskId,
            @ToolParam(description = "Agent 推断出的模板识别结果") TemplateRecognitionResult result
    ) {
        TemplateRecognitionResult validatedResult = templateRuleTool.validateTemplateRecognition(result);
        TemplateBundle templateBundle = templateRuleTool.loadTemplateBundle(validatedResult.presetTemplateCode());
        StandardRequiredFields requiredFields = requiredFieldTool.loadRequiredFields(validatedResult.standardTemplateCode());
        List<ValueSetMetadata> valueSetMetadata = valueSetTool.loadValueSetMetadata(templateBundle.processingRule());

        DataProcessingAgentState state = requiredState(taskId)
                .withTemplateRecognitionResult(validatedResult)
                .withStage(AgentWorkflowStage.TEMPLATE_CONTEXT_READY)
                .withTemplateContext(templateBundle, requiredFields, valueSetMetadata)
                .addTrace("完成模板识别校验，并加载模板上下文。");
        state = stateTool.saveTaskState(state);

        Map<String, Object> resultContext = baseContext(state);
        resultContext.put("templateBundle", templateBundle);
        resultContext.put("requiredFields", requiredFields);
        resultContext.put("valueSetMetadata", valueSetMetadata);
        resultContext.put("nextAction", "请基于 Excel 摘要、加工规则、必填字段和值集元数据生成 FieldBindingPlan，然后调用 accept_field_binding_plan。");
        return resultContext;
    }

    /**
     * 新增暴露给模型的合并工具：接收字段绑定计划，完成校验、保存、确认项生成、确认项保存和阶段推进。
     * 如果存在确认项，直接返回 USER_CONFIRMATION_REQUIRED 响应；否则返回 USER_CONFIRMED 响应。
     */
    @Tool(name = "accept_field_binding_plan", description = "接收并校验字段绑定计划，保存计划，生成并保存用户确认项，返回可直接给前端使用的 Agent 响应。")
    public DataProcessingAgentResponse acceptFieldBindingPlan(
            @ToolParam(description = "任务编号") String taskId,
            @ToolParam(description = "Agent 推断出的字段绑定计划") FieldBindingPlan plan
    ) {
        DataProcessingAgentState currentState = requiredState(taskId);
        FieldBindingPlan validatedPlan = fieldBindingValidationTool.validateFieldBindingPlan(
                plan,
                currentState.parsedExcelSummary().sourceHeaders(),
                currentState.templateBundle().processingRule()
        );

        DataProcessingAgentState stateWithPlan = currentState.withFieldBindingPlan(validatedPlan)
                .withStage(AgentWorkflowStage.FIELD_BINDING_PLAN_READY)
                .addTrace("完成字段绑定计划校验。");
        List<AgentConfirmationItem> confirmationItems = confirmationTool.buildConfirmationItems(stateWithPlan);
        AgentWorkflowStage nextStage = confirmationItems.isEmpty()
                ? AgentWorkflowStage.USER_CONFIRMED
                : AgentWorkflowStage.USER_CONFIRMATION_REQUIRED;

        DataProcessingAgentState savedState = stateTool.saveTaskState(stateWithPlan
                .withConfirmationItems(confirmationItems)
                .withStage(nextStage)
                .addTrace(confirmationItems.isEmpty() ? "无需用户确认。" : "生成并保存用户确认项。"));
        return toResponse(savedState);
    }

    /**
     * 新增暴露给模型的合并工具：接收前端提交的用户确认结果，完成校验、保存和阶段推进。
     */
    @Tool(name = "submit_user_confirmation", description = "接收并校验前端提交的用户确认结果，保存确认决策，并将任务推进到 USER_CONFIRMED。")
    public DataProcessingAgentResponse submitUserConfirmation(
            @ToolParam(description = "任务编号") String taskId,
            @ToolParam(description = "用户确认提交结果") AgentUserConfirmationRequest request
    ) {
        DataProcessingAgentState state = requiredState(taskId);
        List<AgentConfirmationDecision> decisions = confirmationTool.validateUserConfirmationRequest(
                state.confirmationItems(),
                request
        );
        DataProcessingAgentState savedState = stateTool.saveTaskState(state
                .withUserConfirmationResult(decisions)
                .withStage(AgentWorkflowStage.USER_CONFIRMED)
                .addTrace("用户确认结果校验通过。"));
        return toResponse(savedState);
    }

    /**
     * 新增暴露给模型的合并工具：校验 Agent 生成的 SQL 片段计划，并拼接完整 INSERT ... SELECT SQL。
     * 当前实现只负责 SQL 校验和拼接，不执行数据库落表。
     */
    @Tool(name = "execute_processing_plan", description = "校验加工计划 SQL 片段并拼接完整 INSERT SELECT SQL；当前不执行数据库落表。")
    public RenderedProcessingSql executeProcessingPlan(
            @ToolParam(description = "任务编号") String taskId,
            @ToolParam(description = "prepare_sql_generation_context 工具返回的 SQL 生成上下文") AgentSqlGenerationContext sqlGenerationContext,
            @ToolParam(description = "Agent 生成的目标列 SQL 表达式片段计划") ProcessingPlanDsl processingPlanDsl
    ) {
        RenderedProcessingSql renderedSql = processingPlanSqlTool.renderInsertSelectSql(
                taskId,
                sqlGenerationContext,
                processingPlanDsl
        );
        stateTool.saveTaskState(requiredState(taskId)
                .withStage(AgentWorkflowStage.PROCESSING_SQL_RENDERED)
                .addTrace("完成 SQL 片段校验并拼接完整 INSERT SELECT SQL。"));
        return renderedSql;
    }

    /**
     * 暴露给模型的失败兜底工具：将当前任务标记为失败并返回标准 Agent 响应。
     */
    @Tool(name = "mark_task_failed", description = "使用错误编码和错误信息将当前任务标记为失败，并返回标准 Agent 响应。")
    public DataProcessingAgentResponse markTaskFailed(
            @ToolParam(description = "任务编号") String taskId,
            @ToolParam(description = "错误编码") String errorCode,
            @ToolParam(description = "错误信息") String message
    ) {
        DataProcessingAgentState failedState = stateTool.markTaskFailed(requiredState(taskId), errorCode, message);
        return toResponse(failedState);
    }

    /**
     * 原暴露给模型的细粒度工具，现已废弃为模型工具：仅作为 Java 内部能力保留。
     */
    public DataProcessingAgentState loadTaskState(String taskId) {
        Optional<DataProcessingAgentState> state = stateTool.loadTaskState(taskId);
        return state.orElse(null);
    }

    /**
     * 原暴露给模型的细粒度工具，现已废弃为模型工具：初始化动作已合并到 prepare_task_context。
     */
    public DataProcessingAgentState initializeTaskState(String taskId, String parsedFileRef) {
        DataProcessingAgentState state = DataProcessingAgentState.initial(taskId, parsedFileRef)
                .addTrace("初始化 Agent 任务状态。");
        return stateTool.saveTaskState(state);
    }

    /**
     * 原暴露给模型的细粒度工具，现已废弃为模型工具：读取摘要动作已合并到 prepare_task_context。
     */
    public ParsedExcelSummary readParsedExcelSummary(String parsedFileRef) {
        return parsedExcelFileTool.readParsedExcelSummary(parsedFileRef);
    }

    /**
     * 原暴露给模型的细粒度工具，现已废弃为模型工具：保存摘要动作已合并到 prepare_task_context。
     */
    public DataProcessingAgentState saveParsedExcelSummary(String taskId, ParsedExcelSummary summary) {
        DataProcessingAgentState state = requiredState(taskId)
                .withParsedExcelSummary(summary)
                .withStage(AgentWorkflowStage.TASK_CONTEXT_READY)
                .addTrace("读取解析文件摘要。");
        return stateTool.saveTaskState(state);
    }

    /**
     * 原暴露给模型的细粒度工具，现已废弃为模型工具：模板校验动作已合并到 accept_template_recognition。
     */
    public TemplateRecognitionResult validateTemplateRecognition(TemplateRecognitionResult result) {
        return templateRuleTool.validateTemplateRecognition(result);
    }

    /**
     * 原暴露给模型的细粒度工具，现已废弃为模型工具：模板识别保存动作已合并到 accept_template_recognition。
     */
    public DataProcessingAgentState saveTemplateRecognition(String taskId, TemplateRecognitionResult result) {
        DataProcessingAgentState state = requiredState(taskId)
                .withTemplateRecognitionResult(result)
                .withStage(AgentWorkflowStage.TEMPLATE_RECOGNIZED)
                .addTrace("完成模板识别。");
        return stateTool.saveTaskState(state);
    }

    /**
     * 原暴露给模型的细粒度工具，现已废弃为模型工具：模板包加载动作已合并到 accept_template_recognition。
     */
    public TemplateBundle loadTemplateBundle(String presetTemplateCode) {
        return templateRuleTool.loadTemplateBundle(presetTemplateCode);
    }

    /**
     * 原暴露给模型的细粒度工具，现已废弃为模型工具：必填字段加载动作已合并到 accept_template_recognition。
     */
    public StandardRequiredFields loadRequiredFields(String standardTemplateCode) {
        return requiredFieldTool.loadRequiredFields(standardTemplateCode);
    }

    /**
     * 原暴露给模型的细粒度工具，现已废弃为模型工具：值集元数据加载动作已合并到 accept_template_recognition。
     */
    public List<ValueSetMetadata> loadValueSetMetadata(ProcessingRule processingRule) {
        return valueSetTool.loadValueSetMetadata(processingRule);
    }

    /**
     * 原暴露给模型的细粒度工具，现已废弃为模型工具：模板上下文保存动作已合并到 accept_template_recognition。
     */
    public DataProcessingAgentState saveTemplateContext(
            String taskId,
            TemplateBundle templateBundle,
            StandardRequiredFields requiredFields,
            List<ValueSetMetadata> valueSetMetadata
    ) {
        DataProcessingAgentState state = requiredState(taskId)
                .withTemplateContext(templateBundle, requiredFields, valueSetMetadata)
                .withStage(AgentWorkflowStage.TEMPLATE_CONTEXT_READY)
                .addTrace("加载模板、规则、必填字段和值集元数据。");
        return stateTool.saveTaskState(state);
    }

    /**
     * 原暴露给模型的细粒度工具，现已废弃为模型工具：字段绑定校验动作已合并到 accept_field_binding_plan。
     */
    public FieldBindingPlan validateFieldBindingPlan(String taskId, FieldBindingPlan plan) {
        DataProcessingAgentState state = requiredState(taskId);
        return fieldBindingValidationTool.validateFieldBindingPlan(
                plan,
                state.parsedExcelSummary().sourceHeaders(),
                state.templateBundle().processingRule()
        );
    }

    /**
     * 原暴露给模型的细粒度工具，现已废弃为模型工具：字段绑定保存动作已合并到 accept_field_binding_plan。
     */
    public DataProcessingAgentState saveFieldBindingPlan(String taskId, FieldBindingPlan plan) {
        DataProcessingAgentState state = requiredState(taskId)
                .withFieldBindingPlan(plan)
                .withStage(AgentWorkflowStage.FIELD_BINDING_PLAN_READY)
                .addTrace("完成字段绑定计划校验。");
        return stateTool.saveTaskState(state);
    }

    /**
     * 原暴露给模型的细粒度工具，现已废弃为模型工具：确认项生成动作已合并到 accept_field_binding_plan。
     */
    public List<AgentConfirmationItem> buildConfirmationItems(String taskId) {
        DataProcessingAgentState state = requiredState(taskId);
        return confirmationTool.buildConfirmationItems(state);
    }

    /**
     * 原暴露给模型的细粒度工具，现已废弃为模型工具：确认项保存动作已合并到 accept_field_binding_plan。
     */
    public DataProcessingAgentState saveConfirmationItems(String taskId, List<AgentConfirmationItem> items) {
        DataProcessingAgentState state = requiredState(taskId)
                .withConfirmationItems(items)
                .withStage(items == null || items.isEmpty()
                        ? AgentWorkflowStage.USER_CONFIRMED
                        : AgentWorkflowStage.USER_CONFIRMATION_REQUIRED)
                .addTrace(items == null || items.isEmpty() ? "无需用户确认。" : "生成用户确认项。");
        return stateTool.saveTaskState(state);
    }

    /**
     * 原暴露给模型的细粒度工具，现已废弃为模型工具：用户确认校验动作已合并到 submit_user_confirmation。
     */
    public List<AgentConfirmationDecision> validateUserConfirmationRequest(
            String taskId,
            AgentUserConfirmationRequest request
    ) {
        DataProcessingAgentState state = requiredState(taskId);
        return confirmationTool.validateUserConfirmationRequest(state.confirmationItems(), request);
    }

    /**
     * 原暴露给模型的细粒度工具，现已废弃为模型工具：用户确认保存动作已合并到 submit_user_confirmation。
     */
    public DataProcessingAgentState saveUserConfirmationResult(
            String taskId,
            List<AgentConfirmationDecision> decisions
    ) {
        DataProcessingAgentState state = requiredState(taskId)
                .withUserConfirmationResult(decisions)
                .withStage(AgentWorkflowStage.USER_CONFIRMED)
                .addTrace("用户确认结果校验通过。");
        return stateTool.saveTaskState(state);
    }

    /**
     * 原暴露给模型的细粒度工具，现已废弃为模型工具：标准响应由合并工具直接返回。
     */
    public Map<String, Object> getAgentResponse(String taskId) {
        return responseMap(requiredState(taskId));
    }

    private DataProcessingAgentState requiredState(String taskId) {
        return stateTool.loadTaskState(taskId)
                .orElseThrow(() -> new IllegalArgumentException("任务状态不存在: " + taskId));
    }

    private Map<String, Object> baseContext(DataProcessingAgentState state) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("stage", state.stage());
        result.put("taskId", state.taskId());
        result.put("parsedFileRef", state.parsedFileRef());
        result.put("parsedExcelSummary", state.parsedExcelSummary());
        result.put("templateRecognitionResult", state.templateRecognitionResult());
        result.put("confirmationItems", state.confirmationItems());
        result.put("userConfirmationResult", state.userConfirmationResult());
        result.put("summary", state.summary());
        return result;
    }

    private String nextAction(DataProcessingAgentState state) {
        return switch (state.stage()) {
            case RECEIVED -> "请先通过 prepare_task_context 加载解析文件摘要。";
            case TASK_CONTEXT_READY -> "请调用 load_template_catalog，并基于模板目录和 Excel 摘要识别模板。";
            case TEMPLATE_RECOGNIZED, TEMPLATE_CONTEXT_READY -> "请基于已加载的模板上下文生成 FieldBindingPlan，然后调用 accept_field_binding_plan。";
            case FIELD_BINDING_PLAN_READY, CONFIRMATION_ANALYZED -> "请调用 accept_field_binding_plan 生成确认分析结果，或直接返回已有确认分析响应。";
            case USER_CONFIRMATION_REQUIRED -> "请直接返回 agentResponse，等待前端提交用户确认结果。";
            case USER_CONFIRMED -> "请调用 prepare_sql_generation_context 准备 SQL 生成上下文。";
            case SQL_GENERATION_CONTEXT_READY -> "请生成 ProcessingPlanDsl，然后调用 execute_processing_plan。";
            case PROCESSING_SQL_RENDERED -> "完整 SQL 已生成，请直接返回 agentResponse 或最终 SQL 生成结果。";
            case RESULT_TABLE_WRITTEN -> "结果表写入已完成，请返回完成响应。";
            case FAILED -> "任务已失败，请直接返回 agentResponse。";
            case COMPLETED -> "任务已完成，请直接返回 agentResponse。";
        };
    }

    private DataProcessingAgentResponse toResponse(DataProcessingAgentState state) {
        return new DataProcessingAgentResponse(
                state.stage(),
                state.taskId(),
                state.parsedFileRef(),
                state.templateRecognitionResult(),
                state.confirmationItems(),
                state.userConfirmationResult(),
                state.summary(),
                state.stage() == AgentWorkflowStage.FAILED ? "AGENT_TASK_FAILED" : "",
                responseMessage(state)
        );
    }

    private Map<String, Object> responseMap(DataProcessingAgentState state) {
        DataProcessingAgentResponse response = toResponse(state);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("stage", response.stage());
        result.put("taskId", response.taskId());
        result.put("parsedFileRef", response.parsedFileRef());
        result.put("templateRecognitionResult", response.templateRecognitionResult());
        result.put("confirmationItems", response.confirmationItems());
        result.put("userConfirmationResult", response.userConfirmationResult());
        result.put("summary", response.summary());
        result.put("errorCode", response.errorCode());
        result.put("message", response.message());
        return result;
    }

    private String responseMessage(DataProcessingAgentState state) {
        return switch (state.stage()) {
            case RECEIVED -> "任务已接收。";
            case TASK_CONTEXT_READY -> "任务上下文已准备完成。";
            case TEMPLATE_RECOGNIZED -> "模板识别已完成。";
            case TEMPLATE_CONTEXT_READY -> "模板上下文已准备完成。";
            case FIELD_BINDING_PLAN_READY -> "字段绑定计划已校验。";
            case CONFIRMATION_ANALYZED -> "确认项分析已完成。";
            case USER_CONFIRMATION_REQUIRED -> "等待用户确认。";
            case USER_CONFIRMED -> "用户确认阶段已完成。";
            case SQL_GENERATION_CONTEXT_READY -> "SQL 生成上下文已准备完成。";
            case PROCESSING_SQL_RENDERED -> "完整 SQL 已生成，等待落表执行实现接入。";
            case RESULT_TABLE_WRITTEN -> "结果表已写入。";
            case FAILED -> "任务失败。";
            case COMPLETED -> "任务已完成。";
        };
    }
}
