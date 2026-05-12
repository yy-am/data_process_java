package com.example.dataprocess.domain.model;

import java.util.List;
import java.util.Map;

/**
 * 标准化后的输入快照。
 *
 * @param taskId 当前任务 ID
 * @param inputType 输入来源类型，例如 table-import、excel-import
 * @param normalizedHeaders 标准化后的表头列表
 * @param sampleRows 用于识别和生成规则的样例数据
 */
public record InputSnapshot(
        String taskId,
        String inputType,
        List<String> normalizedHeaders,
        List<Map<String, String>> sampleRows
) {
}
