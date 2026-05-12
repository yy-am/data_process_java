package com.example.dataprocess.infrastructure.runtime;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 技能运行时配置属性。
 */
@ConfigurationProperties(prefix = "app.spring-ai-alibaba")
public record SkillRuntimeProperties(
        boolean enabled,
        String skillsClasspathPath
) {
}
