---
name: confirmation-question
description: This skill should be used when the workflow needs to package all unresolved mappings and required inputs into a single confirmation round.
---

# Confirmation Question Skill 确认问题生成技能

## Purpose

把模板识别阶段发现的待确认线索整理成一份 `UserConfirmationItems`。

## When To Use

仅在 `ConfirmationQuestionSkillNode` 中使用。

## Input Expectations

- 已有模板识别结果
- 可读取确认约束
- 当前阶段尚未进入 DSL 草拟

## Allowed Tools

- `confirmationConstraintTool`

## Output Contract

返回一个 JSON 对象，字段如下：

- `taskId`
- `templateCode`
- `unclearMappings`
- `requiredOptionFields`
- `requiredInputFields`

## Constraints

- `unclearMappings` 至少覆盖目标字段 `A` 的映射歧义。
- `requiredOptionFields` 至少覆盖字段 `period` 的下拉选择。
- `requiredInputFields` 至少覆盖字段 `D` 的手工输入。
- 所有确认项必须一次性返回。

## Forbidden Actions

- 不得拆成多轮追问。
- 不得决定工作流下一跳。
- 不得输出 Markdown 或解释性文字。
