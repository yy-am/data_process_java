package com.example.dataprocess.domain.model;

import java.util.List;

/**
 * DSL 生成上下文。
 *
 * <p>这是字段绑定识别、用户确认与 DSL 生成之间的边界对象。它按目标列聚合，
 * 让后续 DSL 生成节点可以逐个目标列生成确定的加工计划。</p>
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
