package com.example.dataprocess.infrastructure.service;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Markdown 资源读取服务。
 *
 * <p>当前项目中的模板目录、规则目录和提示词文档都放在 resources 目录下。
 * 这里统一负责按 UTF-8 读取资源正文，避免每个服务重复写文件读取逻辑。</p>
 */
@Service
public class MarkdownResourceService {

    /**
     * 读取指定类路径资源的 UTF-8 正文。
     */
    public String readUtf8Resource(String resourcePath) {
        ClassPathResource resource = new ClassPathResource(resourcePath);
        if (!resource.exists()) {
            throw new IllegalStateException("未找到资源文件: " + resourcePath);
        }
        try {
            return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("读取资源文件失败: " + resourcePath, ex);
        }
    }
}
