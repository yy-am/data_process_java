package com.example.dataprocess.domain.model;

import java.util.List;

/**
 * 字段映射确认项。
 *
 * <p>这个类同时表示两种状态：
 * 1. 后端生成给前端的待确认题目
 * 2. 用户确认后的映射结果
 *
 * <p>区别只在于 {@code selectedSourceFields} 是否已经有值。</p>
 *
 * @param targetFieldCode 目标字段编码
 * @param targetFieldName 目标字段名称
 * @param question 展示给用户的问题文案
 * @param candidates 可供选择的源字段候选项
 * @param selectedSourceFields 用户最终选中的源字段列表；待确认阶段可为空列表
 */
public record MappingConfirmation(
        String targetFieldCode,
        String targetFieldName,
        String question,
        List<SourceFieldCandidate> candidates,
        List<String> selectedSourceFields
) {
}
