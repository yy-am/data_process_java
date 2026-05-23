package com.example.dataprocess.infrastructure.service;

import com.example.dataprocess.domain.model.RuleContext;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 规则上下文服务。
 *
 * <p>一期先用受控的静态规则摘要和 DSL 骨架承载后端规则知识。
 * 这里的内容全部显式声明，不从隐藏来源拼装。</p>
 */
@Service
public class RuleContextService {

    /**
     * 读取指定模板的规则摘要和 DSL 骨架。
     */
    public RuleContext loadRuleContext(String templateCode) {
        Map<String, Object> skeleton = new LinkedHashMap<>();
        skeleton.put("templateCode", templateCode);
        skeleton.put("mappings", List.of(
                Map.of(
                        "targetField", "A",
                        "sourceFields", List.of("<用户确认后的源字段>")
                )
        ));
        skeleton.put("constants", Map.of(
                "period", "<用户确认后的枚举值>",
                "D", "<用户确认后的输入值>"
        ));

        return new RuleContext(
                templateCode,
                "字段 A 来自用户确认的源字段，period 与 D 由用户确认值写入 constants。",
                skeleton
        );
    }
}
