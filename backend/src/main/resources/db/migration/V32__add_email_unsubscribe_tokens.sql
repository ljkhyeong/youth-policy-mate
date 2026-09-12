ALTER TABLE member_email_outbox ADD COLUMN unsubscribe_token_hash varchar(64),
    ADD CONSTRAINT member_email_unsubscribe_kind CHECK (unsubscribe_token_hash IS NULL OR kind = 'POLICY');
CREATE UNIQUE INDEX member_email_unsubscribe_token ON member_email_outbox(unsubscribe_token_hash)
    WHERE unsubscribe_token_hash IS NOT NULL;
