package com.example.dataprocess.domain.model;

import java.util.List;

/**
 * 模板识别结果对象。
 */
public record TemplateRecognitionResult(
        String templateCode,
        String sceneCode,
        String countryCode,
        Double confidence,
        Boolean needUserConfirm,
        String reason,
        List<String> unresolvedTargetFields
) {
}
