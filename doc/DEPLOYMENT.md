# Deployment

This guide covers deploying the AI-Git-Bot Gateway using Docker Compose.

## Prerequisites

- **Docker** and **Docker Compose** installed
- A **Git hosting platform** configured:
  - Gitea: See [Gitea Setup](GITEA_SETUP.md)
  - GitHub / GitHub Enterprise: See [GitHub Setup](GITHUB_SETUP.md)
  - GitLab / GitLab CE/EE: See [GitLab Setup](GITLAB_SETUP.md)
  - Bitbucket Cloud: See [Bitbucket Setup](BITBUCKET_SETUP.md)
- API credentials for your chosen AI provider (Anthropic, OpenAI) or a local Ollama/llama.cpp instance

## Quick Start

### PostgreSQL (default)

```bash
docker compose up --build -d
```

This starts the bot application on port **8080** together with a
**PostgreSQL 17** database container. Data is persisted in a Docker
volume.

### H2 (embedded — no external database)

```bash
docker compose -f docker-compose.yml -f docker-compose.h2.yml up --build -d
```

This starts only the bot application with an embedded H2 file database.
No external database container is required — suitable for small teams,
personal projects, or evaluation.

Then:
1. Navigate to `http://localhost:8080` to complete initial setup
2. Create your admin account
3. Configure AI and Git integrations via the web UI
4. Create a bot and configure webhooks in your Git provider (Gitea, GitHub, GitLab, or Bitbucket)

See the [User Guide](USER_GUIDE.md) for detailed instructions.

## Docker Compose Template

### PostgreSQL (default)

The bundled `docker-compose.yml` starts the bot with a PostgreSQL 17
database container:

```yaml
services:
  app:
    image: tmseidel/ai-git-bot:latest
    # Or build locally:
    # build: .
    ports:
      - "8080:8080"
    environment:
      SPRING_PROFILES_ACTIVE: docker
      DATABASE_URL: jdbc:postgresql://db:5432/giteabot
      DATABASE_USERNAME: giteabot
      DATABASE_PASSWORD: change-me
      APP_ENCRYPTION_KEY: your-secure-encryption-key-here
    depends_on:
      db:
        condition: service_healthy
    restart: unless-stopped

  db:
    image: postgres:17-alpine
    environment:
      POSTGRES_DB: giteabot
      POSTGRES_USER: giteabot
      POSTGRES_PASSWORD: change-me
    volumes:
      - pgdata:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U giteabot"]
      interval: 5s
      timeout: 5s
      retries: 5
    restart: unless-stopped

volumes:
  pgdata:
```

### H2 (embedded — no external database)

For H2, use the `docker-compose.h2.yml` override or add the H2
configuration manually:

```yaml
services:
  app:
    image: tmseidel/ai-git-bot:latest
    ports:
      - "8080:8080"
    environment:
      SPRING_PROFILES_ACTIVE: docker,h2
      APP_ENCRYPTION_KEY: your-secure-encryption-key-here
    volumes:
      - h2data:/data
    restart: unless-stopped

volumes:
  h2data:
```

> **Note:** Replace placeholders with your actual values. For sensitive values, consider using a `.env` file or Docker secrets.

## Environment Variables

### Required

| Variable | Description |
|----------|-------------|
| `APP_ENCRYPTION_KEY` | Encryption key for sensitive data (API keys, tokens). Set to a fixed value for persistence across restarts. If not set, a random key is generated (data won't survive restarts). |
| `DATABASE_URL` | JDBC connection URL. Defaults are profile-dependent: `jdbc:postgresql://db:5432/giteabot` for PostgreSQL, `jdbc:h2:file:/data/giteabot` for H2. |
| `DATABASE_USERNAME` | Database username (default: `giteabot` for PostgreSQL, `sa` for H2) |
| `DATABASE_PASSWORD` | Database password (default: `giteabot` for PostgreSQL, empty for H2) |

### Agent Configuration (Optional)

The **coding agent** is enabled per coding bot via the web UI. Writer workflows are selected separately by choosing **Bot Type = Writer bot**. These environment variables configure global coding-agent behavior:

| Variable | Default | Description |
|----------|---------|-------------|
| `AGENT_MAX_FILES` | `20` | Maximum files the agent can modify per issue |
| `AGENT_MAX_TOKENS` | `32768` | Maximum tokens for AI responses in agent mode |
| `AGENT_BRANCH_PREFIX` | `ai-agent/` | Prefix for branches created by the agent |
| `AGENT_VALIDATION_ENABLED` | `true` | Enable syntax validation before commit |
| `AGENT_VALIDATION_MAX_RETRIES` | `3` | Max iterations for error correction |

See [Agent Documentation](AGENT.md) for full details.

## Configuration via Web UI

All AI provider and Git configuration is managed through the web interface:

1. **AI Integrations**: Create connections to AI providers (Anthropic, OpenAI, Ollama, llama.cpp)
   - Provider-specific default API URLs are pre-filled
   - Suggested models are available via dropdown
   - API keys are encrypted at rest

2. **Git Integrations**: Create connections to Git hosting platforms
   - **Gitea**: Self-hosted Gitea instances — see [Gitea Setup](GITEA_SETUP.md)
   - **GitHub**: github.com or GitHub Enterprise Server — see [GitHub Setup](GITHUB_SETUP.md)
   - **GitLab**: gitlab.com or self-managed GitLab — see [GitLab Setup](GITLAB_SETUP.md)
   - **Bitbucket Cloud**: bitbucket.org — see [Bitbucket Setup](BITBUCKET_SETUP.md)
   - Tokens are encrypted at rest

3. **Bots**: Create bots that combine an AI integration with a Git integration
   - Each bot gets a unique webhook URL
   - Select a system prompt entry per bot
   - Enable/disable coding-agent issue implementation per coding bot
   - Choose **Writer bot** when you want issue drafting instead of code changes

## System Prompts

System prompts are stored in the database and managed in **System settings → System prompts**. On migration, Flyway creates a default prompt entry from the bundled prompt files and assigns it to all existing bots. The migration removes the legacy per-bot prompt column; copy any custom per-bot prompt text before upgrading if you need to recreate it as a reusable system prompt entry.

When upgrading to the version that adds .NET validation support, Flyway overwrites the **Default** coding-agent system prompt so it includes `.sln` / `.csproj` detection and `dotnet build` / `dotnet test` guidance. Back up changes made directly to the **Default** prompt before upgrading, or clone it to a custom prompt entry and assign that entry to your bots.

The `prompts/` directory is still copied into the image as the source for default prompt content:

```yaml
volumes:
  - ./prompts:/app/prompts:ro
```

After upgrade, edit or clone prompt entries in the UI instead of editing bot prompt text directly.

## Dockerfile Details

The Dockerfile uses a **multi-stage build**:

1. **Build stage** (`eclipse-temurin:21-jdk-alpine`): Compiles the application with Maven
2. **Runtime stage** (`eclipse-temurin:21-jre-alpine`): Runs the JAR as a non-root user

Key features:
- Maven dependency layer caching for fast rebuilds
- Non-root `appuser` for security
- Health check via `/actuator/health` (interval: 30s, start period: 30s)
- JVM tuning: `UseContainerSupport` and `MaxRAMPercentage=75.0`

## Database

The application supports two database backends, selected at deployment
time via Spring profiles:

### PostgreSQL (default)

- PostgreSQL 17 (Alpine) container included in `docker-compose.yml`
- Data is persisted in the `pgdata` Docker volume
- Activated via `SPRING_PROFILES_ACTIVE=docker` (PostgreSQL is the default in the `docker` profile)
- Suitable for production, larger deployments, or when a dedicated database is preferred
- Schema is managed by Flyway using the `db/migration/postgresql/` scripts

### H2 (embedded — opt-in)

- No external database container required
- File-based, persisted in the `h2data` Docker volume at `/data`
- Activated via `SPRING_PROFILES_ACTIVE=docker,h2` (use the `docker-compose.h2.yml` override)
- Suitable for small teams, personal projects, or evaluation
- Schema is managed by Flyway using the `db/migration/h2/` scripts

### Switching backends

The database backend is determined entirely by `SPRING_PROFILES_ACTIVE`
and `DATABASE_URL`. No code changes are required. The application
already maintains parallel Flyway migration scripts for both databases.

Both backends store:
- Admin users
- AI integrations (with encrypted API keys)
- Git integrations (with encrypted tokens)
- Bots
- Review sessions and conversation history

## Health Check

The bot exposes a health endpoint:

```
GET http://<bot-host>:8080/actuator/health
```

Use this for load balancer health checks or container orchestration.

## Stopping

```bash
# PostgreSQL (default)
docker compose down        # Stop containers (data preserved in pgdata volume)
docker compose down -v     # Stop and remove volumes (deletes all data)

# H2
docker compose -f docker-compose.yml -f docker-compose.h2.yml down
docker compose -f docker-compose.yml -f docker-compose.h2.yml down -v
```
