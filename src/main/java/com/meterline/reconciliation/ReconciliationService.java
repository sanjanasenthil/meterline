package com.meterline.reconciliation;

import com.meterline.billing.BillingPlanCatalog;
import com.meterline.pricing.BillingPeriod;
import com.meterline.pricing.PriceResult;
import com.meterline.pricing.PricedLine;
import com.meterline.pricing.PricingEngine;
import com.meterline.pricing.PricingPlan;
import com.meterline.pricing.UsageWindow;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class ReconciliationService {

    private final ReconciliationRepository repository;
    private final BillingPlanCatalog billingPlanCatalog;
    private final PricingEngine pricingEngine;
    private final MeterRegistry meterRegistry;

    public ReconciliationService(ReconciliationRepository repository, BillingPlanCatalog billingPlanCatalog, MeterRegistry meterRegistry) {
        this.repository = repository;
        this.billingPlanCatalog = billingPlanCatalog;
        this.pricingEngine = new PricingEngine();
        this.meterRegistry = meterRegistry;
    }

    public ReconciliationReport reconcile(BillingPeriod period) {
        Timer.Sample sample = Timer.start(meterRegistry);
        meterRegistry.counter("meterline.reconciliation.runs").increment();
        try {
        Map<LineKey, ExpectedLine> expectedLines = expectedLines(period);
        List<InvoiceHeaderForReconciliation> invoices = repository.issuedInvoicesFor(period);
        List<InvoiceLineForReconciliation> actualLines = repository.issuedInvoiceLinesFor(period);
        Map<LineKey, InvoiceLineForReconciliation> actualByKey = new LinkedHashMap<>();
        List<ReconciliationDifference> differences = new ArrayList<>();

        for (InvoiceLineForReconciliation actualLine : actualLines) {
            validateLineShape(period, actualLine, differences);
            LineKey key = new LineKey(actualLine.invoiceCustomerId(), actualLine.meterId());
            actualByKey.put(key, actualLine);
        }

        for (Map.Entry<LineKey, ExpectedLine> entry : expectedLines.entrySet()) {
            LineKey key = entry.getKey();
            ExpectedLine expected = entry.getValue();
            InvoiceLineForReconciliation actual = actualByKey.remove(key);

            if (actual == null) {
                differences.add(newDifference(
                        ReconciliationMismatchType.MISSING_INVOICE_LINE,
                        expected.customerId(),
                        period,
                        null,
                        expected.meterId(),
                        null,
                        expected.amountCents(),
                        0));
                continue;
            }

            if (actual.amountCents() != expected.amountCents()) {
                differences.add(newDifference(
                        ReconciliationMismatchType.LINE_AMOUNT_MISMATCH,
                        expected.customerId(),
                        period,
                        actual.invoiceId(),
                        expected.meterId(),
                        actual.lineId(),
                        expected.amountCents(),
                        actual.amountCents()));
            }
        }

        for (InvoiceLineForReconciliation unexpected : actualByKey.values()) {
            differences.add(newDifference(
                    ReconciliationMismatchType.UNEXPECTED_INVOICE_LINE,
                    unexpected.invoiceCustomerId(),
                    period,
                    unexpected.invoiceId(),
                    unexpected.meterId(),
                    unexpected.lineId(),
                    0,
                    unexpected.amountCents()));
        }

        long expectedTotal = expectedLines.values().stream().mapToLong(ExpectedLine::amountCents).sum();
        long actualLineTotal = actualLines.stream().mapToLong(InvoiceLineForReconciliation::amountCents).sum();
        long actualInvoiceTotal = invoices.stream().mapToLong(InvoiceHeaderForReconciliation::totalCents).sum();

        if (expectedTotal != actualInvoiceTotal) {
            differences.add(newDifference(
                    ReconciliationMismatchType.TOTAL_AMOUNT_MISMATCH,
                    null,
                    period,
                    null,
                    null,
                    null,
                    expectedTotal,
                    actualInvoiceTotal));
        }
        if (actualLineTotal != actualInvoiceTotal) {
            differences.add(newDifference(
                    ReconciliationMismatchType.TOTAL_AMOUNT_MISMATCH,
                    null,
                    period,
                    null,
                    null,
                    null,
                    actualLineTotal,
                    actualInvoiceTotal));
        }

        long differenceCents = actualInvoiceTotal - expectedTotal;
        ReconciliationStatus status = differences.isEmpty()
                ? ReconciliationStatus.MATCH
                : ReconciliationStatus.MISMATCH;

        ReconciliationReport report = new ReconciliationReport(
                period.startInclusive(),
                period.endExclusive(),
                status,
                expectedTotal,
                actualInvoiceTotal,
                differenceCents,
                differences);
        if (!report.matches()) {
            meterRegistry.counter("meterline.reconciliation.mismatches").increment(differences.size());
        }
        return report;
        } catch (RuntimeException exception) {
            meterRegistry.counter("meterline.reconciliation.errors").increment();
            throw exception;
        } finally {
            sample.stop(meterRegistry.timer("meterline.reconciliation.duration"));
        }
    }

    public ReconciliationReport reconcileOrThrow(BillingPeriod period) {
        ReconciliationReport report = reconcile(period);
        if (!report.matches()) {
            throw new ReconciliationFailedException(report);
        }
        return report;
    }

    private Map<LineKey, ExpectedLine> expectedLines(BillingPeriod period) {
        Map<LineKey, ExpectedLine> expected = new LinkedHashMap<>();
        for (RawUsageForReconciliation usage : repository.rawUsageFor(period)) {
            PricingPlan plan = billingPlanCatalog.planFor(usage.customerId(), usage.meterId(), period.startInclusive());
            long amountCents = priceSignedQuantity(period, plan, usage.quantityUnits());
            LineKey key = new LineKey(usage.customerId(), usage.meterId());
            expected.put(key, new ExpectedLine(usage.customerId(), usage.meterId(), amountCents));
        }
        return expected;
    }

    private long priceSignedQuantity(BillingPeriod period, PricingPlan plan, long quantityUnits) {
        if (quantityUnits == 0) {
            return 0;
        }
        long sign = quantityUnits < 0 ? -1 : 1;
        PriceResult price = pricingEngine.price(
                List.of(new UsageWindow(period.startInclusive(), period.endExclusive(), Math.abs(quantityUnits))),
                plan,
                period);
        PricedLine pricedLine = price.lines().getFirst();
        return pricedLine.amount().cents() * sign;
    }

    private static void validateLineShape(
            BillingPeriod period,
            InvoiceLineForReconciliation line,
            List<ReconciliationDifference> differences
    ) {
        if (!Objects.equals(line.invoiceCustomerId(), line.lineCustomerId())) {
            differences.add(newDifference(
                    ReconciliationMismatchType.CUSTOMER_MISMATCH,
                    line.invoiceCustomerId(),
                    period,
                    line.invoiceId(),
                    line.meterId(),
                    line.lineId(),
                    0,
                    line.amountCents()));
        }

        if (!line.invoicePeriodStart().equals(period.startInclusive())
                || !line.invoicePeriodEnd().equals(period.endExclusive())
                || !line.linePeriodStart().equals(period.startInclusive())
                || !line.linePeriodEnd().equals(period.endExclusive())) {
            differences.add(newDifference(
                    ReconciliationMismatchType.PERIOD_MISMATCH,
                    line.invoiceCustomerId(),
                    period,
                    line.invoiceId(),
                    line.meterId(),
                    line.lineId(),
                    0,
                    line.amountCents()));
        }
    }

    private static ReconciliationDifference newDifference(
            ReconciliationMismatchType type,
            String customerId,
            BillingPeriod period,
            String invoiceId,
            String meterId,
            String lineId,
            long expectedAmountCents,
            long actualAmountCents
    ) {
        return new ReconciliationDifference(
                type,
                customerId,
                period.startInclusive(),
                period.endExclusive(),
                invoiceId,
                meterId,
                lineId,
                expectedAmountCents,
                actualAmountCents,
                actualAmountCents - expectedAmountCents,
                "FAIL");
    }

    private record LineKey(String customerId, String meterId) {
    }

    private record ExpectedLine(String customerId, String meterId, long amountCents) {
    }
}
