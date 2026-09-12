ALTER TABLE member_email_outbox DROP CONSTRAINT member_email_outbox_state_check;
ALTER TABLE member_email_outbox ADD CONSTRAINT member_email_outbox_state_check
    CHECK (state IN ('PENDING', 'SENDING', 'SENT', 'FAILED', 'UNKNOWN', 'CANCELED',
                     'DELIVERED', 'DELAYED', 'BOUNCED', 'COMPLAINED', 'SUPPRESSED'));
ALTER TABLE member_email_outbox ADD COLUMN provider varchar(20);
ALTER TABLE member_email_outbox ADD COLUMN provider_message_id uuid UNIQUE;
ALTER TABLE member_email_outbox ADD COLUMN provider_event_at timestamptz;
ALTER TABLE member_email_settings ADD COLUMN delivery_issue varchar(20)
    CHECK (delivery_issue IN ('BOUNCED', 'COMPLAINED', 'SUPPRESSED'));
