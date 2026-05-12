package com.example.dataprocess.domain.model;

/**
 * 最终 DSL 结果对象。
 */
public record FinalDsl(
        String templateCode,
        String dslContent,
        String reason
) {
}
