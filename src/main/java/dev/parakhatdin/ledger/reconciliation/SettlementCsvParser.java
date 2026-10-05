package dev.parakhatdin.ledger.reconciliation;

import dev.parakhatdin.ledger.money.Money;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Parses a settlement report CSV with a header row containing at least {@code reference,net,currency}.
 * Column order does not matter and extra columns are ignored, so Stripe's itemized payout reconciliation
 * export works after renaming its columns. Amounts are decimals ({@code "96.80"}), parsed exactly.
 *
 * <p>Deliberately simple: no quoted fields. A production version would use a CSV library.
 */
public final class SettlementCsvParser {

    private SettlementCsvParser() {}

    public static List<SettlementRow> parse(String csv) {
        List<String> lines = csv.lines().map(String::strip).filter(l -> !l.isEmpty()).toList();
        if (lines.isEmpty()) {
            throw new InvalidReportException("Report is empty");
        }

        List<String> header = Arrays.stream(lines.getFirst().split(","))
                .map(h -> h.strip().toLowerCase(Locale.ROOT))
                .toList();
        int reference = column(header, "reference");
        int net = column(header, "net");
        int currency = column(header, "currency");

        List<SettlementRow> rows = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            String[] cells = lines.get(i).split(",", -1);
            int lineNo = i + 1;
            try {
                rows.add(new SettlementRow(cells[reference].strip(),
                        Money.parse(cells[net], cells[currency].strip())));
            } catch (ArrayIndexOutOfBoundsException | IllegalArgumentException | ArithmeticException e) {
                throw new InvalidReportException("Line " + lineNo + ": " + e.getMessage());
            }
        }
        return rows;
    }

    private static int column(List<String> header, String name) {
        int index = header.indexOf(name);
        if (index < 0) {
            throw new InvalidReportException("Missing column: " + name);
        }
        return index;
    }
}
