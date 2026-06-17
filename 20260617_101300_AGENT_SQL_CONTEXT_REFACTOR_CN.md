# Agent SQL 上下文收敛改动说明

## 改动文件

- `src/main/java/com/example/dataprocess/agent/model/AgentTargetColumnContext.java`
- `src/main/java/com/example/dataprocess/agent/model/AgentSqlGenerationContext.java`
- `src/main/java/com/example/dataprocess/agent/tool/DataProcessingAgentToolMethods.java`
- `src/main/java/com/example/dataprocess/agent/tool/ProcessingPlanSqlTool.java`
- `src/main/java/com/example/dataprocess/domain/model/DslGenerationContext.java`
- `src/main/java/com/example/dataprocess/domain/model/TargetColumnGenerationContext.java`
- `src/main/java/com/example/dataprocess/infrastructure/service/ProcessingPlanDslGenerationService.java`
- `src/main/java/com/example/dataprocess/infrastructure/service/ProcessingPlanDslValidator.java`
- `src/main/resources/prompts/processing-plan-dsl-prompt.md`

## 新增类

### `AgentTargetColumnContext`

新增目标字段 SQL 生成上下文，承载第 8 步 AI 生成 `ProcessingPlanDsl.columns` 所需的单字段信息。

字段包括：

- `targetColumn`
- `ruleType`
- `sourceColumn`
- `bindingDisplayName`
- `bindingStatus`
- `actualColumnMappings`
- `ruleGuide`
- `example`
- `confirmedValue`
- `confirmationType`
- `reason`

## 修改类

### `AgentSqlGenerationContext`

新增字段：

- `List<AgentTargetColumnContext> targetContexts`

调整后该类同时承载：

- SQL 表上下文：`stagingTable`、`resultTable`、`loadedRows`、`columnMappings`
- AI 生成 SQL 片段所需的目标字段上下文：`targetContexts`

### `DataProcessingAgentToolMethods`

#### `prepareSqlGenerationContext(String taskId)`

返回类型调整为 `AgentSqlGenerationContext`。

工具返回中直接包含整理后的 `targetContexts`，第 8 步可直接基于该对象生成 `ProcessingPlanDsl`。

#### `buildSqlGenerationContext(DataProcessingAgentState state)`

构造 `AgentSqlGenerationContext` 时同步构造 `targetContexts`。

#### `executeProcessingPlan(String taskId, ProcessingPlanDsl processingPlanDsl)`

调用 `ProcessingPlanSqlTool.renderInsertSelectSql(...)` 时不再传入 `DslGenerationContext`。

#### `buildAgentTargetColumnContexts(...)`

新增方法，基于以下内容生成 `AgentTargetColumnContext`：

- 字段绑定结果 `fieldBindingPlan.items`
- 用户确认结果 `userConfirmationResult`
- 临时表字段映射 `columnMappings`
- 加工规则中的 `ruleGuide`、`example`

#### `buildPostConfirmationBusinessContext(...)`

调整为以字段绑定结果为主线拼接用户确认结果，不再重新遍历加工规则作为主数据源。

#### 删除的旧逻辑

- 删除 `buildDslGenerationContext(...)`
- 删除 `buildTargetColumnGenerationContexts(...)`
- 删除 `safeColumnMappings(...)`

### `ProcessingPlanSqlTool`

#### `renderInsertSelectSql(...)`

方法签名从：

```java
renderInsertSelectSql(String taskId, AgentSqlGenerationContext context, DslGenerationContext dslGenerationContext, ProcessingPlanDsl processingPlanDsl)
```

调整为：

```java
renderInsertSelectSql(String taskId, AgentSqlGenerationContext context, ProcessingPlanDsl processingPlanDsl)
```

#### `validateContext(...)`

仅保留基础上下文校验：

- `AgentSqlGenerationContext` 非空
- `taskId` 一致
- `ProcessingPlanDsl` 非空
- `ProcessingPlanDsl.taskId` 一致
- `stagingTable`、`resultTable` 表名合法

不再调用 `ProcessingPlanDslValidator` 校验 AI 生成的 `ProcessingPlanDsl`。

## 删除类和资源

### `DslGenerationContext`

已删除。第 8 步上下文统一收敛到 `AgentSqlGenerationContext`。

### `TargetColumnGenerationContext`

已删除。目标字段上下文改由 `AgentTargetColumnContext` 承载。

### `ProcessingPlanDslGenerationService`

已删除。该类为废弃 AI 生成路径。

### `ProcessingPlanDslValidator`

已删除。`execute_processing_plan` 内部不再校验 AI 生成的 `ProcessingPlanDsl`。

### `processing-plan-dsl-prompt.md`

已删除。该提示词仅服务已废弃的 `ProcessingPlanDslGenerationService`。

## 验证

已执行限定编译：

```bash
mvn -q -DskipTests "-Dmaven.compiler.includes=com/example/dataprocess/agent/**/*.java,com/example/dataprocess/domain/model/*.java" compile
```

结果通过。

全量编译当前仍受既有 `DataProcessingStateGraphDefinition.java` 语法错误影响。
