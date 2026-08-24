-- Default admin account for first login.
-- Email: admin@clinic.com / Password: Admin@123
-- (BCrypt hash below corresponds to "Admin@123" - CHANGE THIS IMMEDIATELY after first login in production)
INSERT INTO users (email, password_hash, role, full_name, phone, is_active)
VALUES (
    'admin@clinic.com',
    '$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5L6dNKcXbEFdrTZG0nyE.gxrmuZjK',
    'ADMIN',
    'Clinic Administrator',
    '+910000000000',
    TRUE
);
