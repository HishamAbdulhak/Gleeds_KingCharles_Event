-- One Admin from ADMIN_EMAIL / ADMIN_PASSWORD (Flyway placeholders, see application.yml).
-- Hashed here by pgcrypto; Spring's BCryptPasswordEncoder verifies the $2a$ hash. Cost 10 = Spring's default.
-- ponytail: password is a plain '…' literal, so a ' in ADMIN_PASSWORD breaks the migration; escape it ('') if that ever matters.
CREATE EXTENSION IF NOT EXISTS pgcrypto;
INSERT INTO admin_user (email, password_hash)
VALUES ('${admin_email}', crypt('${admin_password}', gen_salt('bf', 10)));
