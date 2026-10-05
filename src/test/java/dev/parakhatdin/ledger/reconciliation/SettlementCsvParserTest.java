package dev.parakhatdin.ledger.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.parakhatdin.ledger.money.Money;
import org.junit.jupiter.api.Test;

class SettlementCsvParserTest {

    @Test
    void parsesRowsInAnyColumnOrderAndIgnoresExtraColumns() {
        String csv = """
                currency,payout_id,net,reference
                eur,po_1,96.80,pi_1
                EUR,po_1,48.50,pi_2
                """;
        assertThat(SettlementCsvParser.parse(csv)).containsExactly(
                new SettlementRow("pi_1", Money.of(9680, "EUR")),
                new SettlementRow("pi_2", Money.of(4850, "EUR")));
    }

    @Test
    void reportsTheLineNumberOfABadRow() {
        String csv = """
                reference,net,currency
                pi_1,96.80,EUR
                pi_2,abc,EUR
                """;
        assertThatThrownBy(() -> SettlementCsvParser.parse(csv))
                .isInstanceOf(InvalidReportException.class)
                .hasMessageStartingWith("Line 3");
    }

    @Test
    void requiresTheNeededColumns() {
        assertThatThrownBy(() -> SettlementCsvParser.parse("reference,amount\npi_1,1.00"))
                .isInstanceOf(InvalidReportException.class)
                .hasMessage("Missing column: net");
    }

    @Test
    void rejectsAnEmptyReport() {
        assertThatThrownBy(() -> SettlementCsvParser.parse("  \n")).isInstanceOf(InvalidReportException.class);
    }
}
