-- Step 2 of the bot tool configuration rollout (see V11 for the tables):
-- attach every bot to exactly one tool configuration, defaulting existing
-- bots to the auto-generated "Default" configuration so that the new
-- mandatory association does not break upgrades.
--
-- Sequence (idempotent, order matters):
--   1) add the FK column to bots, nullable for backfill
--   2) ensure the default tool configuration row exists
--   3) seed the default configuration with every statically known built-in
--      tool (file/context/repository) so that bots immediately have a
--      usable whitelist after migration
--   4) backfill bots.bot_tool_configuration_id to the default configuration
--   5) add the foreign key constraint
--   6) enforce NOT NULL once every row is backfilled
--
-- Validation tools (mvn, gradle, npm, …) are NOT seeded here because they
-- are sourced from agent.validation.available-tools at runtime. Any
-- additional built-in or validation tools shipped in later releases must
-- be enabled manually by an admin via System settings → Tool configurations;
-- the application does not auto-extend the Default at runtime.

ALTER TABLE bots ADD COLUMN IF NOT EXISTS bot_tool_configuration_id BIGINT;

INSERT INTO bot_tool_configurations (name, default_entry, created_at, updated_at)
SELECT 'Default', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM bot_tool_configurations WHERE default_entry = TRUE
);

INSERT INTO bot_tool_selections (configuration_id, tool_name, tool_kind)
SELECT c.id, v.tool_name, v.tool_kind
FROM bot_tool_configurations c
CROSS JOIN (VALUES
    -- file-mutation tools (coding only)
    ('write-file',      'FILE'),
    ('patch-file',      'FILE'),
    ('mkdir',           'FILE'),
    ('delete-file',     'FILE'),
    -- repository exploration tools (shared by coding + writer)
    ('branch-switcher', 'CONTEXT'),
    ('rg',              'CONTEXT'),
    ('find',            'CONTEXT'),
    ('cat',             'CONTEXT'),
    ('git-log',         'CONTEXT'),
    ('git-blame',       'CONTEXT'),
    ('tree',            'CONTEXT'),
    -- silent context aliases (never advertised to the LLM but classified)
    ('ripgrep',         'CONTEXT'),
    ('grep',            'CONTEXT'),
    -- writer-only repository helpers
    ('get-issue',       'REPOSITORY'),
    ('search-issues',   'REPOSITORY')
) AS v(tool_name, tool_kind)
WHERE c.default_entry = TRUE
  AND NOT EXISTS (
      SELECT 1 FROM bot_tool_selections s
      WHERE s.configuration_id = c.id AND s.tool_name = v.tool_name
  );

UPDATE bots
SET bot_tool_configuration_id = (
    SELECT id FROM bot_tool_configurations WHERE default_entry = TRUE FETCH FIRST 1 ROWS ONLY
)
WHERE bot_tool_configuration_id IS NULL;

ALTER TABLE bots
    ADD CONSTRAINT IF NOT EXISTS fk_bots_tool_configuration
    FOREIGN KEY (bot_tool_configuration_id) REFERENCES bot_tool_configurations(id);

ALTER TABLE bots ALTER COLUMN bot_tool_configuration_id BIGINT NOT NULL;
