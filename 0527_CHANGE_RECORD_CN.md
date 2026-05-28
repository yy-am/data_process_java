# 2026-05-27 修改记录

## 1. 对比基线

本文档以当前远程分支 `origin/main` 的上一版已提交代码为对比基线，记录 0527 本轮围绕“AI 生成 SQL 表达式片段”的主要新增、修改、删除内容。

## 2. 新增类

### 2.1 DSL 上下文字段映射模型

1. `src/main/java/com/example/dataprocess/domain/model/ActualColumnMapping.java`
   - 作用：描述用户实际上传字段与弹性域临时表字段之间的映射关系。
   - 主要字段：
     - `actualColumn`：用户上传 Excel 中的真实表头，只用于帮助 AI 理解业务含义。
     - `elasticColumn`：原始弹性域临时表中的真实字段名，例如 `col1`、`col2`，用于生成可执行 SQL 表达式片段。

### 2.2 加工计划 DSL 生成与校验服务

1. `src/main/java/com/example/dataprocess/infrastructure/service/ProcessingPlanDslGenerationService.java`
   - 作用：根据 `DslGenerationContext` 调用 AI 生成 `ProcessingPlanDsl`。
   - 边界：AI 只生成目标列表达式级 `expressionSql`，不生成完整 SQL、不生成表名、不决定目标写入表。
   - 输出：通过 `ProcessingPlanDslValidator` 校验后的 `ProcessingPlanDsl`。

2. `src/main/java/com/example/dataprocess/infrastructure/service/ProcessingPlanDslValidator.java`
   - 作用：作为 AI 输出进入后续 SQL 拼接层之前的安全闸门。
   - 主要校验：
     - `taskId`、模板编码必须与上下文一致。
     - 输出目标列集合必须与上下文目标列集合一致。
     - `expressionSql` 不能包含完整 SQL 关键字、分号、SQL 注释。
     - `expressionSql` 只能引用当前目标列允许的 `elasticColumn`。
     - `expressionSql` 不能直接引用用户上传表头 `actualColumn`。
     - `DIRECT_MAPPING`、`CASE_WHEN`、`CONSTANT` 分别做操作类型形态校验。

## 3. 修改类

### 3.1 DSL 生成上下文模型

1. `src/main/java/com/example/dataprocess/domain/model/DslGenerationContext.java`
   - 职责说明从“泛化 DSL 生成上下文”收紧为“字段绑定识别、用户确认与目标列表达式 SQL 片段生成之间的边界对象”。
   - 明确不包含来源表、目标表、WHERE 条件等完整 SQL 信息。

2. `src/main/java/com/example/dataprocess/domain/model/TargetColumnGenerationContext.java`
   - 将旧字段 `actualColumns` 调整为 `actualColumnMappings`。
   - 明确每个目标列同时携带：
     - 用户上传表头 `actualColumn`，用于 AI 理解语义。
     - 弹性域真实字段 `elasticColumn`，用于生成可执行 SQL 表达式片段。

### 3.2 加工计划 DSL 模型

1. `src/main/java/com/example/dataprocess/domain/model/ProcessingPlanDsl.java`
   - 明确该对象只承载目标列表达式级 SQL 片段。
   - 不承载 `INSERT`、`FROM`、`WHERE` 等完整 SQL 结构。

2. `src/main/java/com/example/dataprocess/domain/model/ProcessingPlanColumn.java`
   - 删除旧的 `sourceHeaders`、`constantValue`、`expression` 结构。
   - 新增 `actualColumnMappings`，记录该目标列使用的 actual/elastic 映射。
   - 新增 `expressionSql`，表示 AI 生成的 SELECT 列表表达式片段。

3. `src/main/java/com/example/dataprocess/domain/model/ProcessingPlanOperation.java`
   - 保留第一版操作白名单：
     - `DIRECT_MAPPING`
     - `CASE_WHEN`
     - `CONSTANT`
   - 为枚举值补充中文注释，说明每种操作对应的表达式边界。

## 4. 新增资源和文档

1. `src/main/resources/prompts/processing-plan-dsl-prompt.md`
   - 作用：加工计划 DSL 生成提示词。
   - 重点说明：
     - 用户 Excel 表头、弹性域临时表字段、标准模板目标列之间的关系。
     - `actualColumn` 只用于语义理解，`elasticColumn` 才能进入 `expressionSql`。
     - AI 只能生成表达式片段，不能生成完整 SQL、表名或写入目标。

2. `0527_TODO_CN.md`
   - 作用：记录后续待实现功能和任务清单，避免 DSL 到 SQL、DWS 执行链路遗漏。
   - 主要内容：
     - `build_dsl_generation_context` 节点待实现。
     - `compile_processing_plan_dsl` 节点待接入 StateGraph。
     - 完整 DWS SQL 拼接、执行、结果校验待设计实现。

3. `0527_CHANGE_RECORD_CN.md`
   - 作用：记录 0527 本轮新增、修改、删除内容。

## 5. 修改文档

1. `CURRENT_WORKFLOW_DESIGN_CN.md`
   - 同步最新设计边界：
     - 第一段“Excel -> 弹性域临时表”不由当前工作流实现。
     - 后续工作流消费 actual/elastic 映射。
     - AI 只生成 `expressionSql`。
     - 系统代码负责拼接完整 SQL 并决定目标 IT 临时表。

2. `CODEX.md`
   - 补充本轮实现进度、验证结果、尚未接入部分和下一步计划。

## 6. 删除类

本轮没有删除类。

## 7. 本次明确不纳入的内容

以下内容本轮不提交，避免把无关工作区变化混入 DSL 设计提交：

1. `.gitignore` 中的 `.m2/` 变更。
2. `.mvn/maven.config`。
3. `.mvn/settings.xml`。
4. `StructuredConfirmationService.java` 中与本轮 DSL 表达式片段无关的一行待办注释。
5. `TemplateRecognitionService.java` 的无实际内容差异状态。

## 8. 验证结果

已执行编译验证：

```powershell
mvn -q -DskipTests compile
```

结果：编译通过。

