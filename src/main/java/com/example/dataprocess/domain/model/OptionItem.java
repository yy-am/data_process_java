package com.example.dataprocess.domain.model;

/**
 * 用户可选项定义。
 *
 * @param code 选项编码
 * @param value 选项值
 */
public record OptionItem(
        String code,
        String value
) {
}
