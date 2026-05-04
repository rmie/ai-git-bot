ALTER TABLE git_integrations
    ADD COLUMN IF NOT EXISTS git_lab_post_review_action VARCHAR(32) NOT NULL DEFAULT 'NONE';
