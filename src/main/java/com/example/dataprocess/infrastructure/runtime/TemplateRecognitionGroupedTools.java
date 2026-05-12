package com.example.dataprocess.infrastructure.runtime;

import com.example.dataprocess.domain.model.HeaderAlias;
import com.example.dataprocess.domain.model.InputSnapshot;
import com.example.dataprocess.domain.model.TemplateCatalogItem;
import com.example.dataprocess.infrastructure.tool.HeaderAliasTool;
import com.example.dataprocess.infrastructure.tool.InputSnapshotTool;
import com.example.dataprocess.infrastructure.tool.TemplateCatalogTool;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * template-recognition 技能可见工具分组。
 */
@Component
public class TemplateRecognitionGroupedTools {

    private final SkillExecutionStateHolder stateHolder;
    private final InputSnapshotTool inputSnapshotTool;
    private final HeaderAliasTool headerAliasTool;
    private final TemplateCatalogTool templateCatalogTool;

    public TemplateRecognitionGroupedTools(
            SkillExecutionStateHolder stateHolder,
            InputSnapshotTool inputSnapshotTool,
            HeaderAliasTool headerAliasTool,
            TemplateCatalogTool templateCatalogTool
    ) {
        this.stateHolder = stateHolder;
        this.inputSnapshotTool = inputSnapshotTool;
        this.headerAliasTool = headerAliasTool;
        this.templateCatalogTool = templateCatalogTool;
    }

    @Tool(name = "inputSnapshotTool", description = "Load the normalized input snapshot for the current data processing task.")
    public InputSnapshot loadInputSnapshot() {
        return inputSnapshotTool.loadInputSnapshot(stateHolder.getRequiredCurrentState().toTaskSession());
    }

    @Tool(name = "templateCatalogTool", description = "Read the allowed template catalog entries for the current scene.")
    public List<TemplateCatalogItem> readTemplateCatalog() {
        InputSnapshot snapshot = loadInputSnapshot();
        return templateCatalogTool.readTemplateCatalog(snapshot.inputType());
    }

    @Tool(name = "headerAliasTool", description = "Read normalized header aliases for the current task source headers.")
    public List<HeaderAlias> lookupHeaderAliases() {
        return headerAliasTool.lookupHeaderAliases(stateHolder.getRequiredCurrentState().sourceHeaders());
    }

}
