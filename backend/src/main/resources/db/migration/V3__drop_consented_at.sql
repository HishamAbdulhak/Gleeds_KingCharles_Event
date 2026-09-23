-- docs/adr/0003: the email identifies a Player; there is no marketing consent to record.
ALTER TABLE player DROP COLUMN consented_at;
