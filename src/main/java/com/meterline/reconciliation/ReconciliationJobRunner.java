package com.meterline.reconciliation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meterline.pricing.BillingPeriod;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class ReconciliationJobRunner implements CommandLineRunner {

    private final ReconciliationService reconciliationService;
    private final ObjectMapper objectMapper;

    public ReconciliationJobRunner(ReconciliationService reconciliationService, ObjectMapper objectMapper) {
        this.reconciliationService = reconciliationService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(String... args) throws JsonProcessingException {
        if (args.length < 3 || !"reconcile:run".equals(args[0])) {
            return;
        }

        BillingPeriod period = new BillingPeriod(Instant.parse(args[1]), Instant.parse(args[2]));
        ReconciliationReport report = reconciliationService.reconcile(period);
        if (args.length >= 4 && "--json".equals(args[3])) {
            System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(report));
        } else {
            System.out.print(report.toHumanReadable());
        }

        if (!report.matches()) {
            throw new ReconciliationFailedException(report);
        }
    }
}
