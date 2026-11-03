package com.chargewise.config;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import org.springframework.boot.jackson.JacksonComponent;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.exc.InvalidFormatException;

/**
 * Reads request timestamps as ISO-8601 date-times with an explicit offset ({@code Z} or {@code ±hh:mm}), seconds
 * optional, normalised to UTC (architecture D2). Jackson's default parser is {@code ISO_INSTANT}, which demands
 * seconds and so rejects valid values such as {@code 2026-11-05T15:30+05:30}. Zone-less values stay a 400.
 */
@JacksonComponent
public class OffsetInstantDeserializer extends ValueDeserializer<Instant> {

    @Override
    public Instant deserialize(JsonParser p, DeserializationContext ctxt) {
        if (!p.hasToken(JsonToken.VALUE_STRING)) {
            return (Instant) ctxt.handleUnexpectedToken(Instant.class, p);
        }
        String text = p.getValueAsString().trim();
        try {
            return OffsetDateTime.parse(text, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant();
        } catch (DateTimeParseException e) {
            throw InvalidFormatException.from(p, "Not an ISO-8601 timestamp with an explicit offset", text,
                    Instant.class);
        }
    }

    @Override
    public Class<?> handledType() {
        return Instant.class;
    }
}
