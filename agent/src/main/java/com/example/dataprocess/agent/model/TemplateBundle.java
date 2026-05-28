package com.example.dataprocess.agent.model;

import com.example.dataprocess.domain.model.PresetUserTemplateDefinition;
import com.example.dataprocess.domain.model.ProcessingRule;
import com.example.dataprocess.domain.model.StandardTemplateDefinition;

/**
 * Template and rule context loaded for a recognized preset template.
 */
public record TemplateBundle(
        PresetUserTemplateDefinition presetTemplate,
        StandardTemplateDefinition standardTemplate,
        ProcessingRule processingRule
) {
}
