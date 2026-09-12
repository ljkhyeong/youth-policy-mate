CREATE INDEX member_email_outbox_history ON member_email_outbox (created_at DESC, id DESC);
