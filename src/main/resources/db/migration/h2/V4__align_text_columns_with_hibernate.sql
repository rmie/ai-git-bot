-- Align H2 text columns with the Hibernate/JPA TEXT mapping used by the entities.
-- Existing H2 databases created these columns as CLOB, while Hibernate validates them as TEXT/VARCHAR.

ALTER TABLE bots
    ALTER COLUMN last_error_message SET DATA TYPE TEXT;

ALTER TABLE conversation_messages
    ALTER COLUMN content SET DATA TYPE TEXT;

ALTER TABLE system_prompts
    ALTER COLUMN review_system_prompt SET DATA TYPE TEXT;

ALTER TABLE system_prompts
    ALTER COLUMN issue_agent_system_prompt SET DATA TYPE TEXT;

