package dev.parakhatdin.ledger.ledger;

import static dev.parakhatdin.ledger.ledger.LedgerLine.credit;
import static dev.parakhatdin.ledger.ledger.LedgerLine.debit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.parakhatdin.ledger.IntegrationTest;
import dev.parakhatdin.ledger.money.Money;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class LedgerServiceTest extends IntegrationTest {

    @Autowired
    LedgerService ledger;

    private static List<LedgerLine> payment(long gross, long fee) {
        return List.of(
                debit(Accounts.STRIPE_CASH, Money.of(gross - fee, "EUR")),
                debit(Accounts.STRIPE_FEES, Money.of(fee, "EUR")),
                credit(Accounts.SALES_REVENUE, Money.of(gross, "EUR")));
    }

    private long count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
    }

    @Test
    void postsABalancedTransactionWithAllItsLegs() {
        PostedTransaction posted = ledger.post("stripe:pi:pi_1", "Payment pi_1", "pi_1", payment(10_000, 320));

        assertThat(posted.duplicate()).isFalse();
        assertThat(posted.reference()).isEqualTo("pi_1");
        assertThat(posted.entries()).containsExactlyInAnyOrder(
                new PostedTransaction.Entry(Accounts.STRIPE_CASH, Direction.DEBIT, 9_680),
                new PostedTransaction.Entry(Accounts.STRIPE_FEES, Direction.DEBIT, 320),
                new PostedTransaction.Entry(Accounts.SALES_REVENUE, Direction.CREDIT, 10_000));
        assertThat(count("ledger_entries")).isEqualTo(3);
    }

    @Test
    void theSameIdempotencyKeyIsBookedOnlyOnce() {
        PostedTransaction first = ledger.post("stripe:pi:pi_1", "Payment pi_1", "pi_1", payment(10_000, 320));
        PostedTransaction again = ledger.post("stripe:pi:pi_1", "Payment pi_1", "pi_1", payment(10_000, 320));

        assertThat(again.duplicate()).isTrue();
        assertThat(again.id()).isEqualTo(first.id());
        assertThat(count("ledger_transactions")).isEqualTo(1);
        assertThat(count("ledger_entries")).isEqualTo(3);
    }

    @Test
    void concurrentRetriesOfTheSameEventStillBookOnce() throws Exception {
        int threads = 8;
        CountDownLatch start = new CountDownLatch(1);
        Callable<PostedTransaction> task = () -> {
            start.await(); // release all threads at the same moment to force the race
            return ledger.post("stripe:pi:pi_race", "Payment pi_race", "pi_race", payment(5_000, 175));
        };

        List<PostedTransaction> results = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            List<Future<PostedTransaction>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(task));
            }
            start.countDown();
            for (Future<PostedTransaction> f : futures) {
                results.add(f.get());
            }
        }

        assertThat(results).extracting(PostedTransaction::id).containsOnly(results.getFirst().id());
        assertThat(results).filteredOn(r -> !r.duplicate()).hasSize(1); // exactly one thread really wrote
        assertThat(count("ledger_transactions")).isEqualTo(1);
        assertThat(count("ledger_entries")).isEqualTo(3);
    }

    @Test
    void anUnbalancedPostingWritesNothing() {
        List<LedgerLine> unbalanced = List.of(
                debit(Accounts.STRIPE_CASH, Money.of(100, "EUR")),
                credit(Accounts.SALES_REVENUE, Money.of(99, "EUR")));

        assertThatThrownBy(() -> ledger.post("k", "bad", null, unbalanced))
                .isInstanceOf(UnbalancedTransactionException.class);
        assertThat(count("ledger_transactions")).isZero();
    }

    @Test
    void anUnknownAccountRollsBackTheWholePosting() {
        List<LedgerLine> lines = List.of(
                debit(Accounts.STRIPE_CASH, Money.of(100, "EUR")),
                credit("revenue:does_not_exist", Money.of(100, "EUR")));

        assertThatThrownBy(() -> ledger.post("k", "bad", null, lines)).isInstanceOf(UnknownAccountException.class);
        assertThat(count("ledger_transactions")).isZero();
        assertThat(count("ledger_entries")).isZero();
    }
}
