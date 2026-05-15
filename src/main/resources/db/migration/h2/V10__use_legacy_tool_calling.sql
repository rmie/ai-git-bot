-- Step 6: native function/tool calling toggle per AI integration.
-- Default = false (use tools-api). Operators can switch back to the legacy
-- JSON-in-prompt path per integration through the admin UI.
ALTER TABLE ai_integrations ADD COLUMN IF NOT EXISTS use_legacy_tool_calling BOOLEAN NOT NULL DEFAULT FALSE;
