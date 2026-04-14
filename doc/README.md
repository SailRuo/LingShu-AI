# 灵枢 AI (LingShu-AI) 文档中心

欢迎来到灵枢 AI 的文档中心。本文档旨在为开发者、贡献者和用户提供一个清晰的项目全景图。

## 🏗️ 架构与设计 (Architecture & Design)

这些文档描述了系统的核心设计理念、数据模型和交互流程，相对稳定。

- [系统架构设计](architecture/系统架构设计文档.md): 包含分层架构、主链路流程图及核心技术决策。
- [记忆模块设计](architecture/记忆模块设计文档.md): 详细解析情感感知记忆系统、GAM-RAG 混合召回及生命周期管理。
- [UI/UX 设计规范](architecture/UI_UX设计文档.md): 基于代码逆向生成的界面规范，包含主题系统、组件库及动效说明。
- [核心工作流详解](architecture/对话调用链路详解.md): 对话调用链路的深度解析。

## 🚀 实施与计划 (Implementation & Roadmap)

记录项目的开发进度、阶段性目标及各模块的实施细节。

- [开发路线图 (Roadmap)](implementation/灵枢开发计划与进度总览.md): 项目总体进度概览与未来规划。
- [记忆模块实施计划](implementation/记忆模块计划与实施文档/README.md): **(重点建议)** 包含情感事实提取、3D 银河图谱及记忆治理后台的完整记录。
- [系统核心计划](implementation/系统计划与实施文档/Tauri实施文档.md): 包含 Tauri 桌面端迁移及整体系统核心实施方案。
- [系统设置与 Bot 接入](implementation/系统设置计划与实施文档/微信Bot接入计划与实施文档.md): 微信 Bot 接入、系统参数配置等实施细节。

## 🛠️ 开发者指南 (Guides)

帮助你快速搭建环境、部署应用或进行二次开发。

- **环境搭建**: 请参考根目录 `README.md` 中的 Quick Start 章节。
- **API 参考**: 启动后端服务后访问 `/swagger-ui.html` (如已配置)。
- **部署指南**: 参见根目录 `docker-compose.yml` 及相关 Docker 脚本。

## 🔬 调研与决策 (Research)

收录技术选型过程中的调研报告与对比分析。

- [语音技术调研](research/): 包含 Edge-TTS 与豆包 TTS 的技术对比与接入说明。
- [Agent Prompt 库](agent_prompt/): 存储各类智能体的提示词模板。

## 📂 资源文件 (Assets)

- [图片资源](assets/png/): 项目 UI Mockup 及演示截图。
- [图表源文件](assets/diagrams/): Mermaid 或 PlantUML 原始文件。

---

**文档维护建议**:
1. **单一事实来源**: 修改代码时请同步更新相关的设计文档。
2. **链接有效性**: 尽量使用相对路径引用其他文档。
3. **版本同步**: 重大架构变更后，请在文档头部更新“最后更新时间”。
