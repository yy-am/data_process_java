package com.example.dataprocess.domain.model;

import java.util.List;

/**
 * 从 DSL 生成上下文产出的确定性加工计划。
 *
 * <p>该对象只承载目标列表达式级 SQL 片段，不包含 INSERT、FROM、WHERE 等完整 SQL 结构。</p>
 *
 * @param dslVersion DSL 结构版本
 * @param taskId 当前任务 ID
 * @param presetTemplateCode 已识别出的预置用户模板编码
 * @param standardTemplateCode 已匹配的标准模板编码
 * @param columns 目标列加工计划
 */
public record ProcessingPlanDsl(
        String dslVersion,
        String taskId,
        String presetTemplateCode,
        String standardTemplateCode,
        List<ProcessingPlanColumn> columns
) {
}
