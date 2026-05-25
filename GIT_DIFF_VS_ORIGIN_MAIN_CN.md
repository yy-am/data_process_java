# 当前项目相对远程 Git 的变更清单

## 1. 对比基线

本文档的对比基线为：

1. 当前本地分支：`main`
2. 跟踪远程分支：`origin/main`
3. 对比方式：当前工作区相对 `origin/main`

说明：

1. 本文档包含已修改但未提交的文件
2. 本文档包含新增但尚未纳入 Git 跟踪的文件
3. 本文档包含当前工作区中已删除的文件

---

## 2. 新增文件

### 2.1 新增的领域模型类

1. `src/main/java/com/example/dataprocess/domain/model/VagueBindingRecoStatus.java`
   1. 类型：新增
   2. 作用：定义模糊绑定识别状态枚举
2. `src/main/java/com/example/dataprocess/domain/model/VagueBindingRecoItem.java`
   1. 类型：新增
   2. 作用：定义单个规则依赖输入列的绑定识别结果
3. `src/main/java/com/example/dataprocess/domain/model/VagueBindingRecoResult.java`
   1. 类型：新增
   2. 作用：定义模糊绑定识别结果总对象

### 2.2 新增的基础设施类

1. `src/main/java/com/example/dataprocess/infrastructure/service/ProcessingRuleLoader.java`
   1. 类型：新增
   2. 作用：替代原 `ProcessingRuleService`，专职读取和解析规则文档
2. `src/main/java/com/example/dataprocess/infrastructure/service/VagueBindingRecoService.java`
   1. 类型：新增
   2. 作用：负责调用 AI 对规则依赖输入列和上传表头做模糊绑定识别

### 2.3 新增的提示词资源

1. `src/main/resources/prompts/vague-binding-reco-prompt.md`
   1. 类型：新增
   2. 作用：为 `VagueBindingRecoService` 提供运行时提示词模板

### 2.4 新增的设计文档

1. `CURRENT_WORKFLOW_DESIGN_CN.md`
   1. 类型：新增
   2. 作用：描述当前工作流的最新设计
2. `GIT_DIFF_VS_ORIGIN_MAIN_CN.md`
   1. 类型：新增
   2. 作用：记录当前工作区相对 `origin/main` 的差异

---

## 3. 修改文件

### 3.1 模板识别相关

1. `src/main/java/com/example/dataprocess/domain/model/TemplateRecognitionResult.java`
   1. 类型：修改
   2. 变化：删除 `unresolvedTargetFields`
   3. 目的：让模板识别只负责模板识别，不再承载模糊映射识别职责

2. `src/main/java/com/example/dataprocess/infrastructure/service/TemplateRecognitionService.java`
   1. 类型：修改
   2. 变化：
      1. 配合新的 `TemplateRecognitionResult` 结构调整
      2. 去掉对 `unresolvedTargetFields` 的归一化处理
      3. 使用新的英文版模板识别提示词资源结构
   3. 目的：收窄模板识别职责

3. `src/main/resources/prompts/template-recognition-prompt.md`
   1. 类型：修改
   2. 变化：
      1. 去掉 `unresolvedTargetFields` 相关说明
      2. 调整提示词内容，使其只关注模板识别
   3. 目的：使提示词与新的模板识别职责一致

### 3.2 用户确认相关

1. `src/main/java/com/example/dataprocess/infrastructure/service/StructuredConfirmationService.java`
   1. 类型：修改
   2. 变化：
      1. 不再依赖 `TemplateRecognitionResult.unresolvedTargetFields`
      2. 改为调用 `ProcessingRuleLoader`
      3. 改为调用 `VagueBindingRecoService`
      4. 只负责把模糊绑定识别结果和显式 `USER_CONFIRM` 规则整合为结构化确认项
   3. 目的：让确认服务只负责结果整合与校验

### 3.3 DSL 生成相关

1. `src/main/java/com/example/dataprocess/infrastructure/service/RuleDraftingService.java`
   1. 类型：修改
   2. 变化：
      1. 将 `ProcessingRuleService` 替换为 `ProcessingRuleLoader`
      2. 读取规则文档的方法同步调整
   3. 目的：与新的规则读取组件命名保持一致

---

## 4. 删除文件

### 4.1 代码文件删除

1. `src/main/java/com/example/dataprocess/infrastructure/service/ProcessingRuleService.java`
   1. 类型：删除
   2. 原因：其职责已经由 `ProcessingRuleLoader` 接替

2. `src/main/java/com/example/dataprocess/interfaces/restful/package-info.java`
   1. 类型：删除
   2. 说明：该删除当前已存在于工作区
   3. 备注：该文件删除并非本轮核心工作流改造的重点，但当前工作区相对 `origin/main` 已表现为删除状态

### 4.2 旧文档删除

1. `SPRING_AI_ALIBABA_INTRO_QUICKSTART_CN.md`
   1. 类型：删除
   2. 原因：用户要求删除旧文档
2. `SPRING_AI_ALIBABA_STATEGRAPH_SEQUENCE_CN.md`
   1. 类型：删除
   2. 原因：用户要求删除旧文档
3. `SPRING_AI_ALIBABA_STATEGRAPH_SKILL_DESIGN_CN.md`
   1. 类型：删除
   2. 原因：用户要求删除旧文档

---

## 5. 本轮变更的核心主题

从整体上看，当前工作区相对 `origin/main` 的核心变化主要是以下几类：

1. 模板识别职责收窄
   1. 模板识别只负责识别模板
   2. 不再输出模糊映射字段列表

2. 规则读取组件重命名
   1. `ProcessingRuleService` 被 `ProcessingRuleLoader` 替代
   2. 组件职责更加明确

3. 引入模糊绑定识别能力
   1. 新增 `VagueBindingRecoService`
   2. 新增模糊绑定识别结果模型
   3. 新增对应提示词资源

4. 用户确认逻辑重构
   1. `StructuredConfirmationService` 不再自行硬编码构造模糊映射候选
   2. 改为消费 `VagueBindingRecoService` 的结构化识别结果

5. 文档更新
   1. 删除旧设计文档
   2. 补充新的工作流设计说明和当前差异清单

---

## 6. 当前结论

相对 `origin/main`，当前项目已经从“模板识别阶段顺带识别未解决映射项”的设计，演进为：

1. 模板识别只负责模板识别
2. 规则读取独立成加载器
3. 模糊绑定识别独立为 AI 服务
4. 结构化确认服务只负责结果整合和校验

这就是当前工作区相对远程 Git 的主要差异。

