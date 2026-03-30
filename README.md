# 灵枢 (LingShu-AI)

<div align="center">

**一个具备长期记忆、情感演化与现实干预能力的本地化陪伴/协作智能体**

[![Java](https://img.shields.io/badge/Java-21-orange.svg?style=flat-square)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.4-brightgreen.svg?style=flat-square)](https://spring.io/projects/spring-boot)
[![Vue](https://img.shields.io/badge/Vue-3.5-4FC08D.svg?style=flat-square)](https://vuejs.org/)
[![MCP](https://img.shields.io/badge/Protocol-MCP-blue.svg?style=flat-square)](https://modelcontextprotocol.io/)
[![License](https://img.shields.io/badge/License-GPLv3-blue.svg?style=flat-square)](LICENSE)

[**✨ 愿景**](#项目简介) | [**🚀 快速开始**](#快速开始) | [**📖 文档库**](#文档导航) | [**🗺️ 路线图**](#项目路线图)

</div>

---

## 项目简介

**灵**：象征智能、情感感知与主动交互，承载长期记忆，懂你所思、记你所好，时刻陪伴。

**枢**：意为智能体调度中枢，代表开放、兼容、可无限扩展的统御能力。它支持用户自由配置 MCP 工具扩展，兼容 OpenAI 标准 TTS、ASR 等协议，实现本地任务执行、外部工具接入与多智能体协同，是掌控数字世界的核心与入口。

### 核心特性

- **🧠 长期记忆系统 (LTM)**：通过 **Neo4j + pgvector** 构建的多级记忆架构，支持事实提取、层级压缩与语义检索。
- **🌌 银河记忆图谱**：基于 3D 渲染的交互式知识图谱，让 AI 的记忆以“银河系”形式可视化呈现。
- **⚙️ MCP 协议驱动**：支持 **Model Context Protocol**，具备极强的开放性。可一键挂载 MySQL, Postgres, Web Search 等各种符合 MCP 协议的外部工具，赋予 AI 跨平台的现实干预能力。
- **🎭 情感演化引擎**：内置情感分析模型，AI 的回复会根据上下文情感状态产生动态波动。
- **⚡ 流式交互体验**：基于 SSE 的极速流式响应，配合多级缓存机制。
- **🏠 完全本地化**：支持 Ollama 本地部署，不经云端，彻底保护个人数据隐私。
- **🤖 多智能体协作**：支持配置不同性格与专业领域的 AI 智能体共同服务。

---

## 界面预览 (Screenshots)

<div align="center">
  <p><i></i></p>
  <img src="doc/png/lingshu_ai_ui_mockup_1773996576105.png" width="800" alt="UI Mockup">
</div>

---

## 系统架构

```
┌─────────────────────────────────────────────────────────────────┐
│                        Frontend (Vue 3)                          │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐           │
│  │ 对话界面  │ │ 记忆中枢  │ │ 系统设置  │ │ 日志监控  │           │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘           │
└────────────────────────────┬────────────────────────────────────┘
                             │ HTTP / WebSocket / SSE
┌────────────────────────────▼────────────────────────────────────┐
│                     Backend (Spring Boot 3)                      │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │                    lingshu-web (接口层)                      ││
│  │  ChatController │ MemoryController │ SettingController      ││
│  └─────────────────────────────────────────────────────────────┘│
│  ┌─────────────────────────────────────────────────────────────┐│
│  │                    lingshu-core (核心层)                     ││
│  │  ChatService │ MemoryService │ ProactiveService │ McpService││
│  └─────────────────────────────────────────────────────────────┘│
│  ┌─────────────────────────────────────────────────────────────┐│
│  │                lingshu-infrastructure (基础设施层)           ││
│  │  Entity │ Repository │ Memory Store                        ││
│  └─────────────────────────────────────────────────────────────┘│
└────────────────────────────┬────────────────────────────────────┘
                             │
┌────────────────────────────▼────────────────────────────────────┐
│                        数据存储层                                 │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐           │
│  │PostgreSQL│ │  Neo4j   │ │  Redis   │ │  Ollama  │           │
│  │ (向量)   │ │ (图数据库)│ │ (缓存)   │ │ (LLM)   │           │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘           │
└─────────────────────────────────────────────────────────────────┘
```

---

## 技术栈

| 层级 | 技术选型 | 说明 |
|------|----------|------|
| **前端** | Vue 3 + Vite + Naive UI + Tailwind CSS | 响应式 UI，支持深色模式 |
| **后端** | Java 21 + Spring Boot 3.2.4 | 提供 REST API 与 SSE 流式响应 |
| **AI框架** | LangChain4j 1.12.1 | AI 服务编排，工具调用 |
| **图数据库** | Neo4j 5.26 | 存储事实节点与关系图谱 |
| **关系数据库** | PostgreSQL 16 + pgvector | 聊天记录与向量语义检索 |
| **缓存** | Redis 7 | 会话管理与消息发布订阅 |
| **LLM推理** | Ollama / OpenAI 兼容 API | 本地或云端大模型推理 |
| **TTS服务** | OpenAI Edge TTS | 免费语音合成服务 |

---

## 项目结构

```
LingShu-AI/
├── backend/                          # 后端模块
│   ├── lingshu-web/                  # Web接口层
│   │   └── src/main/java/.../web/
│   │       ├── controller/           # REST控制器
│   │       └── LingshuAiApplication.java
│   ├── lingshu-core/                 # 核心业务层
│   │   └── src/main/java/.../core/
│   │       ├── service/              # 业务服务接口
│   │       ├── service/impl/         # 业务服务实现
│   │       ├── dto/                  # 数据传输对象
│   │       └── tool/                 # 工具类
│   ├── lingshu-infrastructure/       # 基础设施层
│   │   └── src/main/java/.../infrastructure/
│   │       ├── entity/               # 数据实体
│   │       ├── repository/           # 数据访问层
│   │       └── memory/               # 记忆存储
│   └── pom.xml                       # 父POM
├── frontend/                         # Vue 3 前端
│   ├── src/
│   │   ├── components/               # Vue组件
│   │   ├── views/                    # 页面视图
│   │   ├── composables/              # 组合式函数
│   │   ├── stores/                   # Pinia状态管理
│   │   └── types/                    # TypeScript类型
│   └── package.json
├── fx-frontend/                      # JavaFX桌面客户端(可选)
├── doc/                              # 项目文档
├── docker-compose.yml                # Docker编排配置
├── Dockerfile                        # 应用镜像构建
└── README.md
```

---

## 文档导航

项目包含详细的设计文档，帮助您深入了解系统架构与实现细节：

### 核心设计文档

| 文档 | 说明 |
|------|------|
| [系统架构设计文档](doc/系统架构设计文档.md) | 整体架构设计、模块划分、核心功能实现及技术选型 |
| [项目计划书](doc/项目计划书.md) | 项目愿景、阶段性演化路径、技术栈选型 |
| [对话调用链路详解](doc/对话调用链路详解.md) | 用户发消息到AI流式回复再到事实提取的完整链路 |

### 记忆系统文档

| 文档 | 说明 |
|------|------|
| [情感感知记忆系统优化设计文档](doc/情感感知记忆系统优化设计文档.md) | 情感前置分析、情感感知的事实提取、记忆生命周期管理 |
| [记忆图谱3D银河系改造方案](doc/记忆图谱3D银河系改造方案.md) | 记忆图谱可视化升级方案，打造"银河系记忆宇宙" |

### 智能体与扩展

| 文档 | 说明 |
|------|------|
| [智能体增强开发规划](doc/智能体增强开发规划.md) | Prompt模块化架构、工具调用规则、主动行为机制 |

### 前端设计

| 文档 | 说明 |
|------|------|
| [UI/UX设计详细文档](doc/UI_UX设计详细文档.md) | Cyber-Zen视觉风格、多主题色彩方案、布局系统 |

---

## 项目路线图

项目目前处于快速迭代阶段，主要演化路径如下：

- **第一阶段：【启蒙】** (已完成) - 建立双向记忆与基础对话链路。
- **第二阶段：【百宝袋】** (进行中) - 通过 MCP 协议获得现实世界干预能力。
- **第三阶段：【共生】** (规划中) - 情感波动与主动意识增强。
- **第四阶段：【深鉴】** (规划中) - 系统可观测性与思维过程可视化。

详细规划请参考 [项目计划书](doc/项目计划书.md)。

---

## 快速开始

### 环境要求

- **Java**: JDK 21+
- **Node.js**: 18+
- **Maven**: 3.9+
- **Docker**: 24+ (用于部署数据库服务)
- **Ollama**: 本地LLM推理引擎 (可选，或使用云端API)

### 方式一：Docker 部署 (推荐)

#### 1. 克隆项目

```bash
git clone https://github.com/SailRuo/LingShu-AI.git
cd LingShu-AI
```

#### 2. Windows 一键构建 (推荐)

项目提供了专为 Windows 环境优化的批处理脚本，可自动完成编译、前端构建、镜像封装及启动：

```bash
# 执行此脚本将完成：Maven 编译 + 前端打包 + Docker 镜像构建 + Compose 启动
.\build-docker.bat
```

#### 3. 手动 Docker 启动 (多步骤)

如果你想逐步控制过程：

```bash
# 构建后端
cd backend
mvn clean package -DskipTests
cd ..

# 启动 (需先配置好 .env)
docker-compose up -d
```

#### 4. 访问应用

- **前端界面**: http://localhost:8080
- **Neo4j控制台**: http://localhost:7474 (用户名: neo4j, 密码: lingshu123)
- **健康检查**: http://localhost:8080/actuator/health

#### 5. 停止服务

```bash
docker-compose down
```

---

### 方式二：本地开发启动

#### 1. 启动基础服务 (数据库)

```bash
# 仅启动数据库服务
docker-compose up -d neo4j postgres redis tts
```

#### 2. 配置后端

编辑 `backend/lingshu-web/src/main/resources/application.yml`：

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/lingshu
    username: postgres
    password: lingshu123
  neo4j:
    uri: bolt://localhost:7687
    authentication:
      username: neo4j
      password: lingshu123
  data:
    redis:
      host: localhost
      port: 6379
```

#### 3. 启动后端服务 (Windows 用户推荐脚本)

```bash
# 直接运行脚本 (已内置 UTF-8 编码处理、JVM 内存优化及僵尸进程清理)
.\run_backend.bat
```

**手动启动命令行方式：**

```bash
cd backend
mvn clean install -DskipTests
mvn spring-boot:run -pl lingshu-web
```

后端服务将在 http://localhost:8080 启动。

#### 4. 启动前端开发服务器

```bash
cd frontend

# 安装依赖
npm install

# 启动开发服务器
npm run dev
```

前端开发服务器将在 http://localhost:5173 启动。

#### 5. 配置 LLM

**使用 Ollama (推荐本地开发)**：

```bash
# 安装 Ollama (参考 https://ollama.ai)
# 拉取模型
ollama pull qwen2.5:7b

# 启动 Ollama 服务
ollama serve
```

在系统设置中配置：
- 模型来源: `ollama`
- 基础URL: `http://localhost:11434`

**使用 OpenAI 兼容 API**：

在系统设置中配置：
- 模型来源: `openai`
- API Key: 你的API密钥
- 基础URL: `https://api.openai.com/v1` 或其他兼容端点

---

## 核心功能说明

### 1. 对话系统

支持同步和流式两种响应模式，通过 `ChatService` 提供核心对话能力：

```java
// 流式对话
Flux<String> streamChat(String message, Long agentId, String userId);
```

### 2. 记忆系统

多级记忆架构：

| 级别 | 存储 | 说明 |
|------|------|------|
| L1 | PostgreSQL | 瞬时记忆，聊天历史窗口 |
| L2 | Neo4j | 事实记忆，结构化知识图谱 |
| L3 | pgvector | 语义记忆，向量相似度检索 |

核心接口：

```java
// 提取事实
void extractFacts(String userId, String message);

// 检索上下文
String retrieveContext(String userId, String message);

// 获取记忆图谱
Object getGraphData(String userId);
```

### 3. 主动交互

基于用户状态的主动问候与关怀：

```java
// 生成问候语
Flux<String> generateGreeting(String userId);

// 获取需要关注的用户
List<UserState> getUsersNeedingAttention();
```

### 4. MCP工具调用

支持 Model Context Protocol，可通过配置接入外部工具：

```java
// MCP服务接口
public interface McpService {
    List<McpServerConfig> getServerConfigs();
    void saveServerConfig(McpServerConfig config);
    void deleteServerConfig(Long id);
}
```

---

## API 接口

### 对话接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/chat/send` | 发送消息 (同步) |
| GET | `/api/chat/stream` | 流式对话 (SSE) |
| GET | `/api/chat/welcome` | 获取欢迎语 |
| GET | `/api/chat/history` | 获取聊天历史 |
| GET | `/api/chat/models` | 获取可用模型列表 |

### 记忆接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/memory/graph` | 获取记忆图谱数据 |
| GET | `/api/memory/context` | 检索记忆上下文 |
| DELETE | `/api/memory/fact/{id}` | 删除事实 |
| GET | `/api/memory/governance` | 记忆治理列表 |

### 设置接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/settings` | 获取系统设置 |
| PUT | `/api/settings` | 更新系统设置 |

### 智能体接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/agents` | 获取智能体列表 |
| POST | `/api/agents` | 创建智能体 |
| PUT | `/api/agents/{id}` | 更新智能体 |
| DELETE | `/api/agents/{id}` | 删除智能体 |

---

## 配置说明

### 系统设置

通过前端界面或直接修改数据库中的 `system_setting` 表：

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `model.source` | 模型来源 (ollama/openai) | ollama |
| `model.name` | 模型名称 | qwen2.5:7b |
| `model.baseUrl` | API基础URL | http://localhost:11434 |
| `model.apiKey` | API密钥 | - |
| `memory.enableExtraction` | 启用记忆提取 | true |
| `proactive.enableGreeting` | 启用主动问候 | true |

### 智能体配置

每个智能体可独立配置：

- 名称与描述
- 系统提示词
- 使用的模型
- 启用的工具

---

## 开发指南

### 后端开发

```bash
# 运行测试
cd backend
mvn test

# 代码格式化 (需要安装spotless)
mvn spotless:apply

# 热重载开发
mvn spring-boot:run -pl lingshu-web -Dspring-boot.run.fork=false
```

### 前端开发

```bash
cd frontend

# 类型检查
npm run typecheck

# 构建生产版本
npm run build

# 预览生产构建
npm run preview
```

### 数据库迁移

项目使用 JPA 自动建表 (ddl-auto: update)。生产环境建议使用 Flyway 或 Liquibase 进行版本化迁移。

---

## 常见问题

### Q: 如何切换不同的 LLM？

A: 在前端"系统设置"页面修改模型配置，支持：
- Ollama 本地模型
- OpenAI API
- 任何 OpenAI 兼容的 API (如 DeepSeek、通义千问等)

### Q: 记忆数据存储在哪里？

A: 
- 聊天历史：PostgreSQL
- 事实图谱：Neo4j
- 向量索引：PostgreSQL (pgvector)

### Q: 如何备份数据？

```bash
# 备份 PostgreSQL
docker exec lingshu-postgres pg_dump -U postgres lingshu > backup.sql

# 备份 Neo4j
docker exec lingshu-neo4j neo4j-admin database dump neo4j --to-path=/backup
```

### Q: 如何查看系统日志？

- Docker 部署：`docker logs lingshu-app`
- 本地开发：查看控制台输出或 `/app/logs/lingshu.log`

---

## 故障排查 (Troubleshooting)

### 1. Windows 控制台中文乱码
- **现象**: 启动脚本或 Maven 编译时输出为乱码。
- **方案**:
  - 后端已在 `run_backend.bat` 内部通过 `chcp 65001` 强制指定 UTF-8 编码。
  - 手动运行时请设置环境变量：`set JAVA_TOOL_OPTIONS=-Dfile.encoding=UTF-8`。

### 2. Maven 编译时内存溢出 (OOM)
- **现象**: 执行 `mvn package` 过程中报 `Native memory allocation` 失败。
- **方案**: 调大 Maven 的 JVM 堆内存限制。
  - `set MAVEN_OPTS=-Xmx1024m -Xms512m` (建议值)。

### 3. 数据库连接失败
- 确保相关端口未被占用：`5432` (Postgres), `7474/7687` (Neo4j), `6379` (Redis)。
- Docker 环境下，检查 `networks` 配置，确保 app 容器能解析 `postgres` / `neo4j` 域名。

### 4. LLM 响应缓慢或报错
- 检查 Ollama 服务监听地址，默认应为 `http://127.0.0.1:11434`。
- 如果在 Docker 内部访问宿主机的 Ollama，Base URL 需设为 `http://host.docker.internal:11434`。

---

## 贡献指南

欢迎提交 Issue 和 Pull Request！

1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feature/amazing-feature`)
3. 提交更改 (`git commit -m 'Add some amazing feature'`)
4. 推送到分支 (`git push origin feature/amazing-feature`)
5. 创建 Pull Request

---

## 许可证

本项目采用 GPLv3 许可证 - 详见 [LICENSE](LICENSE) 文件。

---

## 致谢

- [LangChain4j](https://github.com/langchain4j/langchain4j) - Java AI 框架
- [Naive UI](https://www.naiveui.com/) - Vue 3 组件库
- [Neo4j](https://neo4j.com/) - 图数据库
- [Ollama](https://ollama.ai/) - 本地 LLM 运行时

---

<div align="center">

**灵枢 - 让 AI 拥有记忆与情感**

</div>
