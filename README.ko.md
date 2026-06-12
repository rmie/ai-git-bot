# AI-Git-Bot

[![License: MIT](https://img.shields.io/github/license/tmseidel/ai-git-bot)](LICENSE)
[![Docker Pulls](https://img.shields.io/docker/pulls/tmseidel/ai-git-bot)](https://hub.docker.com/r/tmseidel/ai-git-bot)
[![GitHub release](https://img.shields.io/github/v/release/tmseidel/ai-git-bot)](https://github.com/tmseidel/ai-git-bot/releases)
[![GitHub stars](https://img.shields.io/github/stars/tmseidel/ai-git-bot)](https://github.com/tmseidel/ai-git-bot/stargazers)
[![GitHub issues](https://img.shields.io/github/issues/tmseidel/ai-git-bot)](https://github.com/tmseidel/ai-git-bot/issues)

🌐 언어 선택: [English](README.md) · **한국어** · [中文](README.zh.md) · [日本語](README.ja.md)

> **팀이 이미 사용하고 있는 Git 도구 안에서, 필요하지만 불편한 소프트웨어 개발 작업을 자동화하세요.**

모든 팀에는 *“해야 한다는 건 알고 있다”* 는 엔지니어링 잡무 목록이 있습니다. 코딩을 시작하기 전에 범위가 잘 정의된 이슈를 작성하는 일, 방금 고친 버그에 회귀 테스트를 추가하는 일, 세 번째 force-push 이후 PR을 다시 리뷰하는 일, 오래된 프리뷰 환경을 정리하는 일 같은 것들입니다. 이런 잡무는 **필요하지만** **불편합니다** — 그리고 마감 압박이 오면 가장 먼저 빠집니다.

**AI-Git-Bot은 그런 잡무를 반복 가능한 자동화 워크플로우로 바꿉니다.** 이 워크플로우는 **Gitea, GitHub, GitHub Enterprise, GitLab, Bitbucket Cloud** 안에서 네이티브하게 동작하며, 팀이 *이미* 만들어내고 있는 이벤트(issue 할당, PR 생성, reviewer 재요청, 댓글에서 `@bot` 멘션)에 의해 트리거됩니다. 로컬 LLM까지 포함해 end-to-end로 self-host할 수 있으므로, 어떤 것도 인프라 밖으로 나갈 필요가 없습니다.

> 📣 **처음 오셨나요?** **[pitch](doc/pitch/PITCH.md)** 를 읽어보세요 — 이 프로젝트가 왜 존재하는지, 팀에 무엇을 해주는지, Copilot Workspace / GitLab Duo / Qodo / Aider와 어떻게 비교되는지 설명합니다(~10분).

<p align="center">
  <img src="doc/images/schema.png" alt="AI-Git-Bot Architecture Schema" width="800"/>
</p>

## ✨ 무엇을 하나요

| 워크플로우 | 트리거 | 생성되는 것 |
|---|---|---|
| **PR Review** | 봇이 리뷰어로 지정된 상태에서 PR이 열리거나, 리뷰가 다시 요청됨 | 인라인 + 요약 리뷰 코멘트, 큰 diff는 분할 처리 |
| **Interactive Q&A** | 임의의 PR 또는 인라인 리뷰 코멘트에서 `@bot` 멘션 | 파일 / diff 컨텍스트와 세션 메모리가 포함된 스레드형 답변 |
| **Issue → Code** (coding agent) | 이슈가 *coding* bot에 할당됨 | 변경을 구현한 pull request, 프로젝트 자체 빌드 도구로 검증됨 |
| **Issue → Better Issue** (writer agent) | 이슈가 *writer* bot에 할당됨 | 수용 기준이 포함된 구조화된 `AI Created Issue` |
| **Unit tests** (test author) | PR이 열리거나 `@bot generate-tests` 실행 | diff에 대한 화이트박스 단위 테스트를 프로젝트 자체 테스트 러너로 실행하고 PR 브랜치에 커밋 |
| **Full-stack QA** (E2E tests) | 배포 대상이 있는 봇에서 PR이 열림 | PR별 프리뷰 환경에서 실행되는 생성된 Playwright 스위트, PR에 게시되는 리포트, 종료 시 환경 정리 |

모든 워크플로우는 **봇별 opt-in**입니다 — 가장 아픈 잡무 하나를 고르고, 봇 하나를 연결하면 끝입니다. 건드리지 않는 저장소에는 아무 변화도 없습니다.

> 🎥 **PR 워크플로우가 실제로 동작하는 모습을 보세요:** [AI-Git-Bot — PR workflow walkthrough on YouTube](https://www.youtube.com/watch?v=MjFmZHGIO-w)

<p align="center">
  <img src="doc/images/dashboard_ai_git_bot.PNG" alt="AI-Git-Bot 대시보드" width="800"/>
</p>

<details>
<summary>📸 스크린샷: 플랫폼 전반의 리뷰, 대화, coding agent</summary>

**Gitea:** <img src="doc/screenshots/gitea/screenshot_initial_code_review.png" alt="Gitea Code Review" width="600"/>

**GitHub:** <img src="doc/screenshots/github/github_code_review_with_comment.png" alt="GitHub Code Review" width="600"/>

**GitLab:** <img src="doc/screenshots/gitlab/gitlab-pull-request-with-code-review.png" alt="GitLab Code Review" width="600"/>

**Bitbucket:** <img src="doc/screenshots/bitbucket/bitbucket-code-review.png" alt="Bitbucket Code Review" width="600"/>

**Coding agent (GitHub):** <img src="doc/screenshots/github/github_issue_agent_code_implementation.png" alt="GitHub Agent" width="600"/>

</details>

## 🔌 어떤 AI provider든 어떤 Git 플랫폼과도 조합

AI-Git-Bot은 작은 self-hosted **게이트웨이**입니다. AI provider를 한 번 설정하면 원하는 만큼 많은 bot과 저장소에 붙일 수 있습니다. API key, prompt, 도구 화이트리스트는 하나의 관리자 UI에서 관리됩니다. secret은 저장 시 암호화(AES-256-GCM)되며, 원격 MCP server는 도구별 화이트리스트와 함께 연결할 수 있습니다.

| AI provider | Git 플랫폼 |
|---|---|
| **Anthropic** (Claude) | **Gitea** (self-hosted) |
| **OpenAI** (+ OpenAI-compatible APIs) | **GitHub** / **GitHub Enterprise** |
| **Google AI / Gemini** | **GitLab** (gitlab.com & self-managed) |
| **Ollama** (로컬 LLM) | **Bitbucket Cloud** |
| **llama.cpp** (로컬 GGUF 모델) | |

> 🧪 **프로젝트 성숙도:** Gitea와 GitHub는 프로덕션 사용에서 충분히 테스트되었습니다. GitLab과 Bitbucket Cloud는 실험적입니다(공식 API 문서를 바탕으로 구현하고 smoke test 완료). Full-stack QA / E2E 워크플로우는 가장 복잡한 구성 요소이므로 모든 provider에서 실험적으로 간주해야 합니다. **버그 리포트는 매우 환영합니다** — 모든 비단순 워크플로우에는 재현 가능한 `docker-compose` system-test 스택이 포함되어 있습니다. [Testing Guide](doc/TESTING_GUIDE.md)를 참고하세요.

## 🚀 빠른 시작

Docker Compose로 실행합니다(앱 컨테이너 하나 + PostgreSQL — Kubernetes 불필요):

```bash
git clone https://github.com/tmseidel/ai-git-bot.git
cd ai-git-bot
docker compose up --build -d
```

그다음:

1. `http://localhost:8080`을 열고 관리자 계정을 만듭니다
2. **AI Integration**을 만듭니다(provider + API key)
3. **Git Integration**을 만듭니다([Gitea](doc/GITEA_SETUP.md) · [GitHub](doc/GITHUB_SETUP.md) · [GitLab](doc/GITLAB_SETUP.md) · [Bitbucket](doc/BITBUCKET_SETUP.md))
4. **Bot**을 만들고, 원하는 워크플로우를 활성화한 뒤, **Webhook URL**을 복사합니다
5. Git provider에서 webhook을 설정하면 끝입니다!

➡️ 전체 안내: [Deployment](doc/DEPLOYMENT.md) 및 [Admin Guide](doc/USER_GUIDE.md). 이미지는 [Docker Hub](https://hub.docker.com/r/tmseidel/ai-git-bot)에 있습니다.

## 📚 문서

문서는 **[Documentation Hub](doc/README.md)** 에서 대상 독자별로 정리되어 있습니다:

| 당신은… | 여기서 시작하세요 |
|---|---|
| 👤 **사용자** — bot은 이미 설정되어 있고, Git 플랫폼을 사용하기만 합니다 | [Using the Bot](doc/USING_THE_BOT.md) |
| 🛠️ **관리자** — 소프트웨어, bot, 워크플로우를 설정합니다 | [Deployment](doc/DEPLOYMENT.md) · [Admin Guide](doc/USER_GUIDE.md) |
| 🧪 **테스터** — 기능을 안전하게 시험해 보고 싶습니다 | [Testing Guide](doc/TESTING_GUIDE.md) |
| 💻 **개발자** — 코드로 작업합니다 | [Local Development](doc/LOCAL_DEVELOPMENT.md) · [Architecture](doc/ARCHITECTURE.md) |

## 라이선스

[MIT](LICENSE)
