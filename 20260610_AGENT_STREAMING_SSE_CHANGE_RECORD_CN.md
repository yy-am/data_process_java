# 2026-06-10 Agent 流式输出改造说明

## 改造背景

当前数据加工 Agent 虽然内部已经使用了 `streamMessages(...)`，但前端实际体验仍然不是流式输出，而是调用多个工具后一次性返回。

根因分为两层：

1. `spring-ai-alibaba` 的 `ReactAgent.streamMessages(...)` 在工具调用场景下，往往按一轮推理聚合输出，不能天然保证每个工具开始/结束都实时透出。
2. 当前接口层使用 `SseEmitter + Flux subscribe` 做桥接，在 Spring MVC 场景下可用，但对“每产生一条事件立刻刷给前端”这件事不够稳，容易出现缓冲后再统一返回。

因此本次改造目标不是继续调整 graph，而是把 agent 运行时事件从“被动等待聚合消息”改成“主动发布工具事件 + HTTP 层强制逐条 flush”。

## 本次改动概览

本次改动共包含 6 个代码文件和 1 个配置文件：

1. `src/main/java/com/example/dataprocess/agent/service/AgentStreamEventPublisher.java`
2. `src/main/java/com/example/dataprocess/agent/config/AgentExecutionToolStreamInterceptor.java`
3. `src/main/java/com/example/dataprocess/agent/config/DataProcessingReactAgentConfig.java`
4. `src/main/java/com/example/dataprocess/agent/service/DataProcessingReactAgentService.java`
5. `src/main/java/com/example/dataprocess/agent/interfaces/DataProcessingAgentInterface.java`
6. `src/main/resources/application.yml`

## 详细改动说明

### 1. 新增 AgentStreamEventPublisher

文件：

- `src/main/java/com/example/dataprocess/agent/service/AgentStreamEventPublisher.java`

职责：

- 维护一个按 `taskId` 隔离的事件发布器
- 支持 agent 运行过程中的实时事件推送
- 让工具执行过程中的事件不依赖 `ReactAgent` 最终聚合消息

提供的方法：

- `createStream(String taskId)`：创建或获取任务级事件流
- `emit(String taskId, DataProcessingAgentStreamEvent event)`：发布事件
- `complete(String taskId)`：完成事件流
- `error(String taskId, Throwable ex)`：异常结束事件流
- `remove(String taskId)`：移除任务级 sink

意义：

- 把“工具执行时机”和“模型消息时机”拆开
- 为粗粒度流式事件提供统一出口

### 2. 新增 AgentExecutionToolStreamInterceptor

文件：

- `src/main/java/com/example/dataprocess/agent/config/AgentExecutionToolStreamInterceptor.java`

职责：

- 拦截每一次工具调用
- 在工具真正执行前发出 `TOOL_CALL`
- 在工具执行完成后发出 `TOOL_RESULT`
- 在工具执行异常时发出 `ERROR`

实现方式：

- 从 `request.getExecutionContext().threadId()` 读取当前 `taskId`
- 从 `AgentStateTool` 读取当前 state，对应出当前阶段 `stage`
- 组装为 `DataProcessingAgentStreamEvent`
- 通过 `AgentStreamEventPublisher` 发布给外层流

这样做的价值：

- 不再等待一整轮推理结束后，才从聚合后的 `ToolResponseMessage` 中感知工具结果
- 工具开始和结束时机可以直接推给前端

### 3. 在 ReactAgent 配置中接入工具执行流式拦截器

文件：

- `src/main/java/com/example/dataprocess/agent/config/DataProcessingReactAgentConfig.java`

改动点：

- `dataProcessingReactAgent(...)` Bean 新增两个依赖注入参数：
  - `AgentStreamEventPublisher`
  - `AgentStateTool`
- 在 `.interceptors(...)` 中增加：

```java
new AgentExecutionToolStreamInterceptor(eventPublisher, stateTool)
```

位置上放在：

- `BlankToolInputNormalizingInterceptor`
- `ToolErrorInterceptor`

之前

意义：

- 保证所有 agent tool 调用都能统一经过该拦截器
- 不需要逐个修改 tool 方法本身，就能得到统一的工具事件流

### 4. 重构 DataProcessingReactAgentService 的输出流

文件：

- `src/main/java/com/example/dataprocess/agent/service/DataProcessingReactAgentService.java`

这是本次改造的核心之一。

改动内容：

1. 注入 `AgentStreamEventPublisher`
2. 在 `run(...)` 中创建 `outgoing` sink
3. 同时订阅两类流：
   - `dataProcessingReactAgent.streamMessages(...)` 产生的模型消息流
   - `eventPublisher.createStream(taskId)` 产生的工具执行事件流
4. 将两类事件统一映射为 `DataProcessingAgentStreamEvent`
5. 最终通过 `outgoing.asFlux()` 返回

新增行为：

- 一开始主动发 `START`
- 工具前后主动发 `TOOL_CALL` / `TOOL_RESULT`
- 模型纯文本输出发 `MODEL_DELTA`
- 结束时统一补一个 `FINAL`
- 异常时发 `ERROR`

同时，为了避免重复事件：

- 不再使用 `AssistantMessage.hasToolCalls()` 直接向外透出 `TOOL_CALL`
- 不再消费 `ToolResponseMessage` 去生成 `TOOL_RESULT`

原因：

- 这些事件已经由新的工具拦截器在更准确的执行边界发出了
- 如果继续保留旧逻辑，会出现重复、延迟、甚至和真实工具执行时机不一致的问题

### 5. 将 HTTP SSE 出口从 SseEmitter 改为 StreamingResponseBody

文件：

- `src/main/java/com/example/dataprocess/agent/interfaces/DataProcessingAgentInterface.java`

这是本次“前端为什么还是最后一起收到”的另一个关键改动。

原实现：

- `SseEmitter`
- 手工 `Flux.subscribe(...)`
- 由 `emitter.send(...)` 向外发送事件

新实现：

- 返回 `ResponseEntity<StreamingResponseBody>`
- 在 `StreamingResponseBody` 中直接消费 `agentService.run(request)`
- 每来一条事件，手工写标准 SSE 协议文本：

```text
id: xxx
event: xxx
data: {...}

```

- 每次写完都立即 `flush()`

同时新增响应头：

- `Cache-Control: no-cache`
- `Connection: keep-alive`
- `X-Accel-Buffering: no`

这样做的价值：

1. 更直接控制 SSE 输出格式
2. 更直接控制每条事件发送后的 `flush`
3. 更容易规避 MVC 异步桥接中的缓冲问题
4. 对 Nginx 等代理缓冲场景也更友好

### 6. 关闭 Spring MVC 异步请求超时

文件：

- `src/main/resources/application.yml`

新增配置：

```yml
spring:
  mvc:
    async:
      request-timeout: -1
```

目的：

- 避免长任务执行时 SSE 连接被 Spring MVC 异步超时提前终止
- 适配 Agent 中存在长时间工具执行的场景

## 事件时序变化

改造前：

1. 模型开始推理
2. 多个工具可能已经执行
3. 前端长时间收不到事件或只收到少量事件
4. 一轮工具执行结束后，消息聚合返回
5. 前端一次性看到多条工具结果

改造后：

1. 接口收到请求后立即返回流式响应
2. 发出 `START`
3. 每个工具真正执行前发出 `TOOL_CALL`
4. 每个工具执行完成后立即发出 `TOOL_RESULT`
5. 模型文本消息继续以 `MODEL_DELTA` 输出
6. 最终发出 `FINAL`

## 风险与注意事项

### 1. 当前仓库存在与本次改动无关的编译错误

文件：

- `src/main/java/com/example/dataprocess/application/workflow/DataProcessingStateGraphDefinition.java`

现象：

- `mvn -q -DskipTests compile` 仍会失败

说明：

- 该错误不是本次 agent 流式改动引入的
- 会阻塞全量编译验证

### 2. 如果前端仍然“最后才显示”，问题可能已转移到前端或代理层

本次后端已尽量保证：

- 工具执行边界主动发事件
- HTTP 响应逐条写出并 `flush`
- 禁用常见缓冲响应头

如果前端仍然表现为最终一次性展示，应重点检查：

1. 前端是否真正用 `EventSource` 或逐块读取流
2. 前端是否自己把事件攒起来后再统一渲染
3. 网关 / 反向代理是否做了缓冲
4. 浏览器调试时是否实际拿到了中间 chunk

## 结论

这次改造的核心思想是：

- 不再把“流式”完全寄托在 `ReactAgent.streamMessages(...)`
- 而是把工具执行事件从 agent 内部主动抬出来
- 再通过更可控的 SSE 写出方式实时返回给前端

这比继续只调 agent graph 参数更稳，也更容易定位后续问题到底是在 agent、HTTP 层、代理层还是前端消费层。
