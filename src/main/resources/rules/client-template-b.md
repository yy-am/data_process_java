# 加工规则目录

## presetTemplateCode: client-template-b

- presetTemplateName: 客户模板B
- standardTemplateCode: tax-standard-cn
- 说明: 这份预置用户模板也对应中国税务标准模板，但上传列命名不同。

### targetColumn: invoice_no
- ruleType: DIRECT_MAPPING
- sourceColumns: invoice_id
- 说明: 标准列 invoice_no 直接使用上传文件中的 invoice_id 列。

### targetColumn: tax_a
- ruleType: AI_DERIVED
- sourceColumns: tax_code
- 说明: 标准列 tax_a 需要结合 tax_code 的值生成。
- ruleGuide: 当 tax_code 表示含税时，tax_a 填 1；否则填 0。
- example: CASE WHEN tax_code = 'Y' THEN '1' ELSE '0' END

### targetColumn: country
- ruleType: USER_CONFIRM
- userInputField: country
- options: CN, US
- 说明: 标准列 country 不从上传文件中推导，必须由前端用户选择。

### targetColumn: amount
- ruleType: DIRECT_MAPPING
- sourceColumns: amount_total
- 说明: 标准列 amount 直接使用上传文件中的 amount_total 列。
