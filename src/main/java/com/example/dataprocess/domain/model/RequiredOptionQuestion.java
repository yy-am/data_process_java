package com.example.dataprocess.domain.model;

import java.util.List;

/**
 * 需要用户从候选项中选择的确认问题。
 *
 * @param fieldCode 待确认字段编码
 * @param fieldName 待确认字段名称
 * @param question 给前端展示的提问文案
 * @param options 当前字段允许选择的选项列表
 */
public record RequiredOptionQuestion(
        String fieldCode,
        String fieldName,
        String question,
        List<OptionItem> options
) {
}
