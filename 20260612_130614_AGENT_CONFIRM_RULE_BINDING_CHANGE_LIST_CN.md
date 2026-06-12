# Agent 用户确认类规则字段绑定改动清单

## 背景

`data-processing-agent-skill` 第 3 步已经调整为：`DIRECT_MAPPING`、`EXPR`、`USER_CONFIRM_OPTION`、`USER_CONFIRM_INPUT` 四类规则都必须参与 `FieldBindingPlan` 生成。

新的业务意图是：Agent 先判断每个目标列的取值是否可能已经存在于用户上传的 Excel 中。对于 `USER_CONFIRM_OPTION` 和 `USER_CONFIRM_INPUT`，Agent 找到语义相近原始列时只生成 `NEEDS_CONFIRMATION`，并把 `sourceColumn` 填为该 Excel 原始列名；后续代码读取候选列全量数据后，再决定是否升级为 `CONFIRMED` 并跳过用户确认。

## 必改清单

### 1. FieldBindingValidationTool

文件：`src/main/java/com/example/dataprocess/agent/tool/FieldBindingValidationTool.java`

- `FIELD_BINDING_RULE_TYPES` 需要加入 `USER_CONFIRM_OPTION` 和 `USER_CONFIRM_INPUT`。
- 覆盖范围校验不能再只按 `sourceColumns` 生成 expected keys。
- `DIRECT_MAPPING` 和 `EXPR` 仍按 `targetColumn|ruleType|sourceColumn` 校验。
- `USER_CONFIRM_OPTION` 和 `USER_CONFIRM_INPUT` 应按每条规则校验一条绑定项。
- 这两类规则不能要求 `sourceColumn` 来自规则 `sourceColumns`。
- 这两类规则的 `sourceColumn` 应允许为 Excel 原始列名或空；非空时必须存在于 `sourceHeaders`。
- 这两类规则的 `NEEDS_CONFIRMATION` 应允许 1 个候选列。
- 这两类规则不应接受 Agent 直接传入 `CONFIRMED`。

### 2. ConfirmationTool

文件：`src/main/java/com/example/dataprocess/agent/tool/ConfirmationTool.java`

- `buildConfirmationItems` 当前会无条件为所有 `USER_CONFIRM_OPTION` 生成选项确认、为所有 `USER_CONFIRM_INPUT` 生成输入确认，需要改为先读取 `fieldBindingPlan`。
- 对 `USER_CONFIRM_OPTION` 和 `USER_CONFIRM_INPUT` 的 `NEEDS_CONFIRMATION` 项，读取候选 Excel 列全量数据。
- 如果候选列全量非空，并满足业务校验，则将该绑定升级为 `CONFIRMED`，且不生成用户确认项。
- 如果候选列为空、有空值或业务校验不通过，则继续生成原有选项或输入确认项。
- `MISSING` 继续生成用户确认项。
- 如果工具会升级字段绑定计划，需要把更新后的 `FieldBindingPlan` 写回任务状态，不能只在局部变量中判断。

### 3. ParsedExcelFileTool

文件：`src/main/java/com/example/dataprocess/agent/tool/ParsedExcelFileTool.java`

- 可复用现有 `inspectExcelColumnNulls(parsedFileRef, actualColumns)` 判断候选列是否存在空值。
- 如果 `USER_CONFIRM_OPTION` 还要求候选列值必须落在规则 `options` 中，需要新增列值读取或值集校验能力。

### 4. DataProcessingAgentToolMethods

文件：`src/main/java/com/example/dataprocess/agent/tool/DataProcessingAgentToolMethods.java`

- `acceptFieldBindingPlan` 当前流程是校验计划、保存计划、生成确认项；如果确认项生成阶段会升级绑定计划，需要调整返回结构或保存顺序。
- 可考虑让确认项构建返回“更新后的 FieldBindingPlan + confirmationItems”，再保存最终 state。
- `buildTargetColumnGenerationContexts` 需要支持 `USER_CONFIRM_OPTION` 和 `USER_CONFIRM_INPUT` 从已确认 Excel 列取值。
- 当这两类规则升级为 `CONFIRMED` 后，DSL 上下文应包含对应 `actualColumnMappings`，而不是只依赖 `confirmedValue`。
- `confirmedValue(...)` 仍只负责用户提交的固定值，不应覆盖 Excel 列取值场景。

### 5. ProcessingPlanDslValidator

文件：`src/main/java/com/example/dataprocess/infrastructure/service/ProcessingPlanDslValidator.java`

- 如果 `USER_CONFIRM_OPTION` 或 `USER_CONFIRM_INPUT` 通过 Excel 列取值，`TargetColumnGenerationContext.actualColumnMappings` 必须带上对应 `col_N`。
- 这样现有“只能引用授权 elasticColumn”的校验才能通过。
- 如果没有把映射带入上下文，Agent 生成 `expressionSql: col_N` 会被判定为未授权字段。

## 建议同步项

### 6. DataProcessingAgentToolMethods 的 nextAction 文案

文件：`src/main/java/com/example/dataprocess/agent/tool/DataProcessingAgentToolMethods.java`

- 可同步提示 Agent 生成 `FieldBindingPlan` 时四类规则都要覆盖。

### 7. ProcessingRuleItem 与规则文档

文件：`src/main/java/com/example/dataprocess/domain/model/ProcessingRuleItem.java`

- 当前代码模型中存在 `userInputField` 字段，但本轮设计不应依赖它。
- 后续逻辑应统一按 `targetColumn`、规则说明、选项或输入提示、Excel 表头来判断用户确认类规则的候选原始列。

## 旧 StateGraph 链路如仍使用，也需要同步

### 8. VagueBindingRecoService

文件：`src/main/java/com/example/dataprocess/infrastructure/service/VagueBindingRecoService.java`

- 当前只允许 `DIRECT_MAPPING` 和 `EXPR` 进入字段绑定识别。
- 如旧 workflow 仍在使用，需要同步扩展覆盖范围、`sourceColumn` 校验和 `NEEDS_CONFIRMATION` 候选数量规则。

### 9. StructuredConfirmationService

文件：`src/main/java/com/example/dataprocess/infrastructure/service/StructuredConfirmationService.java`

- 当前无条件把 `USER_CONFIRM_OPTION` 和 `USER_CONFIRM_INPUT` 转成确认项。
- 如旧 workflow 仍在使用，需要改为先看字段绑定识别结果和全量列数据，再决定是否生成确认项。

## 推荐改动顺序

1. 先改 `FieldBindingValidationTool`，让新的 `FieldBindingPlan` 能通过工具校验。
2. 再改 `ConfirmationTool`，实现候选列全量非空则升级 `CONFIRMED`，否则生成确认项。
3. 最后改 DSL 上下文构建，确保升级后的 Excel 列能转成 `actualColumnMappings` 给 SQL 片段使用。
