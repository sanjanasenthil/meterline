package com.meterline.drilldown;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api")
public class DrillDownController {
    private final DrillDownRepository repository;

    public DrillDownController(DrillDownRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/invoices/{invoiceId}")
    public InvoiceDetail invoice(@PathVariable String invoiceId) {
        return repository.invoice(invoiceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "invoice not found"));
    }

    @GetMapping("/invoice-lines/{lineId}/drilldown")
    public InvoiceLineDrillDown line(@PathVariable String lineId) {
        InvoiceLineDetail line = repository.line(lineId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "invoice line not found"));
        List<RawEventDetail> events = repository.eventsFor(line);
        long quantity = events.stream().mapToLong(RawEventDetail::quantityUnits).sum();
        long computedAmount = InvoiceLineDrillDown.amountFor(line.quantityUnits(), line.rateMillionthsOfCent());
        return new InvoiceLineDrillDown(line, events, quantity, computedAmount,
                quantity == line.quantityUnits(), computedAmount == line.amountCents());
    }
}
