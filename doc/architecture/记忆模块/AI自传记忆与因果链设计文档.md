# AI自传记忆与因果链设计文档

> 版本: v4.0  
> 日期: 2026-04-21  
> 状态: **设计中**  
> 更新说明: 新增AI自传记忆系统，实现因果链追踪与情感演化

---

## 一、设计目标

### 1.1 核心问题

当前记忆系统存在以下缺陷：

| 问题 | 表现 | 影响 |
|------|------|------|
| **AI无自我记忆** | 不记住自己说了什么、做了什么承诺 | 无法形成连贯的对话人格 |
| **缺少因果链** | 只记录用户事实，不记录"AI行为→用户反馈"的关联 | 无法体现"我建议了X，你觉得有用"的连贯性 |
| **情感单向** | 只分析用户情感，不记录AI的情感回应效果 | 无法演化出个性化的陪伴策略 |
| **无法自治理** | 记忆只增不演化，缺少自我反思机制 | 记忆库膨胀，低价值信息堆积 |

### 1.2 设计目标

1. **AI自传记忆**：AI记住自己的陈述、承诺、建议、安慰行为
2. **因果链追踪**：建立"AI行为→用户反馈"的关联边
3. **情感双向演化**：记录AI情感回应的效果，形成个性化陪伴策略
4. **自治理机制**：记忆自动链接、压缩、进化，模拟人类学习过程

### 1.3 预期效果

| 场景 | 优化前 | 优化后 |
|------|--------|--------|
| 用户说"上次的方法真有用" | AI无法关联到具体建议 | AI检索到"我昨天建议你运动减压"，回应"很高兴运动对你有帮助！" |
| 用户再次提到压力 | AI重复之前的建议 | AI检索到"上次建议运动有效"，回应"上次运动减压效果不错，今天要继续吗？" |
| AI做出承诺后 | AI忘记自己的承诺 | AI主动跟进"你上次说想尝试冥想，有开始吗？" |
| 长期陪伴后 | AI的回应风格一成不变 | AI演化出"该用户对运动建议接受度高，对冥想建议接受度低"的策略 |

---

## 二、架构设计

### 2.1 整体架构

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        AI自传记忆系统架构                                     │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│    用户消息 ────────────────────────────────────────────────────────►        │
│        │                                                                     │
│        ▼                                                                     │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │              Stage 1: 对话生成 (同步)                                │   │
│  │                                                                      │   │
│  │  • 记忆检索 (retrieveContext)                                        │   │
│  │    - 用户事实检索（现有）                                            │   │
│  │    - AI自传检索（新增）                                              │   │
│  │    - 因果链检索（新增）                                              │   │
│  │  • 关系提示构建 (getRelationshipPrompt)                              │   │
│  │  • System Prompt 合并                                                │   │
│  │  • LLM 流式回复                                                      │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│        │                                                                     │
│        ▼ 异步后处理                                                          │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │              Stage 2: 回合后处理决策 (异步)                          │   │
│  │                                                                      │   │
│  │  TurnDecisionClassifier 判断是否触发：                               │   │
│  │  - analyzeEmotion: 是否需要情感分析                                  │   │
│  │  - extractFacts: 是否需要事实提取                                    │   │
│  │  - extractAIBehavior: 是否需要提取AI行为（新增）                     │   │
│  │  - recordInteraction: 是否记录互动                                   │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│        │                                                                     │
│        ├──────────────────┬──────────────────┬──────────────┐               │
│        ▼                  ▼                  ▼              ▼               │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐ ┌──────────────┐    │
│  │ 情感分析     │  │ 用户事实提取 │  │ AI行为提取   │ │ 因果链构建   │    │
│  │ (现有)       │  │ (现有)       │  │ (新增)       │ │ (新增)       │    │
│  └──────────────┘  └──────────────┘  └──────────────┘ └──────────────┘    │
│        │                  │                  │              │               │
│        │                  │                  ▼              │               │
│        │                  │     ┌─────────────────────┐    │               │
│        │                  │     │ AISelfFactExtractor │    │               │
│        │                  │     │ - AI建议/承诺/安慰  │    │               │
│        │                  │     │ - 情感回应策略      │    │               │
│        │                  │     └─────────────────────┘    │               │
│        │                  │                  │              │               │
│        ▼                  ▼                  ▼              ▼               │
│  ┌──────────────────────────────────────────────────────────────────┐    │
│  │         Stage 3: 记忆存储 (Neo4j + pgvector)                     │    │
│  │                                                                   │    │
│  │  用户记忆图（现有）:                                               │    │
│  │  • FactNode: 用户事实节点                                         │    │
│  │  • EmotionalEpisode: 情感片段                                     │    │
│  │                                                                   │    │
│  │  AI自传图（新增）:                                                 │    │
│  │  • AISelfNode: AI行为节点（建议/承诺/安慰/观点）                  │    │
│  │  • CausalEdge: 因果链边（AI行为→用户反馈）                        │    │
│  │  • ExperiencePattern: 经验模式节点（压缩后的因果链）              │    │
│  │                                                                   │    │
│  │  跨图关联:                                                         │    │
│  │  • AI行为 --TRIGGERED--> 用户事实                                 │    │
│  │  • AI行为 --RESPONDED_TO--> 用户事实                              │    │
│  │  • AI行为 --CAUSED_BY--> 用户事实                                 │    │
│  └──────────────────────────────────────────────────────────────────┘    │
│                                                                              │
│    ══════════════════════════════════════════════════════════════════════   │
│                                                                              │
│    后台任务 (每小时执行)                                                     │
│    ┌───────────────────────────────────────────────────────────────────┐   │
│    │ Stage 4: 记忆自治理 (新增)                                         │   │
│    │                                                                    │   │
│    │ • 因果链完整性检查：AI行为是否有用户反馈？                         │   │
│    │ • 因果链压缩：多次相似建议合并为经验模式                           │   │
│    │ • 情感演化总结：哪些安慰方式对该用户有效？                         │   │
│    │ • 过期承诺清理：AI的承诺是否已兑现？                               │   │
│    │ • 记忆自动链接：新记忆与旧记忆的语义关联                           │   │
│    └───────────────────────────────────────────────────────────────────┘   │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 2.2 记忆分层扩展

```
┌─────────────────────────────────────────────────────────────────────┐
│                        记忆分层架构（扩展）                           │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │                    L1: 工作记忆 (Working Memory)             │   │
│  │                                                               │   │
│  │  存储: 内存 (会话级)                                          │   │
│  │  内容:                                                        │   │
│  │  • 当前对话上下文 (最近5轮)                                   │   │
│  │  • 实时情感状态                                               │   │
│  │  • AI当前回合行为（新增）                                     │   │
│  │                                                               │   │
│  │  生命周期: 会话结束即清除                                      │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                              │                                       │
│                              ▼ 记忆巩固                              │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │                    L2: 情景记忆 (Episodic Memory)            │   │
│  │                                                               │   │
│  │  存储: Neo4j + pgvector                                       │   │
│  │  内容:                                                        │   │
│  │  • 用户情感片段（现有）                                       │   │
│  │  • AI行为片段（新增）：AI在什么场景下说了什么、做了什么       │   │
│  │  • 因果链片段（新增）：AI行为→用户反馈的完整事件              │   │
│  │                                                               │   │
│  │  生命周期: 短期→长期转化，带情感衰减                           │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                              │                                       │
│                              ▼ 提取抽象                              │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │                    L3: 语义记忆 (Semantic Memory)            │   │
│  │                                                               │   │
│  │  存储: Neo4j + pgvector                                       │   │
│  │  内容:                                                        │   │
│  │  • 用户画像事实（现有）                                       │   │
│  │  • AI自传事实（新增）：AI的长期行为模式、承诺、经验           │   │
│  │  • 经验模式（新增）：压缩后的因果链，如"运动减压对该用户有效" │   │
│  │                                                               │   │
│  │  生命周期: 长期持久化，支持修正和遗忘                          │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 三、核心模块设计

### 3.1 AI行为事实类型扩展

#### 新增FactType

```java
public enum FactType {
    // 现有类型（用户相关）
    IDENTITY("身份事实", "用户的身份信息，如名字、职业、年龄等"),
    PREFERENCE("偏好事实", "用户的喜好、兴趣、习惯等"),
    EMOTIONAL_EPISODE("情感片段", "用户经历的情感事件，包含情绪状态和触发因素"),
    RELATIONSHIP("关系事实", "用户与他人或事物的关系信息"),
    GOAL("目标事实", "用户的目标、计划、愿望等"),
    EVENT("事件事实", "用户经历的重要事件"),
    STATE("状态事实", "用户的当前状态，如正在做的事情"),
    TODO("待办事项", "用户需要完成的任务、提醒事项"),
    VOLATILE("临时事实", "情绪激动时的极端表述，需要后续确认"),
    
    // 新增类型（AI自传相关）
    AI_STATEMENT("AI陈述", "AI表达的观点、建议、分析"),
    AI_COMMITMENT("AI承诺", "AI做出的承诺、保证、跟进事项"),
    AI_COMFORT("AI安慰", "AI使用的情感安慰、共情回应"),
    AI_EXPERIENCE("AI经验", "AI从互动中总结的经验模式，如'运动减压对该用户有效'"),
    CAUSAL_LINK("因果链", "AI行为与用户反馈的关联记录");
}
```

#### 新增关系类型

```java
public enum RelationshipType {
    // 现有关系
    RELATED_TO("相关于"),
    SUPERSEDES("替代"),
    CONTRADICTS("矛盾"),
    DEPENDS_ON("依赖于"),
    
    // 新增关系（因果链）
    TRIGGERED("触发", "AI行为触发了用户反馈"),
    RESPONDED_TO("回应于", "AI行为是对用户事实的回应"),
    CAUSED_BY("由...引起", "AI行为是由用户事实引起的"),
    LED_TO("导致", "AI行为导致了某种结果"),
    VALIDATED_BY("被验证", "AI建议被用户反馈验证"),
    REJECTED_BY("被拒绝", "AI建议被用户拒绝"),
    FOLLOWED_UP("跟进", "AI对用户之前状态的跟进");
}
```

### 3.2 AI自传节点模型

#### 数据结构

```java
@Node("AI_Self")
public class AISelfNode {
    @Id
    @GeneratedValue
    private Long id;
    
    // AI标识（支持多AI实例）
    private String agentId;
    
    // 行为类型
    private AISelfBehaviorType behaviorType;
    // STATEMENT: 陈述/建议
    // COMMITMENT: 承诺/保证
    // COMFORT: 安慰/共情
    // EXPERIENCE: 经验模式
    
    // 内容
    private String content;           // "我建议用户通过运动减压"
    private String contextSummary;    // 上下文摘要："用户表示工作压力大"
    
    // 时间
    private LocalDateTime createdAt;  // 创建时间
    private LocalDateTime validFrom;  // 生效时间（双时间模型）
    private LocalDateTime validUntil; // 失效时间（null表示仍有效）
    
    // 状态
    private String outcomeStatus;     // fulfilled/pending/failed/unknown
    // fulfilled: 承诺已兑现/建议被采纳
    // pending: 等待用户反馈
    // failed: 建议被拒绝/承诺未兑现
    // unknown: 未知
    
    // 元数据
    private Double confidence;        // 置信度
    private Double importance;        // 重要性：0.0-1.0
    private LocalDateTime lastRecalledAt; // 最后回忆时间
    private double recallCount;       // 回忆次数
    private String status;            // active/archived/superseded
    
    // 关联
    @Relationship(type = "TRIGGERED", direction = Relationship.Direction.OUTGOING)
    private List<FactNode> triggeredFacts;  // 触发的用户事实
    
    @Relationship(type = "RESPONDED_TO", direction = Relationship.Direction.OUTGOING)
    private List<FactNode> respondedToFacts; // 回应的用户事实
    
    @Relationship(type = "LED_TO", direction = Relationship.Direction.OUTGOING)
    private List<AISelfNode> ledToNodes;     // 导致的后续AI行为
    
    // 生命周期
    public void incrementRecallCount() {
        this.recallCount++;
        this.lastRecalledAt = LocalDateTime.now();
    }
    
    public void markAsFulfilled() {
        this.outcomeStatus = "fulfilled";
    }
    
    public void markAsFailed() {
        this.outcomeStatus = "failed";
    }
}

public enum AISelfBehaviorType {
    STATEMENT("陈述", "AI表达的观点、建议、分析"),
    COMMITMENT("承诺", "AI做出的承诺、保证、跟进事项"),
    COMFORT("安慰", "AI使用的情感安慰、共情回应"),
    EXPERIENCE("经验", "AI从互动中总结的经验模式");
    
    private final String displayName;
    private final String description;
}
```

### 3.3 AI行为提取器

#### 提取器接口

```java
public interface AISelfFactExtractor {

    @SystemMessage("""
            你是灵枢的AI自传记忆系统核心组件：AI行为提取器。

            ═══════════════════════════════════════════════════════════════
            【当前时间上下文】
            当前日期时间: {{currentDateTime}}
            ═══════════════════════════════════════════════════════════════

            【任务】
            从对话中提取AI（助手）的行为事实，用于构建AI的自传记忆。

            【提取类型定义】
            
            1. AI_STATEMENT (AI陈述)
               - AI给出的建议、推荐、分析
               - AI表达的观点、看法
               - AI提供的信息、知识
               示例: "我建议你试试运动减压"、"我认为你可以先从小目标开始"
            
            2. AI_COMMITMENT (AI承诺)
               - AI做出的承诺、保证
               - AI表示会跟进的事项
               - AI答应做的事情
               示例: "我明天会提醒你"、"我会帮你记住这个"、"下次我们继续讨论"
            
            3. AI_COMFORT (AI安慰)
               - AI使用的情感安慰策略
               - AI的共情回应
               - AI的情感支持行为
               示例: "我理解你的感受"、"念一首诗给你听"、"你做得已经很好了"
            
            4. AI_EXPERIENCE (AI经验) - 仅在因果链压缩时生成
               - AI从互动中总结的经验模式
               - 对某用户有效的策略
               示例: "运动减压对该用户有效"、"该用户对冥想建议接受度低"

            【提取准则】
            
            1. 只提取AI的**明确行为**，不要推断或臆测
            2. 内容必须是**自解释**的，包含完整上下文
            3. 对于建议类内容，记录建议的具体内容
            4. 对于承诺类内容，记录承诺的具体事项和时间（如果有）
            5. 对于安慰类内容，记录安慰的策略类型
            
            【严格排除标准】
            1. 普通的对话衔接（如"好的"、"明白了"、"嗯嗯"）
            2. 问题或询问（如"你觉得呢？"、"要不要试试？"）
            3. 重复用户的话（如"你说你压力大"）
            4. 工具调用或系统行为

            【因果链识别】
            同时识别AI行为与用户反馈的关联：
            - AI的X行为 → 用户的Y反馈
            - 用户说A → AI回应B → 用户反馈C（接受/拒绝/无反馈）

            【返回格式】
            你必须且只能返回合法的 JSON 对象，严禁包含任何 Markdown 格式。
            {
              "aiBehaviors": [
                {
                  "content": "AI行为内容（自解释，包含上下文）",
                  "type": "AI_STATEMENT/AI_COMMITMENT/AI_COMFORT",
                  "confidence": 0.9,
                  "outcomeStatus": "unknown/pending/fulfilled/failed",
                  "contextSummary": "触发该行为的用户事实或场景",
                  "userFeedback": "用户对该行为的反馈（接受/拒绝/无反馈/具体回应）"
                }
              ],
              "causalLinks": [
                {
                  "aiBehaviorIndex": 0,
                  "userFactContent": "相关的用户事实内容",
                  "linkType": "TRIGGERED/RESPONDED_TO/CAUSED_BY",
                  "linkDescription": "关联描述"
                }
              ],
              "analysis": "分析简述"
            }

            【当前AI已知行为列表】
            {{currentAIFacts}}
            """)
    @UserMessage("""
            用户消息：{{message}}
            助手回复：{{assistantResponse}}
            请基于以上对话分析AI的行为并直接返回 JSON 结果
            """)
    AISelfExtractionResult extractAIBehaviors(
            @V("message") String message,
            @V("assistantResponse") String assistantResponse,
            @V("currentAIFacts") String currentAIFacts,
            @V("currentDateTime") String currentDateTime
    );
}

public class AISelfExtractionResult {
    private List<AISelfFactDto> aiBehaviors;
    private List<CausalLinkDto> causalLinks;
    private String analysis;
    
    // getters and setters
}

public class AISelfFactDto {
    private String content;
    private String type;          // AI_STATEMENT/AI_COMMITMENT/AI_COMFORT
    private Double confidence;
    private String outcomeStatus; // unknown/pending/fulfilled/failed
    private String contextSummary;
    private String userFeedback;
    
    // getters and setters
}

public class CausalLinkDto {
    private Integer aiBehaviorIndex;  // 对应aiBehaviors数组的索引
    private String userFactContent;   // 相关的用户事实内容
    private String linkType;          // TRIGGERED/RESPONDED_TO/CAUSED_BY
    private String linkDescription;   // 关联描述
    
    // getters and setters
}
```

### 3.4 因果链构建

#### 构建流程

```
┌─────────────────────────────────────────────────────────────────────┐
│                        因果链构建流程                                 │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  输入:                                                               │
│  - 用户消息: "我最近压力好大"                                        │
│  - AI回复: "我理解你的感受。建议你试试每天运动30分钟，               │
│             我之前有个朋友也这样，效果不错。"                        │
│                                                                      │
│  ─────────────────────────────────────────────────────────────────  │
│                                                                      │
│  Step 1: 用户事实提取（现有）                                        │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │ 提取: FactNode {                                            │   │
│  │   content: "用户最近压力大",                                 │   │
│  │   type: STATE,                                              │   │
│  │   id: 123                                                   │   │
│  │ }                                                           │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                      │
│  Step 2: AI行为提取（新增）                                          │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │ 提取: AISelfNode {                                          │   │
│  │   content: "AI建议用户每天运动30分钟减压",                   │   │
│  │   type: AI_STATEMENT,                                       │   │
│  │   contextSummary: "用户表示工作压力大",                      │   │
│  │   outcomeStatus: "pending",                                 │   │
│  │   id: 456                                                   │   │
│  │ }                                                           │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                      │
│  Step 3: 因果链构建（新增）                                          │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │ 创建关系:                                                   │   │
│  │ (AI_Self:456) --RESPONDED_TO--> (Fact:123)                  │   │
│  │   含义: AI的建议是对用户压力大的回应                         │   │
│  │                                                             │   │
│  │ (Fact:123) --CAUSED_BY--> (AI_Self:456)                     │   │
│  │   含义: 用户压力大引起了AI的建议行为                         │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                      │
│  ─────────────────────────────────────────────────────────────────  │
│                                                                      │
│  后续对话:                                                           │
│  用户: "上次你建议的运动真有用，我感觉好多了！"                      │
│                                                                      │
│  Step 4: 因果链更新                                                  │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │ 更新:                                                       │   │
│  │ 1. 提取用户事实: "用户觉得运动减压有效" (Fact:789)           │   │
│  │ 2. 检索到AI行为: AISelfNode:456 (运动建议)                   │   │
│  │ 3. 创建关系:                                                │   │
│  │    (AI_Self:456) --TRIGGERED--> (Fact:789)                  │   │
│  │    含义: AI的建议触发了用户的正面反馈                         │   │
│  │ 4. 更新AI行为状态:                                          │   │
│  │    AISelfNode:456.outcomeStatus = "fulfilled"               │   │
│  │ 5. 生成经验模式:                                            │   │
│  │    AI_EXPERIENCE: "运动减压对该用户有效"                     │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

### 3.5 记忆自治理机制

#### 自治理任务设计

参考前沿研究：
- **A-Mem (NIPS 2025)**: 记忆自动链接、自动进化
- **PREMem (2025 EMNLP)**: 预存储推理，存储时就建立关系
- **Graphiti/Zep**: 双时间模型，事实失效不删除

```java
@Service
public class AISelfMemoryMaintenance {
    
    /**
     * AI自传记忆维护任务
     * 每小时执行一次（与现有记忆维护错开30分钟）
     */
    @Scheduled(cron = "0 30 * * * *")
    public void maintainAISelfMemory() {
        log.info("开始AI自传记忆维护...");
        
        // 1. 因果链完整性检查
        checkCausalChainIntegrity();
        
        // 2. 因果链压缩与经验提取
        compressCausalChains();
        
        // 3. 情感演化总结
        summarizeEmotionEvolution();
        
        // 4. 过期承诺清理
        cleanupExpiredCommitments();
        
        // 5. 记忆自动链接
        autoLinkMemories();
        
        log.info("AI自传记忆维护完成");
    }
    
    /**
     * 1. 因果链完整性检查
     * 
     * 检查AI行为是否有用户反馈：
     * - 建议类：用户是否采纳？
     * - 承诺类：是否已兑现？
     * - 安慰类：用户情绪是否改善？
     * 
     * 长时间无反馈的AI行为标记为低优先级
     */
    private void checkCausalChainIntegrity() {
        // 查询超过7天仍为pending的AI行为
        List<AISelfNode> pendingNodes = aiSelfRepository
            .findByOutcomeStatusAndCreatedAtBefore("pending", 
                LocalDateTime.now().minusDays(7));
        
        for (AISelfNode node : pendingNodes) {
            // 检查是否有相关的用户反馈
            boolean hasFeedback = checkUserFeedback(node.getId());
            
            if (!hasFeedback) {
                // 降低重要性，标记为可能无效
                node.setImportance(node.getImportance() * 0.5);
                node.setOutcomeStatus("unknown");
                aiSelfRepository.save(node);
                
                systemLogService.info(String.format(
                    "AI行为超时未反馈: %s (ID: %d)", 
                    node.getContent(), node.getId()), "AI_MEMORY");
            }
        }
    }
    
    /**
     * 2. 因果链压缩与经验提取
     * 
     * 将多次相似的因果链压缩为经验模式：
     * - "建议运动" x3次 + "用户接受" x3次 → "运动减压对该用户有效"
     * - "建议冥想" x2次 + "用户拒绝" x2次 → "该用户对冥想建议接受度低"
     */
    private void compressCausalChains() {
        // 按内容相似度分组AI行为
        List<AISelfNode> allStatements = aiSelfRepository
            .findByBehaviorTypeAndStatus(AISelfBehaviorType.STATEMENT, "active");
        
        // 使用向量相似度聚类
        Map<String, List<AISelfNode>> clusters = clusterBySimilarity(allStatements);
        
        for (Map.Entry<String, List<AISelfNode>> entry : clusters.entrySet()) {
            List<AISelfNode> cluster = entry.getValue();
            
            // 只处理有3次以上重复的模式
            if (cluster.size() < 3) continue;
            
            // 统计反馈结果
            long fulfilledCount = cluster.stream()
                .filter(n -> "fulfilled".equals(n.getOutcomeStatus()))
                .count();
            long failedCount = cluster.stream()
                .filter(n -> "failed".equals(n.getOutcomeStatus()))
                .count();
            
            // 生成经验模式
            String experienceContent = generateExperiencePattern(
                cluster.get(0).getContent(),
                fulfilledCount,
                failedCount,
                cluster.size()
            );
            
            // 保存经验节点
            AISelfNode experienceNode = new AISelfNode();
            experienceNode.setBehaviorType(AISelfBehaviorType.EXPERIENCE);
            experienceNode.setContent(experienceContent);
            experienceNode.setImportance(0.9);
            experienceNode.setConfidence(
                (double) Math.max(fulfilledCount, failedCount) / cluster.size()
            );
            
            aiSelfRepository.save(experienceNode);
            
            // 链接到原始因果链
            for (AISelfNode original : cluster) {
                relationshipRepository.save(
                    new Relationship(original, "GENERATED", experienceNode)
                );
            }
            
            systemLogService.info(String.format(
                "生成经验模式: %s (基于%d次互动)", 
                experienceContent, cluster.size()), "AI_MEMORY");
        }
    }
    
    /**
     * 3. 情感演化总结
     * 
     * 分析AI的情感回应效果：
     * - 哪些安慰方式对该用户有效？
     * - 用户情绪改善的触发因素是什么？
     */
    private void summarizeEmotionEvolution() {
        // 查询AI安慰行为
        List<AISelfNode> comfortNodes = aiSelfRepository
            .findByBehaviorTypeAndStatus(AISelfBehaviorType.COMFORT, "active");
        
        for (AISelfNode comfort : comfortNodes) {
            // 检查安慰后的用户情绪变化
            List<EmotionalEpisode> episodesAfter = episodeRepository
                .findByUserIdAndOccurredAfter(
                    comfort.getAgentId(),
                    comfort.getCreatedAt()
                );
            
            // 分析情绪趋势
            boolean emotionImproved = analyzeEmotionTrend(episodesAfter);
            
            if (emotionImproved) {
                // 标记该安慰策略有效
                comfort.setImportance(
                    Math.min(1.0, comfort.getImportance() + 0.1)
                );
                aiSelfRepository.save(comfort);
            }
        }
    }
    
    /**
     * 4. 过期承诺清理
     * 
     * 检查AI的承诺是否已兑现：
     * - 已兑现：标记为fulfilled
     * - 已过期：标记为failed
     * - 仍在有效期内：保持pending
     */
    private void cleanupExpiredCommitments() {
        List<AISelfNode> commitments = aiSelfRepository
            .findByBehaviorTypeAndStatus(
                AISelfBehaviorType.COMMITMENT, "active"
            );
        
        for (AISelfNode commitment : commitments) {
            // 检查承诺是否已兑现
            boolean fulfilled = checkCommitmentFulfilled(commitment);
            
            if (fulfilled) {
                commitment.markAsFulfilled();
                aiSelfRepository.save(commitment);
                
                systemLogService.info(String.format(
                    "AI承诺已兑现: %s (ID: %d)", 
                    commitment.getContent(), commitment.getId()), "AI_MEMORY");
            } else if (isCommitmentExpired(commitment)) {
                commitment.markAsFailed();
                aiSelfRepository.save(commitment);
                
                systemLogService.info(String.format(
                    "AI承诺已过期: %s (ID: %d)", 
                    commitment.getContent(), commitment.getId()), "AI_MEMORY");
            }
        }
    }
    
    /**
     * 5. 记忆自动链接
     * 
     * 参考A-Mem：新记忆加入时，自动与已有记忆建立关联
     * LLM分析语义关联，不仅依赖向量相似度
     */
    private void autoLinkMemories() {
        // 查询最近24小时新增的AI行为
        List<AISelfNode> recentNodes = aiSelfRepository
            .findByCreatedAtAfter(LocalDateTime.now().minusHours(24));
        
        for (AISelfNode newNode : recentNodes) {
            // 计算与已有记忆的相似度
            List<AISelfNode> similarNodes = aiSelfRepository
                .findSimilarByEmbedding(newNode.getEmbedding(), 0.7);
            
            // LLM分析是否需要建立链接
            for (AISelfNode similar : similarNodes) {
                boolean shouldLink = llmAnalyzeLink(
                    newNode.getContent(),
                    similar.getContent()
                );
                
                if (shouldLink) {
                    relationshipRepository.save(
                        new Relationship(newNode, "RELATED_TO", similar)
                    );
                    
                    systemLogService.debug(String.format(
                        "自动链接记忆: %d -> %d", 
                        newNode.getId(), similar.getId()), "AI_MEMORY");
                }
            }
        }
    }
}
```

### 3.6 对话检索扩展

#### 检索流程扩展

```java
@Service
public class EnhancedMemoryRetrieval {
    
    /**
     * 扩展的记忆检索：同时检索用户记忆和AI自传
     */
    public String retrieveContext(String userId, String userMessage) {
        StringBuilder context = new StringBuilder();
        
        // 1. 检索用户事实（现有逻辑）
        String userFacts = retrieveUserFacts(userId, userMessage);
        context.append(userFacts);
        
        // 2. 检索AI自传（新增）
        String aiSelfContext = retrieveAISelfContext(userId, userMessage);
        context.append(aiSelfContext);
        
        // 3. 检索因果链（新增）
        String causalChainContext = retrieveCausalChains(userId, userMessage);
        context.append(causalChainContext);
        
        // 4. 检索经验模式（新增）
        String experienceContext = retrieveExperiences(userId, userMessage);
        context.append(experienceContext);
        
        return context.toString();
    }
    
    /**
     * 检索AI自传上下文
     */
    private String retrieveAISelfContext(String userId, String userMessage) {
        // 语义检索AI行为
        List<AISelfNode> aiBehaviors = aiSelfRepository
            .searchBySemanticSimilarity(userMessage, 0.6);
        
        // 过滤：只检索与该用户相关的AI行为
        aiBehaviors = aiBehaviors.stream()
            .filter(node -> isRelatedToUser(node, userId))
            .limit(5)
            .collect(Collectors.toList());
        
        if (aiBehaviors.isEmpty()) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("\n【AI对你的历史行为】\n");
        
        for (AISelfNode node : aiBehaviors) {
            switch (node.getBehaviorType()) {
                case STATEMENT:
                    sb.append("- 我曾建议: ").append(node.getContent());
                    if ("fulfilled".equals(node.getOutcomeStatus())) {
                        sb.append(" (你采纳了)");
                    } else if ("failed".equals(node.getOutcomeStatus())) {
                        sb.append(" (你没采纳)");
                    }
                    sb.append("\n");
                    break;
                case COMMITMENT:
                    sb.append("- 我曾承诺: ").append(node.getContent());
                    if ("fulfilled".equals(node.getOutcomeStatus())) {
                        sb.append(" (已兑现)");
                    } else if ("pending".equals(node.getOutcomeStatus())) {
                        sb.append(" (待兑现)");
                    }
                    sb.append("\n");
                    break;
                case COMFORT:
                    sb.append("- 我曾安慰你: ").append(node.getContent());
                    sb.append("\n");
                    break;
            }
        }
        
        return sb.toString();
    }
    
    /**
     * 检索因果链上下文
     */
    private String retrieveCausalChains(String userId, String userMessage) {
        // 检索与当前消息相关的因果链
        List<CausalChain> chains = causalChainRepository
            .searchByUserMessage(userId, userMessage);
        
        if (chains.isEmpty()) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("\n【相关因果链】\n");
        
        for (CausalChain chain : chains) {
            sb.append(String.format(
                "- 我曾%s → 你反馈%s\n",
                chain.getAiBehaviorContent(),
                chain.getUserFeedbackContent()
            ));
        }
        
        return sb.toString();
    }
    
    /**
     * 检索经验模式
     */
    private String retrieveExperiences(String userId, String userMessage) {
        // 检索与该用户相关的经验模式
        List<AISelfNode> experiences = aiSelfRepository
            .findByBehaviorTypeAndRelatedUser(
                AISelfBehaviorType.EXPERIENCE, userId
            );
        
        if (experiences.isEmpty()) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("\n【对你的了解】\n");
        
        for (AISelfNode exp : experiences) {
            sb.append("- ").append(exp.getContent()).append("\n");
        }
        
        return sb.toString();
    }
}
```

---

## 四、Neo4j图谱设计

### 4.1 节点类型

```
┌─────────────────────────────────────────────────────────────────────┐
│                        Neo4j 节点类型                                │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  用户记忆图（现有）:                                                 │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │ • User: 用户节点                                             │   │
│  │ • Fact: 事实节点（身份、偏好、状态等）                       │   │
│  │ • EmotionalEpisode: 情感片段节点                             │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                      │
│  AI自传图（新增）:                                                   │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │ • AI_Self: AI行为节点                                       │   │
│  │   属性: agentId, behaviorType, content, outcomeStatus, ...   │   │
│  │                                                              │   │
│  │ • ExperiencePattern: 经验模式节点                            │   │
│  │   属性: pattern, effectiveness, sampleSize, confidence       │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

### 4.2 关系类型

```
┌─────────────────────────────────────────────────────────────────────┐
│                        Neo4j 关系类型                                │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  用户记忆图内部（现有）:                                             │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │ • User --HAS_FACT--> Fact                                   │   │
│  │ • Fact --RELATED_TO--> Fact                                 │   │
│  │ • Fact --SUPERSEDES--> Fact                                 │   │
│  │ • Fact --CONTRADICTS--> Fact                                │   │
│  │ • Fact --DEPENDS_ON--> Fact                                 │   │
│  │ • User --EXPERIENCED--> EmotionalEpisode                    │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                      │
│  AI自传图内部（新增）:                                               │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │ • AI_Self --RELATED_TO--> AI_Self                           │   │
│  │ • AI_Self --LED_TO--> AI_Self                               │   │
│  │ • AI_Self --GENERATED--> ExperiencePattern                  │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                      │
│  跨图关联（新增）:                                                   │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │ • AI_Self --RESPONDED_TO--> Fact                            │   │
│  │   含义: AI行为是对用户事实的回应                              │   │
│  │                                                              │   │
│  │ • AI_Self --TRIGGERED--> Fact                               │   │
│  │   含义: AI行为触发了用户事实/反馈                             │   │
│  │                                                              │   │
│  │ • Fact --CAUSED_BY--> AI_Self                               │   │
│  │   含义: 用户事实是由AI行为引起的                              │   │
│  │                                                              │   │
│  │ • AI_Self --VALIDATED_BY--> Fact                            │   │
│  │   含义: AI建议被用户事实验证（有效）                          │   │
│  │                                                              │   │
│  │ • AI_Self --REJECTED_BY--> Fact                             │   │
│  │   含义: AI建议被用户事实拒绝（无效）                          │   │
│  │                                                              │   │
│  │ • AI_Self --FOLLOWED_UP--> Fact                             │   │
│  │   含义: AI跟进用户之前的状态                                  │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

### 4.3 图谱示例

```
示例场景：
用户: "我最近压力好大"
AI: "我理解你的感受。建议你试试每天运动30分钟，效果不错。"

--- 3天后 ---

用户: "上次你建议的运动真有用，我感觉好多了！"

图谱结构：

(User:张三)
    │
    ├──HAS_FACT──> (Fact:123) "用户最近压力大" [STATE, 2026-04-18]
    │
    └──HAS_FACT──> (Fact:789) "用户觉得运动减压有效" [STATE, 2026-04-21]

(AI_Self:灵枢)
    │
    └──MADE_STATEMENT──> (AI_Self:456) "建议用户每天运动30分钟减压"
                              │
                              ├──RESPONDED_TO──> (Fact:123)
                              │     含义: 建议是对用户压力的回应
                              │
                              └──TRIGGERED──> (Fact:789)
                                    含义: 建议触发了用户的正面反馈
                                    属性: outcomeStatus = "fulfilled"

(AI_Self:灵枢)
    │
    └──HAS_EXPERIENCE──> (ExperiencePattern:999) "运动减压对该用户有效"
                              │
                              属性: effectiveness = 1.0
                              属性: sampleSize = 1
                              属性: confidence = 1.0
```

### 4.4 双时间模型

参考 Graphiti/Zep 的设计，每条关系边都有时间属性：

```java
@RelationshipProperties
public class TemporalRelationship {
    @Id
    @GeneratedValue
    private Long id;
    
    // 事件时间：事实在现实世界中何时生效
    private LocalDateTime validFrom;   // 生效时间
    private LocalDateTime validUntil;  // 失效时间（null表示仍有效）
    
    // 系统时间：何时被记录到系统中
    private LocalDateTime createdAt;   // 创建时间
    private LocalDateTime expiredAt;   // 过期时间（逻辑删除）
    
    // 关系属性
    private String fact;               // 关系描述
    private Double confidence;         // 置信度
}
```

**查询示例**：

```cypher
// 查询某个时间点AI对用户的有效建议
MATCH (ai:AI_Self)-[r:TRIGGERED]->(fact:Fact)
WHERE r.validFrom <= datetime('2026-04-20')
  AND (r.validUntil IS NULL OR r.validUntil > datetime('2026-04-20'))
RETURN ai, r, fact

// 查询AI承诺的兑现情况
MATCH (ai:AI_Self {behaviorType: 'COMMITMENT'})
WHERE ai.outcomeStatus = 'pending'
  AND ai.createdAt < datetime() - duration('P7D')
RETURN ai
```

---

## 五、实施计划

### 5.1 实施阶段

| 阶段 | 内容 | 工作量 | 状态 |
|------|------|--------|------|
| **Phase 1** | 新增FactType和关系类型枚举 | 0.5天 | 待开始 |
| **Phase 2** | 创建AISelfNode实体和Repository | 1天 | 待开始 |
| **Phase 3** | 实现AISelfFactExtractor提取器 | 1.5天 | 待开始 |
| **Phase 4** | 扩展TurnPostProcessingService | 1天 | 待开始 |
| **Phase 5** | 实现因果链构建逻辑 | 1.5天 | 待开始 |
| **Phase 6** | 扩展记忆检索逻辑 | 1.5天 | 待开始 |
| **Phase 7** | 实现AI自传记忆维护任务 | 1.5天 | 待开始 |
| **Phase 8** | 前端展示（可选） | 2天 | 待开始 |

### 5.2 文件清单

#### 新增文件

```
backend/lingshu-infrastructure/src/main/java/com/lingshu/ai/infrastructure/entity/
├── AISelfNode.java                    # AI自传节点实体
└── TemporalRelationship.java          # 双时间关系实体

backend/lingshu-infrastructure/src/main/java/com/lingshu/ai/infrastructure/repository/
├── AISelfRepository.java              # AI自传节点Repository
└── TemporalRelationshipRepository.java # 关系Repository

backend/lingshu-core/src/main/java/com/lingshu/ai/core/dto/
├── AISelfFactDto.java                 # AI行为事实DTO
├── CausalLinkDto.java                 # 因果链DTO
└── AISelfExtractionResult.java        # AI行为提取结果

backend/lingshu-core/src/main/java/com/lingshu/ai/core/service/
├── AISelfFactExtractor.java           # AI行为提取器接口
├── AISelfMemoryMaintenance.java       # AI自传维护服务
└── CausalChainBuilder.java            # 因果链构建服务

backend/lingshu-core/src/main/java/com/lingshu/ai/core/service/impl/
└── CausalChainBuilderImpl.java        # 因果链构建实现
```

#### 修改文件

```
backend/lingshu-core/src/main/java/com/lingshu/ai/core/dto/
├── FactType.java                      # 新增AI相关枚举值

backend/lingshu-core/src/main/java/com/lingshu/ai/core/service/
├── TurnPostProcessingServiceImpl.java # 新增AI行为提取决策
└── MemoryServiceImpl.java             # 扩展事实处理逻辑

backend/lingshu-core/src/main/java/com/lingshu/ai/core/service/impl/
└── ChatServiceImpl.java               # 扩展记忆检索逻辑
```

### 5.3 风险与缓解

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| Neo4j图谱膨胀 | 查询性能下降 | 记忆生命周期管理、归档机制 |
| 提取准确率不足 | 因果链错误 | LLM Prompt优化、置信度阈值 |
| 因果链误判 | AI产生错误记忆 | 用户反馈验证、低置信度过滤 |
| 存储成本增加 | 数据库压力 | 向量索引优化、定期清理 |

---

## 六、学术参考

### 6.1 论文参考

| 论文 | 年份 | 核心贡献 | 本项目应用 |
|------|------|----------|------------|
| **MAGMA** (UT Dallas) | 2026-01 | 正交多图架构（语义/时间/因果/实体分离） | 因果链独立于用户事实图 |
| **Graphiti/Zep** | 2025-01 | 双时间模型、时序知识图谱 | AI行为事实的validFrom/validUntil |
| **A-Mem** (NIPS 2025) | 2025 | 能动记忆系统、记忆自动链接与进化 | 记忆自治理机制 |
| **PREMem** (EMNLP 2025) | 2025 | 预存储推理，存储时建立关系 | 因果链在存储时构建 |
| **THEANINE** (首尔大学) | 2024 | 对话发话节点+因果边 | 因果链关系类型设计 |
| **REMem** (2025-09) | 2025 | 情景记忆推理、混合记忆图 | 情景记忆与语义记忆结合 |

### 6.2 设计原则

1. **因果分离**：因果链不混入用户事实图，使用独立的关系类型
2. **双时间追踪**：事实生效时间 vs 系统记录时间，支持历史回溯
3. **预存储推理**：存储时就建立好关系，检索时直接使用（PREMem）
4. **记忆进化**：新知识触发旧记忆更新，模拟人类学习（A-Mem）
5. **图谱自治理**：定期压缩、链接、归档，防止图谱膨胀

---

## 七、验收标准

### 7.1 功能验收

| 测试场景 | 预期结果 | 状态 |
|----------|----------|------|
| AI给出建议后，用户采纳 | AI记住建议，标记为fulfilled | 待测试 |
| AI做出承诺后，用户追问 | AI能回忆起承诺内容 | 待测试 |
| 用户再次提到相同问题 | AI检索到之前的建议和效果 | 待测试 |
| 多次相似建议后 | 自动生成经验模式 | 待测试 |
| 7天无反馈的AI行为 | 自动降级为unknown | 待测试 |

### 7.2 性能验收

| 指标 | 目标值 | 状态 |
|------|--------|------|
| AI行为提取延迟 | < 2秒 | 待测试 |
| 记忆检索延迟 | < 500ms | 待测试 |
| Neo4j查询延迟 | < 200ms | 待测试 |
| 自治理任务执行时间 | < 30秒/小时 | 待测试 |

---

## 八、记忆时间感知设计

### 8.1 问题现状（已修复）

当前记忆系统曾存在**时间感知缺陷**：AI 无法区分用户是什么时候说了某句话。该问题已在 `MemoryServiceImpl` 中修复。

#### 缺陷根因分析

| 环节 | 状态 | 问题 |
|------|------|------|
| 事实提取 | ✅ 有设计 | LLM 会转换相对时间为绝对时间，存入 `eventTime` |
| 事实存储 | ✅ 有字段 | `FactNode` 有 `observedAt`（记录时间）和 `eventTime`（事件时间） |
| 记忆检索 | ✅ 已实现 | 图谱事实检索结果注入 `eventTime/observedAt` 时间标签 |
| 上下文注入 | ✅ 已实现 | AI 可感知记忆时间属性（如“3天前/2周后”） |

**关键代码位置**：
- `backend/lingshu-core/src/main/java/com/lingshu/ai/core/service/impl/MemoryServiceImpl.java`
- `mergeAndDeduplicate(...)`：图谱事实输出时调用 `formatFactWithTime(...)`
- `formatFactWithTime(...)`：注入 `[事件时间: ...]` 与 `[记录于: ...]`
- `formatRelativeTime(...)`：支持“今天/昨天/N天前/N天后”等相对时间显示

```java
// 修复后：图谱事实在合并阶段会注入时间标签
if (!normalizedSet.contains(normalized)) {
    finalFacts.add(formatFactWithTime(content, fact));
    normalizedSet.add(normalized);
}
```

**AI 看到的上下文（修复前）**：
```
关于核心上下文的已知事实与记忆：
- 用户喜欢喝咖啡
- 用户工作压力大
- 用户计划去上海出差
```

**AI 的困惑**：
- 不知道"喜欢喝咖啡"是上周说的还是半年前说的
- 不知道"计划去上海出差"是已经发生了还是未来的计划
- 无法判断记忆的时效性，可能给出过时的回应

### 8.2 设计方案

#### 方案一：检索结果注入时间标签（推荐）

修改 `retrieveContext` 方法，在返回的事实内容中注入时间信息：

```java
private String formatFactWithTime(FactNode fact) {
    StringBuilder sb = new StringBuilder();
    sb.append(fact.getContent());
    
    // 注入事件时间（如果存在）
    if (fact.getEventTime() != null) {
        String timeLabel = formatRelativeTime(fact.getEventTime());
        sb.append(" [事件时间: ").append(timeLabel).append("]");
    }
    
    // 注入记录时间
    if (fact.getObservedAt() != null) {
        String observedLabel = formatRelativeTime(fact.getObservedAt());
        sb.append(" [记录于: ").append(observedLabel).append("]");
    }
    
    return sb.toString();
}

private String formatRelativeTime(LocalDateTime dateTime) {
    LocalDateTime now = LocalDateTime.now();
    Duration duration = Duration.between(dateTime, now);
    long days = duration.toDays();
    
    if (days == 0) return "今天";
    if (days == 1) return "昨天";
    if (days < 7) return days + "天前";
    if (days < 30) return (days / 7) + "周前";
    if (days < 365) return (days / 30) + "个月前";
    return (days / 365) + "年前";
}
```

**AI 看到的上下文（优化后）**：
```
关于核心上下文的已知事实与记忆：
- 用户喜欢喝咖啡 [记录于: 3个月前]
- 用户工作压力大 [记录于: 2天前]
- 用户计划去上海出差 [事件时间: 2026-04-25] [记录于: 5天前]
```

#### 方案二：时间过滤检索

在检索时根据用户当前查询的时间意图过滤记忆：

```java
// 检测用户查询中的时间意图
private TimeIntent detectTimeIntent(String message) {
    if (message.contains("最近") || message.contains("近期")) {
        return TimeIntent.RECENT; // 7天内
    }
    if (message.contains("之前") || message.contains("以前")) {
        return TimeIntent.HISTORICAL; // 7天前
    }
    if (message.contains("计划") || message.contains("准备")) {
        return TimeIntent.FUTURE; // 未来事件
    }
    return TimeIntent.ALL;
}

enum TimeIntent {
    RECENT, HISTORICAL, FUTURE, ALL
}
```

#### 方案三：时间权重衰减

根据记忆的时间属性动态调整检索权重：

```java
private double calculateTimeWeight(FactNode fact, String userMessage) {
    LocalDateTime now = LocalDateTime.now();
    long daysSinceObserved = Duration.between(fact.getObservedAt(), now).toDays();
    
    // 基础时间衰减（30天半衰期）
    double baseWeight = Math.exp(-daysSinceObserved / 30.0);
    
    // 如果事实有明确的 eventTime，优先使用
    if (fact.getEventTime() != null) {
        long daysSinceEvent = Duration.between(fact.getEventTime(), now).toDays();
        baseWeight = Math.max(baseWeight, Math.exp(-daysSinceEvent / 30.0));
    }
    
    return baseWeight;
}
```

### 8.3 实施路径

| 阶段 | 内容 | 工作量 | 状态 |
|------|------|--------|------|
| **Phase 1** | 修改 `retrieveContext` 注入时间标签 | 0.5天 | ✅ 已完成 |
| **Phase 2** | 实现时间意图检测（方案二） | 1天 | 待开始 |
| **Phase 3** | 实现时间权重衰减（方案三） | 1天 | 待开始 |
| **Phase 4** | 前端展示记忆时间轴 | 1天 | 待开始 |

### 8.4 验收标准

| 测试场景 | 预期结果 | 状态 |
|----------|----------|------|
| 用户说"我上次说的那件事" | AI 能检索到最近7天的记忆 | 待测试 |
| 用户问"我之前说过什么" | AI 能按时间顺序回忆 | 待测试 |
| 用户提到未来的计划 | AI 能区分已发生和未发生的事件 | 待测试 |
| 记忆检索上下文 | 包含时间标签，如"[记录于: 3天前]" | 待测试 |

### 8.5 修复记录

- 修复日期：2026-04-21
- 修复范围：`MemoryServiceImpl` 检索结果组装链路
- 修复内容：图谱记忆输出新增时间标签注入（`eventTime`、`observedAt`）
- 时间格式能力：支持过去与未来相对时间（今天、昨天、N天前、明天、N天后）
- 兼容性：仅增强上下文展示，不改变事实抽取与存储模型

---

## 九、后续优化方向

1. **多模态记忆**：记录AI生成的图片、语音等内容的记忆
2. **用户反馈学习**：基于用户显式反馈（点赞/点踩）优化记忆权重
3. **记忆可解释性**：前端展示AI为什么记住某个事实、因果链如何形成
4. **记忆编辑**：允许用户手动修正AI的错误记忆
5. **跨会话记忆迁移**：用户切换设备时的记忆同步
6. **时间图谱可视化**：前端展示记忆的时间轴和演化轨迹
