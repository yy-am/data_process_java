# SseEmitter 接口改动说明

## 改动文件

- `src/main/java/com/example/dataprocess/agent/interfaces/DataProcessingAgentInterface.java`

## 改动方法

- `run(@Valid @RequestBody DataProcessingTaskRequest request)`
  - 返回类型由 `ResponseEntity<StreamingResponseBody>` 改为 `SseEmitter`
  - 改为在方法内订阅 `agentService.run(request)` 返回的 `Flux<DataProcessingAgentStreamEvent>`
  - 增加 `onCompletion`、`onTimeout`、`onError` 回调处理

- `emitMessage(...)`
  - 入参由 `Writer` 改为 `SseEmitter`
  - 改为使用 `emitter.send(SseEmitter.event()...)` 发送 SSE 事件

- `completeWithError(SseEmitter emitter, String taskId, Throwable ex)`
  - 新增
  - 用于统一处理 SSE 异常结束
