package com.example.dataprocess.domain.model;

import java.util.List;

/**
 * 标准模板定义。
 *
 * @param standardTemplateCode 标准模板编码
 * @param sceneCode 所属业务场景编码
 * @param countryCode 所属国家编码
 * @param standardColumns 标准模板列清单
 */
public record StandardTemplateDefinition(
        String standardTemplateCode,
        String sceneCode,
        String countryCode,
        List<String> standardColumns
) {
}
