# AI 数据加工 Skill 与 Tool 建设 PRD

## 1. 文档目标

本文档基于最新方案 [SPRING_AI_ALIBABA_NATIVE_SKILL_FINAL_CN.md](</D:/lsy_projects/data_process_java/SPRING_AI_ALIBABA_NATIVE_SKILL_FINAL_CN.md>)，聚焦整理两类建设需求：

- `skill` 建设需求
- `tool` 封装需求

本文档只覆盖以下范围：

- 基于 `Spring AI Alibaba` 原生 `SKILL.md` 体系的 skill 建设
- 基于 `groupedTools` 的 Java tool 封装
- skill 与 tool 的映射关系
- skill/tool 接入 `DataProcessingAgent 数据加工智能体` 的要求

本文档不覆盖以下范围：

- 前端页面需求
- 数据库详细建表需求
- 规则引擎实现细节
- 非 AI 主链路的接口开发细节

---

## 2. 产品目标

在正式版 AI 数据加工系统中，建立一套可治理、可扩展、可审计的 skill 和 tool 体系，使单智能体能够：

- 通过真实 `SKILL.md` 执行模板识别、规则草拟、确认问题生成等 AI 任务
- 通过 Java tool 获取受控上下文和受控能力
- 在 `workflow-first` 架构下完成受限 AI 决策，而不接管业务主流程

---

## 3. 总体原则

1. 整个系统只有一个 `DataProcessingAgent 数据加工智能体`。
2. `skill` 必须以真实 `SKILL.md` 文件存在。
3. `tool` 必须以 Java 能力实现，并通过 `groupedTools` 暴露。
4. `skill` 负责 AI 认知任务，`tool` 负责受控能力供给。
5. `workflow` 负责流程推进和状态机，skill 和 tool 均无权推进任务状态。

---

## 4. Skill 建设需求

## 4.1 Skill 基础体系

### 需求 S-001：建立真实 Skill 文件体系

AS：
作为系统架构负责人，我希望 AI 技能以真实 `SKILL.md` 文件存在，而不是伪装成 Java 类，以便系统严格贴合 `Spring AI Alibaba` 原生能力。

WHEN：
当系统初始化 skill 能力体系并接入 `SkillRegistry 技能注册表` 时。

THEN：
系统应从约定目录加载 `SKILL.md` 文件，并将其作为唯一 skill 定义来源。

验收标准：
- 存在独立的 skill 目录结构。
- 每个 skill 目录下存在 `SKILL.md` 文件。
- Java 代码中不存在把 skill 本体定义为业务 Java 类的实现方式。
- skill 的运行依赖 `SkillRegistry` 或等价原生能力完成发现与注册。

### 需求 S-002：统一 Skill 标准结构

AS：
作为平台治理负责人，我希望所有 `SKILL.md` 都使用统一结构，以便后续维护、审查和扩展。

WHEN：
当新增任意一个 skill 文件时。

THEN：
该 skill 文件必须包含统一章节结构，包括目的、使用时机、输入要求、允许工具、输出契约、约束和禁止事项。

验收标准：
- 每个 `SKILL.md` 至少包含以下章节：
  - `Purpose 技能目的`
  - `When To Use 使用时机`
  - `Input Expectations 输入要求`
  - `Allowed Tools 允许工具`
  - `Output Contract 输出契约`
  - `Constraints 约束`
  - `Forbidden Actions 禁止事项`
- 新增 skill 时可按统一模板创建。
- 不允许存在结构缺失严重的 skill 文件进入主分支。

### 需求 S-003：Skill 注册方式原生化

AS：
作为开发负责人，我希望 skill 的注册与发现依赖 `Spring AI Alibaba` 原生能力，以便减少自造框架和后续维护成本。

WHEN：
当系统启动并初始化 agent 相关能力时。

THEN：
系统应通过 `FileSystemSkillRegistry 文件系统技能注册表` 或 `ClasspathSkillRegistry 类路径技能注册表` 注册 skills。

验收标准：
- skill 注册方式明确为 `FileSystemSkillRegistry` 或 `ClasspathSkillRegistry`。
- skill 不依赖手写自定义解析器作为主机制。
- skill 注册结果可被 agent 获取和使用。

## 4.2 一期 Skill 清单

### 需求 S-004：建设模板识别 Skill

AS：
作为数据加工运营人员，我希望系统具备模板识别 skill，以便根据输入快照和模板目录识别最匹配模板。

WHEN：
当任务输入已完成解析并生成统一 `InputSnapshot 输入快照` 后。

THEN：
系统应可执行 `skills/template-recognition/SKILL.md`，产出模板识别结构化结果。

验收标准：
- 存在 `skills/template-recognition/SKILL.md`。
- skill 输出至少包含：
  - `templateCode 模板编码`
  - `sceneCode 场景编码`
  - `countryCode 国家编码`
  - `confidence 置信度`
  - `alternatives 候选项`
  - `needUserConfirm 是否需要用户确认`
- skill 明确声明只能从模板目录中选择模板。
- skill 明确禁止编造目录之外模板。

### 需求 S-005：建设规则草拟 Skill

AS：
作为规则设计人员，我希望系统具备规则草拟 skill，以便根据模板、规则知识和样本数据生成 DSL 草稿。

WHEN：
当模板识别结果已产生，且输入快照与规则知识已可获取时。

THEN：
系统应可执行 `skills/rule-drafting/SKILL.md`，产出规则草稿结构化结果。

验收标准：
- 存在 `skills/rule-drafting/SKILL.md`。
- skill 输出至少包含：
  - `draftDsl 草稿DSL`
  - `ambiguousMappings 模糊映射`
  - `missingFields 缺失字段`
  - `defaultSuggestions 默认值建议`
  - `blockingIssues 阻断问题`
- skill 明确声明只能使用允许的 DSL transform 类型。
- skill 明确禁止发明 DSL 之外的隐藏转换逻辑。

### 需求 S-006：建设确认问题生成 Skill

AS：
作为业务确认人员，我希望系统具备确认问题生成 skill，以便把模板冲突、规则歧义和数据问题整理成单轮确认包。

WHEN：
当模板识别和规则草拟结果均已产生后。

THEN：
系统应可执行 `skills/confirmation-question/SKILL.md`，将所有待确认项整理为可落库的问题列表。

验收标准：
- 存在 `skills/confirmation-question/SKILL.md`。
- skill 输出至少包含：
  - `questions 问题列表`
  - `questionType 问题类型`
  - `options 选项`
  - `recommendedOption 建议选项`
- skill 明确要求合并为单轮确认。
- skill 明确禁止拆分成多轮散问。

## 4.3 二期 Skill 清单

### 需求 S-007：建设税局截图提取 Skill

AS：
作为图片场景处理人员，我希望系统具备税局截图提取 skill，以便处理税局网站截图这个特例场景。

WHEN：
当输入类型为税局截图且系统进入图片特例处理链路时。

THEN：
系统应可执行 `skills/tax-screenshot-extraction/SKILL.md`，完成固定结构提取。

验收标准：
- 存在 `skills/tax-screenshot-extraction/SKILL.md`。
- skill 明确标注仅用于税局截图场景。
- skill 明确禁止泛化到任意图片理解任务。

### 需求 S-008：建设知识导入辅助 Skill

AS：
作为知识库维护人员，我希望系统具备知识导入辅助 skill，以便理解知识包上传时的自然语言说明。

WHEN：
当用户上传知识包并附带 `instructionText 自然语言说明` 时。

THEN：
系统应可执行 `skills/knowledge-import-assist/SKILL.md`，输出结构化规则补充建议。

验收标准：
- 存在 `skills/knowledge-import-assist/SKILL.md`。
- skill 输出用于辅助知识整理，而非直接写入生效规则。
- skill 不进入主执行链路。

---

## 5. Tool 封装需求

## 5.1 Tool 基础体系

### 需求 T-001：建立 Java Tool 统一封装规范

AS：
作为平台开发负责人，我希望所有 tool 都遵循统一封装规范，以便降低维护成本并保证对模型暴露的能力一致可控。

WHEN：
当新增任意一个 Java tool 时。

THEN：
该 tool 应以独立 Java 能力形式实现，并具备清晰的职责、输入、输出和调用边界。

验收标准：
- 每个 tool 都有明确单一职责。
- 每个 tool 都有明确输入和输出定义。
- tool 不直接承担多步业务流程。
- tool 默认设计为只读能力，除非业务上有明确例外。

### 需求 T-002：所有 Tool 通过 groupedTools 暴露

AS：
作为系统安全负责人，我希望 tools 按 skill 分组暴露，而不是全量开放给智能体，以便控制模型权限边界。

WHEN：
当系统为某个 skill 配置可调用工具时。

THEN：
系统应通过 `groupedTools 分组工具` 控制该 skill 的工具可见范围。

验收标准：
- tools 不是全局无差别暴露。
- 每个 skill 对应明确的 grouped tool 名称。
- skill 只能访问其允许的那组 tools。

### 需求 T-003：Tool 返回结构化结果

AS：
作为智能体治理负责人，我希望 tool 返回结构化数据，而不是随意文本，以便模型稳定消费并方便后续审计。

WHEN：
当任意 tool 被 agent 调用时。

THEN：
tool 应返回结构化结果对象，而不是不可控自由文本。

验收标准：
- tool 返回值为结构化 DTO 或等价结构。
- 不以自由文本作为主要返回格式。
- tool 返回结果可被日志、审计或回放系统识别。

### 需求 T-004：Tool 不得推进任务状态

AS：
作为系统架构负责人，我希望 tool 不能推进任务状态，以便保证状态机只由 workflow 控制。

WHEN：
当任意 tool 执行完成时。

THEN：
tool 不应直接修改任务状态，不应绕过 workflow 推动主流程前进。

验收标准：
- tool 内不包含任务状态推进逻辑。
- tool 不直接触发导出、最终确认、全量执行等主流程动作。
- 状态推进仅由 workflow 或等价应用编排层完成。

## 5.2 一期 Tool 清单

### 需求 T-005：封装 LoadInputSnapshotTool

AS：
作为模板识别和规则草拟能力的使用者，我希望系统提供 `LoadInputSnapshotTool 加载输入快照工具`，以便智能体读取统一输入快照。

WHEN：
当模板识别或规则草拟 skill 执行时。

THEN：
智能体应能通过该 tool 获取 `InputSnapshot 输入快照`。

验收标准：
- 存在 `LoadInputSnapshotTool`。
- 返回内容至少包含表头、归一化表头、样本行摘要或等价快照信息。
- tool 只读，不修改快照。

### 需求 T-006：封装 LoadSampleRowsTool

AS：
作为模板识别和规则草拟能力的使用者，我希望系统提供 `LoadSampleRowsTool 加载样本行工具`，以便智能体查看样本数据特征。

WHEN：
当相关 skill 需要使用样本行进行判断时。

THEN：
智能体应能通过该 tool 获取样本行。

验收标准：
- 存在 `LoadSampleRowsTool`。
- tool 可返回样本行列表或等价结构。
- 样本行数量和字段范围受控，不返回无限量原始数据。

### 需求 T-007：封装 ReadTemplateCatalogTool

AS：
作为模板识别 skill 的使用者，我希望系统提供 `ReadTemplateCatalogTool 读取模板目录工具`，以便智能体从模板目录中选择模板。

WHEN：
当模板识别 skill 执行时。

THEN：
智能体应能通过该 tool 获取模板目录条目。

验收标准：
- 存在 `ReadTemplateCatalogTool`。
- 返回结果至少包含：
  - `templateCode 模板编码`
  - `scene 场景`
  - `country 国家`
  - `headers 列名集合`
- 不返回知识库之外的模板条目。

### 需求 T-008：封装 LookupHeaderAliasesTool

AS：
作为模板识别 skill 的使用者，我希望系统提供 `LookupHeaderAliasesTool 查询表头别名工具`，以便智能体补充理解多语言或别名表头。

WHEN：
当模板识别需要辅助别名信息时。

THEN：
智能体应能通过该 tool 获取表头别名与归一化信息。

验收标准：
- 存在 `LookupHeaderAliasesTool`。
- 返回结果包含别名和归一化值或等价结构。
- 返回结果可按语言、国家或模板维度过滤。

### 需求 T-009：封装 ReadRuleKnowledgeTool

AS：
作为规则草拟 skill 的使用者，我希望系统提供 `ReadRuleKnowledgeTool 读取规则知识工具`，以便智能体读取场景规则知识。

WHEN：
当规则草拟 skill 执行时。

THEN：
智能体应能通过该 tool 获取指定模板、场景、国家下的规则知识。

验收标准：
- 存在 `ReadRuleKnowledgeTool`。
- 返回结果来源可追溯到规则知识事实源。
- tool 只读，不直接修改规则知识。

### 需求 T-010：封装 BuildRuleDslSkeletonTool

AS：
作为规则草拟 skill 的使用者，我希望系统提供 `BuildRuleDslSkeletonTool 构建规则DSL骨架工具`，以便限制模型在合法结构内补全规则。

WHEN：
当规则草拟 skill 需要生成 DSL 草稿时。

THEN：
智能体应能通过该 tool 获取标准骨架 DSL。

验收标准：
- 存在 `BuildRuleDslSkeletonTool`。
- 返回结果为统一结构的 DSL 骨架。
- 骨架字段与允许的 DSL 结构保持一致。

### 需求 T-011：封装 ValidateDraftDslTool

AS：
作为规则草拟 skill 的使用者，我希望系统提供 `ValidateDraftDslTool 校验草稿DSL工具`，以便在确认前检查草稿规则是否合法。

WHEN：
当规则草拟结果产生后。

THEN：
智能体应能通过该 tool 对草稿 DSL 进行校验。

验收标准：
- 存在 `ValidateDraftDslTool`。
- 返回至少包含：
  - `valid 是否合法`
  - `issues 问题列表`
- 可校验 JSON 结构、transform 类型、字段完整性等核心问题。

### 需求 T-012：封装 LoadAllowedTransformsTool

AS：
作为规则草拟 skill 的使用者，我希望系统提供 `LoadAllowedTransformsTool 加载允许转换类型工具`，以便明确系统允许的 DSL transform 范围。

WHEN：
当规则草拟 skill 执行时。

THEN：
智能体应能通过该 tool 获取允许的 transform 类型集合。

验收标准：
- 存在 `LoadAllowedTransformsTool`。
- 返回结果至少包含当前方案允许的 transform 类型。
- skill 不应依赖硬编码 transform 说明代替该 tool。

### 需求 T-013：封装 LoadTaskContextTool

AS：
作为确认问题生成 skill 的使用者，我希望系统提供 `LoadTaskContextTool 加载任务上下文工具`，以便智能体理解当前任务的确认背景。

WHEN：
当确认问题生成 skill 执行时。

THEN：
智能体应能通过该 tool 获取任务上下文信息。

验收标准：
- 存在 `LoadTaskContextTool`。
- 返回内容包括任务阶段、输入类型、模板状态或等价上下文。
- 不包含推进状态的副作用。

## 5.3 二期 Tool 清单

### 需求 T-014：封装 LoadConfirmationConstraintsTool

AS：
作为确认问题生成 skill 的使用者，我希望系统提供 `LoadConfirmationConstraintsTool 加载确认约束工具`，以便统一单轮确认的组织规则。

WHEN：
当确认问题生成 skill 需要组织问题顺序和结构时。

THEN：
智能体应能通过该 tool 获取确认约束配置。

验收标准：
- 存在 `LoadConfirmationConstraintsTool`。
- 返回内容可用于约束单轮确认包生成。
- tool 不直接生成确认包最终结果。

### 需求 T-015：封装 LoadTaxScreenshotSchemaTool

AS：
作为税局截图 skill 的使用者，我希望系统提供 `LoadTaxScreenshotSchemaTool 加载税局截图结构工具`，以便约束截图提取输出结构。

WHEN：
当税局截图 skill 执行时。

THEN：
智能体应能通过该 tool 获取固定 schema。

验收标准：
- 存在 `LoadTaxScreenshotSchemaTool`。
- 返回结果仅服务于税局截图场景。
- 不将其当作通用图片结构工具使用。

### 需求 T-016：封装 LoadOcrBlocksTool

AS：
作为税局截图 skill 的使用者，我希望系统提供 `LoadOcrBlocksTool 加载OCR文本块工具`，以便在需要时查看 OCR 结果明细。

WHEN：
当图片场景需要分析 OCR 原始块信息时。

THEN：
智能体应能通过该 tool 获取 OCR 文本块详情。

验收标准：
- 存在 `LoadOcrBlocksTool`。
- 返回结果为受控 OCR 文本块结构。
- 不直接将 OCR 原始结果写入正式业务结果。

---

## 6. Skill 与 Tool 映射需求

### 需求 M-001：模板识别 Skill 映射模板识别工具组

AS：
作为系统架构负责人，我希望模板识别 skill 只使用模板识别相关 tools，以便限制权限边界。

WHEN：
当 `skills/template-recognition/SKILL.md` 被执行时。

THEN：
系统应仅向该 skill 暴露 `template-recognition-tools 模板识别工具组`。

验收标准：
- 工具组至少包含：
  - `LoadInputSnapshotTool`
  - `LoadSampleRowsTool`
  - `ReadTemplateCatalogTool`
  - `LookupHeaderAliasesTool`
- 该 skill 默认不可访问规则草拟工具组。

### 需求 M-002：规则草拟 Skill 映射规则草拟工具组

AS：
作为系统架构负责人，我希望规则草拟 skill 只使用规则草拟相关 tools，以便限制其能力范围。

WHEN：
当 `skills/rule-drafting/SKILL.md` 被执行时。

THEN：
系统应仅向该 skill 暴露 `rule-drafting-tools 规则草拟工具组`。

验收标准：
- 工具组至少包含：
  - `LoadInputSnapshotTool`
  - `ReadRuleKnowledgeTool`
  - `BuildRuleDslSkeletonTool`
  - `ValidateDraftDslTool`
  - `LoadAllowedTransformsTool`
- 该 skill 默认不可访问确认工具组。

### 需求 M-003：确认问题 Skill 映射确认工具组

AS：
作为系统架构负责人，我希望确认问题生成 skill 只使用确认相关 tools，以便减少模型越权。

WHEN：
当 `skills/confirmation-question/SKILL.md` 被执行时。

THEN：
系统应仅向该 skill 暴露 `confirmation-tools 确认工具组`。

验收标准：
- 工具组至少包含：
  - `LoadTaskContextTool`
  - `LoadConfirmationConstraintsTool` 或等价阶段性替代能力
- 该 skill 默认不可访问规则草拟工具组。

---

## 7. 与 Agent 和 Workflow 的协作需求

### 需求 A-001：单智能体统一执行 Skill

AS：
作为系统架构负责人，我希望所有 skill 都由单一 `DataProcessingAgent 数据加工智能体` 执行，以便保持 AI 入口统一。

WHEN：
当 workflow 进入某个 AI 决策步骤时。

THEN：
系统应由 `DataProcessingAgent` 读取并执行对应 `SKILL.md`。

验收标准：
- 系统中不存在多个并列业务子智能体作为正式架构。
- skill 执行入口统一。
- 不同 skill 通过上下文切换执行，而非切换不同业务 agent。

### 需求 A-002：Workflow 决定 Skill 执行时机

AS：
作为流程治理负责人，我希望 skill 的执行时机由 workflow 控制，以便保持主流程确定性。

WHEN：
当任务在不同阶段推进时。

THEN：
系统应由 workflow 明确决定调用哪个 skill，而不是由智能体自主决定整个业务流程。

验收标准：
- 模板识别、规则草拟、确认问题生成都由 workflow 显式触发。
- agent 无权自行跳过确认、执行导出或推进主流程。
- workflow-first 原则在实现设计中明确成立。

---

## 8. 运行链路补充需求

### 需求 R-001：图片数据解析转换并落库

AS：
作为图片场景业务人员，我希望系统能够对图片数据完成解析、结构转换并落库，以便图片输入能够进入正式任务链路，而不是停留在临时识别结果。

WHEN：
当用户上传图片文件，且 workflow 判定进入图片解析链路时。

THEN：
系统应完成图片数据解析、统一快照转换，并将解析结果及必要的任务运行态数据持久化存储。

验收标准：
- 图片输入可被系统接收并创建任务。
- 图片解析结果能够转换为统一 `InputSnapshot 输入快照` 或等价正式输入结构。
- 解析后的关键结果可落库，而不是只保存在内存中。
- 图片场景的任务状态可正常推进到后续 skill 或规则执行阶段。
- 若图片场景为税局截图特例，则其结构化结果也必须具备正式落库路径。

### 需求 R-002：Excel 数据加工后支持分页预览

AS：
作为业务确认人员，我希望 Excel 数据加工执行后可以按页预览结果，以便在大量数据场景下高效核查转换效果。

WHEN：
当 Excel 数据加工已完成规则执行并生成 staging 结果后。

THEN：
系统应提供分页预览能力，支持按页查看加工结果和必要的校验信息。

验收标准：
- Excel 加工结果先写入 staging 或等价预览存储层。
- 系统支持分页查询预览结果。
- 预览结果至少包含行级目标数据。
- 预览结果可附带校验状态、告警信息或等价辅助信息。
- 不要求一次性返回全部结果。

### 需求 R-003：Excel 数据加工用户确认后落库存储

AS：
作为业务归档人员，我希望 Excel 数据加工结果在用户确认后能够正式落库存储，以便形成可追溯的正式结果版本。

WHEN：
当用户完成预览确认，并触发最终确认动作后。

THEN：
系统应将确认后的 Excel 数据加工结果写入正式存储，而不是停留在 staging 预览层。

验收标准：
- staging 结果与正式结果存储路径明确区分。
- 用户未确认前，不得写入正式结果存储。
- 用户确认后，系统可将有效结果正式落库。
- 正式结果落库应带有任务标识、版本标识或等价可追溯信息。
- 正式结果写入完成后，任务状态可推进到最终确认后阶段。

### 需求 R-004：代码层面统一大模型客户端访问代码

AS：
作为平台开发负责人，我希望代码层面对大模型访问采用统一客户端封装，以便统一模型接入方式、日志、重试、超时、鉴权和观测能力。

WHEN：
当任意 skill、tool 或模型驱动能力需要访问大模型时。

THEN：
系统应通过统一的大模型客户端访问层发起请求，而不是在各处散写模型调用代码。

验收标准：
- 存在统一的大模型客户端访问层或等价封装。
- skill 不直接在业务代码中分散拼接各类模型访问逻辑。
- 模型访问层至少统一处理：
  - 模型路由或模型选择
  - 超时控制
  - 鉴权配置
  - 日志或审计
  - 错误处理
- 多模态模型访问与文本模型访问应纳入统一治理体系。

---

## 9. 交付物要求

本 PRD 对应的一期交付物至少包括：

- `skills/template-recognition/SKILL.md`
- `skills/rule-drafting/SKILL.md`
- `skills/confirmation-question/SKILL.md`
- `template-recognition-tools` 工具组定义
- `rule-drafting-tools` 工具组定义
- `confirmation-tools` 工具组定义
- 一期 Java tool 实现清单
- 与 `SkillRegistry`、`SkillsAgentHook`、`groupedTools` 的接入方案说明

---

## 10. 最终结论

这份 PRD 的核心要求可以压缩成一句话：

在 AI 数据加工正式版中，必须建设一套基于 `Spring AI Alibaba` 原生 `SKILL.md` 的 skill 体系，以及一套按 `groupedTools` 分组暴露的 Java tool 体系，并由单一智能体执行、由 Java workflow 控制主流程。
