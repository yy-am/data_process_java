package com.example.dataprocess.agent.tool;

import com.example.dataprocess.agent.model.DataProcessingAgentState;
import com.example.dataprocess.agent.model.ParsedExcelFile;
import com.example.dataprocess.agent.repository.DataProcessingAgentStateRepository;
import com.example.dataprocess.agent.repository.ParsedExcelFileRepository;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Inspects parsed Excel columns and decides whether each column still needs user confirmation.
 */
@Component
public class ExcelColumnConfirmationRequirementInspector {

    public static final String CONFIRMATION_REQUIRED = "Y";
    public static final String CONFIRMATION_NOT_REQUIRED = "N";

    private final DataProcessingAgentStateRepository stateRepository;
    private final ParsedExcelFileRepository parsedExcelFileRepository;

    public ExcelColumnConfirmationRequirementInspector(
            DataProcessingAgentStateRepository stateRepository,
            ParsedExcelFileRepository parsedExcelFileRepository
    ) {
        this.stateRepository = stateRepository;
        this.parsedExcelFileRepository = parsedExcelFileRepository;
    }

    /**
     * Return Y for a column when any parsed row is blank in that column; otherwise return N.
     *
     * <p>The method scans the already parsed rows behind the task's parsedFileRef. It avoids copying row data
     * and stops early for columns once their result is known.</p>
     */
    public Map<String, String> inspectRequiredConfirmations(String taskId, List<String> columnNames) {
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException("任务编号不能为空。");
        }
        if (columnNames == null || columnNames.isEmpty()) {
            throw new IllegalArgumentException("待检查列名不能为空。");
        }

        ParsedExcelFile parsedExcelFile = loadParsedExcelFile(taskId);
        Set<String> sourceHeaders = parsedExcelFile.sourceHeaders() == null
                ? Set.of()
                : new LinkedHashSet<>(parsedExcelFile.sourceHeaders());
        Set<String> requestedColumns = normalizeColumns(columnNames);

        Map<String, String> result = new LinkedHashMap<>();
        Set<String> pendingColumns = new LinkedHashSet<>();
        for (String columnName : requestedColumns) {
            if (sourceHeaders.contains(columnName)) {
                result.put(columnName, CONFIRMATION_NOT_REQUIRED);
                pendingColumns.add(columnName);
            } else {
                result.put(columnName, CONFIRMATION_REQUIRED);
            }
        }

        List<Map<String, String>> rows = parsedExcelFile.rows() == null ? List.of() : parsedExcelFile.rows();
        for (Map<String, String> row : rows) {
            if (pendingColumns.isEmpty()) {
                break;
            }
            inspectRow(row, pendingColumns, result);
        }

        return Collections.unmodifiableMap(new LinkedHashMap<>(result));
    }

    private ParsedExcelFile loadParsedExcelFile(String taskId) {
        DataProcessingAgentState state = stateRepository.findByTaskId(taskId)
                .orElseThrow(() -> new IllegalArgumentException("任务状态不存在: " + taskId));
        if (state.parsedFileRef() == null || state.parsedFileRef().isBlank()) {
            throw new IllegalStateException("任务缺少 parsedFileRef: " + taskId);
        }
        return parsedExcelFileRepository.findByRef(state.parsedFileRef());
    }

    private Set<String> normalizeColumns(List<String> columnNames) {
        Set<String> result = new LinkedHashSet<>();
        for (String columnName : columnNames) {
            if (columnName == null || columnName.isBlank()) {
                throw new IllegalArgumentException("待检查列名不能包含空值。");
            }
            result.add(columnName.trim());
        }
        return result;
    }

    private void inspectRow(
            Map<String, String> row,
            Set<String> pendingColumns,
            Map<String, String> result
    ) {
        Iterator<String> iterator = pendingColumns.iterator();
        while (iterator.hasNext()) {
            String columnName = iterator.next();
            String value = row == null ? null : row.get(columnName);
            if (value == null || value.isBlank()) {
                result.put(columnName, CONFIRMATION_REQUIRED);
                iterator.remove();
            }
        }
    }
}
