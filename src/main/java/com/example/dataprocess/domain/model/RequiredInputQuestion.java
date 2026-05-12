package com.example.dataprocess.domain.model;

/**
 * 需要用户手工输入的确认问题。
 *
 * @param fieldCode 待补充字段编码
 * @param fieldName 待补充字段名称
 * @param question 给前端展示的提问文案
 * @param hint 输入提示或示例
 */
public record RequiredInputQuestion(
        String fieldCode,
        String fieldName,
        String question,
        String hint
) {
}
