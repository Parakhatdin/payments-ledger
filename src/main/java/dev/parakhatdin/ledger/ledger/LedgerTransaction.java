package dev.parakhatdin.ledger.ledger;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One balanced posting. Its {@code idempotencyKey} is UNIQUE in the database: the same business event
 * (e.g. Stripe payment {@code pi_123}) can be booked at most once, even under concurrent retries.
 */
@Entity
@Table(name = "ledger_transactions")
public class LedgerTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    /** External id used by reconciliation, e.g. the Stripe payment intent id. */
    private String reference;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "posted_at", nullable = false)
    private Instant postedAt;

    @OneToMany(mappedBy = "transaction", cascade = CascadeType.PERSIST)
    private List<LedgerEntry> entries = new ArrayList<>();

    protected LedgerTransaction() {} // for JPA

    LedgerTransaction(String idempotencyKey, String reference, String description, String currency, Instant postedAt) {
        this.idempotencyKey = idempotencyKey;
        this.reference = reference;
        this.description = description;
        this.currency = currency;
        this.postedAt = postedAt;
    }

    void addEntry(Account account, Direction direction, long amount) {
        entries.add(new LedgerEntry(this, account, direction, amount, currency));
    }

    public Long getId() {
        return id;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getReference() {
        return reference;
    }

    public String getDescription() {
        return description;
    }

    public String getCurrency() {
        return currency;
    }

    public Instant getPostedAt() {
        return postedAt;
    }

    public List<LedgerEntry> getEntries() {
        return Collections.unmodifiableList(entries);
    }
}
