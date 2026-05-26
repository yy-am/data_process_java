# 当前工作流设计说明

## 1. 文档目的

本文档说明当前项目中数据加工工作流的最新设计与实现边界。本文档以当前代码和刚确认的设计为准，不再以历史设计文档为准。

当前项目的一句话目标是：读取用户上传的 Excel 文件，按照预置加工规则和必要的用户确认结果生成确定性 DSL，后续再基于 DSL 将全量数据导入 DWS 并执行转换。

## 2. 当前主工作流

当前 StateGraph 主流程由 `DataProcessingStateGraphDefinition` 定义。

主链路如下：

1. `START`
2. `build_input_snapshot`
3. `template_recognition`
4. `build_user_confirmation_request`
5. `need_user_confirmation_router`
6. `wait_user_confirmation`
7. `apply_user_confirmation`
8. `rule_drafting`
9. `complete`
10. `END`

如果没有任何用户确认项，流程为：

```text
START
-> build_input_snapshot
-> template_recognition
-> build_user_confirmation_request
-> need_user_confirmation_router
-> rule_drafting
-> complete
-> END
```

如果存在用户确认项，流程为：

```text
START
-> build_input_snapshot
-> template_recognition
-> build_user_confirmation_request
-> need_user_confirmation_router
-> wait_user_confirmation
-> apply_user_confirmation
-> rule_drafting
-> complete
-> END
```

`wait_user_confirmation` 节点通过 StateGraph interrupt 机制暂停，等待前端提交用户确认结果后再恢复执行。

## 3. 已确认的分层职责

### 3.1 输入快照层

对应节点：`build_input_snapshot`

中文职责：把任务会话中的 Excel 表头和样例行整理成标准输入快照。

主要产物：`InputSnapshot`

字段说明：

1. `taskId`：当前任务 ID。
2. `inputType`：输入来源类型，例如 `excel-import`。
3. `normalizedHeaders`：上传 Excel 解析后的表头列表。
4. `sampleRows`：上传 Excel 抽样后的样例行。

### 3.2 模板识别层

对应节点：`template_recognition`

中文职责：根据输入快照识别当前上传文件属于哪个预置用户模板，以及对应哪个标准模板。

主要产物：`TemplateRecognitionResult`

字段说明：

1. `presetTemplateCode`：已识别出的预置用户模板编码。
2. `standardTemplateCode`：已匹配的标准模板编码。
3. `sceneCode`：业务场景编码。
4. `countryCode`：国家或地区编码。
5. `confidence`：模板识别置信度。
6. `needUserConfirm`：模板识别结果本身是否仍需要人工复核。
7. `reason`：模型给出的识别原因说明。

### 3.3 用户确认准备层

对应节点：`build_user_confirmation_request`

中文职责：该节点名称不变，但内部完成三件事：

1. 加载本次任务对应的完整加工规则。
2. 识别规则源字段与用户上传 Excel 表头之间的绑定关系。
3. 构建需要前端展示的结构化用户确认项。

该节点输出并写回 StateGraph 状态：

1. `processing_rule`
2. `vague_binding_reco_result`
3. `user_confirmation_items`

### 3.4 加工规则层

核心类：`ProcessingRule`

中文职责：表示某个预置用户模板对应的完整加工规则。

字段说明：

1. `presetTemplateCode`：预置用户模板编码。
2. `presetTemplateName`：预置用户模板名称。
3. `standardTemplateCode`：对应的标准模板编码。
4. `description`：加工规则整体说明。
5. `ruleItems`：目标列加工规则列表。

核心类：`ProcessingRuleItem`

中文职责：表示单个目标列的加工规则。

字段说明：

1. `targetColumn`：目标列名。
2. `ruleType`：规则类型。
3. `sourceColumns`：该目标列依赖的预置源字段列表。
4. `description`：单列规则说明。
5. `ruleGuide`：规则指导，主要用于 `CASE_WHEN`。
6. `example`：规则示例，主要用于 `CASE_WHEN`。
7. `userInputField`：历史兼容字段，后续用户确认统一以 `targetColumn` 为准。
8. `options`：用户选值确认的允许值列表。
9. `inputHint`：用户输入确认的提示。

当前第一版规则类型：

1. `DIRECT_MAPPING`
2. `CASE_WHEN`
3. `USER_CONFIRM`

### 3.5 字段绑定识别层

核心服务：`VagueBindingRecoService`

中文职责：只负责识别规则源字段与用户上传 Excel 表头之间的绑定关系。

核心产物：`VagueBindingRecoResult`

字段说明：

1. `taskId`：当前任务 ID。
2. `presetTemplateCode`：当前预置用户模板编码。
3. `items`：字段绑定识别项列表。

核心产物：`VagueBindingRecoItem`

字段说明：

1. `targetColumn`：该规则项生成的目标列名。
2. `ruleType`：确定的规则类型，例如 `DIRECT_MAPPING` 或 `CASE_WHEN`。
3. `sourceColumn`：规则项中声明的源字段。
4. `status`：绑定识别状态。
5. `selectedHeader`：绑定关系明确时选中的上传表头。
6. `candidateHeaders`：需要用户确认时提供的候选上传表头。
7. `reason`：识别原因说明。

重要边界：

1. `VagueBindingRecoItem` 只表达字段绑定识别结果。
2. `VagueBindingRecoItem` 不承载规则说明。
3. 规则说明只属于 `ProcessingRule` 和 `ProcessingRuleItem`。

### 3.6 用户确认层

核心类：`UserConfirmationItems`

中文职责：展示给前端的结构化确认项集合。

包含三类确认：

1. `mappingConfirmations`：字段模糊映射确认。
2. `optionConfirmations`：目标列选值确认。
3. `inputConfirmations`：目标列输入确认。

核心类：`OptionConfirmation`

字段说明：

1. `targetColumn`：由该确认项填充的目标列名。
2. `question`：展示给用户的问题文案。
3. `options`：允许选择的选项列表。
4. `selectedValue`：用户确认后的选中值，待确认阶段为空。

核心类：`InputConfirmation`

字段说明：

1. `targetColumn`：由该确认项填充的目标列名。
2. `question`：展示给用户的问题文案。
3. `hint`：展示给用户的输入提示。
4. `inputValue`：用户确认后的输入值，待确认阶段为空。

核心类：`UserConfirmationResult`

中文职责：用户提交并通过后端校验后的确认结果。

## 4. DSL 生成上下文设计

该层是后续设计，当前已完成领域模型定义，尚未接入 StateGraph 节点。

核心类：`DslGenerationContext`

中文职责：字段绑定识别、用户确认与 DSL 生成之间的边界对象。它按目标列聚合，让后续 DSL 生成节点可以逐个目标列生成确定的加工计划。

字段说明：

1. `taskId`：当前任务 ID。
2. `presetTemplateCode`：已识别出的预置用户模板编码。
3. `standardTemplateCode`：已匹配的标准模板编码。
4. `targetColumns`：按目标列聚合后的 DSL 生成上下文。

核心类：`TargetColumnGenerationContext`

中文职责：单个目标列的 DSL 生成上下文。

字段说明：

1. `targetColumn`：目标列名。
2. `ruleType`：规则类型，例如 `DIRECT_MAPPING`、`CASE_WHEN` 或 `USER_CONFIRM`。
3. `actualColumns`：用户实际上传文件中参与该目标列生成的字段列表。
4. `ruleGuide`：规则指导，主要用于 `CASE_WHEN`。
5. `example`：规则示例，主要用于 `CASE_WHEN`。
6. `confirmedValue`：用户确认值，主要用于 `USER_CONFIRM`。

重要边界：

1. `ProcessingRuleItem.sourceColumns` 只存在于规则层和上下文构建过程。
2. `TargetColumnGenerationContext` 不再暴露 `sourceColumn`。
3. DSL 生成阶段只关心用户实际上传文件中的 `actualColumns`。

## 5. 未来节点设计

后续建议新增节点：`build_dsl_generation_context`

中文职责：确定性整合 `ProcessingRule`、`VagueBindingRecoResult` 和 `UserConfirmationResult`，生成 `DslGenerationContext`。

输入：

1. `processing_rule`
2. `vague_binding_reco_result`
3. `user_confirmation_result`

输出：

1. `dsl_generation_context`

整合规则待后续继续评审：

1. `CONFIRMED` 状态如何取 `selectedHeader`。
2. `NEEDS_CONFIRMATION` 状态如何使用用户确认结果。
3. `MISSING` 状态如何中断流程。
4. `USER_CONFIRM` 如何转成 `confirmedValue`。
5. `actualColumns` 顺序如何与规则中的 `sourceColumns` 对齐。

## 6. 当前 StateGraph 状态字段

当前已使用或已准备的主要状态字段：

1. `task_id`：当前任务 ID。
2. `input_type`：输入来源类型。
3. `source_headers`：上传 Excel 解析出的表头。
4. `sample_rows`：上传 Excel 抽样后的样例行。
5. `input_snapshot`：标准化输入快照。
6. `template_recognition_result`：模板识别结果。
7. `processing_rule`：本次任务确定的完整加工规则。
8. `vague_binding_reco_result`：完整字段绑定识别结果。
9. `user_confirmation_items`：需要前端展示的结构化确认项。
10. `user_confirmation_request`：前端提交的原始确认请求。
11. `user_confirmation_result`：后端校验通过后的确认结果。
12. `final_dsl`：当前旧 DSL 生成结果。
13. `workflow_stage`：当前工作流阶段。
14. `current_node`：当前节点名称。
15. `retry_count`：重试次数。
16. `error_messages`：错误信息列表。
17. `trace_logs`：执行轨迹日志列表。
18. `next_node`：下一个节点名称。

## 7. 当前已确认但暂缓深入的部分

### 7.1 ProcessingPlanDsl

当前已定义第一版加工 DSL 模型和操作白名单。

操作白名单：

1. `DIRECT_MAPPING`
2. `CASE_WHEN`
3. `CONSTANT`

暂缓内容：

1. DSL 结构细节继续评审。
2. DSL 到 DWS SQL 的编译策略继续评审。
3. Excel 全量导入 DWS staging 的表结构继续评审。
4. DWS 执行、错误行处理、任务进度统计继续评审。

### 7.2 旧 rule_drafting 节点

当前代码仍保留旧 `rule_drafting` 节点和 `RuleDraftingService`，它仍会调用模型生成 `FinalDsl`。

后续目标是逐步替换为：

```text
build_dsl_generation_context
-> compile_processing_plan_dsl
-> import_excel_to_staging
-> generate_dws_sql
-> execute_dws_sql
```

其中 DWS 相关阶段暂不在本轮展开。

## 8. 当前设计结论

1. `ProcessingRule` 表示完整加工规则。
2. `ProcessingRuleItem` 表示单个目标列的规则定义。
3. `VagueBindingRecoItem` 只表示字段绑定识别结果，不承载规则。
4. `UserConfirmationResult` 只表示用户确认后的结果。
5. `DslGenerationContext` 是 DSL 生成的边界上下文。
6. `TargetColumnGenerationContext.actualColumns` 表示用户实际上传文件中参与目标列生成的字段。
7. `ProcessingPlanDsl` 和 DWS 执行链路后续继续评审。
