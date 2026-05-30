package com.example.dataprocess.agent.model;

import com.example.dataprocess.domain.model.ActualColumnMapping;

import java.util.List;

/**
 * SQL table context returned by prepare_sql_generation_context and consumed by execute_processing_plan.
 *
 * @param taskId task id
 * @param stagingTable staging table name, created by deterministic tooling
 * @param resultTable result table name, resolved by deterministic tooling
 * @param loadedRows rows loaded into the staging table
 * @param columnMappings all Excel original column to staging elastic column mappings
 */
public record AgentSqlGenerationContext(
        String taskId,
        String stagingTable,
        String resultTable,
        Integer loadedRows,
        List<ActualColumnMapping> columnMappings
) {
}
