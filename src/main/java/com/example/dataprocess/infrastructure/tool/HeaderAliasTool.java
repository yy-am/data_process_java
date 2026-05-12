package com.example.dataprocess.infrastructure.tool;

import com.example.dataprocess.domain.model.HeaderAlias;
import com.example.dataprocess.infrastructure.runtime.SkillExecutionStateHolder;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 表头别名工具，负责补充源字段的规范化候选。
 */
@Component
public class HeaderAliasTool {

    private final SkillExecutionStateHolder stateHolder;

    public HeaderAliasTool(SkillExecutionStateHolder stateHolder) {
        this.stateHolder = stateHolder;
    }

    @Tool(name = "headerAliasTool", description = "Read normalized header aliases for the current task source headers.")
    public List<HeaderAlias> lookupCurrentHeaderAliases() {
        return lookupHeaderAliases(stateHolder.getRequiredCurrentState().sourceHeaders());
    }

    public List<HeaderAlias> lookupHeaderAliases(List<String> headers) {
        List<HeaderAlias> aliases = new ArrayList<>();
        for (String header : headers) {
            aliases.add(new HeaderAlias(header, header, "en"));
            if ("invoice_no".equalsIgnoreCase(header)) {
                aliases.add(new HeaderAlias("invoice-number", "invoice_no", "zh-CN"));
            }
            if ("amount".equalsIgnoreCase(header)) {
                aliases.add(new HeaderAlias("amount-value", "amount", "zh-CN"));
            }
        }
        return aliases;
    }
}
