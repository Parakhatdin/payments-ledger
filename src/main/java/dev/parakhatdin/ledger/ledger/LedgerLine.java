package dev.parakhatdin.ledger.ledger;

import dev.parakhatdin.ledger.money.Money;
import java.util.Objects;

/** One leg of a posting before it is saved: which account, which side, how much. */
public record LedgerLine(String accountCode, Direction direction, Money amount) {

    public LedgerLine {
        Objects.requireNonNull(accountCode, "accountCode");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(amount, "amount");
    }

    public static LedgerLine debit(String accountCode, Money amount) {
        return new LedgerLine(accountCode, Direction.DEBIT, amount);
    }

    public static LedgerLine credit(String accountCode, Money amount) {
        return new LedgerLine(accountCode, Direction.CREDIT, amount);
    }
}
