# 安装与配置指南 (Installation & Configuration)

本文档提供了灵枢 (LingShu-AI) 的详细安装步骤、环境要求及系统配置说明。

---

## 📋 环境要求

在开始之前，请确保您的系统满足以下要求：

- **Java**: JDK 21+
- **Node.js**: 18+
- **Maven**: 3.9+
- **Docker**: 24+ (用于部署数据库服务)
- **Ollama**: 本地 LLM 推理引擎 (可选，或使用云端 API)

---

## 🚀 快速开始

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
npm install
npm run dev
```
前端开发服务器将在 http://localhost:5173 启动。

---

## ⚙️ 系统配置说明

灵枢提供了丰富的配置选项，所有配置均可通过前端"系统设置"界面可视化修改。

### 1. LLM 配置
**使用 Ollama (推荐本地开发)**：
- 模型来源: `ollama`
- 基础URL: `http://localhost:11434`
- 需先执行 `ollama pull qwen2.5:7b`

**使用 OpenAI 兼容 API**：
- 模型来源: `openai`
- API Key: 你的 API 密钥
- 基础URL: `https://api.openai.com/v1` 或其他兼容端点

### 2. Embedding 配置
- 建议使用与 LLM 匹配的向量化服务。
- 详细配置请参考 [系统设置模块设计文档](architecture/系统设置模块设计文档.md#42-embedding配置-embedding)。
