package com.example.dataprocess.infrastructure.tool;

import com.example.dataprocess.domain.model.TemplateCatalogItem;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 模板目录工具。
 */
@Component
public class TemplateCatalogTool {

    public List<TemplateCatalogItem> readTemplateCatalog(String sceneCode) {
        return List.of(
                new TemplateCatalogItem(
                        "invoice-standard",
                        sceneCode,
                        "CN",
                        List.of("A", "period", "D")
                ),
                new TemplateCatalogItem(
                        "invoice-simple",
                        sceneCode,
                        "CN",
                        List.of("A", "period")
                )
        );
    }
}
