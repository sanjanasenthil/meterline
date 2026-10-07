package com.meterline.billing;

import com.meterline.pricing.BillingPeriod;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class BillingJobRunner implements CommandLineRunner {

    private final BillingService billingService;

    public BillingJobRunner(BillingService billingService) {
        this.billingService = billingService;
    }

    @Override
    public void run(String... args) {
        if (args.length != 3 || !"billing:run".equals(args[0])) {
            return;
        }

        BillingPeriod period = new BillingPeriod(Instant.parse(args[1]), Instant.parse(args[2]));
        BillingRunResult result = billingService.runBilling(period);
        System.out.printf(
                "billing run complete: aggregates=%d invoices=%d usageLines=%d adjustmentLines=%d%n",
                result.aggregatesWritten(),
                result.invoicesWritten(),
                result.usageLinesWritten(),
                result.adjustmentLinesWritten());
    }
}
