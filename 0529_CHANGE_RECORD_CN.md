# 2026-05-29 修改记录

## 1. 本次改动范围

本次提交聚焦数据加工 ReAct Agent 的 Skill 接入方式和工具重复注册问题，主要包括：

1. 恢复并固定使用 `SkillsAgentHook / SkillRegistry` 接入 Skill。
2. 将数据加工 Skill 调整为 Alibaba Skill Registry 可扫描的标准目录结构。
3. 移除旧的直接读取 Skill Markdown 的本地工具。
4. 增加工具回调去重 Hook，解决 `read_skill` 或动态注入工具重复注册导致的启动或运行错误。

## 2. 新增文件

1. `src/main/java/com/example/dataprocess/agent/config/ToolCallbackDeduplicationHook.java`
   - 作为轻量级 `AgentHook` 接入 ReactAgent。
   - 在模型调用前对 `ToolCallingChatOptions` 中的工具和动态注入工具按工具名去重。
   - 避免 `SkillsAgentHook` 提供的 `read_skill` 与后续动态工具注入发生重复注册。

2. `src/main/resources/agent/skills/data-processing-agent-skill/SKILL.md`
   - 将原 `data-processing-agent-skill.md` 调整为 Skill Registry 标准目录形态。
   - 保持原有数据加工 Skill 内容不变，只调整资源放置位置，供 `ClasspathSkillRegistry` 扫描。

3. `0529_CHANGE_RECORD_CN.md`
   - 记录本次提交涉及的新增、修改、删除和验证情况。

## 3. 修改文件

1. `src/main/java/com/example/dataprocess/agent/config/DataProcessingReactAgentConfig.java`
   - 使用 `ClasspathSkillRegistry.builder()` 加载 `agent/skills` 下的 Skill。
   - 显式传入 `SystemPromptTemplate`，避免 registry 内部默认模板构造触发版本兼容问题。
   - 使用 `SkillsAgentHook.builder().skillRegistry(...)` 绑定 Skill Registry。
   - 将 `DataProcessingAgentToolMethods` 转换为 `ToolCallback[]` 后，通过 `groupedTools` 绑定到 `data-processing-agent-skill`。
   - 移除直接 `.methodTools(toolMethods)` 的常驻注册方式，让业务工具在读取 Skill 后通过 `SkillsInterceptor` 动态注入。
   - 增加 `ToolCallbackDeduplicationHook`，放在 `SkillsAgentHook` 后面执行。

## 4. 删除文件

1. `src/main/java/com/example/dataprocess/agent/tool/AgentSkillTool.java`
   - 删除原因：不再通过项目自定义工具直接读取 Skill Markdown。
   - 当前统一由 `SkillsAgentHook / SkillRegistry / read_skill` 管理 Skill 读取。

2. `src/main/resources/agent/data-processing-agent-skill.md`
   - 删除原因：旧路径不符合当前 `ClasspathSkillRegistry` 扫描目录结构。
   - 内容已迁移到 `src/main/resources/agent/skills/data-processing-agent-skill/SKILL.md`。

## 5. 运行机制变化

1. Agent 启动后常驻工具中只应包含 `SkillsAgentHook` 自动提供的 `read_skill`。
2. Agent 必须先调用 `read_skill` 读取 `data-processing-agent-skill`。
3. `SkillsInterceptor` 根据已读取的 Skill，将该 Skill 绑定的业务工具动态注入模型请求。
4. `ToolCallbackDeduplicationHook` 在模型请求进入真实大模型调用前，对工具列表进行去重，降低框架内部工具合并导致重复注册的风险。

## 6. 验证结果

已执行针对本次 agent 配置代码的单独编译验证：

```powershell
javac -encoding UTF-8 -cp "target\classes;$cp" -d target\agent-compile-check src\main\java\com\example\dataprocess\agent\config\ToolCallbackDeduplicationHook.java src\main\java\com\example\dataprocess\agent\config\DataProcessingReactAgentConfig.java
```

结果：通过。

完整 Maven 编译当前未通过，阻塞点在非本次 agent 改动范围内：

```text
src/main/java/com/example/dataprocess/application/workflow/DataProcessingStateGraphDefinition.java
```

该文件当前存在语法错误，导致 `mvn -q -DskipTests compile` 无法完成。

## 7. 本次未纳入提交的工作区变化

工作区中仍存在多处非本次 agent 修复范围的修改和未跟踪文件，本次提交不主动纳入，避免混入无关变更。
