package com.example.dataprocess.agent.model;

import com.example.dataprocess.domain.model.TemplateRecognitionResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Persisted agent state for resumable data-processing execution.
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
        RenderedProcessingSql renderedProcessingSql,
        String exportedExcelDocId,
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
                null,
                null,
                new ArrayList<>(),
                new ArrayList<>()
        );
    }

    public DataProcessingAgentState withStage(AgentWorkflowStage newStage) {
        return copy(newStage, parsedExcelSummary, templateRecognitionResult, templateBundle, requiredFields,
                valueSetMetadata, fieldBindingPlan, confirmationItems, userConfirmationResult,
                renderedProcessingSql, exportedExcelDocId, traceLogs, errorMessages);
    }

    public DataProcessingAgentState withParsedExcelSummary(ParsedExcelSummary value) {
        return copy(stage, value, templateRecognitionResult, templateBundle, requiredFields,
                valueSetMetadata, fieldBindingPlan, confirmationItems, userConfirmationResult,
                renderedProcessingSql, exportedExcelDocId, traceLogs, errorMessages);
    }

    public DataProcessingAgentState withTemplateRecognitionResult(TemplateRecognitionResult value) {
        return copy(stage, parsedExcelSummary, value, templateBundle, requiredFields,
                valueSetMetadata, fieldBindingPlan, confirmationItems, userConfirmationResult,
                renderedProcessingSql, exportedExcelDocId, traceLogs, errorMessages);
    }

    public DataProcessingAgentState withTemplateContext(
            TemplateBundle newTemplateBundle,
            StandardRequiredFields newRequiredFields,
            List<ValueSetMetadata> newValueSetMetadata
    ) {
        return copy(stage, parsedExcelSummary, templateRecognitionResult, newTemplateBundle, newRequiredFields,
                List.copyOf(newValueSetMetadata), fieldBindingPlan, confirmationItems, userConfirmationResult,
                renderedProcessingSql, exportedExcelDocId, traceLogs, errorMessages);
    }

    public DataProcessingAgentState withFieldBindingPlan(FieldBindingPlan value) {
        return copy(stage, parsedExcelSummary, templateRecognitionResult, templateBundle, requiredFields,
                valueSetMetadata, value, confirmationItems, userConfirmationResult,
                renderedProcessingSql, exportedExcelDocId, traceLogs, errorMessages);
    }

    public DataProcessingAgentState withConfirmationItems(List<AgentConfirmationItem> value) {
        return copy(stage, parsedExcelSummary, templateRecognitionResult, templateBundle, requiredFields,
                valueSetMetadata, fieldBindingPlan, List.copyOf(value), userConfirmationResult,
                renderedProcessingSql, exportedExcelDocId, traceLogs, errorMessages);
    }

    public DataProcessingAgentState withUserConfirmationResult(List<AgentConfirmationDecision> value) {
        return copy(stage, parsedExcelSummary, templateRecognitionResult, templateBundle, requiredFields,
                valueSetMetadata, fieldBindingPlan, confirmationItems, List.copyOf(value),
                renderedProcessingSql, exportedExcelDocId, traceLogs, errorMessages);
    }

    public DataProcessingAgentState withRenderedProcessingSql(RenderedProcessingSql value) {
        return copy(stage, parsedExcelSummary, templateRecognitionResult, templateBundle, requiredFields,
                valueSetMetadata, fieldBindingPlan, confirmationItems, userConfirmationResult,
                value, exportedExcelDocId, traceLogs, errorMessages);
    }

    public DataProcessingAgentState withExportedExcelDocId(String value) {
        return copy(stage, parsedExcelSummary, templateRecognitionResult, templateBundle, requiredFields,
                valueSetMetadata, fieldBindingPlan, confirmationItems, userConfirmationResult,
                renderedProcessingSql, value, traceLogs, errorMessages);
    }

    public DataProcessingAgentState addTrace(String message) {
        List<String> newTraceLogs = new ArrayList<>(safeList(traceLogs));
        newTraceLogs.add(message);
        return copy(stage, parsedExcelSummary, templateRecognitionResult, templateBundle, requiredFields,
                valueSetMetadata, fieldBindingPlan, confirmationItems, userConfirmationResult,
                renderedProcessingSql, exportedExcelDocId, newTraceLogs, errorMessages);
    }

    public DataProcessingAgentState addError(String message) {
        List<String> newErrorMessages = new ArrayList<>(safeList(errorMessages));
        newErrorMessages.add(message);
        return copy(stage, parsedExcelSummary, templateRecognitionResult, templateBundle, requiredFields,
                valueSetMetadata, fieldBindingPlan, confirmationItems, userConfirmationResult,
                renderedProcessingSql, exportedExcelDocId, traceLogs, newErrorMessages);
    }

    public Map<String, Object> summary() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("traceLogs", safeList(traceLogs));
        result.put("errorMessages", safeList(errorMessages));
        if (renderedProcessingSql != null) {
            result.put("resultTable", renderedProcessingSql.resultTable());
            result.put("stagingTable", renderedProcessingSql.stagingTable());
            result.put("targetColumns", renderedProcessingSql.targetColumns());
            result.put("insertSelectSql", renderedProcessingSql.insertSelectSql());
            result.put("loadedRows", renderedProcessingSql.loadedRows());
        }
        if (exportedExcelDocId != null && !exportedExcelDocId.isBlank()) {
            result.put("excelDocId", exportedExcelDocId);
        }
        return Collections.unmodifiableMap(result);
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
            RenderedProcessingSql newRenderedProcessingSql,
            String newExportedExcelDocId,
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
                newRenderedProcessingSql,
                newExportedExcelDocId,
                safeList(newTraceLogs),
                safeList(newErrorMessages)
        );
    }

    private static <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
