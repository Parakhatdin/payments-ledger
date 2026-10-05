package dev.parakhatdin.ledger.ledger;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** One debit or credit leg of a {@link LedgerTransaction}. Amount is positive minor units. */
@Entity
@Table(name = "ledger_entries")
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id")
    private LedgerTransaction transaction;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id")
    private Account account;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 6)
    private Direction direction;

    @Column(nullable = false)
    private long amount;

    @Column(nullable = false, length = 3)
    private String currency;

    protected LedgerEntry() {} // for JPA

    LedgerEntry(LedgerTransaction transaction, Account account, Direction direction, long amount, String currency) {
        this.transaction = transaction;
        this.account = account;
        this.direction = direction;
        this.amount = amount;
        this.currency = currency;
    }

    public Long getId() {
        return id;
    }

    public Account getAccount() {
        return account;
    }

    public Direction getDirection() {
        return direction;
    }

    public long getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }
}
