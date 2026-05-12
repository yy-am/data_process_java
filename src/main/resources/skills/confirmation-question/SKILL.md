---
name: confirmation-question
description: 当工作流需要把所有未解决的字段映射和必填输入整合到同一轮用户确认时，使用此技能。
---

# 确认问题技能
## 目的

将模板识别阶段发现的待确认线索整理成一份 `UserConfirmationItems`。

## 何时使用

仅在 `ConfirmationQuestionSkillNode` 中使用。

## 输入要求

- 已有模板识别结果
- 可读取确认约束
- 当前阶段尚未进入 DSL 草拟

## 允许使用的工具

- `confirmationConstraintTool`

## 输出约定

返回一个 JSON 对象，字段如下：

- `taskId`
- `templateCode`
- `unclearMappings`
- `requiredOptionFields`
- `requiredInputFields`

## 约束

- `unclearMappings` 至少覆盖目标字段 `A` 的映射歧义。
- `requiredOptionFields` 至少覆盖字段 `period` 的下拉选择。
- `requiredInputFields` 至少覆盖字段 `D` 的手工输入。
- 所有确认项必须一次性返回。

## 禁止事项

- 不得拆成多轮追问。
- 不得决定工作流下一跳。
- 不得输出 Markdown 或解释性文字。
