package com.example.dataprocess.domain.model;

/**
 * 用户可选项定义。
 *
 * @param code 选项编码
 * @param label 选项展示文案
 */
public record OptionItem(
        String code,
        String label
) {
}
