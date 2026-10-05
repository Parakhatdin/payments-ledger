package dev.parakhatdin.ledger.ledger;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LedgerTransactionRepository extends JpaRepository<LedgerTransaction, Long> {

    @EntityGraph(attributePaths = {"entries", "entries.account"})
    Optional<LedgerTransaction> findByIdempotencyKey(String idempotencyKey);

    /**
     * Net amount booked on one account per external reference: debits minus credits. Computed by the
     * database in a single grouped query, so reconciliation does not load every entry into memory.
     */
    @Query("""
            select t.reference as reference, t.currency as currency,
                   sum(case when e.direction = dev.parakhatdin.ledger.ledger.Direction.DEBIT
                            then e.amount else -e.amount end) as netAmount
            from LedgerEntry e join e.transaction t
            where e.account.code = :accountCode and t.reference is not null
            group by t.reference, t.currency
            """)
    List<NetByReference> netByReference(@Param("accountCode") String accountCode);

    /** Projection for {@link #netByReference(String)}. */
    interface NetByReference {
        String getReference();

        String getCurrency();

        long getNetAmount();
    }
}
