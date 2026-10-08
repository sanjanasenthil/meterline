package com.meterline.billing;

import com.meterline.pricing.BillingPeriod;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class InvoiceIssueJobRunner implements CommandLineRunner {
    private final BillingService billingService;

    public InvoiceIssueJobRunner(BillingService billingService) {
        this.billingService = billingService;
    }

    @Override
    public void run(String... args) {
        if (args.length != 3 || !"billing:issue".equals(args[0])) {
            return;
        }
        BillingPeriod period = new BillingPeriod(Instant.parse(args[1]), Instant.parse(args[2]));
        int issued = billingService.issueInvoices(period);
        System.out.printf("invoice issue complete: invoices=%d%n", issued);
    }
}
