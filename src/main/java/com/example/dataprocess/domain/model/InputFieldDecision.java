package com.example.dataprocess.domain.model;

/**
 * 用户确认后的手工输入字段结果。
 *
 * @param fieldCode 字段编码
 * @param inputValue 用户填写的内容
 */
public record InputFieldDecision(
        String fieldCode,
        String inputValue
) {
}
