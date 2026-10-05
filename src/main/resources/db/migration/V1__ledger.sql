-- Chart of accounts
CREATE TABLE accounts (
    id   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL
);

-- One balanced posting. idempotency_key UNIQUE is the hard guarantee against double-booking:
-- it holds even when two workers race on the same Stripe event.
CREATE TABLE ledger_transactions (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    idempotency_key VARCHAR(200) NOT NULL,
    reference       VARCHAR(200),
    description     VARCHAR(500) NOT NULL,
    currency        VARCHAR(3)   NOT NULL,
    posted_at       TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_ledger_transactions_idempotency_key UNIQUE (idempotency_key)
);

CREATE INDEX ix_ledger_transactions_reference ON ledger_transactions (reference);

-- Debit/credit legs. Amounts are positive integer minor units — never floats.
CREATE TABLE ledger_entries (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    transaction_id BIGINT      NOT NULL REFERENCES ledger_transactions (id),
    account_id     BIGINT      NOT NULL REFERENCES accounts (id),
    direction      VARCHAR(6)  NOT NULL CHECK (direction IN ('DEBIT', 'CREDIT')),
    amount         BIGINT      NOT NULL CHECK (amount > 0),
    currency       VARCHAR(3)  NOT NULL
);

CREATE INDEX ix_ledger_entries_transaction ON ledger_entries (transaction_id);
CREATE INDEX ix_ledger_entries_account ON ledger_entries (account_id);
