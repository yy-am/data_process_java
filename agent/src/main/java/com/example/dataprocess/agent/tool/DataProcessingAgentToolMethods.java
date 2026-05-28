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

    @Tool(name = "load_task_state", description = "Load the current agent task state by taskId. Returns null when the task has not started.")
    public DataProcessingAgentState loadTaskState(@ToolParam(description = "Task id") String taskId) {
        Optional<DataProcessingAgentState> state = stateTool.loadTaskState(taskId);
        return state.orElse(null);
    }

    @Tool(name = "initialize_task_state", description = "Initialize and save a new RECEIVED task state.")
    public DataProcessingAgentState initializeTaskState(
            @ToolParam(description = "Task id") String taskId,
            @ToolParam(description = "Parsed Excel file reference") String parsedFileRef
    ) {
        DataProcessingAgentState state = DataProcessingAgentState.initial(taskId, parsedFileRef)
                .addTrace("初始化 Agent 任务状态。");
        return stateTool.saveTaskState(state);
    }

    @Tool(name = "read_parsed_excel_summary", description = "Read parsed Excel summary by parsedFileRef.")
    public ParsedExcelSummary readParsedExcelSummary(
            @ToolParam(description = "Parsed Excel file reference") String parsedFileRef
    ) {
        return parsedExcelFileTool.readParsedExcelSummary(parsedFileRef);
    }

    @Tool(name = "save_parsed_excel_summary", description = "Save parsed Excel summary into task state.")
    public DataProcessingAgentState saveParsedExcelSummary(
            @ToolParam(description = "Task id") String taskId,
            @ToolParam(description = "Parsed Excel summary") ParsedExcelSummary summary
    ) {
        DataProcessingAgentState state = requiredState(taskId)
                .withParsedExcelSummary(summary)
                .addTrace("读取解析文件摘要。");
        return stateTool.saveTaskState(state);
    }

    @Tool(name = "load_template_catalog", description = "Load the template catalog markdown.")
    public String loadTemplateCatalog() {
        return templateRuleTool.loadTemplateCatalog();
    }

    @Tool(name = "validate_template_recognition", description = "Validate template recognition result against catalog.")
    public TemplateRecognitionResult validateTemplateRecognition(
            @ToolParam(description = "Agent inferred template recognition result") TemplateRecognitionResult result
    ) {
        return templateRuleTool.validateTemplateRecognition(result);
    }

    @Tool(name = "save_template_recognition", description = "Save validated template recognition result and move to TEMPLATE_RECOGNIZED.")
    public DataProcessingAgentState saveTemplateRecognition(
            @ToolParam(description = "Task id") String taskId,
            @ToolParam(description = "Validated template recognition result") TemplateRecognitionResult result
    ) {
        DataProcessingAgentState state = requiredState(taskId)
                .withTemplateRecognitionResult(result)
                .withStage(AgentWorkflowStage.TEMPLATE_RECOGNIZED)
                .addTrace("完成模板识别。");
        return stateTool.saveTaskState(state);
    }

    @Tool(name = "load_template_bundle", description = "Load preset template, standard template, and processing rule by presetTemplateCode.")
    public TemplateBundle loadTemplateBundle(
            @ToolParam(description = "Preset template code") String presetTemplateCode
    ) {
        return templateRuleTool.loadTemplateBundle(presetTemplateCode);
    }

    @Tool(name = "load_required_fields", description = "Load required target columns for standard template.")
    public StandardRequiredFields loadRequiredFields(
            @ToolParam(description = "Standard template code") String standardTemplateCode
    ) {
        return requiredFieldTool.loadRequiredFields(standardTemplateCode);
    }

    @Tool(name = "load_value_set_metadata", description = "Load value-set metadata for USER_CONFIRM_OPTION rule items.")
    public List<ValueSetMetadata> loadValueSetMetadata(
            @ToolParam(description = "Processing rule") ProcessingRule processingRule
    ) {
        return valueSetTool.loadValueSetMetadata(processingRule);
    }

    @Tool(name = "save_template_context", description = "Save template bundle, required fields, and value-set metadata into task state.")
    public DataProcessingAgentState saveTemplateContext(
            @ToolParam(description = "Task id") String taskId,
            @ToolParam(description = "Template bundle") TemplateBundle templateBundle,
            @ToolParam(description = "Required fields") StandardRequiredFields requiredFields,
            @ToolParam(description = "Value-set metadata") List<ValueSetMetadata> valueSetMetadata
    ) {
        DataProcessingAgentState state = requiredState(taskId)
                .withTemplateContext(templateBundle, requiredFields, valueSetMetadata)
                .addTrace("加载模板、规则、必填字段和值集元数据。");
        return stateTool.saveTaskState(state);
    }

    @Tool(name = "validate_field_binding_plan", description = "Validate field binding plan. Only DIRECT_MAPPING and EXPR sourceColumns may be included.")
    public FieldBindingPlan validateFieldBindingPlan(
            @ToolParam(description = "Task id") String taskId,
            @ToolParam(description = "Agent inferred field binding plan") FieldBindingPlan plan
    ) {
        DataProcessingAgentState state = requiredState(taskId);
        return fieldBindingValidationTool.validateFieldBindingPlan(
                plan,
                state.parsedExcelSummary().sourceHeaders(),
                state.templateBundle().processingRule()
        );
    }

    @Tool(name = "save_field_binding_plan", description = "Save validated field binding plan into task state.")
    public DataProcessingAgentState saveFieldBindingPlan(
            @ToolParam(description = "Task id") String taskId,
            @ToolParam(description = "Validated field binding plan") FieldBindingPlan plan
    ) {
        DataProcessingAgentState state = requiredState(taskId)
                .withFieldBindingPlan(plan)
                .addTrace("完成字段绑定计划校验。");
        return stateTool.saveTaskState(state);
    }

    @Tool(name = "build_confirmation_items", description = "Build and validate confirmation items from current task state. Also checks blank values for required mapped fields.")
    public List<AgentConfirmationItem> buildConfirmationItems(@ToolParam(description = "Task id") String taskId) {
        DataProcessingAgentState state = requiredState(taskId);
        return confirmationTool.buildConfirmationItems(state);
    }

    @Tool(name = "save_confirmation_items", description = "Save confirmation items and set stage to USER_CONFIRMATION_REQUIRED or USER_CONFIRMED.")
    public DataProcessingAgentState saveConfirmationItems(
            @ToolParam(description = "Task id") String taskId,
            @ToolParam(description = "Validated confirmation items") List<AgentConfirmationItem> items
    ) {
        DataProcessingAgentState state = requiredState(taskId)
                .withConfirmationItems(items)
                .withStage(items == null || items.isEmpty()
                        ? AgentWorkflowStage.USER_CONFIRMED
                        : AgentWorkflowStage.USER_CONFIRMATION_REQUIRED)
                .addTrace(items == null || items.isEmpty() ? "无需用户确认。" : "生成用户确认项。");
        return stateTool.saveTaskState(state);
    }

    @Tool(name = "validate_user_confirmation_request", description = "Validate frontend confirmation request against pending confirmation items.")
    public List<AgentConfirmationDecision> validateUserConfirmationRequest(
            @ToolParam(description = "Task id") String taskId,
            @ToolParam(description = "User confirmation request") AgentUserConfirmationRequest request
    ) {
        DataProcessingAgentState state = requiredState(taskId);
        return confirmationTool.validateUserConfirmationRequest(state.confirmationItems(), request);
    }

    @Tool(name = "save_user_confirmation_result", description = "Save validated user confirmation result and move to USER_CONFIRMED.")
    public DataProcessingAgentState saveUserConfirmationResult(
            @ToolParam(description = "Task id") String taskId,
            @ToolParam(description = "Validated confirmation decisions") List<AgentConfirmationDecision> decisions
    ) {
        DataProcessingAgentState state = requiredState(taskId)
                .withUserConfirmationResult(decisions)
                .withStage(AgentWorkflowStage.USER_CONFIRMED)
                .addTrace("用户确认结果校验通过。");
        return stateTool.saveTaskState(state);
    }

    @Tool(name = "mark_task_failed", description = "Mark current task failed with a code and message.")
    public DataProcessingAgentState markTaskFailed(
            @ToolParam(description = "Task id") String taskId,
            @ToolParam(description = "Error code") String errorCode,
            @ToolParam(description = "Error message") String message
    ) {
        return stateTool.markTaskFailed(requiredState(taskId), errorCode, message);
    }

    @Tool(name = "get_agent_response", description = "Build the current DataProcessingAgentResponse shape from task state.")
    public Map<String, Object> getAgentResponse(@ToolParam(description = "Task id") String taskId) {
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
