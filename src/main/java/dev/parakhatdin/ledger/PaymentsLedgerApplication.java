package dev.parakhatdin.ledger;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
public class PaymentsLedgerApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentsLedgerApplication.class, args);
    }

    /** Injected instead of calling Instant.now() directly, so time can be fixed in tests. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
