package com.example.dataprocess.agent.model;

import com.example.dataprocess.domain.model.TemplateRecognitionResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Persisted agent state for resumable execution up to user confirmation.
 */
public record DataProcessingAgentState(
        String taskId,
        String parsedFileRef,
        AgentWorkflowStage stage,
        ParsedExcelSummary parsedExcelSummary,
        TemplateRecognitionResult templateRecognitionResult,
        TemplateBundle templateBundle,
        StandardRequiredFields requiredFields,
        List<ValueSetMetadata> valueSetMetadata,
        FieldBindingPlan fieldBindingPlan,
        List<AgentConfirmationItem> confirmationItems,
        List<AgentConfirmationDecision> userConfirmationResult,
        List<String> traceLogs,
        List<String> errorMessages
) {

    public static DataProcessingAgentState initial(String taskId, String parsedFileRef) {
        return new DataProcessingAgentState(
                taskId,
                parsedFileRef,
                AgentWorkflowStage.RECEIVED,
                null,
                null,
                null,
                null,
                List.of(),
                null,
                List.of(),
                List.of(),
                new ArrayList<>(),
                new ArrayList<>()
        );
    }

    public DataProcessingAgentState withStage(AgentWorkflowStage newStage) {
        return copy(newStage, parsedExcelSummary, templateRecognitionResult, templateBundle, requiredFields,
                valueSetMetadata, fieldBindingPlan, confirmationItems, userConfirmationResult, traceLogs, errorMessages);
    }

    public DataProcessingAgentState withParsedExcelSummary(ParsedExcelSummary value) {
        return copy(stage, value, templateRecognitionResult, templateBundle, requiredFields,
                valueSetMetadata, fieldBindingPlan, confirmationItems, userConfirmationResult, traceLogs, errorMessages);
    }

    public DataProcessingAgentState withTemplateRecognitionResult(TemplateRecognitionResult value) {
        return copy(stage, parsedExcelSummary, value, templateBundle, requiredFields,
                valueSetMetadata, fieldBindingPlan, confirmationItems, userConfirmationResult, traceLogs, errorMessages);
    }

    public DataProcessingAgentState withTemplateContext(
            TemplateBundle newTemplateBundle,
            StandardRequiredFields newRequiredFields,
            List<ValueSetMetadata> newValueSetMetadata
    ) {
        return copy(stage, parsedExcelSummary, templateRecognitionResult, newTemplateBundle, newRequiredFields,
                List.copyOf(newValueSetMetadata), fieldBindingPlan, confirmationItems, userConfirmationResult, traceLogs, errorMessages);
    }

    public DataProcessingAgentState withFieldBindingPlan(FieldBindingPlan value) {
        return copy(stage, parsedExcelSummary, templateRecognitionResult, templateBundle, requiredFields,
                valueSetMetadata, value, confirmationItems, userConfirmationResult, traceLogs, errorMessages);
    }

    public DataProcessingAgentState withConfirmationItems(List<AgentConfirmationItem> value) {
        return copy(stage, parsedExcelSummary, templateRecognitionResult, templateBundle, requiredFields,
                valueSetMetadata, fieldBindingPlan, List.copyOf(value), userConfirmationResult, traceLogs, errorMessages);
    }

    public DataProcessingAgentState withUserConfirmationResult(List<AgentConfirmationDecision> value) {
        return copy(stage, parsedExcelSummary, templateRecognitionResult, templateBundle, requiredFields,
                valueSetMetadata, fieldBindingPlan, confirmationItems, List.copyOf(value), traceLogs, errorMessages);
    }

    public DataProcessingAgentState addTrace(String message) {
        List<String> newTraceLogs = new ArrayList<>(safeList(traceLogs));
        newTraceLogs.add(message);
        return copy(stage, parsedExcelSummary, templateRecognitionResult, templateBundle, requiredFields,
                valueSetMetadata, fieldBindingPlan, confirmationItems, userConfirmationResult, newTraceLogs, errorMessages);
    }

    public DataProcessingAgentState addError(String message) {
        List<String> newErrorMessages = new ArrayList<>(safeList(errorMessages));
        newErrorMessages.add(message);
        return copy(stage, parsedExcelSummary, templateRecognitionResult, templateBundle, requiredFields,
                valueSetMetadata, fieldBindingPlan, confirmationItems, userConfirmationResult, traceLogs, newErrorMessages);
    }

    public Map<String, Object> summary() {
        return Map.of(
                "traceLogs", safeList(traceLogs),
                "errorMessages", safeList(errorMessages)
        );
    }

    private DataProcessingAgentState copy(
            AgentWorkflowStage newStage,
            ParsedExcelSummary newParsedExcelSummary,
            TemplateRecognitionResult newTemplateRecognitionResult,
            TemplateBundle newTemplateBundle,
            StandardRequiredFields newRequiredFields,
            List<ValueSetMetadata> newValueSetMetadata,
            FieldBindingPlan newFieldBindingPlan,
            List<AgentConfirmationItem> newConfirmationItems,
            List<AgentConfirmationDecision> newUserConfirmationResult,
            List<String> newTraceLogs,
            List<String> newErrorMessages
    ) {
        return new DataProcessingAgentState(
                taskId,
                parsedFileRef,
                newStage,
                newParsedExcelSummary,
                newTemplateRecognitionResult,
                newTemplateBundle,
                newRequiredFields,
                safeList(newValueSetMetadata),
                newFieldBindingPlan,
                safeList(newConfirmationItems),
                safeList(newUserConfirmationResult),
                safeList(newTraceLogs),
                safeList(newErrorMessages)
        );
    }

    private static <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
