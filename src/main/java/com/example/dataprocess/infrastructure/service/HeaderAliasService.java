package com.example.dataprocess.infrastructure.service;

import com.example.dataprocess.domain.model.HeaderAlias;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 表头别名服务。
 *
 * <p>一期先提供少量显式别名规则，所有规则都直接写在代码里，避免隐藏推断。</p>
 */
@Service
public class HeaderAliasService {

    /**
     * 根据当前表头列表补充可用于模板识别的别名信息。
     */
    public List<HeaderAlias> lookupHeaderAliases(List<String> headers) {
        List<HeaderAlias> aliases = new ArrayList<>();
        for (String header : headers) {
            aliases.add(new HeaderAlias(header, header, "default"));
            if ("invoice_no".equalsIgnoreCase(header)) {
                aliases.add(new HeaderAlias("invoice-number", "invoice_no", "zh-CN"));
            }
            if ("amount".equalsIgnoreCase(header)) {
                aliases.add(new HeaderAlias("amount-value", "amount", "zh-CN"));
            }
        }
        return aliases;
    }
}
