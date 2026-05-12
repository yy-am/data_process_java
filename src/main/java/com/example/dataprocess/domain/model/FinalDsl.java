package com.example.dataprocess.domain.model;

/**
 * 最终产出的 DSL 结果。
 *
 * @param templateCode 选中的模板编码
 * @param dslContent DSL 的完整文本或 JSON 字符串
 * @param reason 产出该 DSL 的原因说明，便于追踪模型决策
 */
public record FinalDsl(
        String templateCode,
        String dslContent,
        String reason
) {
}
