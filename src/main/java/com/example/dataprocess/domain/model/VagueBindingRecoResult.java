package com.example.dataprocess.domain.model;

import java.util.List;

/**
 * 规则源字段绑定识别结果。
 *
 * @param taskId 当前任务 ID
 * @param presetTemplateCode 当前预置用户模板编码
 * @param items 字段绑定识别项列表
 */
public record VagueBindingRecoResult(
        String taskId,
        String presetTemplateCode,
        List<VagueBindingRecoItem> items
) {
}
