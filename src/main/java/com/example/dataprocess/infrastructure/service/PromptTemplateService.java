package com.example.dataprocess.infrastructure.service;

import org.springframework.stereotype.Service;

/**
 * 提示词模板加载服务。
 *
 * <p>当前约定所有运行时提示词都放在 {@code resources/prompts} 目录中，
 * 并通过固定 Markdown 标题和代码块结构进行读取。</p>
 */
@Service
public class PromptTemplateService {

    private static final String HEADING_PREFIX = "## ";
    private static final String CODE_FENCE = "```";

    private final MarkdownResourceService markdownResourceService;

    public PromptTemplateService(MarkdownResourceService markdownResourceService) {
        this.markdownResourceService = markdownResourceService;
    }

    /**
     * 从指定 Markdown 资源中读取某个标题下的第一个代码块内容。
     */
    public String loadPromptSection(String resourcePath, String sectionTitle) {
        String markdown = markdownResourceService.readUtf8Resource(resourcePath);
        String heading = HEADING_PREFIX + sectionTitle;

        int headingStart = markdown.indexOf(heading);
        if (headingStart < 0) {
            throw new IllegalStateException("提示词资源缺少标题 " + sectionTitle + "，文件: " + resourcePath);
        }

        int codeFenceStart = markdown.indexOf(CODE_FENCE, headingStart);
        if (codeFenceStart < 0) {
            throw new IllegalStateException("提示词资源缺少代码块开始标记，文件: " + resourcePath + "，标题: " + sectionTitle);
        }

        int firstLineBreak = markdown.indexOf('\n', codeFenceStart);
        if (firstLineBreak < 0) {
            throw new IllegalStateException("提示词代码块格式不完整，文件: " + resourcePath + "，标题: " + sectionTitle);
        }

        int codeFenceEnd = markdown.indexOf(CODE_FENCE, firstLineBreak + 1);
        if (codeFenceEnd < 0) {
            throw new IllegalStateException("提示词资源缺少代码块结束标记，文件: " + resourcePath + "，标题: " + sectionTitle);
        }

        return markdown.substring(firstLineBreak + 1, codeFenceEnd).trim();
    }

}
