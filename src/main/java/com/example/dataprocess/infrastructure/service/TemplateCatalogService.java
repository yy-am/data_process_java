package com.example.dataprocess.infrastructure.service;

import com.example.dataprocess.domain.model.TemplateCatalogItem;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 模板目录服务。
 *
 * <p>一期先用显式内置目录承载模板事实，避免在代码里埋隐式推断。
 * 后续如果切换到配置中心或文档仓储，只需要替换这一层。</p>
 *
 * <p>当前模板目录的人工维护镜像文档位于：
 * {@code src/main/resources/catalog/TEMPLATE_CATALOG.md}。
 * 该文档用于评审和维护，不作为运行时自动加载源。</p>
 */
@Service
public class TemplateCatalogService {

    /**
     * 按场景读取允许识别的模板目录。
     */
    public List<TemplateCatalogItem> readTemplateCatalog(String sceneCode) {
        return List.of(
                new TemplateCatalogItem("invoice-standard", sceneCode, "CN", List.of("A", "period", "D")),
                new TemplateCatalogItem("invoice-simple", sceneCode, "CN", List.of("A", "period"))
        );
    }

    /**
     * 按模板编码读取模板目录项。
     */
    public TemplateCatalogItem getRequiredTemplate(String sceneCode, String templateCode) {
        return readTemplateCatalog(sceneCode).stream()
                .filter(item -> item.templateCode().equals(templateCode))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未找到模板目录项: " + templateCode));
    }
}
