---
name: template-recognition
description: This skill should be used when the workflow needs to recognize the best matching template from the input snapshot and template catalog.
---

# Template Recognition Skill 模板识别技能

## Purpose

基于输入快照、模板目录和表头别名，识别最匹配的目标模板。

## When To Use

仅在 `TemplateRecognitionSkillNode` 中使用。

## Input Expectations

- 已存在标准化 `InputSnapshot`
- 可读取模板目录
- 可读取表头别名

## Allowed Tools

- `inputSnapshotTool`
- `templateCatalogTool`
- `headerAliasTool`

## Output Contract

返回一个 JSON 对象，字段如下：

- `templateCode`
- `sceneCode`
- `countryCode`
- `confidence`
- `needUserConfirm`
- `reason`
- `unresolvedTargetFields`

## Constraints

- 只能从模板目录中选择模板。
- 如果字段 `A`、`period`、`D` 仍无法自动确定，就把它们放入 `unresolvedTargetFields`。
- 如果存在待确认项，`needUserConfirm` 必须为 `true`。

## Forbidden Actions

- 不得编造目录之外的模板。
- 不得决定工作流下一跳。
- 不得输出 Markdown 或解释性文字。
