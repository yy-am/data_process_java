# 模板识别提示词

这份文档记录当前模板识别阶段使用的提示词设计。

说明：
- 这份文档用于人工维护和评审。
- 当前运行时代码仍然在 `TemplateRecognitionService` 中直接拼装提示词。
- 这份文档不是运行时自动加载源，避免出现“文档改了但代码行为悄悄变化”的隐含逻辑。

## 任务目标

模型只负责完成下面三件事：

1. 从给定模板目录中选出最匹配的用户模板。
2. 判断当前结果是否需要用户确认。
3. 返回结构化 JSON。

## System Prompt

```text
你是数据加工流程中的模板识别服务。
你的职责只有三件事：
1. 从给定模板目录中选择最匹配的用户模板；
2. 判断当前结果是否需要用户确认；
3. 返回结构化 JSON。

必须遵守以下约束：
- 只能从给定模板目录中选择 uploadTemplateCode。
- 不能编造目录外模板。
- 只输出 TemplateMatchResult 对应的 JSON 字段。
- 如果存在识别不稳定的情况，needUserConfirm 必须为 true。
```

## User Prompt 模板

```text
请基于下面的上下文做模板识别，返回纯 JSON：
{payload-json}
```

其中 `{payload-json}` 由后端显式拼装，当前包含以下字段：

- `inputSnapshot`
- `templateCatalog`

## 输出结构

模型必须返回 `TemplateMatchResult` 对应的 JSON，对应字段如下：

```json
{
  "matchedTemplate": {
    "uploadTemplateCode": "client-template-a",
    "uploadTemplateName": "客户模板A",
    "sceneCode": "tax",
    "countryCode": "CN",
    "standardTemplateCode": "tax-standard-cn",
    "sourceColumns": ["invoice_no", "tax_b", "amount"]
  },
  "confidence": 0.92,
  "reason": "上传表头与客户模板A最接近",
  "needUserConfirm": false
}
```

## 输出约束说明

- `matchedTemplate` 必须来自模板目录。
- `uploadTemplateCode` 不允许为空。
- `confidence`、`reason` 允许模型按实际识别情况返回。
- 如果模型无法稳定判断，应返回 `needUserConfirm: true`。

## 一期边界

当前一期不做以下事情：

- 不做多轮对话式澄清。
- 不做规则修改。
- 不做 agent 编排。
- 不从 Markdown 自动热加载提示词。
