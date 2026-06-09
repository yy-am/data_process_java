# 数据加工 Agent 方案设计 PPT 结构

## 1. 标题页

标题：数据加工 Agent 方案设计

副标题：面向 Excel 数据转换的可控型 Agent 架构

开场表达：

这个方案不是让 AI 直接加工 Excel，而是让 AI 在受控边界内生成加工规则，由确定性工具完成校验、执行和恢复。

## 2. 业务背景与设计目标

一句话需求：

读取用户上传的 Excel 文件，按照预置规则，结合用户干预，完成数据加工转换，写入数据库并导出结果。

设计目标：

- 支持多模板、多规则的数据加工自动化。
- 支持用户前置确认和后续多轮规则修正。
- 避免 AI 直接处理全量数据和核心执行动作。
- 让流程可恢复、可校验、可自修复。

## 3. 整体 Agent 架构设计

建议画分层架构图：

```text
前端交互层
上传 Excel / 用户确认 / 多轮对话 / 结果预览

接口层
文件解析接口 / Agent SSE 运行接口 / 用户确认提交接口

Agent 编排层
ReAct Agent / Skill 运行规约 / 工具调用决策

确定性 Tool 层
模板加载 / 字段绑定校验 / 确认项生成 / SQL 上下文准备 / SQL 拼接 / 执行 / 导出

状态层
Task State / 阶段流转 / 上下文沉淀 / 错误与执行轨迹

数据层
原始 Excel / stagingTable / resultTable / 导出 Excel
```

核心结论：

AI 是受约束的语义决策器，确定性代码负责状态、校验和执行。

## 4. Agent 与 Tool 的职责边界

建议做左右对比。

Agent 负责：

- 模板识别。
- 字段语义匹配。
- 生成字段绑定计划。
- 生成目标列 SQL 表达式片段。
- 理解用户新规则。
- 根据错误反馈重新推理。

Tool / 代码负责：

- 文件、模板、规则、数据库访问。
- 状态保存与恢复。
- 字段绑定、确认结果、SQL 片段校验。
- 生成确认项。
- 拼接完整 SQL。
- 执行落表和导出。

页尾结论：

让 AI 做语义判断，让代码做边界控制和最终执行。

## 5. 关键设计一：AI 不直接加工数据，数据库执行，代码控 SQL

一句话主张：

全量数据进入数据库，AI 只基于摘要和规则生成目标列级 SQL 片段；完整 SQL 结构、表名、执行动作全部由代码控制。

左侧：数据处理边界

```text
Excel 全量数据
    ↓
stagingTable
    ↓
数据库侧 SQL 加工
    ↓
resultTable
```

AI 只看到：

- Excel 表头。
- 样例数据。
- 模板规则。
- 字段映射。
- 用户确认/干预结果。
- SQL 字段映射上下文。

右侧：SQL 生成边界

```text
AI 生成：
expressionSql

代码填槽：
INSERT INTO resultTable (...)
SELECT expressionSql AS targetColumn
FROM stagingTable
```

代码控制：

- `stagingTable`。
- `resultTable`。
- 目标列清单。
- SQL 骨架。
- SQL 安全校验。
- SQL 执行与落表。

AI 不允许生成：

- 完整 SQL。
- 表名/库名。
- `FROM / WHERE / JOIN / INSERT / UPDATE / DELETE / DROP`。
- 分号、多语句、注释。
- 未授权字段引用。

页尾结论：

把 AI 的影响范围压缩到字段表达式级别。

## 6. 关键设计二：用户干预机制，从前置确认到多轮规则校准

一句话主张：

用户干预不是单点确认，而是贯穿加工前、加工后和多轮修正的规则校准机制。

上半部分：两类干预。

当前前置确认：

- 字段映射歧义。
- 值集选择。
- 必填字段缺失或映射列空值。

未来多轮对话干预：

- 用户检查结果预览。
- 用户在对话框提出新加工规则。
- Agent 理解新规则并重新生成加工计划。
- Tool 校验并重新执行。

下半部分：多轮干预为什么可行。

```text
                 用户新诉求
                    ↓
              Agent 理解规则变化
                    ↓
          判断影响范围：全量 / 局部
             ↓                    ↓
路径 A：基于 stagingTable     路径 B：基于 resultTable
重算完整结果                 更新少数字段
             ↓                    ↓
       INSERT SELECT           UPDATE SET
             ↓                    ↓
          新结果表             局部更新结果表
                    ↓
                结果预览
```

底层支撑：

- 原始数据已落到 `stagingTable`，可以随时回到原始事实重算。
- 结果已落到 `resultTable`，可以只更新用户指出的问题字段。
- State 保存模板、字段映射、确认结果、表名和历史上下文。
- Agent 只重新生成受影响的 SQL 片段。

页尾结论：

前置确认解决执行前的不确定性，多轮对话解决执行后的需求偏差。

## 7. 关键设计三：全局 State + Tool 内聚上下文

一句话主张：

上下文不靠 AI 临场记忆，而是沉淀在 State 中；复杂参数不让 AI 全量组装，而是由 Tool 基于 `taskId` 自行读取。

State 承载：

- 当前阶段 stage。
- Excel 摘要。
- 模板识别结果。
- 模板包、规则、必填字段、值集。
- 字段绑定计划。
- 用户确认/干预结果。
- SQL 渲染结果。
- 错误信息和执行轨迹。

Tool 内聚：

- Agent 只传必要参数，如 `taskId`、用户决策、SQL 片段。
- Tool 通过 `taskId` 从 State 读取完整上下文。
- 非必要信息不暴露给 AI。
- 降低 AI 漏传、错传、乱组装复杂对象的风险。

页尾结论：

State 管上下文，Tool 管校验和执行，Agent 只做必要的语义决策。

## 8. 关键设计四：Agent 自修复机制

一句话主张：

失败不是终点，而是进入“错误反馈 → 重新推理 → 工具校验 → 再执行”的闭环。

重点场景：SQL 执行失败。

例如：

- 数值列混入字符串。
- 日期格式不统一。
- 类型转换失败。
- 空值处理不符合数据库要求。

处理方式：

- 捕获数据库报错。
- 结合目标列、原 SQL 片段、字段映射、样例值。
- Agent 重新生成更稳健的表达式。
- Tool 校验并重新执行。

其他自修复场景：

- 字段绑定校验失败。
- SQL 片段安全校验失败。
- DSL 完整性校验失败。
- 模板识别置信度低。
- 结果预览不符合用户预期。

页尾结论：

Agent 不只负责第一次生成，也负责在失败反馈中持续修正。

## 9. 关键设计五：Skill 作为 Agent 运行规约

一句话主张：

Skill 不是普通提示词，而是把 Agent 约束成可恢复、可校验的状态机。

Skill 约束：

- 每次运行必须先准备任务上下文。
- 只能调用白名单工具。
- 不允许绕过工具校验。
- 不允许自行编造模板、表名、结果表、导出文件。
- 到达等待用户、SQL 已渲染、失败、完成等阶段必须停止。
- 每个阶段都有明确恢复入口。

页尾结论：

Skill 把模型的自由推理，收敛成可控的工程流程。

## 10. 端到端闭环总结

用一张总流程图把关键设计串起来：

```text
Excel 上传
  ↓
文件解析与摘要生成
  ↓
模板识别 + 字段绑定
  ↓
用户前置确认
  ↓
原始数据落 stagingTable
  ↓
Agent 生成 SQL 片段
  ↓
Tool 拼接、校验、执行 SQL
  ↓
结果写入 resultTable
  ↓
结果预览 / 导出 Excel
  ↓
用户多轮干预
  ↓
局部或全量重算
```

讲法：

每个关键设计都服务于同一个闭环：数据沉淀、规则生成、工具校验、用户干预、失败修复、重新加工。

## 11. 方案价值与后续建设方向

价值总结：

- 可控：AI 不直接处理全量数据，不生成完整 SQL。
- 可靠：State 恢复、Tool 校验、用户干预、自修复。
- 安全：参数来源受控，SQL 片段受限，数据库执行由代码掌控。
- 可演进：支持多轮对话、新规则追加、局部重算和全量重算。

后续建设方向：

- 多轮对话式加工规则追加。
- 结果预览与差异对比。
- 局部字段重算能力。
- 模板和规则版本管理。
- SQL 执行审计与可观测性。
- Tool 进一步内聚上下文，减少 Agent 参数复杂度。

收束语：

这个方案的核心不是让 AI 替代数据加工系统，而是把 AI 放进一个有状态、有边界、有反馈的加工闭环里。

---

# 附录：当前 Skill 流程摘要

本附录用于辅助理解 `data-processing-agent-skill` 的实际运行步骤。PPT 正文建议不要按本流程逐页展开，而是围绕架构设计和关键保障机制展开。

## 输入前提

进入 Agent 前，Excel 已经由外部接口解析完成。Agent 只接收：

- `taskId`：任务唯一标识。
- `parsedFileRef`：已解析 Excel 文件引用。
- `userConfirmationRequest`：可选，前端提交的用户确认结果。

Agent 不直接处理上传流，不直接解析 Excel，不直接读取本地文件系统。所有文件、模板、规则、状态、数据库访问都必须通过工具完成。

## 主要阶段

- `RECEIVED`：任务已创建，但尚未加载解析文件摘要。
- `TASK_CONTEXT_READY`：解析文件摘要已加载，可以进入模板识别。
- `TEMPLATE_CONTEXT_READY`：模板识别、标准模板、加工规则、必填字段和值集元数据已准备完成。
- `FIELD_BINDING_PLAN_READY`：字段绑定计划已接收并校验。
- `CONFIRMATION_ANALYZED`：用户确认项分析已完成。
- `USER_CONFIRMATION_REQUIRED`：需要前端用户确认。
- `USER_CONFIRMED`：用户确认已完成，或无需用户确认。
- `POST_CONFIRMATION_CONTEXT_READY`：确认后的加工上下文已校验通过。
- `SQL_GENERATION_CONTEXT_READY`：临时表和 SQL 生成上下文已准备完成。
- `PROCESSING_SQL_RENDERED`：完整 `INSERT SELECT` SQL 已拼接完成。
- `RESULT_TABLE_WRITTEN`：结果表写入已执行，可以导出新的 Excel 文件。
- `COMPLETED`：新的 Excel 文件已导出，任务完整完成。
- `FAILED`：任务失败。

## 流程步骤

### 1. 准备任务上下文

必须先调用：

```text
prepare_task_context(taskId, parsedFileRef)
```

工具负责加载或初始化任务状态，读取并保存 Excel 摘要，并返回当前阶段、下一步动作和标准响应。

如果当前阶段已经是等待用户确认、SQL 已渲染、失败或完成，则按 Skill 分支直接返回或进入对应恢复步骤。

### 2. 加载模板目录并识别模板

调用：

```text
load_template_catalog()
```

Agent 基于模板目录、Excel 表头和样例数据识别最匹配的预置模板，并构造 `templateRecognitionResult`。

识别完成后调用：

```text
accept_template_recognition(taskId, templateRecognitionResult)
```

工具负责校验模板关系，保存识别结果，并加载标准模板、加工规则、必填字段和值集元数据。

### 3. 生成字段绑定计划

Agent 基于 Excel 摘要和加工规则生成 `FieldBindingPlan`。

字段绑定计划只覆盖 `DIRECT_MAPPING` 和 `EXPR` 规则中的 `sourceColumns`，不覆盖 `USER_CONFIRM_OPTION` 和 `USER_CONFIRM_INPUT`。

每个绑定项只能是以下状态之一：

- `CONFIRMED`：可明确映射到某个 Excel 原始列。
- `NEEDS_CONFIRMATION`：存在多个候选列，需要用户确认。
- `MISSING`：没有可靠可映射列。

### 4. 提交字段绑定计划

调用：

```text
accept_field_binding_plan(taskId, fieldBindingPlan)
```

工具负责：

- 校验字段绑定计划覆盖范围和字段合法性。
- 保存字段绑定计划。
- 根据模糊字段映射生成映射确认项。
- 根据值集规则生成选择确认项。
- 根据手工输入规则或必填字段缺失生成输入确认项。
- 返回 `USER_CONFIRMATION_REQUIRED` 或 `USER_CONFIRMED`。

如果存在任何待确认项，必须停止并等待前端提交用户确认。

### 5. 处理用户确认提交

仅当阶段为 `USER_CONFIRMATION_REQUIRED` 且本次输入包含 `userConfirmationRequest` 时执行：

```text
submit_user_confirmation(taskId, userConfirmationRequest)
```

工具负责校验确认结果是否完整覆盖全部确认项，并保存用户确认决策。成功后进入 `USER_CONFIRMED`。

### 6. 准备确认后的加工上下文

当阶段为 `USER_CONFIRMED` 时调用：

```text
prepare_post_confirmation_context(taskId)
```

工具负责校验模板、规则、字段绑定计划、确认项和用户确认结果是否齐备。该工具不写临时表、不生成 SQL，只做确认后上下文完整性校验和阶段推进。

成功后进入 `POST_CONFIRMATION_CONTEXT_READY`。

### 7. 准备 SQL 生成上下文

当阶段为 `POST_CONFIRMATION_CONTEXT_READY` 时调用：

```text
prepare_sql_generation_context(taskId)
```

工具负责将原始 Excel 全量数据写入临时表，并返回确定性的 SQL 表上下文：

```json
{
  "taskId": "任务编号",
  "stagingTable": "临时表标识",
  "resultTable": "结果表标识",
  "loadedRows": 0,
  "columnMappings": [
    {
      "actualColumn": "Excel 原始列名",
      "elasticColumn": "临时表弹性字段名"
    }
  ]
}
```

该工具只返回 SQL 表上下文，不重复返回模板、规则、字段绑定计划或用户确认结果。

### 8. 生成目标列 SQL 表达式片段计划

Agent 同时使用确认后的业务上下文和 SQL 表上下文，生成 `processingPlanDsl`。

`processingPlanDsl` 的核心结构：

```json
{
  "dslVersion": "v1",
  "taskId": "任务编号",
  "presetTemplateCode": "预置模板编码",
  "standardTemplateCode": "标准模板编码",
  "columns": [
    {
      "targetColumn": "目标列",
      "actualColumnMappings": [
        {
          "actualColumn": "Excel 原始列",
          "elasticColumn": "临时表弹性字段"
        }
      ],
      "expressionSql": "只能放在 SELECT 列表中的 SQL 表达式片段"
    }
  ]
}
```

生成优先级从高到低：

1. 必填字段手工输入确认结果。
2. `USER_CONFIRM_INPUT` 用户输入结果。
3. `USER_CONFIRM_OPTION` 用户选择结果。
4. 用户确认后的字段映射选择结果。
5. `DIRECT_MAPPING` 已确认字段绑定结果。
6. `EXPR` 加工规则表达式。

### 9. 提交加工计划并写入结果表

调用：

```text
execute_processing_plan(taskId, sqlGenerationContext, processingPlanDsl)
```

工具负责：

- 校验 `processingPlanDsl` 完整性。
- 基于任务状态重建 DSL 校验上下文。
- 校验 SQL 表达式片段安全性。
- 使用 `resultTable` 和 `stagingTable` 拼接完整 `INSERT SELECT` SQL。
- 执行或交由确定性实现执行结果表写入。

如果只完成 SQL 渲染，阶段为 `PROCESSING_SQL_RENDERED`，不得伪造结果表写入或导出结果。

### 10. 基于结果表导出新的 Excel 文件

仅当结果表已经写入完成，且阶段为 `RESULT_TABLE_WRITTEN` 时调用：

```text
export_processed_excel(taskId, resultTable)
```

调用成功即表示导出请求已提交。导出文件生成可能是异步过程，Agent 不需要等待最终 `excelDocId`，也不得为了获取 `excelDocId` 继续追加推理或重复调用导出工具。

任务完整完成的标准是：

- 结果表已经成功写入数据。
- 已经成功调用 `export_processed_excel` 发起新的 Excel 文件导出。
