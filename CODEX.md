# Progress Notes

## 2026-05-28

### 本轮已实现

- 新增 `ActualColumnMapping`，明确 `actualColumn` 用于 AI 理解用户上传表头含义，`elasticColumn` 用于生成真实可执行 SQL 表达式片段。
- 调整 `TargetColumnGenerationContext`，将旧 `actualColumns` 改为 `actualColumnMappings`。
- 调整 `ProcessingPlanDsl` / `ProcessingPlanColumn`，第一版只承载目标列表达式级 SQL 片段，不承载完整 SQL。
- 新增 `ProcessingPlanDslGenerationService`，负责调用 AI 生成 `ProcessingPlanDsl`。
- 新增 `ProcessingPlanDslValidator`，校验 AI 输出不能包含完整 SQL、危险关键字、越权弹性域字段或用户上传表头字段名。
- 新增提示词 `src/main/resources/prompts/processing-plan-dsl-prompt.md`，约束 AI 只能输出表达式片段。
- 新增 `0527_TODO_CN.md`，记录后续 StateGraph 接入、完整 SQL 拼接、DWS 执行、结果校验等待办。
- 更新 `CURRENT_WORKFLOW_DESIGN_CN.md`，同步 actual/elastic 映射和“AI 只生成 expressionSql，系统拼完整 SQL”的设计边界。
- 模板识别阶段改为把完整模板目录 Markdown 原文交给 AI，结构化目录解析仅用于返回结果校验。
- 清理未使用代码：删除旧导出预留接口、旧兼容方法和未使用工具方法。

### 本轮已验证

- 已执行 `mvn -q -DskipTests compile`，编译通过。

### 已实现但未接入

- `ProcessingPlanDslGenerationService` 尚未接入 StateGraph。
- `build_dsl_generation_context` 节点尚未实现。
- 完整 DWS SQL 拼接、执行、错误行处理与发布流程尚未实现。

### 下一步计划

- 评审 `DslGenerationContext` 如何由 `ProcessingRule`、`VagueBindingRecoResult`、`UserConfirmationResult` 和 actual/elastic 映射生成。
- 评审 `compile_processing_plan_dsl` 节点接入 StateGraph 的入参、出参和 state 字段。
- 设计系统拼接 `INSERT INTO IT_TEMP (...) SELECT expressionSql ... FROM ELASTIC_TEMP ...` 的边界和校验策略。

## 2026-05-12

### 本轮已实现

- 引入 `DataProcessingGraph` 作为应用层图编排入口，收口任务提交与用户确认恢复流程。
- 新增 `GraphNode`、`TemplateRecognitionNode`、`ConfirmationQuestionNode`、`RuleDraftingNode`、`DslTransformationNode`。
- 新增 `SkillRegistry`、`GroupedToolRegistry`、`SkillExecutor`、`SkillDocumentLoader`。
- 为三个 `SKILL.md` 显式补充 allowed tools 定义。
- `SkillService` 已改为通过 `SkillExecutor` 执行 skill，不再手工把所有工具结果直接拼成通用上下文。
- 新增 `SPRING_AI_ALIBABA_STATEGRAPH_SKILL_DESIGN_CN.md`，明确后续正式方向应切换为 `StateGraph + ReactAgent + Spring AI Alibaba 原生 skill 能力`。
- 已根据用户确认修正方案边界：`工作流内部只有 skill，没有 agent`。
- 已删除旧方案文档、自定义 graph 过渡实现、自定义 skill runtime 过渡实现。

### 本轮已验证

- 已确认当前环境缺少 `mvn` 与 `java` 命令，无法在本轮完成本地编译验证。

### 已实现但未验证

- graph orchestration 与显式 skill/tool 绑定的 Spring Bean 装配。
- 当前自定义 `SkillDocumentLoader / SkillRegistry / GroupedToolRegistry / SkillExecutor` 仅适合作为过渡实现，后续需要按新设计替换。
- `SkillService / DataProcessingWorkflow / UserConfirmationWorkflow` 现已收敛为待迁移壳子，等待接入 Spring AI Alibaba 原生 `StateGraph + skill runtime`。

### 下一步计划

- 在具备 Java/Maven 的环境中运行 `mvn compile` 检查依赖与编译错误。
- 按新设计将自定义 graph 替换为 `StateGraph` 编排。
- 按新设计将自定义 skill runtime 替换为 Spring AI Alibaba 原生 skill/runtime 装配。
