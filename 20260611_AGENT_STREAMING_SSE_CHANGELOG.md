# Agent Streaming SSE 改动说明

本文档仅说明本次为了打通 agent 对外流式返回而改动的类与方法，方便你在其他项目中按点替换。

## 1. `src/main/java/com/example/dataprocess/agent/service/DataProcessingReactAgentService.java`

### 改动的字段

- 新增字段：
  - `private final AgentStreamEventPublisher eventPublisher;`

### 改动的构造方法

- `public DataProcessingReactAgentService(...)`
  - 新增 `AgentStreamEventPublisher eventPublisher` 入参

### 改动的方法

- `public Flux<DataProcessingAgentStreamEvent> run(DataProcessingTaskRequest request)`
  - 开启 `AGENT_INTERNAL_MODEL_STREAMING_KEY = true`
  - 增加 `eventPublisher.createStream(taskId)` 订阅
  - 将 `publishedEvents` 与 `mainEvents` 合并输出
  - 在主流结束时调用 `eventPublisher.complete(taskId)`

- `private String buildAgentInstruction(DataProcessingTaskRequest request, String parsedFileRef)`
  - 调整了指令文本内容

- `private List<AssistantMessage> toAssistantMessages(...)`
  - 方法签名改为接收 `NodeOutput`、`AtomicReference<OverAllState>`
  - 从 `StreamingOutput` 中提取消息
  - 更新 `latestState`

- `private List<AssistantMessage> assistantMessages(...)`
  - 方法签名改为接收 `node`、`StreamingOutput<?>`
  - 输出事件类型区分 `MODEL_DELTA` / `MODEL_MESSAGE`
  - 不再在这里生成 `TOOL_CALL` 事件

- `private AssistantMessage toFinalMessage(...)`
  - 方法签名新增 `OverAllState latestState`

- `private DataProcessingAgentResponse resolveFinalResponse(...)`
  - 方法签名新增 `OverAllState latestState`
  - 当 `latestAssistantMessage` 为空时，尝试从 `latestState` 里取最后一个 `AssistantMessage`

### 新增的方法

- `private Optional<AssistantMessage> latestAssistant(OverAllState state)`

- `private String outputTypeName(StreamingOutput<?> output)`

## 2. `src/main/java/com/example/dataprocess/agent/service/AgentStreamEventPublisher.java`

### 改动的方法

- `public Flux<DataProcessingAgentStreamEvent> createStream(String taskId)`
  - 改为使用统一的 `newSink()`

- `public void emit(String taskId, DataProcessingAgentStreamEvent event)`
  - 改为使用统一的 `newSink()`

### 新增的方法

- `private Sinks.Many<DataProcessingAgentStreamEvent> newSink()`
  - 当前实现：`Sinks.many().replay().limit(256)`

## 3. 本次提交未改动但与本次链路直接相关的类

以下类本次没有继续修改，但仍然参与实时流式链路：

- `src/main/java/com/example/dataprocess/agent/config/AgentExecutionToolStreamInterceptor.java`
  - 负责发出 `TOOL_CALL` / `TOOL_RESULT` / `ERROR`

- `src/main/java/com/example/dataprocess/agent/interfaces/DataProcessingAgentInterface.java`
  - 负责将 `Flux<DataProcessingAgentStreamEvent>` 写成 SSE 响应
