package com.example.dataprocess.domain.model;

import java.util.List;

/**
 * 单个规则源字段的模糊绑定识别结果。
 *
 * @param targetColumn 该规则项生成的目标列名
 * @param ruleType 确定的规则类型，例如 DIRECT_MAPPING 或 CASE_WHEN
 * @param sourceColumn 规则项中声明的源字段
 * @param status 绑定识别状态
 * @param selectedHeader 绑定关系明确时选中的上传表头
 * @param candidateHeaders 需要用户确认时提供的候选上传表头
 * @param reason 识别原因说明
 */
public record VagueBindingRecoItem(
        String targetColumn,
        String ruleType,
        String sourceColumn,
        VagueBindingRecoStatus status,
        String selectedHeader,
        List<String> candidateHeaders,
        String reason
) {
}
