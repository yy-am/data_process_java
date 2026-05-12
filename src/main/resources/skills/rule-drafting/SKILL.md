---
name: rule-drafting
description: 当工作流需要基于模板识别结果和用户确认结论起草最终 DSL 时，使用此技能。
---

# 规则草拟技能
## 目的

根据模板识别结果、用户确认结果和规则知识生成完整 DSL。

## 何时使用

仅在 `RuleDraftingSkillNode` 中使用。

## 输入要求

- 已有模板识别结果
- 已有用户确认结果
- 可读取规则知识和 DSL 骨架

## 允许使用的工具

- `ruleDslTool`

## 输出约定

返回一个 JSON 对象，字段如下：

- `templateCode`
- `dslContent`
- `reason`

其中 `dslContent` 必须是一个 JSON 字符串。

## 约束

- 字段 `A` 使用用户最终确认的源字段。
- 字段 `period` 和 `D` 写入 `constants`。
- 输出的 `dslContent` 只允许包含 `templateCode`、`mappings`、`constants` 三部分。

## 禁止事项

- 不得发明未允许的 `transform` 类型。
- 不得绕过 DSL 结构约束编写隐式逻辑。
- 不得决定工作流下一跳。
- 不得输出 Markdown 或解释性文字。
