package com.example.dataprocess.infrastructure.tool;

import com.example.dataprocess.domain.model.HeaderAlias;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 表头别名工具。
 */
@Component
public class HeaderAliasTool {

    public List<HeaderAlias> lookupHeaderAliases(List<String> headers) {
        List<HeaderAlias> aliases = new ArrayList<>();
        for (String header : headers) {
            aliases.add(new HeaderAlias(header, header, "en"));
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
