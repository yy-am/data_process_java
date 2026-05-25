# 模板目录

## 标准模板

### standardTemplateCode: tax-standard-cn
- sceneCode: tax
- countryCode: CN
- standardColumns: invoice_no, tax_a, country, amount

### standardTemplateCode: tax-standard-us
- sceneCode: tax
- countryCode: US
- standardColumns: invoice_no, tax_a, country, amount

## 预置用户模板

### presetTemplateCode: client-template-a
- presetTemplateName: 客户模板A
- sceneCode: tax
- countryCode: CN
- standardTemplateCode: tax-standard-cn
- sourceColumns: invoice_no, tax_b, amount

### presetTemplateCode: client-template-b
- presetTemplateName: 客户模板B
- sceneCode: tax
- countryCode: CN
- standardTemplateCode: tax-standard-cn
- sourceColumns: invoice_id, tax_code, amount_total
