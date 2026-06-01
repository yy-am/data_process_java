# 数据加工 Agent 正式开发 PRD 需求清单

本文档基于当前 `src/main/java/com/example/dataprocess/agent` 与 `src/main/resources/agent/skills/data-processing-agent-skill/SKILL.md` 的实际实现整理，用于后续正式开发、排期、验收和联调。

## PRD-01 已解析 Excel 文件接入

需求描述：
【As】数据加工任务发起方
【I want】在 Agent 执行前通过接口上传或传入已经解析完成的 Excel 文件摘要和数据引用
【so that】Agent 不直接处理文件流，也不直接解析本地文件，而是基于标准化的 `parsedFileRef` 推进后续加工流程

验收标准：
【Given】前端或上游系统提交 Excel 文件，或提交包含 `taskId`、`inputType`、`sourceHeaders`、`sampleRows` 的任务请求
【When】系统调用解析文件接口或 Agent 任务启动接口
【Then】系统应生成或复用 `parsedFileRef`，并可通过工具读取 `ParsedExcelSummary`，包含原始表头、样例行、输入类型和工作表信息

## PRD-02 Agent 任务状态初始化与恢复

需求描述：
【As】数据加工平台
【I want】每个 Agent 任务都以 `taskId` 为唯一标识保存状态
【so that】任务可在用户确认前后、异常中断后继续恢复，而不是每次从头执行

验收标准：
【Given】一个新的或已有的 `taskId`
【When】Agent 调用 `prepare_task_context(taskId, parsedFileRef)`
【Then】系统应初始化或加载 `DataProcessingAgentState`，保存解析文件摘要，并返回当前阶段、下一步动作提示和标准 Agent 响应

## PRD-03 Skill 驱动的 ReAct 流程约束

需求描述：
【As】Agent 编排维护者
【I want】Agent 每次执行前读取并遵守 `data-processing-agent-skill`
【so that】模型工具调用顺序、分支处理、最终响应格式和错误处理保持稳定可控

验收标准：
【Given】Agent 收到一次数据加工任务请求
【When】ReactAgent 开始推理
【Then】模型必须先读取 skill，并只能按 skill 中声明的合并工具和阶段分支推进任务，最终输出合法的 `DataProcessingAgentResponse` JSON

## PRD-04 模板目录加载与模板识别

需求描述：
【As】数据加工业务人员
【I want】Agent 根据上传 Excel 表头和样例数据自动识别最匹配的预置模板
【so that】系统可以自动匹配标准模板、加工规则、公司和场景，减少人工选择成本

验收标准：
【Given】任务上下文已包含非空 `parsedExcelSummary.sourceHeaders` 和样例行
【When】Agent 调用 `load_template_catalog()` 并生成 `templateRecognitionResult`
【Then】`presetTemplateCode`、`standardTemplateCode`、`sceneCode`、`companyCode` 必须来自模板目录，且 `accept_template_recognition` 校验通过后保存模板识别结果和模板上下文

## PRD-05 模板包、加工规则、必填字段和值集加载

需求描述：
【As】加工规则开发者
【I want】模板识别通过后自动加载预置模板、标准模板、加工规则、必填字段和值集元数据
【so that】字段绑定、用户确认和 SQL 生成都能基于确定性业务上下文执行

验收标准：
【Given】`templateRecognitionResult` 已通过校验
【When】系统执行 `accept_template_recognition`
【Then】任务状态中应保存 `TemplateBundle`、`StandardRequiredFields`、`ValueSetMetadata`，阶段推进到 `TEMPLATE_CONTEXT_READY`

## PRD-06 字段绑定计划生成与校验

需求描述：
【As】数据加工 Agent
【I want】根据加工规则将规则源字段绑定到 Excel 原始列
【so that】后续 SQL 表达式只能基于经过校验的字段映射生成

验收标准：
【Given】模板上下文已准备完成，且加工规则中存在 `DIRECT_MAPPING` 或 `EXPR` 规则
【When】Agent 生成并提交 `FieldBindingPlan`
【Then】计划必须覆盖全部相关 `sourceColumns`，不得包含 `USER_CONFIRM_OPTION` 或 `USER_CONFIRM_INPUT` 取值确认，且所有 `selectedHeader` 和 `candidateHeaders` 必须来自本次 Excel 表头

## PRD-07 字段映射歧义确认

需求描述：
【As】业务用户
【I want】当 Agent 无法唯一判断某个规则源字段对应哪个 Excel 原始列时，由前端展示候选项让我确认
【so that】字段映射不确定时不会被模型强行猜测

验收标准：
【Given】`FieldBindingPlan` 中存在 `NEEDS_CONFIRMATION` 的字段绑定项
【When】系统执行 `accept_field_binding_plan`
【Then】系统应生成 `MAPPING_CONFIRMATION` 确认项，包含 `confirmationKey`、目标列、规则源字段、候选 Excel 表头和中文问题描述，并返回 `USER_CONFIRMATION_REQUIRED`

## PRD-08 值集选择确认

需求描述：
【As】业务用户
【I want】对加工规则中要求用户选择固定值的目标列，在前端从值集中选择
【so that】如国家、区域、类别等固定枚举值能由用户明确确认

验收标准：
【Given】加工规则中存在 `USER_CONFIRM_OPTION` 类型规则
【When】系统执行确认项生成逻辑
【Then】系统应生成 `OPTION_CONFIRMATION` 确认项，包含 `valueSetCode`、可选值列表、目标列和中文提示，用户提交的选择必须在合法值集中

## PRD-09 手工输入确认

需求描述：
【As】业务用户
【I want】对加工规则中要求手工输入的目标列，或缺少可靠映射的必填字段，在前端输入固定值
【so that】结果表必填字段和业务固定值能够完整写入

验收标准：
【Given】加工规则存在 `USER_CONFIRM_INPUT`，或标准模板必填字段没有可靠映射列
【When】系统生成用户确认项
【Then】系统应生成 `INPUT_CONFIRMATION` 确认项；用户提交时 `inputValue` 不得为空，校验通过后应保存到 `userConfirmationResult`

## PRD-10 必填字段空值全量覆盖

需求描述：
【As】数据质量负责人
【I want】当必填字段映射到 Excel 列但该列存在空值时，要求用户输入固定值并在后续结果表中整列覆盖
【so that】结果表必填字段不会出现空值，且处理规则明确一致

验收标准：
【Given】标准模板声明某目标列为必填字段，字段绑定为 `CONFIRMED`，但对应 Excel 列存在空值
【When】系统执行确认项生成逻辑
【Then】系统应生成必填字段 `INPUT_CONFIRMATION`，并说明用户输入值将整列全量覆盖，而不是只覆盖空值行

## PRD-11 用户确认提交校验

需求描述：
【As】前端系统
【I want】提交用户确认结果后由后端校验确认项覆盖范围、类型和值是否合法
【so that】后续加工不会使用缺失、重复或非法的确认结果

验收标准：
【Given】任务阶段为 `USER_CONFIRMATION_REQUIRED` 且存在待确认项
【When】前端提交 `AgentUserConfirmationRequest`
【Then】系统应校验所有 `confirmationKey` 完整覆盖且无重复，确认类型与目标列匹配；校验通过后保存决策并推进到 `USER_CONFIRMED`

## PRD-12 确认后加工上下文准备

需求描述：
【As】Agent 编排服务
【I want】在进入临时表和 SQL 生成前，确定性校验模板、规则、字段绑定和用户确认结果都已经齐备
【so that】后续 SQL 生成不会依赖不完整上下文

验收标准：
【Given】任务阶段为 `USER_CONFIRMED`
【When】Agent 调用 `prepare_post_confirmation_context(taskId)`
【Then】系统应校验解析摘要、模板识别结果、模板包、字段绑定计划和用户确认结果；成功后推进到 `POST_CONFIRMATION_CONTEXT_READY` 并返回 DSL 生成所需业务上下文

## PRD-13 临时表落库与 SQL 表上下文准备

需求描述：
【As】数据加工执行引擎
【I want】用户确认完成后将原始 Excel 全量数据写入临时表，并返回确定性的 SQL 表上下文
【so that】模型只需要生成目标列表达式片段，不直接操作原始文件或数据库细节

验收标准：
【Given】任务阶段为 `POST_CONFIRMATION_CONTEXT_READY`
【When】Agent 调用 `prepare_sql_generation_context(taskId)`
【Then】系统应返回 `stagingTable`、`resultTable`、`loadedRows` 和 `columnMappings`；返回内容不得包含模板、规则、字段绑定计划或用户确认结果

## PRD-14 加工计划 DSL 生成与安全校验

需求描述：
【As】平台安全负责人
【I want】Agent 只能生成目标列表达式级 DSL，完整 SQL 由后端确定性拼接和校验
【so that】避免模型直接生成危险 SQL、越权引用表名或使用未授权字段

验收标准：
【Given】业务上下文和 SQL 表上下文均已准备完成
【When】Agent 提交 `ProcessingPlanDsl` 并调用 `execute_processing_plan`
【Then】系统应校验 DSL 头部字段、目标列覆盖范围、SQL 片段安全性和弹性字段引用范围；通过后拼接完整 `INSERT INTO ... SELECT ... FROM ...` SQL

## PRD-15 SQL 渲染后中间态返回

需求描述：
【As】后端集成开发者
【I want】在结果表实际落表执行未接入前，Agent 能返回已渲染 SQL 的中间态响应
【so that】可以先验证 DSL 生成和 SQL 拼接正确性，再接入真实数据库写入

验收标准：
【Given】`execute_processing_plan` 只完成 SQL 校验与拼接，尚未执行结果表落库
【When】Agent 完成 SQL 渲染
【Then】任务阶段应为 `PROCESSING_SQL_RENDERED`，响应 `summary` 中应包含 `resultTable`、`stagingTable`、`insertSelectSql` 和 `loadedRows`

## PRD-16 结果表写入与 Excel 导出

需求描述：
【As】业务用户
【I want】结果表写入完成后自动导出新的 Excel 文件，并返回文件标识
【so that】用户可以下载经过标准化加工后的结果文件

验收标准：
【Given】任务阶段为 `RESULT_TABLE_WRITTEN` 且任务状态或前序工具返回了最终 `resultTable`
【When】Agent 调用 `export_processed_excel(taskId, resultTable)`
【Then】工具应返回非空 `excelDocId`，最终响应阶段应为 `COMPLETED`，并在 `summary` 中包含 `resultTable`、`excelDocId`、写入行数和执行摘要

## PRD-17 SSE 流式事件输出

需求描述：
【As】前端联调开发者
【I want】Agent 运行接口以 SSE 方式输出运行事件
【so that】前端可以实时展示任务开始、工具调用、工具结果、模型消息、最终响应和错误信息

验收标准：
【Given】前端以 `Accept: text/event-stream` 调用 Agent 运行接口
【When】Agent 任务执行过程中产生消息
【Then】接口应输出带 `id`、`event` 和 JSON `data` 的 SSE 事件，事件名至少支持 `START`、`TOOL_CALL`、`TOOL_RESULT`、`MODEL_MESSAGE`、`MODEL_DELTA`、`FINAL` 和 `ERROR`

## PRD-18 Agent 内部模型流式开关

需求描述：
【As】Agent 平台维护者
【I want】外部接口保留 SSE 事件流，但关闭 Agent 内部 LLM token 级 streaming
【so that】避免部分模型流式工具调用片段缺少 `toolCallId`，导致 `ToolResponseMessage must have an id`

验收标准：
【Given】Agent 通过 SSE 对外输出事件流
【When】服务构建 `RunnableConfig`
【Then】应设置内部模型流式元数据 `_stream_ = false`；工具调用如 `read_skill` 完成后不得因缺少 tool response id 导致任务失败

## PRD-19 错误兜底与失败响应

需求描述：
【As】调用方
【I want】Agent 任意阶段失败时都返回结构化失败响应，而不是抛出不可解析异常
【so that】前端和调用方可以统一展示失败原因并决定是否重试

验收标准：
【Given】Agent 运行过程中发生工具异常、模型异常、响应解析失败或上下文缺失
【When】异常被捕获
【Then】系统应保存失败状态，返回 `stage=FAILED`、`errorCode`、中文 `message` 和 `summary.errorMessages`

## PRD-20 任务运行日志与调试可观测性

需求描述：
【As】后端调试人员
【I want】Agent SSE 接口在请求进入、订阅开始、事件发出、异常和完成时输出关键日志
【so that】没有前端联调环境时，也可以通过本地日志判断 SSE 事件是否逐条产生

验收标准：
【Given】本地通过 Postman 或 curl 调用 Agent SSE 接口
【When】请求进入并执行 Agent 流程
【Then】日志中应打印 `Agent SSE request received`、`Agent SSE stream subscribed`、`Agent SSE event emitted`、`Agent SSE stream completed` 或对应错误日志，且日志不得输出过大的完整消息体
