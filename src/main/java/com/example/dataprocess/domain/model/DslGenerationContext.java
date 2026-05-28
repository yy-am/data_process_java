package com.example.dataprocess.domain.model;

import java.util.List;

/**
 * DSL 生成上下文。
 *
 * <p>这是字段绑定识别、用户确认与目标列表达式 SQL 片段生成之间的边界对象。
 * 它不包含目标结果表、来源弹性域表、WHERE 条件等完整 SQL 信息。</p>
 *
 * @param taskId 当前任务 ID
 * @param presetTemplateCode 已识别出的预置用户模板编码
 * @param standardTemplateCode 已匹配的标准模板编码
 * @param targetColumns 按目标列聚合后的 DSL 生成上下文
 */
public record DslGenerationContext(
        String taskId,
        String presetTemplateCode,
        String standardTemplateCode,
        List<TargetColumnGenerationContext> targetColumns
) {
}
