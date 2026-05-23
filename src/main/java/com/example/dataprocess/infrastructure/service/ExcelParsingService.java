package com.example.dataprocess.infrastructure.service;

import com.example.dataprocess.domain.model.ParsedExcelContent;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Excel 解析服务。
 *
 * <p>一期只抽取模板识别和 DSL 生成所需的列名与少量样本行，
 * 不把全量 Excel 数据长期驻留在内存中。</p>
 */
@Service
public class ExcelParsingService {

    private static final int DEFAULT_SAMPLE_ROW_LIMIT = 5;

    /**
     * 解析 Excel 文件并提取列名与少量样本数据。
     */
    public ParsedExcelContent parse(MultipartFile excelFile, String sheetName, Integer sheetIndex, Integer sampleRowLimit) {
        if (excelFile == null || excelFile.isEmpty()) {
            throw new IllegalArgumentException("Excel 文件不能为空。");
        }

        int resolvedSampleRowLimit = sampleRowLimit == null || sampleRowLimit < 1
                ? DEFAULT_SAMPLE_ROW_LIMIT
                : sampleRowLimit;

        try (InputStream inputStream = excelFile.getInputStream();
             Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = resolveSheet(workbook, sheetName, sheetIndex);
            return extractSheetContent(sheet, resolvedSampleRowLimit);
        } catch (IOException ex) {
            throw new IllegalStateException("读取上传的 Excel 文件失败。", ex);
        }
    }

    /**
     * 根据工作表名称或下标解析目标工作表。
     */
    private Sheet resolveSheet(Workbook workbook, String sheetName, Integer sheetIndex) {
        if (sheetName != null && !sheetName.isBlank()) {
            Sheet byName = workbook.getSheet(sheetName);
            if (byName == null) {
                throw new IllegalArgumentException("未找到指定工作表: " + sheetName);
            }
            return byName;
        }

        int resolvedSheetIndex = sheetIndex == null ? 0 : sheetIndex;
        if (resolvedSheetIndex < 0 || resolvedSheetIndex >= workbook.getNumberOfSheets()) {
            throw new IllegalArgumentException("工作表下标越界: " + resolvedSheetIndex);
        }
        return workbook.getSheetAt(resolvedSheetIndex);
    }

    /**
     * 从工作表中抽取表头和样本行。
     */
    private ParsedExcelContent extractSheetContent(Sheet sheet, int sampleRowLimit) {
        DataFormatter formatter = new DataFormatter();
        Row headerRow = sheet.getRow(sheet.getFirstRowNum());
        if (headerRow == null) {
            throw new IllegalArgumentException("Excel 工作表缺少表头行。");
        }

        List<String> headers = extractHeaders(headerRow, formatter);
        if (headers.isEmpty()) {
            throw new IllegalArgumentException("Excel 表头为空。");
        }

        List<Map<String, String>> sampleRows = new ArrayList<>();
        int lastRowNum = sheet.getLastRowNum();
        for (int rowIndex = headerRow.getRowNum() + 1; rowIndex <= lastRowNum && sampleRows.size() < sampleRowLimit; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null || isBlankRow(row, headers.size(), formatter)) {
                continue;
            }
            sampleRows.add(extractRow(row, headers, formatter));
        }

        return new ParsedExcelContent(
                "excel-import",
                sheet.getSheetName(),
                List.copyOf(headers),
                sampleRows.stream().map(Map::copyOf).toList()
        );
    }

    /**
     * 读取表头行，并为匿名空表头补上稳定列名。
     */
    private List<String> extractHeaders(Row headerRow, DataFormatter formatter) {
        List<String> headers = new ArrayList<>();
        short lastCellNum = headerRow.getLastCellNum();
        if (lastCellNum <= 0) {
            return headers;
        }

        for (int columnIndex = 0; columnIndex < lastCellNum; columnIndex++) {
            Cell headerCell = headerRow.getCell(columnIndex);
            String header = headerCell == null ? "" : formatter.formatCellValue(headerCell).trim();
            if (header.isEmpty()) {
                header = "column_" + (columnIndex + 1);
            }
            headers.add(header);
        }
        return headers;
    }

    /**
     * 判断一行是否为空白行。
     */
    private boolean isBlankRow(Row row, int headerSize, DataFormatter formatter) {
        for (int columnIndex = 0; columnIndex < headerSize; columnIndex++) {
            Cell cell = row.getCell(columnIndex);
            if (cell != null && !formatter.formatCellValue(cell).trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * 按表头顺序提取单行数据。
     */
    private Map<String, String> extractRow(Row row, List<String> headers, DataFormatter formatter) {
        Map<String, String> values = new LinkedHashMap<>();
        for (int columnIndex = 0; columnIndex < headers.size(); columnIndex++) {
            Cell cell = row.getCell(columnIndex);
            values.put(headers.get(columnIndex), cell == null ? "" : formatter.formatCellValue(cell).trim());
        }
        return values;
    }
}
