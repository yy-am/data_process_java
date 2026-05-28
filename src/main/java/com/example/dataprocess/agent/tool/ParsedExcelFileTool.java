package com.example.dataprocess.agent.tool;

import com.example.dataprocess.agent.model.ColumnNullInspectionResult;
import com.example.dataprocess.agent.model.ParsedExcelFile;
import com.example.dataprocess.agent.model.ParsedExcelSummary;
import com.example.dataprocess.agent.repository.ParsedExcelFileRepository;
import com.example.dataprocess.interfaces.restful.request.DataProcessingTaskRequest;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tool facade for parsed Excel files.
 */
@Component
public class ParsedExcelFileTool {

    private static final int DEFAULT_SAMPLE_ROW_LIMIT = 5;

    private final ParsedExcelFileRepository repository;

    public ParsedExcelFileTool(ParsedExcelFileRepository repository) {
        this.repository = repository;
    }

    public ParsedExcelFile parseAndStore(
            MultipartFile excelFile,
            String sheetName,
            Integer sheetIndex,
            Integer sampleRowLimit
    ) {
        if (excelFile == null || excelFile.isEmpty()) {
            throw new IllegalArgumentException("Excel 文件不能为空。");
        }

        try (InputStream inputStream = excelFile.getInputStream()) {
            ParsedExcelFile parsedExcelFile = parse(inputStream, sheetName, sheetIndex, sampleRowLimit);
            return repository.save(parsedExcelFile);
        } catch (IOException ex) {
            throw new IllegalStateException("读取上传 Excel 文件失败。", ex);
        }
    }

    public ParsedExcelFile storeTaskRequest(DataProcessingTaskRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("DataProcessingTaskRequest 不能为空。");
        }
        ParsedExcelFile parsedExcelFile = new ParsedExcelFile(
                "parsed-task-" + request.taskId(),
                request.taskId(),
                request.inputType(),
                "request-payload",
                List.copyOf(request.sourceHeaders()),
                request.sampleRows().stream().map(Map::copyOf).toList(),
                request.sampleRows().stream().map(Map::copyOf).toList()
        );
        return repository.save(parsedExcelFile);
    }

    public ParsedExcelSummary readParsedExcelSummary(String parsedFileRef) {
        ParsedExcelFile parsedExcelFile = repository.findByRef(parsedFileRef);
        return new ParsedExcelSummary(
                parsedExcelFile.parsedFileRef(),
                parsedExcelFile.inputType(),
                parsedExcelFile.sheetName(),
                parsedExcelFile.sourceHeaders(),
                parsedExcelFile.sampleRows(),
                parsedExcelFile.parsedFileRef()
        );
    }

    public List<ColumnNullInspectionResult> inspectExcelColumnNulls(
            String parsedFileRef,
            List<String> actualColumns
    ) {
        ParsedExcelFile parsedExcelFile = repository.findByRef(parsedFileRef);
        List<ColumnNullInspectionResult> results = new ArrayList<>();
        for (String actualColumn : actualColumns) {
            boolean hasBlankValue = parsedExcelFile.rows().stream()
                    .anyMatch(row -> {
                        String value = row.get(actualColumn);
                        return value == null || value.isBlank();
                    });
            results.add(new ColumnNullInspectionResult(actualColumn, hasBlankValue));
        }
        return List.copyOf(results);
    }

    private ParsedExcelFile parse(
            InputStream inputStream,
            String sheetName,
            Integer sheetIndex,
            Integer sampleRowLimit
    ) {
        int resolvedSampleRowLimit = sampleRowLimit == null || sampleRowLimit < 1
                ? DEFAULT_SAMPLE_ROW_LIMIT
                : sampleRowLimit;

        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = resolveSheet(workbook, sheetName, sheetIndex);
            return extractSheetContent(sheet, resolvedSampleRowLimit);
        } catch (IOException ex) {
            throw new IllegalStateException("解析 Excel 文件失败。", ex);
        }
    }

    private Sheet resolveSheet(Workbook workbook, String sheetName, Integer sheetIndex) {
        if (sheetName != null && !sheetName.isBlank()) {
            Sheet byName = workbook.getSheet(sheetName);
            if (byName == null) {
                throw new IllegalArgumentException("指定工作表不存在: " + sheetName);
            }
            return byName;
        }

        int resolvedSheetIndex = sheetIndex == null ? 0 : sheetIndex;
        if (resolvedSheetIndex < 0 || resolvedSheetIndex >= workbook.getNumberOfSheets()) {
            throw new IllegalArgumentException("工作表下标越界: " + resolvedSheetIndex);
        }
        return workbook.getSheetAt(resolvedSheetIndex);
    }

    private ParsedExcelFile extractSheetContent(Sheet sheet, int sampleRowLimit) {
        DataFormatter formatter = new DataFormatter();
        Row headerRow = sheet.getRow(sheet.getFirstRowNum());
        if (headerRow == null) {
            throw new IllegalArgumentException("Excel 缺少表头行。");
        }

        List<String> headers = extractHeaders(headerRow, formatter);
        if (headers.isEmpty()) {
            throw new IllegalArgumentException("Excel 表头为空。");
        }

        List<Map<String, String>> rows = new ArrayList<>();
        int lastRowNum = sheet.getLastRowNum();
        for (int rowIndex = headerRow.getRowNum() + 1; rowIndex <= lastRowNum; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null || isBlankRow(row, headers.size(), formatter)) {
                continue;
            }
            rows.add(extractRow(row, headers, formatter));
        }

        List<Map<String, String>> sampleRows = rows.stream()
                .limit(sampleRowLimit)
                .map(Map::copyOf)
                .toList();

        return new ParsedExcelFile(
                "parsed-" + UUID.randomUUID(),
                null,
                "excel-import",
                sheet.getSheetName(),
                List.copyOf(headers),
                sampleRows,
                rows.stream().map(Map::copyOf).toList()
        );
    }

    private List<String> extractHeaders(Row headerRow, DataFormatter formatter) {
        List<String> headers = new ArrayList<>();
        short lastCellNum = headerRow.getLastCellNum();
        if (lastCellNum <= 0) {
            return headers;
        }
        for (int columnIndex = 0; columnIndex < lastCellNum; columnIndex++) {
            Cell headerCell = headerRow.getCell(columnIndex);
            String header = headerCell == null ? "" : formatter.formatCellValue(headerCell).trim();
            headers.add(header.isEmpty() ? "column_" + (columnIndex + 1) : header);
        }
        return headers;
    }

    private boolean isBlankRow(Row row, int headerSize, DataFormatter formatter) {
        for (int columnIndex = 0; columnIndex < headerSize; columnIndex++) {
            Cell cell = row.getCell(columnIndex);
            if (cell != null && !formatter.formatCellValue(cell).trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private Map<String, String> extractRow(Row row, List<String> headers, DataFormatter formatter) {
        Map<String, String> values = new LinkedHashMap<>();
        for (int columnIndex = 0; columnIndex < headers.size(); columnIndex++) {
            Cell cell = row.getCell(columnIndex);
            values.put(headers.get(columnIndex), cell == null ? "" : formatter.formatCellValue(cell).trim());
        }
        return values;
    }
}
