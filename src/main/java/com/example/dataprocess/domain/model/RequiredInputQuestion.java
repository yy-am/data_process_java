package com.example.dataprocess.domain.model;

/**
 * 需要用户手工输入的确认问题。
 */
public record RequiredInputQuestion(
        String fieldCode,
        String fieldName,
        String question,
        String hint
) {
}
