# 2026-06-10 ToolCall ID 修复说明

## 背景

在 Agent 改为基于 `streamMessages(...)` 进行流式输出后，运行过程中出现如下报错：

```text
ToolResponseMessage must have an id
```

结合 `spring-ai-alibaba-agent-framework` 与 `spring-ai-model` 源码可确认：

- `ToolResponseMessage.ToolResponse.id` 来自上一轮模型输出中的 `AssistantMessage.ToolCall.id`
- 当模型返回的某个 `toolCall.id` 为空时，后续 `ToolResponseMessage` 构建或消费阶段就会报错

因此，这不是前端问题，也不是单个业务 Tool 的问题，而是 Agent 模型输出到 Tool 执行衔接处的兼容性问题。

## 本次改动目标

在 **模型输出之后、Tool 执行之前**，统一为缺失的 `toolCall.id` 自动补值，确保：

1. 流式 `streamMessages(...)` 路径可继续消费 `ToolResponseMessage`
2. 非流式与流式路径都兼容
3. 不侵入现有业务 Tool 实现

## 改动文件

### 1. 新增 `EnsureToolCallIdHook`

文件：

- `src/main/java/com/example/dataprocess/agent/config/EnsureToolCallIdHook.java`

作用：

- 新增一个 `AgentHook`
- 通过内部 `ModelInterceptor` 拦截模型返回
- 检查 `AssistantMessage.ToolCall.id`
- 对缺失 id 的 tool call 自动补为 `call-<UUID>`

实现覆盖两种路径：

- 非流式：直接处理 `AssistantMessage`
- 流式：处理 `Flux<ChatResponse>`

### 2. 将 Hook 挂到 ReactAgent 配置

文件：

- `src/main/java/com/example/dataprocess/agent/config/DataProcessingReactAgentConfig.java`

改动：

- 在 `ReactAgent.builder().hooks(...)` 中新增 `new EnsureToolCallIdHook()`

这样可以保证整个数据加工 Agent 在所有模型调用轮次里统一应用此修复。

### 3. 补齐 `GraphRunnerException` import

文件：

- `src/main/java/com/example/dataprocess/agent/service/DataProcessingReactAgentService.java`

改动：

- 补充 `GraphRunnerException` import，避免当前 service 中 `parseResponse(...)` 的编译引用缺失

## 修复原理

当前框架链路中，tool response id 传播关系如下：

```text
AssistantMessage.ToolCall.id
    -> ToolCallResponse.toolCallId
    -> ToolResponseMessage.ToolResponse.id
```

只要源头 `AssistantMessage.ToolCall.id` 非空，后续：

- `AgentToolNode`
- `ToolCallResponse`
- `ToolResponseMessage`
- `streamMessages(...)`

都能正常工作。

因此，本次修复选择在最上游统一兜底，而不是在业务 Tool 或前端层做兼容。

## 影响范围

### 直接影响

- 数据加工 Agent 的流式消息消费路径
- 基于 `streamMessages(...)` 的 `TOOL_CALL` / `TOOL_RESULT` 输出稳定性

### 不影响

- 业务 Tool 方法签名
- Agent 状态机逻辑
- 前端事件协议结构
- 数据加工业务规则本身

## 验证情况

本次尝试执行了：

```text
mvn -q -DskipTests compile
```

但当前仓库存在**与本次改动无关**的语法错误，导致整仓编译未能完成：

- `src/main/java/com/example/dataprocess/application/workflow/DataProcessingStateGraphDefinition.java`

因此，本次无法通过整仓编译给出完整通过结论。

不过从本次改动本身看：

- 新增 Hook 结构与现有 `ToolCallbackDeduplicationHook` 风格一致
- 使用的 `ModelInterceptor`、`ModelResponse`、`ChatResponse`、`AssistantMessage` API 均与当前依赖源码匹配

## 后续建议

1. 先修复 `DataProcessingStateGraphDefinition.java` 中现有语法错误
2. 重新执行整仓编译验证
3. 在实际流式运行中重点观察：
   - `TOOL_CALL` 是否正常输出
   - `TOOL_RESULT` 是否仍出现 `id` 相关报错
   - 同一轮多个 tool call 是否都能补齐 id
