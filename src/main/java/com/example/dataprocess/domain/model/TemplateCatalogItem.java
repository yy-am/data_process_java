package com.example.dataprocess.domain.model;

import java.util.List;

/**
 * 模板目录项。
 *
 * @param templateCode 模板编码
 * @param sceneCode 模板所属场景
 * @param countryCode 模板适用国家或区域
 * @param targetFields 模板要求输出的目标字段列表
 */
public record TemplateCatalogItem(
        String templateCode,
        String sceneCode,
        String countryCode,
        List<String> targetFields
) {
}
