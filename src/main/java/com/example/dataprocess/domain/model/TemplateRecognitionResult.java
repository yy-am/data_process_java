package com.example.dataprocess.domain.model;

import java.util.List;

/**
 * 模板识别结果。
 *
 * @param templateCode 识别出的模板编码
 * @param sceneCode 识别出的场景编码
 * @param countryCode 识别出的国家或区域编码
 * @param confidence 识别结果置信度
 * @param needUserConfirm 是否仍需用户补充确认
 * @param reason 模型给出的识别理由
 * @param unresolvedTargetFields 尚未确定映射关系的目标字段
 */
public record TemplateRecognitionResult(
        String templateCode,
        String sceneCode,
        String countryCode,
        Double confidence,
        Boolean needUserConfirm,
        String reason,
        List<String> unresolvedTargetFields
) {
}
