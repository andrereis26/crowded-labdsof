package com.crowded.core.shared.infrastructure.persistence;

import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Converts the domain's structured values to and from the text of {@code jsonb} columns. */
@Component
public class JsonColumnConverter {

  private static final TypeReference<Map<String, Object>> OBJECT = new TypeReference<>() {};

  private final JsonMapper json;

  public JsonColumnConverter(JsonMapper json) {
    this.json = json;
  }

  public String toJson(Map<String, Object> value) {
    return value == null ? null : json.writeValueAsString(value);
  }

  public Map<String, Object> toMap(String value) {
    return value == null ? null : json.readValue(value, OBJECT);
  }
}
