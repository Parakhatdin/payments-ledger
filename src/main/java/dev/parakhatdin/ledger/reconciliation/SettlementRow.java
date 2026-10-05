package dev.parakhatdin.ledger.reconciliation;

import dev.parakhatdin.ledger.money.Money;

/** One line of the processor's settlement report: which payment, and how much net cash it settled. */
public record SettlementRow(String reference, Money net) {}
