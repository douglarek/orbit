# Orbit

A Minimal coding agent.

## Configuration

Orbit reads model settings from `~/.orbit/config.toml`.

DashScope:

```toml
model = "dashscope:qwen3.6-plus"

[dashscope]
apiKey = "..."
```

OpenAI API:

```toml
model = "openai:your-model"

[openai]
apiKey = "..."
baseUrl = "https://example.com/v1"
```
