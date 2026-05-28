# 0527 待办事项

## 一、当前已确认但后续还要继续实现的范围

1. 第一段落库是“用户原始 Excel -> 弹性域临时表”，这部分暂时不由当前代码实现。
2. 第一段落库后，需要把“用户实际上传字段 actualColumn -> 弹性域真实字段 elasticColumn（如 col1、col2）”的映射传入后续工作流。
3. 第二段加工是“弹性域临时表 -> 标准模板对应的 IT 临时表”，当前只先实现 AI 生成目标列表达式片段，不实现完整 SQL 执行。
4. AI 只允许生成 `expressionSql`，系统代码后续负责拼接 `INSERT INTO ... SELECT ... FROM ...`。
5. 写入目标表必须由系统根据标准模板决定，不能由 AI 生成或选择，避免误写正式业务表。

## 二、DSL 生成上下文相关待办

1. 新增或接入 `build_dsl_generation_context` 节点，把 `ProcessingRule`、`VagueBindingRecoResult`、`UserConfirmationResult`、actual/elastic 映射整合成 `DslGenerationContext`。
2. 明确 actual/elastic 映射在 state 中的字段名、写入时机和来源。
3. `DslGenerationContext` 需要按 `targetColumn` 聚合，且每个目标列只暴露 DSL 生成所需信息。
4. `TargetColumnGenerationContext.actualColumnMappings` 中，`actualColumn` 用于 AI 理解业务语义，`elasticColumn` 用于生成真实可执行 SQL 表达式。
5. 继续保持边界：`VagueBindingRecoItem` 只做字段绑定识别，不承载加工规则。

## 三、AI 生成 SQL 表达式片段相关待办

1. 接入 `ProcessingPlanDslGenerationService` 到 StateGraph，建议节点名为 `compile_processing_plan_dsl`。
2. 确认 `ProcessingPlanDsl` 第一版只支持 `DIRECT_MAPPING`、`CASE_WHEN`、`CONSTANT`。
3. 增强 `ProcessingPlanDslValidator`，后续可引入 SQL AST 解析器做更强的表达式级校验。
4. 增加测试用例覆盖：直接映射、CASE WHEN、常量、越权字段、完整 SQL 注入、actualColumn 误用。
5. 建立少量真实业务样例，持续校验 AI 是否稳定遵守“只生成表达式片段”的边界。

## 四、系统拼接完整 SQL 与 DWS 执行待办

1. 设计完整 SQL 拼接服务：输入 `ProcessingPlanDsl`、源弹性域临时表、目标 IT 临时表，输出系统拼接后的可执行 SQL。
2. 拼接 SQL 时必须由系统追加目标列清单、源表名、任务过滤条件、批次号等安全条件。
3. 目标表只能是标准模板预先配置的 AI/IT 临时表，不能是正式业务表。
4. DWS 执行服务需要支持事务、幂等、任务状态更新、失败回滚或补偿策略。
5. 对几十万行数据，优先让 DWS 执行集合式 SQL，不把全量数据拉回 Java 内存加工。

## 五、结果校验与用户体验待办

1. 加工后提供结果预览能力，让用户先看标准模板临时表中的抽样结果。
2. 增加行级错误或异常值统计，例如无法匹配 CASE WHEN、必填字段为空、类型转换失败。
3. 明确从 IT 临时表发布到正式表的人工确认或审批流程。
4. 增加任务审计记录：输入文件、模板识别结果、用户确认结果、DSL、最终执行 SQL、执行结果。
