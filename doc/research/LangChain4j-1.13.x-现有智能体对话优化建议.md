# LangChain4j 1.13.x 现有智能体对话优化建议

## 1. 背景

当前项目后端主版本已经升级到 `LangChain4j 1.13.0`，核心对话链路仍然以 `AiServices + StreamingChatModel + ChatMemory + 动态 ToolProvider` 为主，而不是 `langchain4j-agentic` 的工作流/监督者模式。

这意味着：

- 你们已经可以直接吃到 `1.13.x` 在 `Skills`、`MCP`、`ToolProvider`、多模态工具返回上的收益。
- 但 `Agent 执行状态持久化`、`optional agents` 这类能力，暂时更适合用于“本地任务执行”或“多步任务编排”子链路，而不是先改主聊天链路。


## 2. 现状观察

结合当前代码，和 `1.13.x` 最相关的几个点如下。

### 2.1 主对话链路还是单体式 Tool 暴露

在 [ChatServiceImpl.java](/E:/Project/LingShu-AI/backend/lingshu-core/src/main/java/com/lingshu/ai/core/service/impl/ChatServiceImpl.java) 中：

- `AiServices.builder(...).maxSequentialToolsInvocations(30)` 直接驱动主聊天对话。
- Skills 会被加载进 `loadedSkills.toolProvider()`。
- MCP 工具会通过 `SafeMcpToolProvider` 全量加入。
- 内置工作区工具通过 `BuiltinWorkspaceToolProvider` 加入。

关键位置：

- `Skills` 加载与合并工具：`395-454`
- MCP 与内置工具一起进入 `builder.toolProviders(toolProviders)`：`438-454`

这套结构能工作，但有个明显问题：**MCP 工具仍然是“全局暴露”的，只有内置工作区工具开始做了半套 skill 隔离。**

### 2.2 Skill-scoped tools 只用到了一半

当前只有 `.lingshu/skills` 中的技能被加载，但现有技能目录里只有一个：

- [SKILL.md](/E:/Project/LingShu-AI/.lingshu/skills/deep_talk_philosophy_comfort/SKILL.md)

这是一个纯对话风格 Skill，没有 `tools:` front matter，也没有任务执行型工具绑定。

同时，在 [SkillToolManifest.java](/E:/Project/LingShu-AI/backend/lingshu-core/src/main/java/com/lingshu/ai/core/tool/SkillToolManifest.java) 里，你们已经实现了从 `SKILL.md` front matter 解析 `tools:`，并在 [ChatServiceImpl.java](/E:/Project/LingShu-AI/backend/lingshu-core/src/main/java/com/lingshu/ai/core/service/impl/ChatServiceImpl.java) 里据此启用内置工具。

这说明基础设施已经有了，但目前还没真正把“本地任务执行”收拢进 Skill 体系。

### 2.3 `execute_command` 目前是默认常驻工具

在 [ChatServiceImpl.java](/E:/Project/LingShu-AI/backend/lingshu-core/src/main/java/com/lingshu/ai/core/service/impl/ChatServiceImpl.java:447) 附近：

- `enabledBuiltinTools.add("execute_command");`

在 [BuiltinWorkspaceToolProvider.java](/E:/Project/LingShu-AI/backend/lingshu-core/src/main/java/com/lingshu/ai/core/tool/BuiltinWorkspaceToolProvider.java) 中：

- `execute_command` 默认可见
- `read_file` / `write_file` 只有在 Skill front matter 里声明时才会开放

这会导致一个很现实的问题：**普通聊天、情绪陪伴、闲聊，也一直看得见本地命令执行能力。**

对模型来说，这会带来：

- 工具选择噪声变大
- token 开销增大
- 误触发风险增大
- “明明只想聊天，却进入任务执行模式”的概率增加

### 2.4 MCP 接入是可用的，但兼容层偏厚

在 [McpServiceImpl.java](/E:/Project/LingShu-AI/backend/lingshu-core/src/main/java/com/lingshu/ai/core/service/impl/McpServiceImpl.java) 中：

- HTTP 侧已经统一走 `StreamableHttpMcpTransport`
- `STDIO`、HTTP Header、client cache、动态启停都已经做了
- 但为了兼容不同版本 API，仍在用反射判断 `headers()` / `customHeaders()`
- `getToolDetails()` 也在通过反射和字符串兜底提取工具名描述

这说明当前实现“能跑”，但版本对齐度不高，后续继续叠功能会越来越容易出现兼容性分叉。

### 2.5 版本存在“主 BOM 与 community BOM 不一致”

在 [backend/pom.xml](/E:/Project/LingShu-AI/backend/pom.xml) 中：

- `langchain4j.version = 1.13.0`
- 但 `langchain4j-community-bom = 1.12.2-beta22`

这属于当前最值得先修的点之一。因为你们项目里已经同时用了：

- `langchain4j-mcp`
- `langchain4j-skills`
- community 相关依赖

主版本和 community beta 版本错层，会直接拉高这些问题的概率：

- MCP builder 方法不一致
- metadata / resources API 行为不一致
- 技能与工具返回能力的组合边界不稳定


## 3. 对 1.13.x 最值得做的优化

下面按“收益 / 改动量 / 对现有链路侵入度”综合排序。

### 3.1 P0：先做版本对齐

建议：

- 直接把后端统一对齐到 `1.13.1` 稳定版
- 同时把 `langchain4j-community-bom` 对齐到同一代 `1.13.x beta`

原因：

- 这是所有后续优化的前提
- 可以减少你们在 `McpServiceImpl` 里对 builder API 的反射兜底
- 也能降低多模态工具返回、Skills、MCP metadata 行为不一致的风险

这一项本身不一定立刻带来用户体验提升，但它能明显降低后续维护成本。

### 3.2 P0：把本地任务执行彻底改成 Skill 触发，而不是默认常驻

当前最大可优化点不是“再加工具”，而是**让工具更少、更晚出现、更符合上下文**。

建议做法：

- 把 `execute_command` 从默认常驻改成按 Skill 启用
- 新增任务型 Skills，例如：
  - `workspace_readonly_inspection`
  - `workspace_file_edit`
  - `local_env_diagnosis`
  - `project_build_and_test`
- 在这些 Skill 的 front matter 中声明：
  - `tools: [read_file]`
  - `tools: [read_file, write_file]`
  - `tools: [execute_command, read_file]`

收益：

- 普通聊天不再看到执行命令能力
- 本地任务执行会先经过 `activate_skill`，推理更稳定
- 工具调用链条更接近“先识别任务类型，再进入任务模式”

这会是 `1.13.x` 里最贴合你们现状的收益点。

### 3.3 P0：给 MCP 工具做“按场景暴露”，不要全局平铺

当前 `SafeMcpToolProvider` 会把所有激活的 MCP server 工具全部加进来。

建议改成两层收敛：

1. 按 agent 或会话模式过滤
2. 按 skill 激活再暴露

比较实用的落地方式：

- “陪伴对话” agent：默认不挂 MCP
- “任务执行” agent：只挂指定 MCP server
- “资料查询” skill：只开放文档类 / 搜索类 MCP
- “开发协作” skill：只开放代码、shell、repo 相关 MCP

这样做比“一个总工具池”更适合你们现在的产品形态，因为你们的主场景不是纯工程 agent，而是“情感陪伴 + 少量任务能力”混合体。

### 3.4 P1：启用 Tool Search，解决工具过多时的上下文膨胀

LangChain4j `1.13.x` 已经很适合在 AI Service 级别挂 `ToolSearchStrategy`。

对你们来说，最合理的方案是：

- `activate_skill` 保持可见
- 极少数核心工具保持 `always visible`
- 大多数 MCP 工具转为 searchable

尤其在 MCP server 数量增多后，Tool Search 的价值会很明显：

- 首轮 prompt 不再塞满工具描述
- 模型先搜索，再调用
- 对“这个任务到底该不该用工具”判断更稳

如果后续你们要把 MCP 插件生态继续做大，这一项基本是必做的。

### 3.5 P1：利用 MCP metadata 做工具命名、分组和前端展示

LangChain4j 现在已经会把 MCP tool metadata 暴露到 `ToolSpecification.metadata()`。

你们当前在 [McpController.java](/E:/Project/LingShu-AI/backend/lingshu-web/src/main/java/com/lingshu/ai/web/controller/McpController.java) 里对外只返回了：

- `name`
- `description`

建议后续补充：

- server 名
- tool title
- annotations / `_meta`
- tool category
- 是否建议自动调用

收益：

- 前端 MCP 设置页不只是“列工具名”
- 可以做“推荐用途”“风险等级”“来源 server”展示
- 也更方便后续做按类目暴露、按权限暴露

### 3.6 P1：把 MCP Resources / Prompts 接进来，而不是只用 Tools

你们现在基本只把 MCP 当“工具执行通道”在用。

但对 LingShu 这类产品，MCP 的另外两类能力也很有价值：

- `resources`
- `prompts`

适合的场景：

- 外部知识源、用户手册、设定集、角色资料，用 resource 暴露
- 复杂任务模板、风格模板、对话模板，用 prompt 暴露

相比一股脑把这些内容塞到系统提示词里，资源化会更轻，也更适合按需读取。

### 3.7 P1：把“陪伴对话”和“任务执行”拆成两条策略链

当前主链路在一个 `ChatServiceImpl` 里同时承接：

- 情绪陪伴
- 多模态
- MCP 工具
- Skills
- 本地命令执行

功能是全的，但模式混杂。

建议逻辑上拆成两条运行策略：

- 对话模式：低工具密度，重点是 memory、prompt、风格 skill
- 任务模式：高工具密度，重点是 skill、MCP、workspace tool、tool timeline

不一定要拆成两个 Service，先拆成两套 builder 配置也可以。

这样能显著降低“同一个 assistant 既像陪伴者又像本地运维 agent”的人格漂移。

### 3.8 P2：把 Agent 状态持久化用于“长任务”，不要先硬塞进主聊天

`1.13.0` 的 agent execution state persist/recover 很有价值，但我不建议第一步就拿来改当前聊天主链。

更适合的用法是：

- 本地任务执行中断后恢复
- 多步文件处理任务断点续跑
- 需要数分钟甚至更久的 MCP / shell workflow

也就是说，它更适合未来的：

- `任务代理`
- `开发代理`
- `本地工作流代理`

而不是先改“普通聊天回复”。

### 3.9 P2：把工具可观测性从日志升级成结构化指标

你们现在已经有：

- `TurnTimelineService`
- `ToolEventListener`
- `tool result artifact registry`

这是非常好的基础。

下一步建议统计：

- 每轮是否触发工具
- 每个 skill 的工具命中率
- 每个 MCP server 的成功率 / 平均耗时
- `execute_command` 的平均调用次数
- tool arguments repair 触发率

这些指标能直接帮助你判断：

- 哪些工具该保留
- 哪些工具该 skill 化
- 哪些 server 描述不清导致模型误用


## 4. 对“本地任务执行”这一块的专项建议

如果只看你提到的“本地任务执行”，我会优先做下面 4 件事。

### 4.1 去掉默认常驻 `execute_command`

保留默认常驻的建议只有两个：

- `activate_skill`
- 极少数陪伴场景必需的静态工具

`execute_command` 不应该在闲聊时出现。

### 4.2 增加只读任务 Skill

先别急着开放写文件，先做一个只读 skill：

- 允许 `read_file`
- 允许 `execute_command`
- 明确要求先读、先看、先诊断，再决定是否要修改

这样比一开始就开放 `write_file` 更稳。

### 4.3 把命令执行结果分层

当前 `execute_command` 返回 JSON 文本没问题，但建议再补两层语义：

- `stdout`
- `stderr`
- `exitCode`
- `timedOut`
- `truncated`

这样 LLM 对“命令失败但输出有价值”和“命令真正没跑起来”会更容易区分。

### 4.4 给命令执行加“任务边界提示”

建议在任务型 skill 文本里明确要求模型遵循：

1. 先读取相关文件或目录
2. 再执行只读命令确认状态
3. 只有用户明确要求修改时，才进入写文件步骤
4. 每次执行前说明目的

这比单纯增强 `execute_command` 的 description 更有效。


## 5. 对 MCP 的专项建议

### 5.1 给工具名加 server 维度的命名规范

如果多个 MCP server 里存在相似工具名，建议统一做前缀或 metadata 标记，例如：

- `github_search_code`
- `filesystem_read_file`
- `browser_open_url`

这样可以减少模型误选。

### 5.2 在前端显示“工具来源 server”

当前前端设置页更偏配置中心，下一步可以让它更像“能力清单”：

- server 名
- tool 数量
- tool 分类
- 最近一次连接状态
- 最近一次 tool 调用是否成功

### 5.3 引入资源型 MCP

如果你们未来想让模型读取角色设定、知识库摘要、用户规则，而不是直接塞大 prompt，MCP resources 很值得接。

### 5.4 给不同 MCP server 设置不同暴露策略

建议至少分三档：

- 默认不暴露
- 任务 skill 激活后暴露
- 始终可见

大多数 server 应该属于第二档，而不是第三档。


## 6. 推荐落地顺序

### 第一阶段：低风险高收益

1. 对齐到 `1.13.1 + 对应 community BOM`
2. 去掉默认常驻 `execute_command`
3. 新增 1 到 2 个任务型 Skills
4. 让 `read_file/write_file/execute_command` 只由 Skill front matter 决定是否暴露

### 第二阶段：降低工具噪声

1. 给 MCP 工具做 server 级分组和命名规范
2. 引入 `ToolSearchStrategy`
3. 把非核心 MCP 工具转为 searchable

### 第三阶段：增强任务链路

1. 拆分对话模式 / 任务模式
2. 把长任务迁到 agentic workflow
3. 对长任务接入 execution state persist/recover


## 7. 我对当前项目的结论

如果只说一句话，我的判断是：

**你们现在最应该吃满的不是 1.13.x 的“更强 agent 编排”，而是“更细粒度的 Skill 工具隔离 + MCP/本地工具的按场景暴露”。**

原因很简单：

- 现有主链路本质上还是聊天系统，不是纯 agent 平台
- 目前真正影响质量的，是工具噪声和能力暴露边界
- 这些问题，正好是 `1.13.x` 在 Skills / ToolProvider / MCP 上最擅长解决的

所以从 ROI 看，推荐优先级如下：

1. 版本对齐
2. `execute_command` skill 化
3. MCP 工具按场景暴露
4. Tool Search
5. 再考虑 agentic 持久化工作流


## 8. 相关代码入口

- [backend/pom.xml](/E:/Project/LingShu-AI/backend/pom.xml)
- [ChatServiceImpl.java](/E:/Project/LingShu-AI/backend/lingshu-core/src/main/java/com/lingshu/ai/core/service/impl/ChatServiceImpl.java)
- [AiConfig.java](/E:/Project/LingShu-AI/backend/lingshu-core/src/main/java/com/lingshu/ai/core/config/AiConfig.java)
- [BuiltinWorkspaceToolProvider.java](/E:/Project/LingShu-AI/backend/lingshu-core/src/main/java/com/lingshu/ai/core/tool/BuiltinWorkspaceToolProvider.java)
- [SafeMcpToolProvider.java](/E:/Project/LingShu-AI/backend/lingshu-core/src/main/java/com/lingshu/ai/core/tool/SafeMcpToolProvider.java)
- [McpServiceImpl.java](/E:/Project/LingShu-AI/backend/lingshu-core/src/main/java/com/lingshu/ai/core/service/impl/McpServiceImpl.java)
- [McpController.java](/E:/Project/LingShu-AI/backend/lingshu-web/src/main/java/com/lingshu/ai/web/controller/McpController.java)
- [SKILL.md](/E:/Project/LingShu-AI/.lingshu/skills/deep_talk_philosophy_comfort/SKILL.md)


## 9. 官方参考

- [LangChain4j Releases](https://github.com/langchain4j/langchain4j/releases)
- [LangChain4j Skills](https://docs.langchain4j.dev/tutorials/skills/)
- [LangChain4j Tools](https://docs.langchain4j.dev/tutorials/tools/)
- [LangChain4j MCP](https://docs.langchain4j.dev/tutorials/mcp/)
