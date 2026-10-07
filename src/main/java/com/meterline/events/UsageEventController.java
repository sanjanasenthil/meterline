package com.meterline.events;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/events")
public class UsageEventController {

    private final UsageEventService usageEventService;

    public UsageEventController(UsageEventService usageEventService) {
        this.usageEventService = usageEventService;
    }

    @PostMapping
    public ResponseEntity<IngestUsageEventResponse> ingest(@Valid @RequestBody UsageEventRequest request) {
        IngestUsageEventResponse response = usageEventService.ingest(request);
        HttpStatus status = response.inserted() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(response);
    }

    @ExceptionHandler(InvalidUsageEventException.class)
    public ResponseEntity<Map<String, String>> invalidUsageEvent(InvalidUsageEventException exception) {
        return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> invalidRequest(MethodArgumentNotValidException exception) {
        return ResponseEntity.badRequest().body(Map.of("error", "request is missing required fields"));
    }
}
