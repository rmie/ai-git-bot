# GitHub MCP system test

This setup starts AI-Git-Bot with PostgreSQL and GitHub's official MCP server.

## Prerequisites

- Docker Compose
- `GITHUB_PERSONAL_ACCESS_TOKEN` with read access to the repository used in the scenario
- An AI provider that supports MCP server configuration (currently Anthropic requests forward MCP server JSON)

## Run

```bash
export GITHUB_PERSONAL_ACCESS_TOKEN=ghp_...
docker compose -f systemtest/docker-compose-mcp-github.yml up --build
```

In the web UI:

1. Create an AI integration for an MCP-capable provider.
2. Go to **System settings** and create an MCP configuration similar to:

   ```json
   {
     "name": "github",
     "type": "url",
     "url": "http://github-mcp:8080/mcp"
   }
   ```

3. Assign that MCP configuration to a bot.
4. Ask the bot to use code from a GitHub repository that is not otherwise present in the prompt or webhook payload.

The scenario should succeed only when the MCP configuration is assigned and the GitHub MCP server is reachable.
