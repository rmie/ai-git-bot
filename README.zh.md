# AI-Git-Bot

[![License: MIT](https://img.shields.io/github/license/tmseidel/ai-git-bot)](LICENSE)
[![Docker Pulls](https://img.shields.io/docker/pulls/tmseidel/ai-git-bot)](https://hub.docker.com/r/tmseidel/ai-git-bot)
[![GitHub release](https://img.shields.io/github/v/release/tmseidel/ai-git-bot)](https://github.com/tmseidel/ai-git-bot/releases)
[![GitHub stars](https://img.shields.io/github/stars/tmseidel/ai-git-bot)](https://github.com/tmseidel/ai-git-bot/stargazers)
[![GitHub issues](https://img.shields.io/github/issues/tmseidel/ai-git-bot)](https://github.com/tmseidel/ai-git-bot/issues)

🌐 语言版本：[English](README.md) · [한국어](README.ko.md) · [日本語](README.ja.md) · **中文**

> **直接在您的团队已经在使用的 Git 工具中，自动化软件开发中那些必要但令人不适的部分。**

每个团队都有一份 *“我们知道应该做这个”* 的工程杂务清单：在开始编码前写一个范围明确的 issue，为刚修复的 bug 添加回归测试，第三次 force-push 后重新审查 PR，拆除过期的预览环境。这些杂务**必要**却**令人不适**——而在截止日期压力下，它们总是最先被砍掉。

**AI-Git-Bot 将这些杂务变成可重复执行的自动化工作流**，并原生存在于 **Gitea、GitHub、GitHub Enterprise、GitLab 和 Bitbucket Cloud** 中——由您的团队*已经*在产生的事件触发（issue 被分配、PR 被打开、重新请求 reviewer、在评论中提到 `@bot`）。它可以端到端自托管，包括本地 LLM——没有任何东西必须离开您的基础设施。

> 📣 **第一次来？** 阅读 **[pitch](doc/pitch/PITCH.md)** —— 了解这个项目为什么存在、它能为团队做什么，以及它与 Copilot Workspace / GitLab Duo / Qodo / Aider 相比如何（约 10 分钟）。

<p align="center">
  <img src="doc/images/schema.png" alt="AI-Git-Bot Architecture Schema" width="800"/>
</p>

## ✨ 它能做什么

| 工作流 | 触发方式 | 产出内容 |
|---|---|---|
| **PR Review** | PR 打开时 bot 已被指定为 reviewer，或后来重新请求 review | 内联 + 摘要审查评论；大 diff 会被分块处理 |
| **Interactive Q&A** | 在任何 PR 或内联审查评论中提到 `@bot` | 带文件 / diff 上下文和会话记忆的线程式回复 |
| **Issue → Code**（coding agent） | issue 被分配给*编码* bot | 一个实现该改动的 pull request，并用您项目自己的构建工具验证 |
| **Issue → Better Issue**（writer agent） | issue 被分配给*写作* bot | 一个带验收标准的结构化 `AI Created Issue` |
| **Unit tests**（test author） | PR 被打开，或执行 `@bot generate-tests` | 针对 diff 的白盒单元测试，用您项目自己的测试运行器执行，并提交到 PR 分支 |
| **Full-stack QA**（E2E tests） | PR 在带有部署目标的 bot 上打开 | 生成的 Playwright 套件，在每个 PR 的预览环境上运行；报告发布到 PR，关闭时拆除环境 |

所有工作流都是**按 bot 选择启用**——挑最痛的那件杂务，接一个 bot，就完成了。您没触碰的仓库不会发生任何变化。

> 🎥 **观看 PR 工作流实际运行：** [AI-Git-Bot — YouTube 上的 PR workflow walkthrough](https://www.youtube.com/watch?v=MjFmZHGIO-w)

<p align="center">
  <img src="doc/images/dashboard_ai_git_bot.PNG" alt="AI-Git-Bot 控制台" width="800"/>
</p>

<details>
<summary>📸 截图：跨平台的审查、对话和 coding agent</summary>

**Gitea：** <img src="doc/screenshots/gitea/screenshot_initial_code_review.png" alt="Gitea Code Review" width="600"/>

**GitHub：** <img src="doc/screenshots/github/github_code_review_with_comment.png" alt="GitHub Code Review" width="600"/>

**GitLab：** <img src="doc/screenshots/gitlab/gitlab-pull-request-with-code-review.png" alt="GitLab Code Review" width="600"/>

**Bitbucket：** <img src="doc/screenshots/bitbucket/bitbucket-code-review.png" alt="Bitbucket Code Review" width="600"/>

**Coding agent（GitHub）：** <img src="doc/screenshots/github/github_issue_agent_code_implementation.png" alt="GitHub Agent" width="600"/>

</details>

## 🔌 任意 AI provider 与任意 Git 平台自由组合

AI-Git-Bot 是一个小型自托管**网关**：配置一次 AI provider，就可以将它连接到任意数量的 bot 和仓库。API key、prompt 和工具白名单都在同一个管理 UI 中管理；secret 以静态加密方式保存（AES-256-GCM）；远程 MCP server 可按工具白名单接入。

| AI provider | Git 平台 |
|---|---|
| **Anthropic**（Claude） | **Gitea**（自托管） |
| **OpenAI**（+ OpenAI 兼容 API） | **GitHub** / **GitHub Enterprise** |
| **Google AI / Gemini** | **GitLab**（gitlab.com 与自管理） |
| **Ollama**（本地 LLM） | **Bitbucket Cloud** |
| **llama.cpp**（本地 GGUF 模型） | |

> 🧪 **项目成熟度：** Gitea 和 GitHub 已在生产使用中得到充分测试；GitLab 和 Bitbucket Cloud 属于实验性（根据官方 API 文档实现并做过 smoke test）。Full-stack QA / E2E 工作流是最复杂的活动部件，在每个 provider 上都应视为实验性。**非常欢迎 bug 报告**——每个非平凡工作流都附带一个可复现的 `docker-compose` system-test 栈；请参见 [Testing Guide](doc/TESTING_GUIDE.md)。

## 🚀 快速开始

使用 Docker Compose 运行（一个应用容器 + PostgreSQL——不需要 Kubernetes）：

```bash
git clone https://github.com/tmseidel/ai-git-bot.git
cd ai-git-bot
docker compose up --build -d
```

然后：

1. 打开 `http://localhost:8080` 并创建管理员账号
2. 创建一个 **AI Integration**（provider + API key）
3. 创建一个 **Git Integration**（[Gitea](doc/GITEA_SETUP.md) · [GitHub](doc/GITHUB_SETUP.md) · [GitLab](doc/GITLAB_SETUP.md) · [Bitbucket](doc/BITBUCKET_SETUP.md)）
4. 创建一个 **Bot**，启用您需要的工作流，并复制它的 **Webhook URL**
5. 在您的 Git provider 中配置 webhook——完成！

➡️ 完整说明：[Deployment](doc/DEPLOYMENT.md) 和 [Admin Guide](doc/USER_GUIDE.md)。镜像位于 [Docker Hub](https://hub.docker.com/r/tmseidel/ai-git-bot)。

## 📚 文档

文档在 **[Documentation Hub](doc/README.md)** 中按受众组织：

| 您是… | 从这里开始 |
|---|---|
| 👤 **用户** — bot 已经配置好，您只是使用 Git 平台 | [Using the Bot](doc/USING_THE_BOT.md) |
| 🛠️ **管理员** — 您负责配置软件、bot 和工作流 | [Deployment](doc/DEPLOYMENT.md) · [Admin Guide](doc/USER_GUIDE.md) |
| 🧪 **测试者** — 您想安全地试用功能 | [Testing Guide](doc/TESTING_GUIDE.md) |
| 💻 **开发者** — 您要处理代码 | [Local Development](doc/LOCAL_DEVELOPMENT.md) · [Architecture](doc/ARCHITECTURE.md) |

## 许可证

[MIT](LICENSE)
