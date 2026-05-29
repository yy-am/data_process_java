package com.example.dataprocess.domain.model;

import java.util.List;

/**
 * 预置用户模板定义。
 *
 * @param presetTemplateCode 预置用户模板编码
 * @param presetTemplateName 预置用户模板名称
 * @param sceneCode 所属业务场景编码
 * @param companyCode 所属公司编码
 * @param standardTemplateCode 对应标准模板编码
 * @param sourceColumns 预置用户模板包含的源列清单
 */
public record PresetUserTemplateDefinition(
        String presetTemplateCode,
        String presetTemplateName,
        String sceneCode,
        String companyCode,
        String standardTemplateCode,
        List<String> sourceColumns
) {
}
