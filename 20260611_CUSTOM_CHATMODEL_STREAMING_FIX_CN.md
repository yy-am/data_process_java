# 自定义 ChatModel 流式 tool_call 修复说明

## 背景

这次修复针对的是自定义 `myChatModel` 在流式场景下过早执行 tool call 的问题。

原始问题表现为：

- 每个流式 chunk 都会立刻转成 `ChatResponse`
- `buildGeneration(...)` 会直接把 chunk 里的 delta tool call 转成
  `new AssistantMessage.ToolCall(id, "function", name, arguments)`
- 在 `internalStream(...)` 中，`flatMap(...)` 会立刻调用
  `toolExecutionEligibilityPredicate.isToolExecutionRequired(...)`
  和 `toolCallingManager.executeToolCalls(...)`
- 当模型尚未完整输出 tool call 参数时，就可能出现：
  - `name=read_skill, arguments=""`
  - `name=null, arguments="}]"`
- 最终导致 tool 被过早执行，或被后续 validator 误判为参数不完整

## 产物

本次在当前仓库新增了一个参考实现文件：

- `myChatModel_streaming_fixed.java`

说明：

- 这是根据你提供的完整源码改出的“可替换参考版”
- 因为真正的自定义 `ChatModel` 项目不在当前 workspace，所以这里没有覆盖原项目文件，而是单独生成参考实现

## 主要改动方法

### 1. `internalStream(Prompt prompt, ChatResponse previousChatResponse)`

改动目的：

- 不再直接让原始 chunk 转出的 `ChatResponse` 参与 tool execution
- 先引入流式 tool_call 累加器，再进入后续执行判断

主要改动：

- 新增 `StreamingToolCallAccumulator toolCallAccumulator = new StreamingToolCallAccumulator();`
- `getChatResponseFlux(...)` 新增 `toolCallAccumulator` 参数
- `flatMap(...)` 内部不再直接用原来的条件，而是改为调用：
  - `shouldExecuteTools(prompt, response)`

### 2. `getChatResponseFlux(...)`

原始问题：

- 之前是 `completionChunks.map(this::chunkToChmyompletion)` 后，直接 `buildGeneration(...)`
- 没有任何跨 chunk 的 tool_call delta 合并逻辑

现在改动：

- 方法签名新增：
  - `StreamingToolCallAccumulator toolCallAccumulator`
- 在处理每个 choice 时，先执行：

```java
List<myCopilotApi.ChmyompletionMessage.ToolCall> mergedToolCalls =
        toolCallAccumulator.merge(id, choice.index(), choice.message().toolCalls());
```

- 然后改为：

```java
return buildGeneration(choice, metadata, mergedToolCalls);
```

效果：

- 先把同一轮流式输出中的 tool call 名称、id、arguments 逐步累积起来
- 再把“合并后的 tool call”映射成 Spring AI 的 `AssistantMessage.ToolCall`

### 3. `buildGeneration(...)`

改动内容：

- 保留原有重载：
  - `buildGeneration(myCopilotApi.Chmyompletion.Choice choice, Map<String, Object> metadata)`
- 新增新的重载：

```java
private Generation buildGeneration(
        myCopilotApi.Chmyompletion.Choice choice,
        Map<String, Object> metadata,
        List<myCopilotApi.ChmyompletionMessage.ToolCall> mergedToolCalls
)
```

目的：

- 让 `buildGeneration(...)` 消费的是“已合并完成的 toolCalls”
- 不再直接把 chunk delta 原样映射出去

### 4. `buildResponse(ChatResponse response, Prompt prompt)`

改动内容：

- 原来直接使用：

```java
this.toolExecutionEligibilityPredicate.isToolExecutionRequired(prompt.getOptions(), response)
```

- 现在改为：

```java
shouldExecuteTools(prompt, response)
```

目的：

- 让同步 `call(...)` 和递归回调逻辑也统一经过更严格的可执行判断

### 5. 新增 `shouldExecuteTools(Prompt prompt, ChatResponse response)`

这是这次修复的关键方法之一。

作用：

- 即使 `toolExecutionEligibilityPredicate` 认为需要执行工具，也不会立刻放行
- 只有在满足以下条件时才真正进入 `executeToolCalls(...)`

判断条件包括：

- `response` 不为空
- `response.getResult().getOutput()` 不为空
- `output.getToolCalls()` 非空
- `finishReason` 为 `tool_calls` 或 `stop`
- 每个 tool call 都满足：
  - `name` 非空
  - `arguments` 非空
  - `arguments` 能被解析为 JSON object

### 6. 新增 `isExecutableToolCall(AssistantMessage.ToolCall toolCall)`

作用：

- 对单个 tool call 进行执行前校验

校验内容：

- `toolCall` 非空
- `toolCall.name()` 非空
- `toolCall.arguments()` 非空
- `arguments` 能被 `ObjectMapper.readTree(...)` 解析且结果为 object

这样可以避免以下情况提前执行：

- `arguments=""`
- `arguments="}]"` 或 `"kill\"}"`
- `name=null`

### 7. 新增内部类 `StreamingToolCallAccumulator`

这是这次修复的核心。

作用：

- 在流式输出过程中，跨 chunk 维护 tool call 累计状态

主要职责：

- 按 `streamId + choiceIndex` 维度缓存本轮累计中的 tool calls
- 对同一位置的 tool call 进行 merge
- merge 规则：
  - `id`：优先取当前非空，否则取之前
  - `type`：优先取当前非空，否则取之前
  - `function.name`：优先取当前非空，否则取之前
  - `function.arguments`：字符串拼接累计

新增方法包括：

- `merge(String streamId, Integer choiceIndex, List<ToolCall> deltaToolCalls)`
- `existing(String streamId)`
- `key(String streamId, Integer choiceIndex)`
- `mergeToolCall(previous, current)`
- `functionName(...)`
- `functionArguments(...)`
- `safe(...)`
- `hasText(...)`

## 替换建议

建议你在真实项目中按下面方式替换：

1. 以当前项目中的 `myChatModel_streaming_fixed.java` 为基准
2. 将其中逻辑替换到真实项目里的 `myChatModel.java`
3. 保留真实项目已有的：
   - package 声明
   - import 列表
   - logger / 常量 / 依赖注入字段
   - 自定义 DTO 的真实命名
4. 重点确认真实项目中的 `ToolCall` 构造函数签名是否与参考实现一致

## 这次没有改动的方法

以下方法保留原有职责，没有做逻辑重写：

- `chunkToChmyompletion(...)`
- `from(...)`
- `getDefaultUsage(...)`
- `buildRequestPrompt(...)`
- `createRequest(...)`
- `buildRequest(...)`
- `getFunctionTools(...)`
- `getDefaultOptions()`
- `toString()`
- `setObservationConvention(...)`
- `Builder`

这些方法没有成为这次 bug 的主因，所以没有大动。

## 一句话总结

这次修复的本质是：

- **先合并流式 tool_call delta**
- **再生成 AssistantMessage.ToolCall**
- **最后才判断并执行工具**

而不是像原来那样：

- **每个 chunk 直接转 tool call**
- **立即判断是否执行工具**

