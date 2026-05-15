-- Step 7.1: persist the latest parsed implementation plan directly on the
-- agent session so that PR-body and follow-up comment generation no longer
-- needs to re-parse the entire conversation history.
ALTER TABLE agent_sessions ADD COLUMN IF NOT EXISTS last_plan_summary VARCHAR(2048);
ALTER TABLE agent_sessions ADD COLUMN IF NOT EXISTS last_plan_json    CLOB;
ALTER TABLE agent_sessions ADD COLUMN IF NOT EXISTS last_plan_at      TIMESTAMP;

