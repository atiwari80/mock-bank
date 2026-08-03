-- Expands the seed so the POC spec's own test cases are actually reachable.
-- V2 stayed deliberately minimal (enough to log in and demo). Three scenarios
-- in the spec cannot be exercised against it at all:
--   * CUSTOMER_INACTIVE   — every seeded customer was 'active'
--   * date filtering      — only 4 transactions, all in one month
--   * large-history paging — same problem
--   * EXCEEDS_DAILY_LIMIT in a single call — every daily_withdrawn was 0.00
-- Ids stay below 100 so the identity sequences set in V2 are untouched.

-- ---------------------------------------------------------------------------
-- New personas
-- ---------------------------------------------------------------------------
INSERT INTO customers (id, name, status) VALUES
    (5, 'Frank Osei',  'frozen'),   -- the only frozen customer: CUSTOMER_INACTIVE
    (6, 'Priya Shah',  'active');   -- part-way through the day's withdrawal cap

INSERT INTO accounts (id, customer_id, balance, hold, daily_withdrawn, status) VALUES
    (5, 5, 4000.00, FALSE,    0.00, 'active'),
    -- 1,800 of the 2,000 daily cap already used, so ONE call can trip the daily
    -- limit without the per-transaction limit also failing:
    --   withdraw 200 -> 2000.00 exactly -> allowed (boundary is inclusive)
    --   withdraw 500 -> 2300.00         -> EXCEEDS_DAILY_LIMIT
    (6, 6, 6000.00, FALSE, 1800.00, 'active');

-- Frank is frozen but still needs a valid target, so a transfer attempt fails
-- on CUSTOMER_INACTIVE and not on RECIPIENT_NOT_ENROLLED.
INSERT INTO recipients (id, customer_id, name, enrolled) VALUES
    (5, 5, 'Lakeside Realty',  TRUE),
    (6, 4, 'Halcyon Partners', FALSE);  -- un-enrolled target for the funded customer

-- ---------------------------------------------------------------------------
-- Deep transaction history for account 1 (Alice), May–Aug 2026.
-- 30 rows on top of the 4 from V2 = 34 total: enough for several pages and for
-- from/to date filtering to return meaningfully different slices.
-- ---------------------------------------------------------------------------
INSERT INTO transactions (id, account_id, type, amount, status, created_at) VALUES
    ( 5, 1, 'transfer', 120.00, 'completed', TIMESTAMP '2026-05-04 10:12:00'),
    ( 6, 1, 'withdraw', 200.00, 'completed', TIMESTAMP '2026-05-07 14:48:00'),
    ( 7, 1, 'billpay',   65.25, 'completed', TIMESTAMP '2026-05-09 09:03:00'),
    ( 8, 1, 'transfer', 340.00, 'completed', TIMESTAMP '2026-05-12 16:27:00'),
    ( 9, 1, 'withdraw', 100.00, 'failed',    TIMESTAMP '2026-05-15 11:35:00'),
    (10, 1, 'billpay',   45.00, 'completed', TIMESTAMP '2026-05-18 08:20:00'),
    (11, 1, 'transfer', 275.50, 'completed', TIMESTAMP '2026-05-21 13:09:00'),
    (12, 1, 'withdraw',  60.00, 'completed', TIMESTAMP '2026-05-24 17:55:00'),
    (13, 1, 'billpay',  130.00, 'pending',   TIMESTAMP '2026-05-28 07:41:00'),
    (14, 1, 'transfer', 410.00, 'completed', TIMESTAMP '2026-06-01 12:02:00'),
    (15, 1, 'withdraw', 250.00, 'completed', TIMESTAMP '2026-06-04 15:18:00'),
    (16, 1, 'billpay',   88.40, 'completed', TIMESTAMP '2026-06-07 10:44:00'),
    (17, 1, 'transfer',  95.00, 'failed',    TIMESTAMP '2026-06-10 18:31:00'),
    (18, 1, 'withdraw', 300.00, 'completed', TIMESTAMP '2026-06-13 09:57:00'),
    (19, 1, 'billpay',   52.75, 'completed', TIMESTAMP '2026-06-16 14:06:00'),
    (20, 1, 'transfer', 180.00, 'completed', TIMESTAMP '2026-06-19 11:23:00'),
    (21, 1, 'withdraw', 140.00, 'completed', TIMESTAMP '2026-06-22 16:49:00'),
    (22, 1, 'billpay',  210.00, 'failed',    TIMESTAMP '2026-06-25 08:14:00'),
    (23, 1, 'transfer', 500.00, 'completed', TIMESTAMP '2026-06-28 13:38:00'),
    (24, 1, 'withdraw',  80.00, 'completed', TIMESTAMP '2026-07-02 17:11:00'),
    (25, 1, 'billpay',   99.99, 'completed', TIMESTAMP '2026-07-05 09:26:00'),
    (26, 1, 'transfer', 640.00, 'completed', TIMESTAMP '2026-07-09 12:52:00'),
    (27, 1, 'withdraw', 220.00, 'completed', TIMESTAMP '2026-07-13 15:04:00'),
    (28, 1, 'billpay',   47.30, 'pending',   TIMESTAMP '2026-07-17 10:33:00'),
    (29, 1, 'transfer', 155.00, 'completed', TIMESTAMP '2026-07-20 18:07:00'),
    (30, 1, 'withdraw', 190.00, 'completed', TIMESTAMP '2026-07-23 11:45:00'),
    (31, 1, 'billpay',   73.60, 'completed', TIMESTAMP '2026-07-26 07:58:00'),
    (32, 1, 'transfer', 310.00, 'failed',    TIMESTAMP '2026-07-29 14:22:00'),
    (33, 1, 'withdraw', 125.00, 'completed', TIMESTAMP '2026-07-31 16:40:00'),
    (34, 1, 'billpay',   68.00, 'completed', TIMESTAMP '2026-08-01 08:49:00');

-- ---------------------------------------------------------------------------
-- Scheduled payments in every state, so cancellation has something to refuse.
-- V2 seeded id 1 as 'scheduled' (the cancellable one).
-- ---------------------------------------------------------------------------
INSERT INTO scheduled_payments (id, account_id, payee, amount, fire_date, status) VALUES
    (2, 1, 'Metro Internet',  89.00, DATE '2026-08-05', 'pending'),
    (3, 1, 'Cascade Water',   41.50, DATE '2026-07-15', 'paid'),
    (4, 1, 'Gorge Gas',      112.00, DATE '2026-07-20', 'failed');

-- approvals stays empty: rows are created by the transfer flow.
