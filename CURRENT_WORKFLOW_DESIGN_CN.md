# 当前工作流设计说明

## 1. 文档目的

本文档说明当前项目中数据处理工作流的最新设计与实现方式，重点讲清楚：

1. 整个工作流的节点顺序
2. 每个节点的职责
3. 每个节点的输入字段与输出字段
4. 每个节点涉及的 Service 服务
5. 模糊绑定识别 `VagueBindingRecoService` 在整体流程中的位置

本文档以当前代码实现为准，不以历史方案设计文档为准。

---

## 2. 整体工作流概览

当前工作流由 `DataProcessingStateGraphDefinition` 定义，主链路如下：

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

其中有两条运行路径：

1. 不需要用户确认：`START -> build_input_snapshot -> template_recognition -> build_user_confirmation_request -> need_user_confirmation_router -> rule_drafting -> complete -> END`
2. 需要用户确认：`START -> build_input_snapshot -> template_recognition -> build_user_confirmation_request -> need_user_confirmation_router -> wait_user_confirmation -> apply_user_confirmation -> rule_drafting -> complete -> END`

工作流在 `wait_user_confirmation` 节点前会中断，等待前端提交用户确认结果后，再从该节点继续恢复执行。

---

## 3. 核心分层职责

### 3.1 模板识别层

对应服务：

1. `TemplateRecognitionService`

职责：

1. 只负责识别当前上传内容最匹配的预置用户模板
2. 只负责返回该预置用户模板对应的标准模板关系
3. 不再负责识别模糊映射字段

### 3.2 规则加载层

对应组件：

1. `ProcessingRuleLoader`

职责：

1. 只负责从 `resources/rules/{presetTemplateCode}.md` 读取规则文档
2. 只负责把 Markdown 解析为 `ProcessingRuleDocument`
3. 不负责模板识别
4. 不负责用户确认逻辑

### 3.3 模糊绑定识别层

对应服务：

1. `VagueBindingRecoService`

职责：

1. 根据规则依赖输入列和当前上传表头，调用 AI 识别绑定关系
2. 对每个规则依赖列输出结构化识别结果
3. 判断每个规则依赖列当前是“已明确”“需要确认”还是“缺失”
4. 不直接组装前端确认项
5. 不直接生成 DSL

### 3.4 结构化确认组装层

对应服务：

1. `StructuredConfirmationService`

职责：

1. 调用 `ProcessingRuleLoader` 读取规则
2. 调用 `VagueBindingRecoService` 获取模糊绑定识别结果
3. 将需要用户确认的绑定项转成 `mappingConfirmations`
4. 将规则中显式声明的 `USER_CONFIRM` 项转成 `optionConfirmations` 或 `inputConfirmations`
5. 校验前端提交的用户确认结果

### 3.5 DSL 生成层

对应服务：

1. `RuleDraftingService`

职责：

1. 结合输入快照、模板识别结果、用户确认结果、模板目录、规则文档生成最终 DSL
2. 校验 DSL 结果结构最小合法性

---

## 4. 运行时状态字段说明

工作流运行时主要使用以下状态字段：

1. `task_id`：任务唯一标识
2. `input_type`：输入类型，例如 Excel 导入
3. `source_headers`：上传文件解析出的原始表头列表
4. `sample_rows`：上传文件抽样后的样例数据行
5. `input_snapshot`：标准化后的输入快照对象
6. `template_recognition_result`：模板识别结果对象
7. `user_confirmation_items`：前端需要展示的结构化确认项
8. `user_confirmation_request`：前端提交的用户确认请求对象
9. `user_confirmation_result`：后端校验通过后的用户确认结果对象
10. `final_dsl`：最终生成的 DSL 对象
11. `workflow_stage`：当前工作流阶段
12. `current_node`：当前节点名称
13. `retry_count`：重试次数
14. `error_messages`：错误信息列表
15. `trace_logs`：执行轨迹日志列表
16. `next_node`：下一节点名称

---

## 5. 节点详细说明

## 5.1 `build_input_snapshot`

### 节点职责

将初始任务会话中的输入信息转换为标准化输入快照，供后续模板识别、模糊绑定识别、DSL 生成统一使用。

### 涉及服务数量

1 个服务

### 涉及服务

1. `BuildInputSnapshotNode`
2. `InputSnapshotService`

### 输入字段

1. `task_id`：任务唯一标识
2. `input_type`：输入类型
3. `source_headers`：上传文件原始表头
4. `sample_rows`：上传文件样例数据

### 输出字段

1. `input_snapshot`
   含义：
   1. `taskId`：任务唯一标识
   2. `inputType`：输入类型
   3. `normalizedHeaders`：标准化后的表头列表
   4. `sampleRows`：样例数据
2. `workflow_stage`：更新为“已构建输入快照”
3. `current_node`：更新为 `build_input_snapshot`
4. `trace_logs`：追加“已构建输入快照”的轨迹日志

---

## 5.2 `template_recognition`

### 节点职责

根据输入快照与模板目录识别当前命中的预置用户模板。

### 涉及服务数量

1 个主服务，内部依赖 3 个辅助服务/能力

### 涉及服务

1. `TemplateRecognitionNode`
2. `TemplateRecognitionService`
3. `TemplateCatalogService`
4. `PromptTemplateService`
5. `ChatModel`

### 输入字段

1. `input_snapshot`
   含义：
   1. `taskId`：任务唯一标识
   2. `inputType`：输入类型
   3. `normalizedHeaders`：标准化后的表头列表
   4. `sampleRows`：样例数据

### 输出字段

1. `template_recognition_result`
   含义：
   1. `presetTemplateCode`：识别出的预置用户模板编码
   2. `standardTemplateCode`：对应标准模板编码
   3. `sceneCode`：场景编码
   4. `countryCode`：国家或地区编码
   5. `confidence`：识别置信度
   6. `needUserConfirm`：模板识别本身是否仍需人工复核
   7. `reason`：模板识别原因说明
2. `workflow_stage`：更新为“已识别模板”
3. `current_node`：更新为 `template_recognition`
4. `next_node`：更新为 `build_user_confirmation_request`
5. `trace_logs`：追加“已完成模板识别”的轨迹日志

### 特别说明

1. 本节点不再输出“未解决目标字段列表”
2. 本节点不负责模糊映射识别
3. 本节点只回答“这是什么模板”

---

## 5.3 `build_user_confirmation_request`

### 节点职责

基于已识别模板、规则文档、上传表头和样例数据，生成结构化用户确认项。

### 涉及服务数量

1 个主服务，内部依赖 2 个关键服务

### 涉及服务

1. `BuildUserConfirmationRequestNode`
2. `StructuredConfirmationService`
3. `ProcessingRuleLoader`
4. `VagueBindingRecoService`

### 输入字段

1. `task_id`：任务唯一标识
2. `input_type`：输入类型
3. `source_headers`：上传文件原始表头
4. `sample_rows`：上传文件样例数据
5. `template_recognition_result`
   含义：
   1. `presetTemplateCode`：已识别预置模板编码
   2. `standardTemplateCode`：对应标准模板编码
   3. `sceneCode`：场景编码
   4. `countryCode`：国家或地区编码
   5. `confidence`：识别置信度
   6. `needUserConfirm`：模板识别是否需复核
   7. `reason`：模板识别原因

### 输出字段

1. `user_confirmation_items`
   含义：
   1. `taskId`：任务唯一标识
   2. `presetTemplateCode`：当前预置模板编码
   3. `standardTemplateCode`：当前标准模板编码
   4. `mappingConfirmations`：需要用户确认的映射绑定项列表
   5. `optionConfirmations`：需要用户选择枚举值的确认项列表
   6. `inputConfirmations`：需要用户手动输入值的确认项列表
2. `workflow_stage`
   含义：
   1. 如果确认项非空，更新为“需要用户确认”
   2. 如果确认项为空，保持“已识别模板”
3. `current_node`：更新为 `build_user_confirmation_request`
4. `next_node`：更新为 `need_user_confirmation_router`
5. `trace_logs`：追加“已构建结构化用户确认请求”的轨迹日志

### 节点内部流程

1. 使用 `ProcessingRuleLoader` 加载当前模板对应规则文档
2. 使用 `VagueBindingRecoService` 识别每个规则依赖输入列与上传表头的绑定关系
3. 将 `NEEDS_CONFIRMATION` 的绑定识别结果转为 `mappingConfirmations`
4. 将规则中 `USER_CONFIRM` 且带 `options` 的规则转为 `optionConfirmations`
5. 将规则中 `USER_CONFIRM` 且不带 `options` 的规则转为 `inputConfirmations`

### `VagueBindingRecoService` 输出结构

`VagueBindingRecoService` 输出 `VagueBindingRecoResult`：

1. `taskId`：任务唯一标识
2. `presetTemplateCode`：当前预置模板编码
3. `items`：绑定识别结果列表

每个 `item` 的字段含义：

1. `targetColumn`：当前规则最终要生成的目标列
2. `ruleType`：规则类型，例如 `DIRECT_MAPPING`、`AI_DERIVED`
3. `sourceColumn`：当前规则依赖的输入列名，也就是规则中的单个 `sourceColumns` 成员
4. `status`：绑定识别状态
   1. `CONFIRMED`：绑定关系已明确
   2. `NEEDS_CONFIRMATION`：存在多个合理候选，需要用户确认
   3. `MISSING`：没有找到合适的上传表头
5. `selectedHeader`：当状态为 `CONFIRMED` 时，AI 识别出的唯一绑定表头
6. `candidateHeaders`：当状态为 `NEEDS_CONFIRMATION` 时，AI 给出的候选上传表头列表
7. `reason`：AI 对本次识别判断的原因说明

---

## 5.4 `need_user_confirmation_router`

### 节点职责

根据 `user_confirmation_items` 是否为空，决定后续进入“等待用户确认”还是“直接生成 DSL”。

### 涉及服务数量

0 个服务

### 输入字段

1. `user_confirmation_items`
   含义：
   1. `mappingConfirmations`：映射确认项列表
   2. `optionConfirmations`：枚举确认项列表
   3. `inputConfirmations`：输入确认项列表

### 输出字段

1. `current_node`：更新为 `need_user_confirmation_router`
2. `next_node`
   含义：
   1. 如果确认项存在，设置为 `wait_user_confirmation`
   2. 如果确认项为空，设置为 `rule_drafting`
3. `trace_logs`：追加“已完成用户确认路由判断”的轨迹日志

### 特别说明

1. 本节点不看 `TemplateRecognitionResult.needUserConfirm`
2. 本节点只看真正生成出来的结构化确认项是否为空

---

## 5.5 `wait_user_confirmation`

### 节点职责

进入等待用户确认状态，并在该节点前中断工作流执行。

### 涉及服务数量

0 个服务

### 输入字段

1. `user_confirmation_items`：前端需要展示的结构化确认项

### 输出字段

1. `current_node`：更新为 `wait_user_confirmation`
2. `workflow_stage`：更新为“需要用户确认”
3. `next_node`：更新为 `apply_user_confirmation`
4. `trace_logs`：追加“等待结构化用户确认”的轨迹日志

### 特别说明

1. 工作流编译配置中使用 `interruptBefore(wait_user_confirmation)` 实现暂停
2. 前端提交确认结果后，再由 `resume` 恢复执行

---

## 5.6 `apply_user_confirmation`

### 节点职责

接收前端提交的结构化确认结果，做字段完整性校验、候选合法性校验，并转成后端可消费的确认结果对象。

### 涉及服务数量

1 个服务

### 涉及服务

1. `ApplyUserConfirmationNode`
2. `StructuredConfirmationService`

### 输入字段

1. `user_confirmation_items`
   含义：
   1. 待确认映射项列表
   2. 待确认选项项列表
   3. 待确认输入项列表
2. `user_confirmation_request`
   含义：
   1. `taskId`：任务唯一标识
   2. `presetTemplateCode`：当前预置模板编码
   3. `standardTemplateCode`：当前标准模板编码
   4. `mappingConfirmations`：用户提交的映射确认结果列表
   5. `optionConfirmations`：用户提交的枚举确认结果列表
   6. `inputConfirmations`：用户提交的输入确认结果列表

### 输出字段

1. `user_confirmation_result`
   含义：
   1. `taskId`：任务唯一标识
   2. `presetTemplateCode`：当前预置模板编码
   3. `standardTemplateCode`：当前标准模板编码
   4. `mappingConfirmations`：校验通过后的映射确认结果列表
   5. `optionConfirmations`：校验通过后的枚举确认结果列表
   6. `inputConfirmations`：校验通过后的输入确认结果列表
2. `workflow_stage`：更新为“已完成用户确认”
3. `current_node`：更新为 `apply_user_confirmation`
4. `next_node`：更新为 `rule_drafting`
5. `trace_logs`：追加“已应用结构化用户确认”的轨迹日志

### 当前实现限制

1. 当前 `MISSING` 类型的绑定识别结果尚未转成专门的用户确认项
2. 当前 DSL 生成阶段尚未消费 `CONFIRMED` 的规则绑定识别结果
3. 这两部分属于下一步可继续扩展的范围

---

## 5.7 `rule_drafting`

### 节点职责

在所有必要上下文准备完毕后，调用 AI 生成最终 DSL。

### 涉及服务数量

1 个主服务，内部依赖 3 个辅助服务/能力

### 涉及服务

1. `RuleDraftingNode`
2. `RuleDraftingService`
3. `TemplateCatalogService`
4. `ProcessingRuleLoader`
5. `PromptTemplateService`
6. `ChatModel`

### 输入字段

1. `input_snapshot`
   含义：
   1. 任务唯一标识
   2. 输入类型
   3. 标准化表头列表
   4. 样例数据
2. `template_recognition_result`
   含义：
   1. 当前预置模板编码
   2. 当前标准模板编码
   3. 场景编码
   4. 国家或地区编码
   5. 识别置信度
   6. 是否需要模板复核
   7. 模板识别原因
3. `user_confirmation_result`
   含义：
   1. 用户确认后的映射结果
   2. 用户确认后的枚举结果
   3. 用户确认后的输入结果

### 输出字段

1. `final_dsl`
   含义：
   1. `presetTemplateCode`：当前预置模板编码
   2. `dslContent`：最终 DSL 内容
   3. `reason`：DSL 生成原因说明
2. `workflow_stage`：更新为“已生成 DSL”
3. `current_node`：更新为 `rule_drafting`
4. `next_node`：更新为 `complete`
5. `trace_logs`：追加“已生成最终 DSL”的轨迹日志

---

## 5.8 `complete`

### 节点职责

标记工作流完成。

### 涉及服务数量

0 个服务

### 输入字段

1. `final_dsl`：最终 DSL 结果

### 输出字段

1. `workflow_stage`：更新为“已完成”
2. `current_node`：更新为 `complete`
3. `trace_logs`：追加“工作流已完成”的轨迹日志

---

## 6. 每个节点与 Service 的对应关系汇总

1. `build_input_snapshot`
   1. `BuildInputSnapshotNode`
   2. `InputSnapshotService`
2. `template_recognition`
   1. `TemplateRecognitionNode`
   2. `TemplateRecognitionService`
   3. `TemplateCatalogService`
   4. `PromptTemplateService`
   5. `ChatModel`
3. `build_user_confirmation_request`
   1. `BuildUserConfirmationRequestNode`
   2. `StructuredConfirmationService`
   3. `ProcessingRuleLoader`
   4. `VagueBindingRecoService`
   5. `PromptTemplateService`
   6. `ChatModel`
4. `need_user_confirmation_router`
   1. 无
5. `wait_user_confirmation`
   1. 无
6. `apply_user_confirmation`
   1. `ApplyUserConfirmationNode`
   2. `StructuredConfirmationService`
7. `rule_drafting`
   1. `RuleDraftingNode`
   2. `RuleDraftingService`
   3. `TemplateCatalogService`
   4. `ProcessingRuleLoader`
   5. `PromptTemplateService`
   6. `ChatModel`
8. `complete`
   1. 无

---

## 7. 实际例子

## 7.1 当前仓库中的真实规则例子

假设上传文件被识别为预置模板 `client-template-b`，对应规则如下：

1. `invoice_no` 目标列使用规则 `DIRECT_MAPPING`
   1. 规则依赖输入列：`invoice_id`
2. `tax_a` 目标列使用规则 `AI_DERIVED`
   1. 规则依赖输入列：`tax_code`
3. `country` 目标列使用规则 `USER_CONFIRM`
   1. 需要用户从 `CN`、`US` 中选择
4. `amount` 目标列使用规则 `DIRECT_MAPPING`
   1. 规则依赖输入列：`amount_total`

上传表头如下：

1. `invoice_id`
2. `tax_code`
3. `amount_total`

样例数据如下：

1. 第 1 行：`invoice_id=INV-001, tax_code=Y, amount_total=100`

则工作流行为如下：

1. `template_recognition`
   1. 识别出 `presetTemplateCode=client-template-b`
   2. 识别出 `standardTemplateCode=tax-standard-cn`
2. `build_user_confirmation_request`
   1. `VagueBindingRecoService` 对 `invoice_id`、`tax_code`、`amount_total` 分别识别为 `CONFIRMED`
   2. 因为没有 `NEEDS_CONFIRMATION`，所以没有 `mappingConfirmations`
   3. 因为 `country` 是 `USER_CONFIRM` 规则，所以生成一个 `optionConfirmation`
3. `need_user_confirmation_router`
   1. 因为确认项不为空，所以进入 `wait_user_confirmation`
4. 用户选择 `country=CN` 后
5. `apply_user_confirmation`
   1. 校验选项合法
6. `rule_drafting`
   1. 结合确认结果生成最终 DSL

这个例子说明：

1. 模板识别只负责识别模板
2. 规则依赖绑定识别由 `VagueBindingRecoService` 负责
3. 显式用户确认项仍由规则驱动

## 7.2 模糊绑定识别例子

假设某条规则如下：

1. `targetColumn = customer_level`
2. `ruleType = AI_DERIVED`
3. `sourceColumns = level_code`

上传表头如下：

1. `level code`
2. `level_code_backup`
3. `amount`

则 `VagueBindingRecoService` 可能输出如下识别结果：

1. `targetColumn`：`customer_level`
2. `ruleType`：`AI_DERIVED`
3. `sourceColumn`：`level_code`
4. `status`：`NEEDS_CONFIRMATION`
5. `selectedHeader`：空
6. `candidateHeaders`
   1. `level code`
   2. `level_code_backup`
7. `reason`：两个上传表头都可能对应规则依赖字段，无法唯一判断

随后 `StructuredConfirmationService` 会把它转成一个 `mappingConfirmation`，前端展示给用户选择。

这个例子说明：

1. 模糊绑定识别是围绕“规则依赖输入列”进行的
2. 不是围绕“目标字段名称像不像”进行的
3. 真正需要用户确认时，才会生成映射确认项

---

## 8. 当前设计的结论

1. `TemplateRecognitionService` 只做模板识别
2. `ProcessingRuleLoader` 只做规则读取解析
3. `VagueBindingRecoService` 负责 AI 模糊绑定识别
4. `StructuredConfirmationService` 只做结构化确认项组装与确认结果校验
5. `RuleDraftingService` 只做最终 DSL 生成

这就是当前项目最新的工作流职责划分。
