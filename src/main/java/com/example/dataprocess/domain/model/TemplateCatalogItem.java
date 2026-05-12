package com.example.dataprocess.domain.model;

import java.util.List;

/**
 * 模板目录项对象。
 */
public record TemplateCatalogItem(
        String templateCode,
        String sceneCode,
        String countryCode,
        List<String> targetFields
) {
}
