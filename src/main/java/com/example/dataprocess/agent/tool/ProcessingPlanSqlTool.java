package com.example.dataprocess.agent.tool;

import com.example.dataprocess.agent.model.AgentSqlGenerationContext;
import com.example.dataprocess.agent.model.RenderedProcessingSql;
import com.example.dataprocess.domain.model.ProcessingPlanColumn;
import com.example.dataprocess.domain.model.ProcessingPlanDsl;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Deterministic SQL rendering tool for the post-confirmation agent flow.
 */
@Component
public class ProcessingPlanSqlTool {

    private static final Pattern SIMPLE_IDENTIFIER_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Pattern QUALIFIED_TABLE_PATTERN = Pattern.compile(
            "[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)?"
    );

    /**
     * Validate SQL fragments and render a complete INSERT ... SELECT statement.
     *
     * <p>This method intentionally does not execute the SQL. The database write step should be connected by
     * the caller after the rendered SQL has been reviewed or routed to the execution component.</p>
     */
    public RenderedProcessingSql renderInsertSelectSql(
            String taskId,
            AgentSqlGenerationContext context,
            ProcessingPlanDsl processingPlanDsl
    ) {
        validateContext(taskId, context, processingPlanDsl);
        String sql = renderSql(context, processingPlanDsl);
        return new RenderedProcessingSql(
                taskId,
                context.resultTable(),
                context.stagingTable(),
                processingPlanDsl.columns().stream().map(ProcessingPlanColumn::targetColumn).toList(),
                sql,
                context.loadedRows(),
                processingPlanDsl
        );
    }

    private void validateContext(String taskId, AgentSqlGenerationContext context, ProcessingPlanDsl processingPlanDsl) {
        if (context == null) {
            throw new IllegalArgumentException("SQL 生成上下文不能为空。");
        }
        if (isBlank(taskId)) {
            throw new IllegalArgumentException("任务编号不能为空。");
        }
        if (!taskId.equals(context.taskId())) {
            throw new IllegalArgumentException("SQL 生成上下文 taskId 与工具入参不一致。");
        }
        if (processingPlanDsl == null) {
            throw new IllegalArgumentException("加工计划不能为空。");
        }
        if (!taskId.equals(processingPlanDsl.taskId())) {
            throw new IllegalArgumentException("加工计划 taskId 与工具入参不一致。");
        }
        validateQualifiedTable(context.stagingTable(), "临时表名非法。");
        validateQualifiedTable(context.resultTable(), "结果表名非法。");
    }

    private String renderSql(AgentSqlGenerationContext context, ProcessingPlanDsl plan) {
        List<String> targetColumns = plan.columns().stream()
                .map(ProcessingPlanColumn::targetColumn)
                .peek(column -> validateSimpleIdentifier(column, "目标列名非法: " + column))
                .toList();
        String insertColumns = String.join(", ", targetColumns);
        String selectExpressions = plan.columns().stream()
                .map(column -> column.expressionSql() + " AS " + column.targetColumn())
                .reduce((left, right) -> left + ", " + right)
                .orElseThrow(() -> new IllegalArgumentException("加工计划缺少目标列。"));

        return "INSERT INTO " + context.resultTable()
                + " (" + insertColumns + ") "
                + "SELECT " + selectExpressions
                + " FROM " + context.stagingTable();
    }

    private void validateQualifiedTable(String value, String message) {
        if (isBlank(value) || !QUALIFIED_TABLE_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(message + " value=" + value);
        }
    }

    private void validateSimpleIdentifier(String value, String message) {
        if (isBlank(value) || !SIMPLE_IDENTIFIER_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(message);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
