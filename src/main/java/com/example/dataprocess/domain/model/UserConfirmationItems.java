package com.example.dataprocess.domain.model;

import java.util.List;

/**
 * 一轮用户确认项集合。
 */
public record UserConfirmationItems(
        String taskId,
        String templateCode,
        List<UnclearMappingQuestion> unclearMappings,
        List<RequiredOptionQuestion> requiredOptionFields,
        List<RequiredInputQuestion> requiredInputFields
) {
}
