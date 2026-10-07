package com.meterline.billing;

import com.meterline.pricing.BillingPeriod;
import com.meterline.pricing.PriceResult;
import com.meterline.pricing.PricedLine;
import com.meterline.pricing.PricingEngine;
import com.meterline.pricing.PricingPlan;
import com.meterline.pricing.UsageWindow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class BillingService {

    private final BillingRepository billingRepository;
    private final BillingPlanCatalog billingPlanCatalog;
    private final PricingEngine pricingEngine;

    public BillingService(BillingRepository billingRepository, BillingPlanCatalog billingPlanCatalog) {
        this.billingRepository = billingRepository;
        this.billingPlanCatalog = billingPlanCatalog;
        this.pricingEngine = new PricingEngine();
    }

    @Transactional
    public BillingRunResult runBilling(BillingPeriod period) {
        billingRepository.ensureOpenPeriod(period);
        int aggregatesWritten = billingRepository.aggregateUsage(period);
        List<UsageAggregate> aggregates = billingRepository.aggregatesFor(period);

        Set<String> invoiceIds = new LinkedHashSet<>();
        int usageLinesWritten = 0;
        for (UsageAggregate aggregate : aggregates) {
            String invoiceId = upsertInvoiceFor(aggregate.customerId(), period);
            invoiceIds.add(invoiceId);
            usageLinesWritten += upsertUsageLine(period, invoiceId, aggregate);
        }

        int adjustmentLinesWritten = 0;
        for (LateUsageEvent lateEvent : billingRepository.lateEventsForAdjustment(period)) {
            String invoiceId = upsertInvoiceFor(lateEvent.customerId(), period);
            invoiceIds.add(invoiceId);
            adjustmentLinesWritten += upsertAdjustmentLine(period, invoiceId, lateEvent);
        }

        invoiceIds.forEach(billingRepository::refreshInvoiceTotal);

        return new BillingRunResult(
                aggregatesWritten,
                invoiceIds.size(),
                usageLinesWritten,
                adjustmentLinesWritten,
                new ArrayList<>(invoiceIds));
    }

    @Transactional
    public void closePeriod(BillingPeriod period) {
        billingRepository.closePeriod(period);
    }

    public List<InvoiceView> invoicesFor(BillingPeriod period) {
        return billingRepository.invoicesFor(period);
    }

    private String upsertInvoiceFor(String customerId, BillingPeriod period) {
        String invoiceId = StableBillingIds.invoiceId(customerId, period.startInclusive(), period.endExclusive());
        return billingRepository.upsertInvoice(invoiceId, customerId, period);
    }

    private int upsertUsageLine(BillingPeriod period, String invoiceId, UsageAggregate aggregate) {
        PricingPlan plan = billingPlanCatalog.planFor(aggregate.customerId(), aggregate.meterId(), period.startInclusive());
        SignedPrice signedPrice = priceSignedQuantity(period, plan, aggregate.quantityUnits());
        String lineId = StableBillingIds.usageLineId(invoiceId, aggregate.aggregateId());

        return billingRepository.upsertUsageLine(
                lineId,
                invoiceId,
                aggregate,
                signedPrice.amountCents(),
                signedPrice.rateMillionthsOfCent());
    }

    private int upsertAdjustmentLine(BillingPeriod targetPeriod, String invoiceId, LateUsageEvent lateEvent) {
        PricingPlan plan = billingPlanCatalog.planFor(lateEvent.customerId(), lateEvent.meterId(), targetPeriod.startInclusive());
        SignedPrice signedPrice = priceSignedQuantity(targetPeriod, plan, lateEvent.quantityUnits());
        String lineId = StableBillingIds.adjustmentLineId(invoiceId, lateEvent.eventId());

        return billingRepository.upsertAdjustmentLine(
                lineId,
                invoiceId,
                targetPeriod,
                lateEvent,
                signedPrice.amountCents(),
                signedPrice.rateMillionthsOfCent());
    }

    private SignedPrice priceSignedQuantity(BillingPeriod period, PricingPlan plan, long quantityUnits) {
        if (quantityUnits == 0) {
            return new SignedPrice(0, plan.rateVersions().getFirst().rate().millionthsOfCentPerUnit());
        }
        long sign = quantityUnits < 0 ? -1 : 1;
        PriceResult price = pricingEngine.price(
                List.of(new UsageWindow(period.startInclusive(), period.endExclusive(), Math.abs(quantityUnits))),
                plan,
                period);
        PricedLine pricedLine = price.lines().getFirst();
        return new SignedPrice(price.total().cents() * sign, pricedLine.rate().millionthsOfCentPerUnit());
    }

    private record SignedPrice(long amountCents, long rateMillionthsOfCent) {
    }
}
