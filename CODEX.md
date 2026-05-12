# Progress Notes

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
