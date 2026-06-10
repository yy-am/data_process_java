# 2026-06-10 流式 Tool Call 修复改动说明

## 背景

当前 Agent 在启用模型原生流式调用时，会出现首个工具调用参数不完整的问题。典型表现是：

- `read_skill` 工具被调用时缺少 `skillName`
- 运行时报错 `skill_name is required`
- 参考社区 issue `#4406`，流式场景下可能出现：
  - 只有工具名没有参数
  - 只有参数没有工具名
  - 同一个 tool call 被拆成多个碎片

根因不在业务 tool 本身，而在于：

1. 模型流式输出的 `AssistantMessage.toolCalls` 可能是中间态
2. `ReactAgent` 看到 `hasToolCalls()` 后会进入工具执行
3. 工具节点可能在参数未完整时就执行 tool

## 本次改动目标

在不放弃模型 API 流式调用的前提下，修复流式 tool call 的中间态问题：

- 保留前端逐字流式输出
- 在工具执行前修复或拦截不完整的 `toolCalls`
- 避免 `read_skill` 之类的 tool 在坏参数状态下被提前执行

## 改动文件

本次提交包含以下文件：

1. `src/main/java/com/example/dataprocess/agent/config/ToolCallMessageValidator.java`
2. `src/main/java/com/example/dataprocess/agent/config/StreamingToolCallRepairHook.java`
3. `src/main/java/com/example/dataprocess/agent/config/DataProcessingReactAgentConfig.java`

## 详细改动说明

### 1. 新增 ToolCallMessageValidator

文件：

- `src/main/java/com/example/dataprocess/agent/config/ToolCallMessageValidator.java`

职责：

- 扫描 `List<Message>`
- 识别 `AssistantMessage.toolCalls` 中的异常情况
- 对可合并的流式碎片进行合并
- 对无法安全执行的坏 tool call 直接移除
- 输出修复后的消息列表和修复明细

第一版处理的重点场景：

1. tool call 有 `name` 但 `arguments` 为空
2. tool call 的 `arguments` 不是合法完整 JSON
3. 同一轮 tool call 被拆成多个片段，且可按 `id` 合并
4. 启发式合并：
   - 一个片段只有 `name`
   - 一个片段只有 `arguments`
   - 可组合为完整 tool call

额外约束：

- 对 `read_skill` 单独校验必须包含 `skillName` 或 `skill_name`
- 即使参数 JSON 结构合法，但若缺少 `skillName`，仍视为不完整 tool call

### 2. 新增 StreamingToolCallRepairHook

文件：

- `src/main/java/com/example/dataprocess/agent/config/StreamingToolCallRepairHook.java`

实现方式：

- 继承 `MessagesModelHook`
- 使用 `@HookPositions({HookPosition.BEFORE_MODEL, HookPosition.AFTER_MODEL})`
- 在两个阶段都调用 `ToolCallMessageValidator`

为什么选 `MessagesModelHook`：

- 它直接处理 `List<Message>`
- 返回 `AgentCommand`
- 天然适合做消息级修复
- 比普通 `ModelHook` 更适合修改 assistant message 和控制模型/工具跳转

阶段职责：

#### `beforeModel`

- 在下一轮模型请求前修复历史消息
- 防止坏掉的 `toolCalls` 进入后续上下文

#### `afterModel`

- 在模型刚返回后立即修复当前轮输出
- 防止不完整 tool call 直接进入工具执行

跳转策略：

- 如果修复结果表明最后一条 assistant message 中的 tool call 仍不适合立即执行
- 则返回 `AgentCommand(JumpTo.model, repairedMessages)`
- 强制回到 model 继续完成输出，而不是提前进入 tool 节点

### 3. 接入 ReactAgent 配置

文件：

- `src/main/java/com/example/dataprocess/agent/config/DataProcessingReactAgentConfig.java`

改动点：

1. 注入 `ObjectMapper`
2. 组合当前已知 tool 列表：
   - 业务 tools
   - `SkillsAgentHook` 提供的 `read_skill`
3. 构造 `ToolCallMessageValidator`
4. 将 `StreamingToolCallRepairHook` 注册到 `.hooks(...)`

当前 hook 顺序：

1. `skillsAgentHook`
2. `EnsureToolCallIdHook`
3. `StreamingToolCallRepairHook`
4. `ToolCallbackDeduplicationHook`
5. `ModelCallLimitHook`

这个顺序的意义：

- 先保证 `toolCall.id` 存在
- 再修复流式拆裂/坏参数 tool call
- 然后再执行工具去重逻辑

## 当前修复策略的边界

本次修复是第一版，重点解决：

- `read_skill` 缺 `skillName`
- tool call 被拆裂后导致参数未完整就执行

暂未实现：

1. 跨轮次持久化 pending tool call 合并
2. synthetic `ToolResponseMessage`
3. 更复杂的多工具并发碎片重组

但对当前问题已经足够关键：可以阻止不完整 tool call 提前落到工具节点执行。

## 验证情况

已完成的验证：

1. 新增 `ToolCallMessageValidator` 单独编译通过
2. 新增 `StreamingToolCallRepairHook` 单独编译通过
3. `DataProcessingReactAgentConfig` 接线后单独编译通过

未完成的验证：

- 全量 `mvn compile`

原因：

- 仓库内已有与本次改动无关的编译问题，会阻塞全量编译

## 预期效果

改动后预期表现：

1. 流式首个 `read_skill` 不再以空参数执行
2. 不完整 `toolCalls` 会先在 hook 中被修复或阻断
3. 模型 API 流式调用仍然保留
4. 前端仍然可以看到逐字流式输出

## 备注

本次提交只包含流式 tool call 修复相关的 agent 配置代码，不包含工作区内其他业务改动。
