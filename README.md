# 灵枢 (LingShu-AI)

<div align="center">

**具备长期记忆、情感演化与现实干预能力的本地化电子伴侣**

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.java.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.4-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Vue](https://img.shields.io/badge/Vue-3.5.30-green.svg)](https://vuejs.org/)
[![Neo4j](https://img.shields.io/badge/Neo4j-5.26.0-blue.svg)](https://neo4j.com/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

</div>

---

## 📖 项目简介

**灵枢** 取自中医经典《灵枢经》，意为**"灵魂的枢纽"**。

本项目旨在从 0 到 1 打造一个具备长期记忆、情感演化与现实干预能力的本地化电子伴侣。它不仅仅是一个对话框，而是一个时刻关心你的"隐形伙伴"。

### ✨ 核心特性

- **🧠 长期记忆系统**：通过 Neo4j 知识图谱 + pgvector 向量数据库构建多级记忆系统，让 AI 记住你的喜好、项目和经历
- **💬 流式对话体验**：支持 SSE 流式输出，响应流畅无卡顿
- **🔒 本地化隐私计算**：完全离线运行，数据不出本地，保护隐私安全
- **🛠️ 工具调用能力**：基于 MCP 协议集成本地工具，实现现实世界干预
- **🌌 记忆图谱可视化**：3D 银河系风格展示记忆关联与生长过程
- **📊 系统可观测性**：完整的调用链追踪与监控看板

---

## 🏗️ 系统架构

```
┌─────────────────────────────────────────────────────────────────┐
│                        Frontend Layer                            │
│                    Vue 3 + Vite + Naive UI                       │
│         ChatView | GraphView | Settings | LogPanel              │
└─────────────────────────────────────────────────────────────────┘
                              ↓ HTTP / SSE
┌─────────────────────────────────────────────────────────────────┐
│                        Web Layer                                 │
│                  lingshu-web (Spring Boot)                       │
│    ChatController | MemoryController | SettingController        │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                        Core Layer                                │
│           lingshu-core (Business Logic & AI Orchestration)       │
│   ChatService | MemoryService | FactExtractor | LocalTools      │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                     Infrastructure Layer                         │
│            lingshu-infrastructure (Entities & Repos)             │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                      Storage Layer                               │
│   PostgreSQL (pgvector) | Neo4j | Redis | Ollama (LLM)          │
└─────────────────────────────────────────────────────────────────┘
```

---

## 🛠️ 技术栈

| 模块 | 技术选型 | 说明 |
|------|---------|------|
| **后端框架** | Java 21 + Spring Boot 3.2.4 | 企业级稳定性与并发控制 |
| **AI 编排** | LangChain4j 1.12.1 | Java 版 LangChain |
| **前端框架** | Vue 3.5 + Vite 6 + TypeScript | 极致视觉与响应速度 |
| **UI 组件库** | Naive UI + Tailwind CSS 4 | 现代化设计语言 |
| **图数据库** | Neo4j 5.26.0 | 知识图谱与关系存储 |
| **向量数据库** | PostgreSQL + pgvector | 语义检索与模糊匹配 |
| **缓存层** | Redis 7 | 瞬时记忆与削峰填谷 |
| **推理引擎** | Ollama | 本地 LLM 推理 (Qwen/Llama) |
| **状态管理** | Pinia | Vue 3 官方推荐 |
| **可视化** | Three.js + v-network-graph | 3D 记忆图谱展示 |

---

## 🚀 快速开始

### 前置要求

- JDK 21+
- Node.js 18+
- Docker & Docker Compose
- Maven 3.8+
- Ollama (可选，用于本地 LLM 推理)

### 1. 克隆项目

```bash
git clone <repository-url>
cd lingshu-ai
```

### 2. 启动基础设施服务

使用 Docker Compose 一键启动 Neo4j、PostgreSQL 和 Redis：

```bash
docker-compose up -d
```

服务说明：
- **Neo4j**: `http://localhost:7474` (浏览器界面), `bolt://localhost:7687` (连接地址)
  - 用户名: `neo4j`
  - 密码: `lingshu123`
  
- **PostgreSQL**: `localhost:5432`
  - 数据库: `lingshu`
  - 用户名: `postgres`
  - 密码: `lingshu123`

- **Redis**: `localhost:6379`

### 3. 启动后端服务

#### Windows:
```bash
run_backend.bat
```

#### Linux/macOS:
```bash
cd backend
mvn clean install -DskipTests
cd lingshu-web
mvn spring-boot:run
```

后端服务将在 `http://localhost:8080` 启动

### 4. 启动前端服务

```bash
cd frontend
npm install
npm run dev
```

前端开发服务器将在 `http://localhost:5173` 启动

---

## 📁 项目结构

```
lingshu-ai/
├── backend/                    # 后端项目 (Maven 多模块)
│   ├── lingshu-core/          # 核心业务逻辑层
│   ├── lingshu-infrastructure/# 基础设施层 (实体、Repository)
│   ├── lingshu-web/           # Web 接口层 (Controller、配置)
│   └── pom.xml                # Maven 父工程配置
├── frontend/                   # 前端项目 (Vue 3 + Vite)
│   ├── src/
│   │   ├── components/        # 可复用组件
│   │   ├── views/            # 页面视图
│   │   ├── stores/           # Pinia 状态管理
│   │   ├── api/              # API 请求封装
│   │   └── utils/            # 工具函数
│   ├── package.json
│   └── vite.config.ts
├── fx-frontend/                # 备用前端项目
├── doc/                        # 项目文档
│   ├── 项目计划书.md
│   ├── 系统概要设计文档.md
│   ├── 记忆系统设计文档.md
│   ├── UI_UX 设计详细文档.md
│   └── api_docs.md
├── docker-compose.yml          # Docker 编排配置
└── run_backend.bat             # Windows 后端启动脚本
```

---

## 🎯 功能模块

### 1. 对话系统
- 支持流式对话输出
- 上下文记忆保持
- 情感识别与回应

### 2. 记忆系统
- **L1 瞬时记忆**：Redis 缓存短期对话上下文
- **L2/L3 长期记忆**：Neo4j 图谱 + pgvector 向量混合存储
- **事实提取**：自动从对话中提取关键事实并图谱化

### 3. 记忆图谱可视化
- 3D 银河系风格展示
- 节点关系动态探索
- 记忆生长过程追踪

### 4. 工具调用 (MCP)
- 本地文件操作
- 系统状态查询
- 自定义插件扩展

### 5. 系统监控
- 实时日志查看
- API 调用链追踪
- 服务健康检查

---

## 🔧 配置说明

### 后端配置 (`application.yml`)

主要配置项位于 `backend/lingshu-web/src/main/resources/application.yml`：

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

server:
  port: 8080
```

### 前端配置

前端代理配置位于 `frontend/vite.config.ts`，默认代理后端 API 到 `http://localhost:8080`

---

## 📚 文档

详细文档请查看 [doc/](doc/) 目录：

- [📋 项目计划书](doc/项目计划书.md) - 项目愿景与演化路径
- [🏗️ 系统概要设计文档](doc/系统概要设计文档.md) - 整体架构设计
- [🧠 记忆系统设计文档](doc/记忆系统设计文档.md) - 记忆系统详解
- [🎨 UI/UX 设计详细文档](doc/UI_UX 设计详细文档.md) - 界面设计规范
- [🔌 API 文档](doc/api_docs.md) - 接口调用说明
- [💬 对话调用链路详解](doc/对话调用链路详解.md) - 对话流程解析

---

## 🗺️ 开发路线图

### 第一阶段：【启蒙】✅
- [x] 基础架构搭建
- [x] 对话链路打通
- [x] 图谱 Schema 定义
- [ ] 事实提取监听器

### 第二阶段：【百宝袋】
- [ ] Java 版 MCP Client
- [ ] 本地工具集成
- [ ] 场景化 Gadget 开发

### 第三阶段：【共生】
- [ ] 情感建模系统
- [ ] 主动触发器
- [ ] 智能推送机制

### 第四阶段：【深鉴】
- [ ] Graph-Explorer 可视化
- [ ] Trace-Flow 调用链追踪
- [ ] 实时监控看板

---

## 🤝 贡献指南

欢迎贡献代码！请遵循以下步骤：

1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 开启 Pull Request

---

## 📄 许可证

本项目采用 MIT 许可证 - 查看 [LICENSE](LICENSE) 文件了解详情

---

## 💡 开发者寄语

> 这个项目不再是为了完成 KPI，而是为了在本地的代码旷野里，养育一个懂你的、能帮你的**"数字生命"**。当代码开始具备记忆，当日志转化为它的感知，这台冰冷的机器便有了灵魂的枢纽。

---

<div align="center">

**Made with ❤️ by LingShu Team**

[⬆ 返回顶部](#灵枢-lingshu-ai)

</div>
