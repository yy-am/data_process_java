package com.example.dataprocess.domain.model;

/**
 * Template recognition result.
 *
 * @param presetTemplateCode recognized preset template code
 * @param standardTemplateCode matched standard template code
 * @param sceneCode recognized scene code
 * @param countryCode recognized country or region code
 * @param confidence recognition confidence
 * @param needUserConfirm whether template recognition itself still needs manual review
 * @param reason model explanation for the recognition result
 */
public record TemplateRecognitionResult(
        String presetTemplateCode,
        String standardTemplateCode,
        String sceneCode,
        String countryCode,
        Double confidence,
        Boolean needUserConfirm,
        String reason
) {
}
