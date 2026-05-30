package com.example.dataprocess.agent.model;

import com.example.dataprocess.domain.model.ProcessingPlanDsl;

import java.util.List;

/**
 * Rendered SQL result returned before the real database write implementation is connected.
 *
 * @param taskId task id
 * @param resultTable target result table
 * @param stagingTable source staging table
 * @param targetColumns target columns inserted by the rendered SQL
 * @param insertSelectSql complete INSERT ... SELECT SQL rendered by deterministic tooling
 * @param loadedRows rows loaded into the staging table
 * @param validatedPlan validated ProcessingPlanDsl used to render SQL
 */
public record RenderedProcessingSql(
        String taskId,
        String resultTable,
        String stagingTable,
        List<String> targetColumns,
        String insertSelectSql,
        Integer loadedRows,
        ProcessingPlanDsl validatedPlan
) {
}
