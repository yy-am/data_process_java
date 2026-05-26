# 2026-05-26 修改记录

## 1. 对比基线

本文档以远程分支 `origin/main` 为对比基线，记录本次工作区相对远程代码的主要新增、修改、删除内容。

## 2. 新增类

### 2.1 DSL 上下文与加工计划模型

1. `src/main/java/com/example/dataprocess/domain/model/DslGenerationContext.java`
   - 作用：DSL 生成上下文，作为字段绑定、用户确认与 DSL 生成之间的边界对象。
   - 主要字段：`taskId`、`presetTemplateCode`、`standardTemplateCode`、`targetColumns`。

2. `src/main/java/com/example/dataprocess/domain/model/TargetColumnGenerationContext.java`
   - 作用：单个目标列的 DSL 生成上下文。
   - 主要字段：`targetColumn`、`ruleType`、`actualColumns`、`ruleGuide`、`example`、`confirmedValue`。

3. `src/main/java/com/example/dataprocess/domain/model/ProcessingPlanDsl.java`
   - 作用：后续可执行加工 DSL 的整体模型。
   - 当前仅完成模型定义，DWS SQL 编译和执行链路尚未展开。

4. `src/main/java/com/example/dataprocess/domain/model/ProcessingPlanColumn.java`
   - 作用：单个目标列的加工计划。

5. `src/main/java/com/example/dataprocess/domain/model/ProcessingPlanOperation.java`
   - 作用：第一版加工操作白名单。
   - 当前枚举值：`DIRECT_MAPPING`、`CASE_WHEN`、`CONSTANT`。

### 2.2 加工规则与确认准备结果

1. `src/main/java/com/example/dataprocess/domain/model/ProcessingRule.java`
   - 作用：某个预置用户模板对应的完整加工规则。
   - 替代原 `ProcessingRuleDocument`。

2. `src/main/java/com/example/dataprocess/domain/model/UserConfirmationPreparationResult.java`
   - 作用：用户确认准备结果。
   - 包含：`processingRule`、`vagueBindingRecoResult`、`userConfirmationItems`。

## 3. 修改类

### 3.1 工作流状态与节点

1. `DataProcessingGraphState`
   - 新增 `processingRule` 字段。
   - 保留并写回 `vagueBindingRecoResult`。
   - 中文化类和字段注释。

2. `TaskSession`
   - 新增 `processingRule` 字段。
   - 新增 `withProcessingRule(...)` 方法。
   - 中文化类和字段注释。

3. `DataProcessingStateGraphDefinition`
   - 新增 StateGraph 状态键：`processing_rule`。
   - `build_user_confirmation_request` 节点现在写回：
     - `processing_rule`
     - `vague_binding_reco_result`
     - `user_confirmation_items`

4. `BuildUserConfirmationRequestNode`
   - 返回类型由旧确认构建结果调整为 `UserConfirmationPreparationResult`。
   - 节点名称不变，仍为 `build_user_confirmation_request`。

### 3.2 规则加载、字段绑定与确认服务

1. `ProcessingRuleLoader`
   - 返回类型由 `ProcessingRuleDocument` 改为 `ProcessingRule`。
   - 中文化类和方法注释。

2. `StructuredConfirmationService`
   - 输出调整为 `UserConfirmationPreparationResult`。
   - 内部仍完成：
     - 加载加工规则。
     - 调用字段绑定识别。
     - 构建用户确认项。
   - 选值确认和输入确认统一使用 `targetColumn`。
   - 补充中文方法注释和关键逻辑注释。

3. `VagueBindingRecoService`
   - 入参中的规则类型改为 `ProcessingRule`。
   - 不再向 `VagueBindingRecoItem` 填充规则信息。
   - 保持职责为“规则源字段到上传表头”的绑定识别。
   - 补充中文方法注释和关键逻辑注释。

4. `RuleDraftingService`
   - 规则模型引用由 `ProcessingRuleDocument` 改为 `ProcessingRule`。
   - prompt 上下文中规则对象字段名从 `processingRuleDocument` 调整为 `processingRule`。

### 3.3 用户确认模型

1. `OptionConfirmation`
   - 删除 `fieldCode`、`fieldName`。
   - 新增并统一使用 `targetColumn`。
   - 中文化类和字段注释。

2. `InputConfirmation`
   - 删除 `fieldCode`、`fieldName`。
   - 新增并统一使用 `targetColumn`。
   - 中文化类和字段注释。

3. `OptionConfirmationDto`
   - 提交字段由 `fieldCode` 改为 `targetColumn`。
   - 中文化类和字段注释。

4. `InputConfirmationDto`
   - 提交字段由 `fieldCode` 改为 `targetColumn`。
   - 中文化类和字段注释。

### 3.4 字段绑定识别模型

1. `VagueBindingRecoItem`
   - 保持纯字段绑定职责。
   - 删除规则承载设计，不再包含 `itemRule`。
   - 中文化类和字段注释。

2. `VagueBindingRecoResult`
   - 中文化类和字段注释。

3. `VagueBindingRecoStatus`
   - 中文化枚举注释。

### 3.5 其他模型和资源

1. `TemplateRecognitionResult`
   - 中文化类和字段注释。

2. `src/main/resources/rules/client-template-a.md`
   - 将 `ruleType: AI_DERIVED` 调整为 `ruleType: CASE_WHEN`。

3. `src/main/resources/rules/client-template-b.md`
   - 将 `ruleType: AI_DERIVED` 调整为 `ruleType: CASE_WHEN`。

4. `src/main/resources/prompts/rule-drafting-prompt.md`
   - 示例中的 `AI_DERIVED` 调整为 `CASE_WHEN`。

## 4. 删除类

1. `src/main/java/com/example/dataprocess/domain/model/ProcessingRuleDocument.java`
   - 删除原因：命名偏文档层，不符合当前业务语义。
   - 替代类：`ProcessingRule`。

## 5. 本次明确不纳入的临时设计

以下设计在讨论中被放弃，最终未作为工作流边界保留：

1. 不让 `VagueBindingRecoItem` 承载规则说明。
2. 不保留 `VagueBindingItemRule`。
3. 不保留 `DslSourceBinding`。
4. 不保留 `DslTargetContext`。
5. 不保留 `UserConfirmationBuildResult`，改为 `UserConfirmationPreparationResult`。

## 6. 验证结果

已执行编译验证：

```powershell
.\mvnw.cmd -q -DskipTests compile
```

结果：编译通过。
