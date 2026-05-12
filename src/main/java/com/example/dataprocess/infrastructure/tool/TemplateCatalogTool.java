package com.example.dataprocess.infrastructure.tool;

import com.example.dataprocess.domain.model.InputSnapshot;
import com.example.dataprocess.domain.model.TemplateCatalogItem;
import com.example.dataprocess.infrastructure.runtime.SkillExecutionStateHolder;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 模板目录工具，负责提供当前场景下允许识别的模板集合。
 */
@Component
public class TemplateCatalogTool {

    private final SkillExecutionStateHolder stateHolder;

    public TemplateCatalogTool(SkillExecutionStateHolder stateHolder) {
        this.stateHolder = stateHolder;
    }

    @Tool(name = "templateCatalogTool", description = "Read the allowed template catalog entries for the current scene.")
    public List<TemplateCatalogItem> readCurrentTemplateCatalog() {
        var currentState = stateHolder.getRequiredCurrentState();
        InputSnapshot inputSnapshot = currentState.inputSnapshot();
        String inputType = inputSnapshot == null ? currentState.inputType() : inputSnapshot.inputType();
        return readTemplateCatalog(inputType);
    }

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
