package dev.parakhatdin.ledger;

import org.springframework.boot.SpringApplication;

public class TestPaymentsLedgerApplication {

	public static void main(String[] args) {
		SpringApplication.from(PaymentsLedgerApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
