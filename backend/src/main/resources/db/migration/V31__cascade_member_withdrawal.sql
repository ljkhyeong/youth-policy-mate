ALTER TABLE saved_policies DROP CONSTRAINT saved_policies_member_id_fkey,
    ADD CONSTRAINT saved_policies_member_id_fkey FOREIGN KEY (member_id) REFERENCES members(id) ON DELETE CASCADE;
ALTER TABLE policy_reminders DROP CONSTRAINT policy_reminders_member_id_fkey,
    ADD CONSTRAINT policy_reminders_member_id_fkey FOREIGN KEY (member_id) REFERENCES members(id) ON DELETE CASCADE;
ALTER TABLE member_notifications DROP CONSTRAINT member_notifications_member_id_fkey,
    ADD CONSTRAINT member_notifications_member_id_fkey FOREIGN KEY (member_id) REFERENCES members(id) ON DELETE CASCADE;
