# Agent 响应契约与用户确认结构改动说明

本文档汇总远程基线提交与本次 Agent 响应契约调整，便于正式开发、前端联调和后续代码审查。

## 远程基线

- 基线远程分支：`origin/main`
- 基线提交：`6513f91 Add agent formal PRD requirements`
- 基线提交内容：新增 `AGENT_FORMAL_PRD_REQUIREMENTS_CN.md`，整理当前 Agent 正式开发 PRD，按 `As/I want/so that` 与 `Given/When/Then` 拆分需求。

## 本次改动目标

本次改动解决前端无法直接消费 `Flux<AssistantMessage>` 的问题，并补齐用户确认阶段需要展示的字段映射结构。

核心目标：

- 对外流式返回改为稳定业务事件 `DataProcessingAgentStreamEvent`。
- `DataProcessingAgentResponse` 增加 `fieldBindingPlan`，让前端能看到明确映射、模糊映射和缺失映射。
- 保留当前 Agent 已真实赋值的 `AgentConfirmationItem` 与 `AgentConfirmationDecision`，不引入已废弃 workflow 的确认结构。
- 让 Skill 最终返回协议与 Java 响应结构保持一致。

## 新增类

### `DataProcessingAgentStreamEvent`

路径：`src/main/java/com/example/dataprocess/agent/model/DataProcessingAgentStreamEvent.java`

作用：

- 作为 Agent SSE 流式响应的稳定前端契约。
- 替代直接向前端暴露 Spring AI 内部对象 `AssistantMessage`。

字段：

- `event`：事件类型，例如 `START`、`TOOL_CALL`、`TOOL_RESULT`、`MODEL_MESSAGE`、`FINAL`、`ERROR`。
- `taskId`：任务编号。
- `stage`：当前 Agent 业务阶段。
- `skillStage`：当前阶段名称字符串，便于前端直接展示。
- `node`：触发事件的 Agent 节点。
- `message`：事件文本。
- `response`：业务响应 `DataProcessingAgentResponse`。
- `detail`：工具名、输出类型等调试详情。

## 修改类与方法

### `DataProcessingAgentResponse`

路径：`src/main/java/com/example/dataprocess/agent/model/DataProcessingAgentResponse.java`

修改内容：

- 新增字段 `FieldBindingPlan fieldBindingPlan`。

修改后语义：

- `fieldBindingPlan`：完整字段绑定计划，前端根据 `items.status` 展示字段映射状态。
- `confirmationItems`：需要用户操作的确认项，前端根据 `confirmationType` 展示具体控件。
- `userConfirmationResult`：用户提交并通过校验的确认结果。

前端展示规则：

- `fieldBindingPlan.items.status = CONFIRMED`：明确映射。
- `fieldBindingPlan.items.status = NEEDS_CONFIRMATION`：模糊映射。
- `fieldBindingPlan.items.status = MISSING`：缺失映射。
- `confirmationItems.confirmationType = MAPPING_CONFIRMATION`：需要用户选择字段映射。
- `confirmationItems.confirmationType = OPTION_CONFIRMATION`：需要用户选择值集。
- `confirmationItems.confirmationType = INPUT_CONFIRMATION`：需要用户手工填值。

### `DataProcessingReactAgentService`

路径：`src/main/java/com/example/dataprocess/agent/service/DataProcessingReactAgentService.java`

修改方法：

- `run(DataProcessingTaskRequest request)`
  - 返回类型从 `Flux<AssistantMessage>` 改为 `Flux<DataProcessingAgentStreamEvent>`。
  - 内部仍使用 `AssistantMessage` 处理 Spring AI 输出，但对外统一转换为业务事件。
  - 异常分支也统一返回 `DataProcessingAgentStreamEvent`。

- `resolveFinalResponse(...)`
  - 构造失败兜底响应时补齐 `fieldBindingPlan` 参数。

- `toFailedResponse(...)`
  - 从已保存的 `DataProcessingAgentState` 中读取 `fieldBindingPlan` 并放入失败响应。

- `toResponse(DataProcessingAgentState state)`
  - 从任务状态中读取 `state.fieldBindingPlan()` 并写入 `DataProcessingAgentResponse`。

新增方法：

- `toStreamEvent(String taskId, AssistantMessage message)`
  - 将内部 `AssistantMessage` 转换成前端稳定事件。
  - 从消息 metadata 读取 `event`、`node`、`response`。
  - 当消息没有 response 时，从任务状态推断当前 `stage`。

- `responseValue(Object value)`
  - 从 metadata 中安全提取 `DataProcessingAgentResponse`。

- `eventDetail(Map<String, Object> metadata)`
  - 过滤 `event`、`taskId`、`node`、`response` 等保留字段，剩余内容作为调试详情返回。

- `metadataValue(Map<String, Object> metadata, String key, String defaultValue)`
  - 统一读取 metadata 字符串值。

### `DataProcessingAgentInterface`

路径：`src/main/java/com/example/dataprocess/agent/interfaces/DataProcessingAgentInterface.java`

修改方法：

- `emitMessage(...)`
  - 入参从 `AssistantMessage` 改为 `DataProcessingAgentStreamEvent`。
  - SSE `event name` 直接使用 `event.event()`。
  - SSE `data` 直接发送业务事件 JSON。
  - 日志改为记录 `stage` 与 `detailKeys`，不再记录 Spring AI metadata。

- `textLength(...)`
  - 入参从 `AssistantMessage` 改为 `DataProcessingAgentStreamEvent`。

删除方法：

- `metadataValue(Map<String, Object> metadata, String key, String defaultValue)`
  - 删除原因：Controller 不再解析 `AssistantMessage.metadata`，事件名由 `DataProcessingAgentStreamEvent.event` 直接提供。

### `DataProcessingAgentToolMethods`

路径：`src/main/java/com/example/dataprocess/agent/tool/DataProcessingAgentToolMethods.java`

修改方法：

- `toResponse(DataProcessingAgentState state)`
  - 将 `state.fieldBindingPlan()` 写入 `DataProcessingAgentResponse`。

- `responseMap(DataProcessingAgentState state)`
  - 返回 Map 时增加 `fieldBindingPlan`。
  - 保障工具返回的 `agentResponse` 与 Java record 字段一致。

### `data-processing-agent-skill/SKILL.md`

路径：`src/main/resources/agent/skills/data-processing-agent-skill/SKILL.md`

修改内容：

- 增加前端确认视图硬规则。
- 明确 `accept_field_binding_plan` 返回 `USER_CONFIRMATION_REQUIRED` 时，最终响应必须保留完整 `fieldBindingPlan` 与 `confirmationItems`。
- 最终返回协议示例增加 `fieldBindingPlan`。
- 明确前端通过 `fieldBindingPlan` 展示明确映射、模糊映射和缺失映射。

## 删除内容

### 删除类

无已跟踪 Java 类删除。

### 删除方法

- `DataProcessingAgentInterface.metadataValue(...)`
  - Controller 已不再直接处理 `AssistantMessage.metadata`，因此删除。

## 当前最终响应结构

```java
public record DataProcessingAgentResponse(
        AgentWorkflowStage stage,
        String taskId,
        String parsedFileRef,
        TemplateRecognitionResult templateRecognitionResult,
        FieldBindingPlan fieldBindingPlan,
        List<AgentConfirmationItem> confirmationItems,
        List<AgentConfirmationDecision> userConfirmationResult,
        Map<String, Object> summary,
        String errorCode,
        String message
) {
}
```

## 当前 SSE 事件结构

```java
public record DataProcessingAgentStreamEvent(
        String event,
        String taskId,
        AgentWorkflowStage stage,
        String skillStage,
        String node,
        String message,
        DataProcessingAgentResponse response,
        Map<String, Object> detail
) {
}
```

## 设计结论

- 当前 Agent 不使用已废弃 workflow 的 `UserConfirmationItems`、`UserConfirmationResult` 作为响应结构。
- 当前 Agent 确认链路继续使用已经真实赋值的 `AgentConfirmationItem`、`AgentConfirmationDecision`。
- `fieldBindingPlan` 是字段映射展示的唯一完整来源。
- `confirmationItems` 是用户确认控件展示的唯一完整来源。
- 前端不需要理解 Spring AI 的 `AssistantMessage`。

## 验证情况

- 已执行 `git diff --check`，未发现本次 Agent 相关改动的空白格式问题。
- 全量 `mvn -q -DskipTests compile` 当前会被已废弃 workflow 文件 `DataProcessingStateGraphDefinition.java` 的既有语法错误阻断，本次未修改 workflow。
