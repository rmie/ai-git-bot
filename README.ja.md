# AI-Git-Bot

[![License: MIT](https://img.shields.io/github/license/tmseidel/ai-git-bot)](LICENSE)
[![Docker Pulls](https://img.shields.io/docker/pulls/tmseidel/ai-git-bot)](https://hub.docker.com/r/tmseidel/ai-git-bot)
[![GitHub release](https://img.shields.io/github/v/release/tmseidel/ai-git-bot)](https://github.com/tmseidel/ai-git-bot/releases)
[![GitHub stars](https://img.shields.io/github/stars/tmseidel/ai-git-bot)](https://github.com/tmseidel/ai-git-bot/stargazers)
[![GitHub issues](https://img.shields.io/github/issues/tmseidel/ai-git-bot)](https://github.com/tmseidel/ai-git-bot/issues)

🌐 言語選択：[English](README.md) · **日本語** · [中文](README.zh.md) · [한국어](README.ko.md)

> **チームがすでに使っている Git ツールの中で、必要だけれど不快なソフトウェア開発作業を自動化しましょう。**

どのチームにも *「やるべきだとは分かっている」* エンジニアリング雑務の一覧があります。コーディング開始前に適切にスコープされた issue を書くこと、直したばかりのバグに回帰テストを追加すること、3 回目の force-push の後に PR を再レビューすること、古くなったプレビュー環境を破棄すること。こうした雑務は**必要**ですが**不快**です — そして締め切りの圧力がかかると真っ先に削られます。

**AI-Git-Bot は、そうした雑務を繰り返し実行できる自動化ワークフローに変えます。** それらは **Gitea、GitHub、GitHub Enterprise、GitLab、Bitbucket Cloud** の中でネイティブに動作し、チームが*すでに*発生させているイベント（issue の割り当て、PR の作成、reviewer の再リクエスト、コメントでの `@bot` メンション）によってトリガーされます。ローカル LLM まで含めて end-to-end でセルフホスト可能なので、インフラの外へ何かを出す必要はありません。

> 📣 **初めてですか？** **[pitch](doc/pitch/PITCH.md)** を読んでください — このプロジェクトがなぜ存在するのか、チームに何をもたらすのか、Copilot Workspace / GitLab Duo / Qodo / Aider と比べてどうなのかを説明しています（約 10 分）。

<p align="center">
  <img src="doc/images/schema.png" alt="AI-Git-Bot Architecture Schema" width="800"/>
</p>

## ✨ 何ができるか

| ワークフロー | トリガー | 生成されるもの |
|---|---|---|
| **PR Review** | bot が reviewer として指定された状態で PR が開かれる、またはレビューが再リクエストされる | インライン + 要約レビューコメント、大きな diff は分割処理 |
| **Interactive Q&A** | 任意の PR またはインラインレビューコメントで `@bot` がメンションされる | ファイル / diff コンテキストとセッションメモリ付きのスレッド返信 |
| **Issue → Code** (coding agent) | issue が *coding* bot に割り当てられる | 変更を実装した pull request。プロジェクト自身のビルドツールで検証済み |
| **Issue → Better Issue** (writer agent) | issue が *writer* bot に割り当てられる | 受け入れ基準を含む構造化された `AI Created Issue` |
| **Unit tests** (test author) | PR が開かれる、または `@bot generate-tests` | diff に対するホワイトボックスなユニットテスト。プロジェクト自身のテストランナーで実行され、PR ブランチへコミットされる |
| **Full-stack QA** (E2E tests) | デプロイ対象を持つ bot 上で PR が開かれる | PR ごとのプレビュー環境に対して実行される生成済み Playwright スイート、PR に投稿されるレポート、クローズ時の環境破棄 |

すべてのワークフローは **bot ごとの opt-in** です — 一番つらい雑務を選び、bot を 1 つつなげば完了です。触らないリポジトリには何も変化はありません。

> 🎥 **PR ワークフローが実際に動く様子を見る:** [AI-Git-Bot — PR workflow walkthrough on YouTube](https://www.youtube.com/watch?v=MjFmZHGIO-w)

<p align="center">
  <img src="doc/images/dashboard_ai_git_bot.PNG" alt="AI-Git-Bot ダッシュボード" width="800"/>
</p>

<details>
<summary>📸 スクリーンショット: 複数プラットフォームでのレビュー、会話、coding agent</summary>

**Gitea:** <img src="doc/screenshots/gitea/screenshot_initial_code_review.png" alt="Gitea Code Review" width="600"/>

**GitHub:** <img src="doc/screenshots/github/github_code_review_with_comment.png" alt="GitHub Code Review" width="600"/>

**GitLab:** <img src="doc/screenshots/gitlab/gitlab-pull-request-with-code-review.png" alt="GitLab Code Review" width="600"/>

**Bitbucket:** <img src="doc/screenshots/bitbucket/bitbucket-code-review.png" alt="Bitbucket Code Review" width="600"/>

**Coding agent (GitHub):** <img src="doc/screenshots/github/github_issue_agent_code_implementation.png" alt="GitHub Agent" width="600"/>

</details>

## 🔌 任意の AI provider と任意の Git プラットフォームを自由に組み合わせる

AI-Git-Bot は小さなセルフホスト型 **ゲートウェイ** です。AI provider を一度設定すれば、好きな数の bot やリポジトリに接続できます。API key、prompt、ツールホワイトリストは 1 つの管理 UI で管理されます。secret は保存時に暗号化（AES-256-GCM）され、リモート MCP server はツールごとのホワイトリスト付きで接続できます。

| AI provider | Git プラットフォーム |
|---|---|
| **Anthropic** (Claude) | **Gitea** (self-hosted) |
| **OpenAI** (+ OpenAI-compatible APIs) | **GitHub** / **GitHub Enterprise** |
| **Google AI / Gemini** | **GitLab** (gitlab.com & self-managed) |
| **Ollama** (ローカル LLM) | **Bitbucket Cloud** |
| **llama.cpp** (ローカル GGUF モデル) | |

> 🧪 **プロジェクト成熟度:** Gitea と GitHub は本番利用で十分にテストされています。GitLab と Bitbucket Cloud は実験的です（公式 API ドキュメントをもとに実装し、smoke test 済み）。Full-stack QA / E2E ワークフローは最も複雑な可動部分であり、すべての provider 上で実験的と見なしてください。**バグ報告は大歓迎です** — すべての非自明なワークフローには、再現可能な `docker-compose` system-test スタックが付属しています。[Testing Guide](doc/TESTING_GUIDE.md) を参照してください。

## 🚀 クイックスタート

Docker Compose で実行します（アプリコンテナ 1 つ + PostgreSQL — Kubernetes は不要です）：

```bash
git clone https://github.com/tmseidel/ai-git-bot.git
cd ai-git-bot
docker compose up --build -d
```

次に：

1. `http://localhost:8080` を開き、管理者アカウントを作成します
2. **AI Integration** を作成します（provider + API key）
3. **Git Integration** を作成します（[Gitea](doc/GITEA_SETUP.md) · [GitHub](doc/GITHUB_SETUP.md) · [GitLab](doc/GITLAB_SETUP.md) · [Bitbucket](doc/BITBUCKET_SETUP.md)）
4. **Bot** を作成し、必要なワークフローを有効化して、その **Webhook URL** をコピーします
5. Git provider 側で webhook を設定すれば完了です！

➡️ 詳細手順: [Deployment](doc/DEPLOYMENT.md) と [Admin Guide](doc/USER_GUIDE.md)。イメージは [Docker Hub](https://hub.docker.com/r/tmseidel/ai-git-bot) にあります。

## 📚 ドキュメント

ドキュメントは **[Documentation Hub](doc/README.md)** で対象読者別に整理されています：

| あなたは… | ここから始めてください |
|---|---|
| 👤 **ユーザー** — bot はすでに設定済みで、Git プラットフォームを使うだけ | [Using the Bot](doc/USING_THE_BOT.md) |
| 🛠️ **管理者** — ソフトウェア、bot、ワークフローを設定する | [Deployment](doc/DEPLOYMENT.md) · [Admin Guide](doc/USER_GUIDE.md) |
| 🧪 **テスター** — 機能を安全に試したい | [Testing Guide](doc/TESTING_GUIDE.md) |
| 💻 **開発者** — コードを扱う | [Local Development](doc/LOCAL_DEVELOPMENT.md) · [Architecture](doc/ARCHITECTURE.md) |

## ライセンス

[MIT](LICENSE)
