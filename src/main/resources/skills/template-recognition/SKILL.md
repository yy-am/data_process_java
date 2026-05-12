---
name: template-recognition
description: 当工作流需要根据输入快照和模板目录识别最匹配的模板时，使用此技能。
---

# 模板识别技能
## 目的

基于输入快照、模板目录和表头别名，识别最匹配的目标模板。

## 何时使用

仅在 `TemplateRecognitionSkillNode` 中使用。

## 输入要求

- 已存在标准化 `InputSnapshot`
- 可读取模板目录
- 可读取表头别名

## 允许使用的工具

- `inputSnapshotTool`
- `templateCatalogTool`
- `headerAliasTool`

## 输出约定

返回一个 JSON 对象，字段如下：

- `templateCode`
- `sceneCode`
- `countryCode`
- `confidence`
- `needUserConfirm`
- `reason`
- `unresolvedTargetFields`

## 约束

- 只能从模板目录中选择模板。
- 如果字段 `A`、`period`、`D` 仍无法自动确定，就把它们放入 `unresolvedTargetFields`。
- 如果存在待确认项，`needUserConfirm` 必须为 `true`。

## 禁止事项

- 不得编造目录之外的模板。
- 不得决定工作流下一跳。
- 不得输出 Markdown 或解释性文字。
