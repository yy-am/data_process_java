package com.example.dataprocess.domain.model;

/**
 * 模板识别结果。
 *
 * @param presetTemplateCode 已识别出的预置用户模板编码
 * @param standardTemplateCode 已匹配的标准模板编码
 * @param sceneCode 已识别出的业务场景编码
 * @param companyCode 已识别出的公司编码
 * @param confidence 模板识别置信度
 * @param needUserConfirm 模板识别结果本身是否仍需要人工复核
 * @param reason 模型给出的识别原因说明
 */
public record TemplateRecognitionResult(
        String presetTemplateCode,
        String standardTemplateCode,
        String sceneCode,
        String companyCode,
        Double confidence,
        Boolean needUserConfirm,
        String reason
) {
}
