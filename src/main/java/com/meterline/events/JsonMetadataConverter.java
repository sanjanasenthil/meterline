package com.meterline.events;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class JsonMetadataConverter {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    public JsonMetadataConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String toJson(Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata == null ? Map.of() : metadata);
        } catch (Exception exception) {
            throw new IllegalArgumentException("metadata must be JSON-serializable", exception);
        }
    }

    public Map<String, Object> fromJson(String metadata) {
        try {
            return objectMapper.readValue(metadata == null ? "{}" : metadata, MAP_TYPE);
        } catch (Exception exception) {
            throw new IllegalArgumentException("stored metadata is not valid JSON", exception);
        }
    }
}
