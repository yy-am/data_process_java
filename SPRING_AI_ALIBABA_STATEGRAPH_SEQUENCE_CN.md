# Spring AI Alibaba StateGraph Skill 执行时序补充说明

> 说明
>
> - 本文档是对 `SPRING_AI_ALIBABA_STATEGRAPH_SKILL_DESIGN_CN.md` 的补充。
> - 重点回答“核心类的运行先后顺序、调用关系是什么”。
> - 图中的英文类名都补充了中文注释，便于直接用于评审和沟通。

---

## 1. 核心类运行先后顺序

下面这张图描述的是“提交任务后，到生成最终 DSL 并执行转换”为止的主链路先后顺序。

```mermaid
flowchart TD
    A["DataProcessingTaskSubmissionInterface
    数据处理任务提交接口"] --> B["DataProcessingStateGraphWorkflow
    数据处理状态图工作流"]
    B --> C["DataProcessingStateGraphDefinition
    数据处理状态图定义"]
    C --> D["BuildInputSnapshotNode
    构建输入快照节点"]
    D --> E["TemplateRecognitionSkillNode
    模板识别技能节点"]
    E --> F["TemplateRecognitionSkillRuntime
    模板识别技能运行时适配"]
    F --> G["SkillRegistry
    技能注册表，负责读取 SKILL.md"]
    F --> H["groupedTools
    分组工具集合，只暴露当前 skill 可见工具"]
    F --> I["ChatClient / ChatModel
    大模型调用入口"]
    I --> J["TemplateRecognitionResult
    模板识别结果"]

    J --> K{"NeedUserConfirmationRouter
    是否需要用户确认路由"}
    K -->|是 Yes| L["ConfirmationQuestionSkillNode
    确认问题生成技能节点"]
    L --> M["ConfirmationQuestionSkillRuntime
    确认问题技能运行时适配"]
    M --> N["UserConfirmationItems
    用户确认项结果"]
    N --> O["WaitUserConfirmationNode
    等待用户确认节点"]
    O --> P["UserConfirmationInterface
    用户确认接口"]
    P --> Q["DataProcessingStateGraphWorkflow
    工作流恢复执行"]

    K -->|否 No| R["RuleDraftingSkillNode
    规则草拟技能节点"]
    Q --> R
    R --> S["RuleDraftingSkillRuntime
    规则草拟技能运行时适配"]
    S --> T["FinalDsl
    最终 DSL 结果"]
    T --> U["DslValidationNode
    DSL 校验节点"]
    U -->|通过 Pass| V["DslTransformationNode
    DSL 转换执行节点"]
    V --> W["DslTransformationEngine
    DSL 转换引擎"]
    W --> X["CompleteNode
    流程完成节点"]
```

---

## 2. 主链路调用时序图

下面这张时序图描述“任务提交后”各核心类之间的调用关系，以及它们的前后顺序。

```mermaid
sequenceDiagram
    autonumber
    participant API as DataProcessingTaskSubmissionInterface\n数据处理任务提交接口
    participant WF as DataProcessingStateGraphWorkflow\n数据处理状态图工作流
    participant DEF as DataProcessingStateGraphDefinition\n数据处理状态图定义
    participant SNAP as BuildInputSnapshotNode\n构建输入快照节点
    participant TRN as TemplateRecognitionSkillNode\n模板识别技能节点
    participant TRR as TemplateRecognitionSkillRuntime\n模板识别技能运行时适配
    participant SR as SkillRegistry\n技能注册表
    participant GT as groupedTools\n分组工具集合
    participant LLM as ChatClient / ChatModel\n大模型调用入口
    participant CQN as ConfirmationQuestionSkillNode\n确认问题生成技能节点
    participant CQR as ConfirmationQuestionSkillRuntime\n确认问题技能运行时适配
    participant RDN as RuleDraftingSkillNode\n规则草拟技能节点
    participant RDR as RuleDraftingSkillRuntime\n规则草拟技能运行时适配
    participant VAL as DslValidationNode\nDSL 校验节点
    participant TFN as DslTransformationNode\nDSL 转换执行节点
    participant ENG as DslTransformationEngine\nDSL 转换引擎

    API->>WF: submit(request)\n提交任务
    WF->>DEF: build()/load graph\n构建或加载状态图
    WF->>SNAP: execute(state)\n执行输入快照构建
    SNAP-->>WF: InputSnapshot\n返回输入快照

    WF->>TRN: execute(state)\n执行模板识别技能节点
    TRN->>TRR: execute(state)\n调用模板识别运行时
    TRR->>SR: readSkillContent(template-recognition)\n读取技能定义
    TRR->>GT: resolve template-recognition tools\n获取该 skill 可见工具
    TRR->>LLM: prompt + toolCallbacks\n发起模型推理
    LLM-->>TRR: TemplateRecognitionResult\n返回模板识别结果
    TRR-->>TRN: result
    TRN-->>WF: result

    alt needUserConfirm = true\n需要用户确认
        WF->>CQN: execute(state)\n执行确认问题技能节点
        CQN->>CQR: execute(state)\n调用确认问题运行时
        CQR->>SR: readSkillContent(confirmation-question)\n读取技能定义
        CQR->>GT: resolve confirmation-question tools\n获取该 skill 可见工具
        CQR->>LLM: prompt + toolCallbacks\n发起模型推理
        LLM-->>CQR: UserConfirmationItems\n返回确认问题结果
        CQR-->>CQN: result
        CQN-->>WF: result
        Note over WF: 进入等待用户确认阶段，外部确认后再恢复工作流
    else needUserConfirm = false\n不需要用户确认
        Note over WF: 直接进入规则草拟阶段
    end

    WF->>RDN: execute(state)\n执行规则草拟技能节点
    RDN->>RDR: execute(state)\n调用规则草拟运行时
    RDR->>SR: readSkillContent(rule-drafting)\n读取技能定义
    RDR->>GT: resolve rule-drafting tools\n获取该 skill 可见工具
    RDR->>LLM: prompt + toolCallbacks\n发起模型推理
    LLM-->>RDR: FinalDsl\n返回最终 DSL
    RDR-->>RDN: result
    RDN-->>WF: result

    WF->>VAL: validate(finalDsl)\n校验 DSL
    VAL-->>WF: validation result\n返回校验结果
    WF->>TFN: execute(finalDsl)\n执行 DSL 转换
    TFN->>ENG: transform(finalDsl)\n调用 DSL 转换引擎
    ENG-->>TFN: transformed rows\n返回转换结果
    TFN-->>WF: done
    WF-->>API: DataProcessingTaskResponse\n返回任务响应
```

---

## 3. 三个 SkillNode 的内部调用关系

这张图专门描述单个 `SkillNode` 内部是如何继续下钻到 `SkillRuntime`、`SkillRegistry`、`groupedTools` 和模型调用层的。

```mermaid
sequenceDiagram
    autonumber
    participant NODE as *SkillNode\n技能节点
    participant RT as *SkillRuntime\n技能运行时适配
    participant STATE as SkillExecutionStateHolder\n技能执行状态持有器
    participant REG as SkillRegistry\n技能注册表
    participant TOOLS as groupedTools\n分组工具集合
    participant CLIENT as ChatClient\n聊天客户端
    participant MODEL as ChatModel\n聊天模型

    NODE->>RT: execute(graphState)\n传入图状态
    RT->>STATE: setCurrentState(state)\n写入当前执行上下文
    RT->>REG: readSkillContent(skillId)\n读取对应 SKILL.md
    RT->>TOOLS: get(skillId)\n获取对应工具回调列表
    RT->>CLIENT: create(model)\n创建聊天客户端调用
    CLIENT->>MODEL: system prompt + user prompt + tools\n携带提示词与工具发起推理
    MODEL-->>CLIENT: structured result\n返回结构化结果
    CLIENT-->>RT: entity(ResultClass)\n解析为目标结果对象
    RT->>STATE: clear()\n清理执行上下文
    RT-->>NODE: result\n返回技能执行结果
```

---

## 4. 运行顺序总结

可以把当前方案的核心类顺序概括为：

1. `Interface` 接收请求。
2. `Workflow` 启动或恢复 `StateGraph`。
3. `StateGraphDefinition` 决定下一步该执行哪个节点。
4. 普通节点直接处理确定性逻辑，例如构建快照、DSL 校验、DSL 转换。
5. `SkillNode` 只负责把当前 `graphState` 交给对应的 `*SkillRuntime`。
6. `*SkillRuntime` 读取 `SKILL.md`、解析当前 skill 可见工具、再发起模型调用。
7. `Tool` 只在当前 skill 的允许范围内暴露给模型。
8. `Workflow` 根据 skill 输出结果继续路由到下一节点。

---

## 5. 评审时建议重点看什么

- `Workflow` 是否只负责流程推进，而不是掺入具体 prompt 细节。
- `SkillNode` 是否只绑定一个明确的 skill。
- `*SkillRuntime` 是否只负责“技能执行适配”，而不是承担主流程路由。
- `groupedTools` 是否真正限制了每个 skill 的工具可见范围。
- `DslValidationNode`、`DslTransformationNode` 是否仍然保持确定性执行，而不是重新交给模型判断。

