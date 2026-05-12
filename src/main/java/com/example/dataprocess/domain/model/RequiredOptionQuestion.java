package com.example.dataprocess.domain.model;

import java.util.List;

/**
 * 需要用户从候选项中选择的确认问题。
 */
public record RequiredOptionQuestion(
        String fieldCode,
        String fieldName,
        String question,
        List<OptionItem> options
) {
}
