package com.example.dataprocess.domain.model;

import java.util.List;

/**
 * Structured recognition result for rule source bindings.
 *
 * @param taskId current task id
 * @param presetTemplateCode current preset template code
 * @param items binding recognition items
 */
public record VagueBindingRecoResult(
        String taskId,
        String presetTemplateCode,
        List<VagueBindingRecoItem> items
) {
}
