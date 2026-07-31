-- Minimal demo seed: just enough to log in and exercise the UI.
-- Four customers, each one a distinct test persona. Keep this small — feature
-- verticals add their own fixtures, they do not grow this file.

INSERT INTO customers (id, name, status) VALUES
    (1, 'Alice Nguyen',    'active'),   -- healthy happy path
    (2, 'Brian Kowalski',  'active'),   -- account carries a hold
    (3, 'Chloe Ramos',     'active'),   -- almost no money
    (4, 'Dev Patel',       'active');   -- funded well enough for large/risky transfers

INSERT INTO accounts (id, customer_id, balance, hold, daily_withdrawn, status) VALUES
    (1, 1,  5000.00, FALSE, 0.00, 'active'),
    (2, 2,  3200.00, TRUE,  0.00, 'active'),
    (3, 3,    50.00, FALSE, 0.00, 'active'),
    (4, 4, 25000.00, FALSE, 0.00, 'active');

INSERT INTO recipients (id, customer_id, name, enrolled) VALUES
    (1, 1, 'Riverside Property Mgmt', TRUE),
    (2, 1, 'Jordan Blake',            TRUE),
    (3, 1, 'Sam Whitfield',           FALSE),  -- un-enrolled on purpose
    (4, 4, 'Northgate Holdings',      TRUE);

INSERT INTO transactions (id, account_id, type, amount, status, created_at) VALUES
    (1, 1, 'transfer',  250.00, 'completed', TIMESTAMP '2026-07-01 09:14:00'),
    (2, 1, 'withdraw',  100.00, 'completed', TIMESTAMP '2026-07-03 17:42:00'),
    (3, 1, 'billpay',    75.50, 'pending',   TIMESTAMP '2026-07-08 08:05:00'),
    (4, 1, 'transfer',  900.00, 'failed',    TIMESTAMP '2026-07-11 12:30:00');

INSERT INTO scheduled_payments (id, account_id, payee, amount, fire_date, status) VALUES
    (1, 1, 'City Power & Light', 120.00, DATE '2026-08-15', 'scheduled');

-- approvals starts empty: rows are created by the transfer flow.

-- Explicit ids above leave the identity sequences at 1; push them past the seed
-- so application inserts do not collide with seeded rows.
ALTER TABLE customers          ALTER COLUMN id RESTART WITH 100;
ALTER TABLE accounts           ALTER COLUMN id RESTART WITH 100;
ALTER TABLE recipients         ALTER COLUMN id RESTART WITH 100;
ALTER TABLE transactions       ALTER COLUMN id RESTART WITH 100;
ALTER TABLE scheduled_payments ALTER COLUMN id RESTART WITH 100;
ALTER TABLE approvals          ALTER COLUMN id RESTART WITH 100;
