---
name: rule-drafting
description: This skill should be used when the workflow needs to draft the final DSL from template recognition and confirmed user decisions.
---

# Rule Drafting Skill 规则草拟技能

## Purpose

根据模板识别结果、用户确认结果和规则知识生成完整 DSL。

## When To Use

仅在 `RuleDraftingSkillNode` 中使用。

## Input Expectations

- 已有模板识别结果
- 已有用户确认结果
- 可读取规则知识和 DSL 骨架

## Allowed Tools

- `ruleDslTool`

## Output Contract

返回一个 JSON 对象，字段如下：

- `templateCode`
- `dslContent`
- `reason`

其中 `dslContent` 必须是一个 JSON 字符串。

## Constraints

- 字段 `A` 使用用户最终确认的源字段。
- 字段 `period` 和 `D` 写入 `constants`。
- 输出的 `dslContent` 只允许包含 `templateCode`、`mappings`、`constants` 三部分。

## Forbidden Actions

- 不得发明未允许的 transform 类型。
- 不得绕过 DSL 结构约束写隐式逻辑。
- 不得决定工作流下一跳。
- 不得输出 Markdown 或解释性文字。
