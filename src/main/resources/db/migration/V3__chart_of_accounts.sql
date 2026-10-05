-- Codes must match dev.parakhatdin.ledger.ledger.Accounts
INSERT INTO accounts (code, name) VALUES
    ('assets:stripe_cash',   'Stripe balance (net of fees)'),
    ('revenue:sales',        'Sales revenue (gross)'),
    ('expenses:stripe_fees', 'Stripe processing fees');
