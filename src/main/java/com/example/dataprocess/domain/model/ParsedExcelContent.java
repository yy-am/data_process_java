package com.example.dataprocess.domain.model;

import java.util.List;
import java.util.Map;

/**
 * Excel 文件解析后的标准化内容。
 *
 * @param inputType 输入来源类型，当前固定为 excel-import
 * @param sheetName 实际解析的工作表名称
 * @param sourceHeaders 从表头行提取出的源字段列表
 * @param sampleRows 从数据区提取出的样例数据行
 */
public record ParsedExcelContent(
        String inputType,
        String sheetName,
        List<String> sourceHeaders,
        List<Map<String, String>> sampleRows
) {
}
