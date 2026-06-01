# Agent SSE 本地调试命令

## 用途

用于本地验证 `/api/agent/data-processing/run` 是否按 SSE 事件流返回。Postman 可能会把响应内容集中显示在 Body 面板里，因此调试时建议同时观察后端日志。

## Postman 请求配置

- Method: `POST`
- URL: `http://localhost:8080/api/agent/data-processing/run`
- Header:
  - `Content-Type: application/json`
  - `Accept: text/event-stream`
- Body: `raw` / `JSON`

```json
{
  "taskId": "debug-1",
  "inputType": "excel-import",
  "sourceHeaders": ["invoice_no", "tax_b", "amount"],
  "sampleRows": [
    {
      "invoice_no": "A001",
      "tax_b": "Y",
      "amount": "100"
    }
  ]
}
```

## PowerShell curl 调用

必须使用 `curl.exe`，不要只写 `curl`。PowerShell 中 `curl` 可能是别名。`-N` 表示关闭 curl 输出缓冲，更适合观察 SSE 分段输出。

```powershell
curl.exe -N `
  -H "Content-Type: application/json" `
  -H "Accept: text/event-stream" `
  -X POST http://localhost:8080/api/agent/data-processing/run `
  -d "{\"taskId\":\"debug-1\",\"inputType\":\"excel-import\",\"sourceHeaders\":[\"invoice_no\",\"tax_b\",\"amount\"],\"sampleRows\":[{\"invoice_no\":\"A001\",\"tax_b\":\"Y\",\"amount\":\"100\"}]}"
```

## 预期响应形态

```text
id: debug-1-1
event: START
data: ...

id: debug-1-2
event: TOOL_CALL
data: ...

id: debug-1-3
event: TOOL_RESULT
data: ...

id: debug-1-4
event: FINAL
data: ...
```

## 后端日志判断

本地不挂 Debug 也可以通过日志判断接口是否逐个发出 SSE 事件。控制台中应能看到类似日志：

```text
Agent SSE request received, taskId=debug-1, inputType=excel-import, sourceHeaderCount=3, sampleRowCount=1
Agent SSE stream subscribed, taskId=debug-1
Agent SSE event emitted, taskId=debug-1, id=debug-1-1, event=START, textLength=...
Agent SSE event emitted, taskId=debug-1, id=debug-1-2, event=TOOL_CALL, textLength=...
Agent SSE event emitted, taskId=debug-1, id=debug-1-3, event=TOOL_RESULT, textLength=...
Agent SSE event emitted, taskId=debug-1, id=debug-1-4, event=FINAL, textLength=...
Agent SSE stream completed, taskId=debug-1, eventCount=...
```

如果日志里的 `Agent SSE event emitted` 出现多次，说明后端已经逐个产生 SSE 事件；Postman 一次性展示 Body 不一定代表后端没有流式输出。
