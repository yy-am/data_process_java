# Agent 字段绑定展示字段改动说明

## 背景

当加工规则存在 `CASE WHEN ... THEN ...` 等复杂表达式时，一个目标字段可能依赖多个规则源字段。前端在展示字段绑定关系时，如果继续直接展示单个 `sourceColumn`，用户很难理解该目标字段真实的加工含义。

本次改动采用独立展示字段方案：保留 `sourceColumn` 作为内部稳定字段绑定标识，新增 `bindingDisplayName` 作为前端展示名称或规则说明。

## 设计原则

- `sourceColumn` 继续表示加工规则 `sourceColumns` 中声明的单个源字段，用于校验、确认 key、后续加工定位。
- `bindingDisplayName` 仅用于前端展示，不参与字段绑定唯一性判断。
- 多源复杂 `EXPR` 优先展示加工规则 `ruleGuide`，帮助用户理解目标字段整体加工逻辑。
- 单源加工或直接映射继续展示 `sourceColumn`，避免简单场景被复杂化。

## 新增内容

### `FieldBindingItem.bindingDisplayName`

文件：`src/main/java/com/example/dataprocess/agent/model/FieldBindingItem.java`

新增字段：

```java
String bindingDisplayName
```

字段含义：

- 给前端展示的规则源名称或规则说明。
- 复杂多源 `EXPR` 场景可承载加工规则 `ruleGuide`。
- 简单单源加工或 `DIRECT_MAPPING` 场景承载 `sourceColumn`。

## 修改内容

### `FieldBindingValidationTool`

文件：`src/main/java/com/example/dataprocess/agent/tool/FieldBindingValidationTool.java`

修改方法：

- `validateItem(...)`
- `validateConfirmed(...)`
- `validateNeedsConfirmation(...)`
- `validateMissing(...)`

新增方法：

```java
private String resolveBindingDisplayName(FieldBindingItem item, ProcessingRuleItem ruleItem)
```

处理逻辑：

- 如果 Agent 已传入非空 `bindingDisplayName`，保留该值。
- 如果规则类型为 `EXPR`，并且同一目标列依赖多个 `sourceColumns`，且 `ruleGuide` 非空，则使用 `ruleGuide`。
- 其他情况使用 `sourceColumn`。

影响：

- 字段绑定计划经过工具校验后，会统一补齐 `bindingDisplayName`。
- 原有 `sourceColumn` 覆盖范围校验、候选列校验、状态校验保持不变。

### `ConfirmationTool`

文件：`src/main/java/com/example/dataprocess/agent/tool/ConfirmationTool.java`

修改方法：

```java
private AgentConfirmationItem mappingConfirmation(FieldBindingItem item)
```

修改点：

- 字段映射确认项的展示字段从 `item.sourceColumn()` 改为 `item.bindingDisplayName()`。
- 确认项 key 仍然使用 `item.sourceColumn()`，保持内部定位稳定。

影响：

- 前端确认页面可以看到更符合业务语义的规则源描述。
- 用户提交确认结果时，后端仍按原有 `sourceColumn` 维度定位，不改变确认提交协议的稳定性。

### `SKILL.md`

文件：`src/main/resources/agent/skills/data-processing-agent-skill/SKILL.md`

修改内容：

- 字段绑定计划结构新增 `bindingDisplayName`。
- 明确 `sourceColumn` 必须始终等于加工规则 `sourceColumns` 中声明的单个源字段。
- 明确 `bindingDisplayName` 仅用于前端展示，不参与字段绑定唯一性判断。
- 明确多源 `EXPR` 使用 `ruleGuide` 作为展示名称，单源加工或直接映射使用 `sourceColumn`。
- 明确前端展示字段绑定来源时优先使用 `bindingDisplayName`，内部定位和后续加工仍使用 `sourceColumn`。

## 删除内容

本次没有删除类、方法或字段。

## 前端契约变化

`fieldBindingPlan.items[]` 新增字段：

```json
{
  "targetColumn": "目标列",
  "ruleType": "DIRECT_MAPPING 或 EXPR",
  "sourceColumn": "内部绑定用规则源字段",
  "bindingDisplayName": "前端展示用规则源名称或规则说明",
  "status": "CONFIRMED 或 NEEDS_CONFIRMATION 或 MISSING",
  "selectedHeader": "已确认映射的 Excel 原始列",
  "candidateHeaders": ["待用户确认的候选 Excel 原始列"],
  "reason": "绑定原因"
}
```

前端展示建议：

- 展示字段绑定来源时使用 `bindingDisplayName`。
- 不要把 `bindingDisplayName` 当作后端提交 key。
- 若需要展示调试信息或规则原始字段，可额外展示 `sourceColumn`。

## 验证

已执行：

```bash
mvn -q -DskipTests compile
```

编译通过。
