package com.example.dataprocess.agent.tool;

import com.example.dataprocess.agent.model.AgentConfirmationDecision;
import com.example.dataprocess.agent.model.AgentConfirmationItem;
import com.example.dataprocess.agent.model.AgentUserConfirmationRequest;
import com.example.dataprocess.agent.model.AgentWorkflowStage;
import com.example.dataprocess.agent.model.DataProcessingAgentState;
import com.example.dataprocess.agent.model.FieldBindingPlan;
import com.example.dataprocess.agent.model.ParsedExcelSummary;
import com.example.dataprocess.agent.model.StandardRequiredFields;
import com.example.dataprocess.agent.model.TemplateBundle;
import com.example.dataprocess.agent.model.ValueSetMetadata;
import com.example.dataprocess.domain.model.ProcessingRule;
import com.example.dataprocess.domain.model.TemplateRecognitionResult;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Spring AI method tools exposed to ReactAgent.
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

    public DataProcessingAgentToolMethods(
            AgentStateTool stateTool,
            ParsedExcelFileTool parsedExcelFileTool,
            TemplateRuleTool templateRuleTool,
            RequiredFieldTool requiredFieldTool,
            ValueSetTool valueSetTool,
            FieldBindingValidationTool fieldBindingValidationTool,
            ConfirmationTool confirmationTool
    ) {
        this.stateTool = stateTool;
        this.parsedExcelFileTool = parsedExcelFileTool;
        this.templateRuleTool = templateRuleTool;
        this.requiredFieldTool = requiredFieldTool;
        this.valueSetTool = valueSetTool;
        this.fieldBindingValidationTool = fieldBindingValidationTool;
        this.confirmationTool = confirmationTool;
    }

    @Tool(name = "load_task_state", description = "根据任务编号读取当前 Agent 任务状态；如果任务尚未开始，返回空。")
    public DataProcessingAgentState loadTaskState(@ToolParam(description = "任务编号") String taskId) {
        Optional<DataProcessingAgentState> state = stateTool.loadTaskState(taskId);
        return state.orElse(null);
    }

    @Tool(name = "initialize_task_state", description = "初始化并保存一个新的 RECEIVED 阶段任务状态。")
    public DataProcessingAgentState initializeTaskState(
            @ToolParam(description = "任务编号") String taskId,
            @ToolParam(description = "已解析 Excel 文件引用") String parsedFileRef
    ) {
        DataProcessingAgentState state = DataProcessingAgentState.initial(taskId, parsedFileRef)
                .addTrace("初始化 Agent 任务状态。");
        return stateTool.saveTaskState(state);
    }

    @Tool(name = "read_parsed_excel_summary", description = "根据已解析文件引用读取 Excel 摘要信息。")
    public ParsedExcelSummary readParsedExcelSummary(
            @ToolParam(description = "已解析 Excel 文件引用") String parsedFileRef
    ) {
        return parsedExcelFileTool.readParsedExcelSummary(parsedFileRef);
    }

    @Tool(name = "save_parsed_excel_summary", description = "将已解析 Excel 摘要保存到任务状态中。")
    public DataProcessingAgentState saveParsedExcelSummary(
            @ToolParam(description = "任务编号") String taskId,
            @ToolParam(description = "已解析 Excel 摘要") ParsedExcelSummary summary
    ) {
        DataProcessingAgentState state = requiredState(taskId)
                .withParsedExcelSummary(summary)
                .addTrace("读取解析文件摘要。");
        return stateTool.saveTaskState(state);
    }

    @Tool(name = "load_template_catalog", description = "加载模板目录 Markdown 原文。")
    public String loadTemplateCatalog() {
        return templateRuleTool.loadTemplateCatalog();
    }

    @Tool(name = "validate_template_recognition", description = "根据模板目录校验 Agent 推断出的模板识别结果。")
    public TemplateRecognitionResult validateTemplateRecognition(
            @ToolParam(description = "Agent 推断出的模板识别结果") TemplateRecognitionResult result
    ) {
        return templateRuleTool.validateTemplateRecognition(result);
    }

    @Tool(name = "save_template_recognition", description = "保存已校验通过的模板识别结果，并将任务推进到 TEMPLATE_RECOGNIZED 阶段。")
    public DataProcessingAgentState saveTemplateRecognition(
            @ToolParam(description = "任务编号") String taskId,
            @ToolParam(description = "已校验通过的模板识别结果") TemplateRecognitionResult result
    ) {
        DataProcessingAgentState state = requiredState(taskId)
                .withTemplateRecognitionResult(result)
                .withStage(AgentWorkflowStage.TEMPLATE_RECOGNIZED)
                .addTrace("完成模板识别。");
        return stateTool.saveTaskState(state);
    }

    @Tool(name = "load_template_bundle", description = "根据预置模板编码加载预置模板、标准模板和加工规则。")
    public TemplateBundle loadTemplateBundle(
            @ToolParam(description = "预置模板编码") String presetTemplateCode
    ) {
        return templateRuleTool.loadTemplateBundle(presetTemplateCode);
    }

    @Tool(name = "load_required_fields", description = "加载标准模板中的必填目标字段集合。")
    public StandardRequiredFields loadRequiredFields(
            @ToolParam(description = "标准模板编码") String standardTemplateCode
    ) {
        return requiredFieldTool.loadRequiredFields(standardTemplateCode);
    }

    @Tool(name = "load_value_set_metadata", description = "加载 USER_CONFIRM_OPTION 加工规则所需的值集元数据。")
    public List<ValueSetMetadata> loadValueSetMetadata(
            @ToolParam(description = "加工规则") ProcessingRule processingRule
    ) {
        return valueSetTool.loadValueSetMetadata(processingRule);
    }

    @Tool(name = "save_template_context", description = "将模板包、必填字段和值集元数据保存到任务状态中。")
    public DataProcessingAgentState saveTemplateContext(
            @ToolParam(description = "任务编号") String taskId,
            @ToolParam(description = "模板上下文包") TemplateBundle templateBundle,
            @ToolParam(description = "必填字段集合") StandardRequiredFields requiredFields,
            @ToolParam(description = "值集元数据") List<ValueSetMetadata> valueSetMetadata
    ) {
        DataProcessingAgentState state = requiredState(taskId)
                .withTemplateContext(templateBundle, requiredFields, valueSetMetadata)
                .addTrace("加载模板、规则、必填字段和值集元数据。");
        return stateTool.saveTaskState(state);
    }

    @Tool(name = "validate_field_binding_plan", description = "校验字段绑定计划；只有 DIRECT_MAPPING 和 EXPR 规则允许包含来源列。")
    public FieldBindingPlan validateFieldBindingPlan(
            @ToolParam(description = "任务编号") String taskId,
            @ToolParam(description = "Agent 推断出的字段绑定计划") FieldBindingPlan plan
    ) {
        DataProcessingAgentState state = requiredState(taskId);
        return fieldBindingValidationTool.validateFieldBindingPlan(
                plan,
                state.parsedExcelSummary().sourceHeaders(),
                state.templateBundle().processingRule()
        );
    }

    @Tool(name = "save_field_binding_plan", description = "将已校验通过的字段绑定计划保存到任务状态中。")
    public DataProcessingAgentState saveFieldBindingPlan(
            @ToolParam(description = "任务编号") String taskId,
            @ToolParam(description = "已校验通过的字段绑定计划") FieldBindingPlan plan
    ) {
        DataProcessingAgentState state = requiredState(taskId)
                .withFieldBindingPlan(plan)
                .addTrace("完成字段绑定计划校验。");
        return stateTool.saveTaskState(state);
    }

    @Tool(name = "build_confirmation_items", description = "根据当前任务状态生成并校验用户确认项，同时检查必填映射字段是否存在空值。")
    public List<AgentConfirmationItem> buildConfirmationItems(@ToolParam(description = "任务编号") String taskId) {
        DataProcessingAgentState state = requiredState(taskId);
        return confirmationTool.buildConfirmationItems(state);
    }

    @Tool(name = "save_confirmation_items", description = "保存确认项，并根据是否存在确认项将阶段设置为 USER_CONFIRMATION_REQUIRED 或 USER_CONFIRMED。")
    public DataProcessingAgentState saveConfirmationItems(
            @ToolParam(description = "任务编号") String taskId,
            @ToolParam(description = "已校验通过的确认项") List<AgentConfirmationItem> items
    ) {
        DataProcessingAgentState state = requiredState(taskId)
                .withConfirmationItems(items)
                .withStage(items == null || items.isEmpty()
                        ? AgentWorkflowStage.USER_CONFIRMED
                        : AgentWorkflowStage.USER_CONFIRMATION_REQUIRED)
                .addTrace(items == null || items.isEmpty() ? "无需用户确认。" : "生成用户确认项。");
        return stateTool.saveTaskState(state);
    }

    @Tool(name = "validate_user_confirmation_request", description = "根据待确认项校验前端提交的用户确认结果。")
    public List<AgentConfirmationDecision> validateUserConfirmationRequest(
            @ToolParam(description = "任务编号") String taskId,
            @ToolParam(description = "用户确认提交结果") AgentUserConfirmationRequest request
    ) {
        DataProcessingAgentState state = requiredState(taskId);
        return confirmationTool.validateUserConfirmationRequest(state.confirmationItems(), request);
    }

    @Tool(name = "save_user_confirmation_result", description = "保存已校验通过的用户确认结果，并将任务推进到 USER_CONFIRMED 阶段。")
    public DataProcessingAgentState saveUserConfirmationResult(
            @ToolParam(description = "任务编号") String taskId,
            @ToolParam(description = "已校验通过的确认决策") List<AgentConfirmationDecision> decisions
    ) {
        DataProcessingAgentState state = requiredState(taskId)
                .withUserConfirmationResult(decisions)
                .withStage(AgentWorkflowStage.USER_CONFIRMED)
                .addTrace("用户确认结果校验通过。");
        return stateTool.saveTaskState(state);
    }

    @Tool(name = "mark_task_failed", description = "使用错误编码和错误信息将当前任务标记为失败。")
    public DataProcessingAgentState markTaskFailed(
            @ToolParam(description = "任务编号") String taskId,
            @ToolParam(description = "错误编码") String errorCode,
            @ToolParam(description = "错误信息") String message
    ) {
        return stateTool.markTaskFailed(requiredState(taskId), errorCode, message);
    }

    @Tool(name = "get_agent_response", description = "根据当前任务状态构造 DataProcessingAgentResponse 响应结构。")
    public Map<String, Object> getAgentResponse(@ToolParam(description = "任务编号") String taskId) {
        DataProcessingAgentState state = requiredState(taskId);
        return Map.of(
                "stage", state.stage(),
                "taskId", state.taskId(),
                "parsedFileRef", state.parsedFileRef(),
                "templateRecognitionResult", state.templateRecognitionResult(),
                "confirmationItems", state.confirmationItems(),
                "userConfirmationResult", state.userConfirmationResult(),
                "summary", state.summary(),
                "errorCode", state.stage() == AgentWorkflowStage.FAILED ? "AGENT_TASK_FAILED" : "",
                "message", responseMessage(state)
        );
    }

    private DataProcessingAgentState requiredState(String taskId) {
        return stateTool.loadTaskState(taskId)
                .orElseThrow(() -> new IllegalArgumentException("任务状态不存在: " + taskId));
    }

    private String responseMessage(DataProcessingAgentState state) {
        return switch (state.stage()) {
            case USER_CONFIRMATION_REQUIRED -> "等待用户确认。";
            case USER_CONFIRMED -> "用户确认阶段已完成。";
            case FAILED -> "任务失败。";
            default -> "任务阶段: " + state.stage();
        };
    }
}
