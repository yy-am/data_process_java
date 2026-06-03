# Agent DSL operation 删除与 SQL 片段生成提示优化说明

本文档记录本次 Agent 加工计划 DSL 与字段绑定状态的调整，用于前端联调、后端审查和后续维护。

## 开发任务清单

1. 删除 `ProcessingPlanColumn.operation` 字段。
2. 删除 `ProcessingPlanOperation` 枚举。
3. 移除 `ProcessingPlanDslValidator` 中基于 `operation` 的形态校验。
4. 保留现有 SQL 安全边界校验，不新增 `CASE WHEN 1=1`、`CASE WHEN TRUE` 等低质量表达式校验。
5. 将 Agent 字段绑定状态从 `CONFIRMED / NEEDS_CONFIRMATION / MISSING` 调整为更明确的 `EXACT_MAPPING / FUZZY_MAPPING / MISSING`。
6. 修改 `SKILL.md` 中字段绑定计划的状态说明。
7. 修改 `SKILL.md` 中第 8 步 SQL 片段生成提示词，强化 DWS SQL 表达式专家角色。
8. 不修改已废弃的 `processing-plan-dsl-prompt.md`。
9. 不生成测试用例。
10. 生成本变更说明文档，并提交远程 Git。

## 为什么删除 operation

原结构中 `operation` 的取值为：

```text
DIRECT_MAPPING
CASE_WHEN
CONSTANT
```

实际 SQL 拼接时只使用：

```text
targetColumn
expressionSql
```

`operation` 只用于 validator 中的辅助形态校验，并不参与最终 SQL 拼接。

随着加工规则变复杂，`operation` 会让 AI 误判表达式类型。例如数值四舍五入、日期格式化、字符串清洗、空值兜底、多字段拼接等都不是 `DIRECT_MAPPING`、`CONSTANT` 或 `CASE_WHEN`，模型容易为了填充 `operation=CASE_WHEN` 而生成无意义的 `CASE WHEN` 包裹。

删除 `operation` 后，AI 只需要专注生成符合 DWS 语法和业务规则的 `expressionSql`。

## ProcessingPlanColumn 结构变化

调整前：

```java
public record ProcessingPlanColumn(
        String targetColumn,
        ProcessingPlanOperation operation,
        List<ActualColumnMapping> actualColumnMappings,
        String expressionSql
) {
}
```

调整后：

```java
public record ProcessingPlanColumn(
        String targetColumn,
        List<ActualColumnMapping> actualColumnMappings,
        String expressionSql
) {
}
```

## ProcessingPlanDslValidator 变化

删除内容：

- 删除 `ProcessingPlanOperation` import。
- 删除 `column.operation()` 非空校验。
- 删除 `validateOperationShape(...)`。
- 删除 `isCaseWhenExpression(...)`。
- 删除基于 operation 的以下约束：
  - `DIRECT_MAPPING` 必须等于一个 elasticColumn。
  - `CONSTANT` 不能引用 elasticColumn。
  - `CASE_WHEN` 必须包含 CASE/WHEN/THEN/END。

保留内容：

- `expressionSql` 不能为空。
- 禁止完整 SQL 关键字。
- 禁止分号、SQL 注释和多语句。
- 禁止直接引用 Excel 原始表头 `actualColumn`。
- 表达式中出现的 `colN` 必须属于当前目标列授权的 `elasticColumn`。
- 输出目标列集合必须和后端构造的上下文目标列集合一致。

本次明确不新增：

- 不新增 `CASE WHEN 1=1` 校验。
- 不新增 `CASE WHEN TRUE` 校验。
- 不新增低质量表达式专门校验。

## 字段绑定状态变化

调整前：

```java
public enum FieldBindingStatus {
    CONFIRMED,
    NEEDS_CONFIRMATION,
    MISSING
}
```

调整后：

```java
public enum FieldBindingStatus {
    EXACT_MAPPING,
    FUZZY_MAPPING,
    MISSING
}
```

语义说明：

- `EXACT_MAPPING`：明确映射，可以唯一确定 Excel 原始列。
- `FUZZY_MAPPING`：模糊映射，存在多个候选列，需要前端用户确认。
- `MISSING`：缺失映射，没有可靠可映射列。

相关修改：

- `FieldBindingValidationTool`
  - 校验 `EXACT_MAPPING` 必须包含 `selectedHeader`，不能包含 `candidateHeaders`。
  - 校验 `FUZZY_MAPPING` 必须包含至少两个 `candidateHeaders`，不能包含 `selectedHeader`。
- `ConfirmationTool`
  - 仅对 `FUZZY_MAPPING` 生成字段映射确认项。
  - 必填字段空值检查仅使用 `EXACT_MAPPING` 的 `selectedHeader`。
- `DataProcessingAgentToolMethods`
  - 解析字段绑定时，`EXACT_MAPPING` 直接使用 `selectedHeader`。
  - `FUZZY_MAPPING` 从用户确认结果中读取最终选择。

## Skill 字段绑定计划变化

`SKILL.md` 中字段绑定计划状态从：

```text
CONFIRMED 或 NEEDS_CONFIRMATION 或 MISSING
```

调整为：

```text
EXACT_MAPPING 或 FUZZY_MAPPING 或 MISSING
```

这样前端查看 `fieldBindingPlan.items[].status` 时，可以直接理解：

- 明确映射
- 模糊映射
- 缺失映射

`ruleType` 仍然保留加工规则类型语义，例如：

- `DIRECT_MAPPING`
- `EXPR`

`status` 表达字段绑定状态，`ruleType` 表达加工规则类型，两者不混用。

## Skill SQL 片段生成提示变化

本次只修改：

```text
src/main/resources/agent/skills/data-processing-agent-skill/SKILL.md
```

不修改已废弃的：

```text
src/main/resources/prompts/processing-plan-dsl-prompt.md
```

主要变化：

- processingPlanDsl 的 columns 示例删除 `operation`。
- 第 8 步增加 DWS SQL 表达式片段专家要求。
- 强调 `EXPR` 不等于 `CASE WHEN`。
- 要求 AI 先理解 `ruleGuide`、`example`、字段类型说明、用户确认值和字段映射关系，再生成最小必要表达式。
- 要求 AI 面对数值处理、日期处理、字符串处理、空值处理、类型转换、多字段拼接等场景时，选择 DWS 中合适的标量函数或表达式。
- 不写死具体函数，不强制数值一定用某个函数，也不强制日期一定用某个函数。
- 明确 `example` 是语义参考，不是可直接照抄的 SQL；如果 example 中出现 Excel 原始表头或加工规则源字段，必须替换为对应 `elasticColumn`。

## 新的 processingPlanDsl columns 示例

```json
{
  "targetColumn": "目标列",
  "actualColumnMappings": [
    {
      "actualColumn": "Excel 原始列",
      "elasticColumn": "临时表弹性字段"
    }
  ],
  "expressionSql": "DWS SQL 标量表达式片段"
}
```

## 本次不做的事项

- 不引入字段类型元数据。
- 不修改 `processing-plan-dsl-prompt.md`。
- 不生成测试用例。
- 不修改废弃 workflow 代码。
- 不新增低质量 `CASE WHEN` 专门校验。

## 验证情况

- 已执行静态扫描，目标代码和 `SKILL.md` 中不再包含 `ProcessingPlanOperation`、`operation()`、`"operation"` 以及 Agent 字段绑定旧状态 `CONFIRMED / NEEDS_CONFIRMATION`。
- 已执行 `git diff --check`，本次目标文件未发现空白格式问题。
- 已执行 `mvn -q -DskipTests compile`，当前仍被已废弃 workflow 文件 `DataProcessingStateGraphDefinition.java` 的既有语法错误阻断；本次未修改 workflow。
