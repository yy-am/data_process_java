package com.example.dataprocess.domain.model;

import java.util.Map;

/**
 * 规则草拟阶段使用的受控规则上下文。
 *
 * @param templateCode 当前模板编码
 * @param ruleSummary 当前模板的规则摘要
 * @param dslSkeleton 当前模板允许填充的 DSL 骨架
 */
public record RuleContext(
        String templateCode,
        String ruleSummary,
        Map<String, Object> dslSkeleton
) {
}
