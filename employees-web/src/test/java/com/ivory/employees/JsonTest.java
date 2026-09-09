package com.ivory.employees;

import com.ivory.employees.json.Json;
import com.ivory.employees.json.JsonException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonTest {

    @Test
    void writesCompactJsonInInsertionOrder() {
        Map<String, Object> employee = Json.object();
        employee.put("code", 1001L);
        employee.put("name", "Avi Cohen");
        employee.put("active", true);
        employee.put("annualIncome", new BigDecimal("28000.00"));
        employee.put("address", Map.of("city", "Tel Aviv"));
        employee.put("salaries", List.of());
        employee.put("manager", null);

        assertEquals("{\"code\":1001,\"name\":\"Avi Cohen\",\"active\":true,\"annualIncome\":28000.00,"
                        + "\"address\":{\"city\":\"Tel Aviv\"},\"salaries\":[],\"manager\":null}",
                Json.write(employee, false));
    }

    @Test
    void escapesControlCharactersAndQuotes() {
        assertEquals("{\"note\":\"a \\\"quoted\\\"\\nline\\tend\"}",
                Json.write(Map.of("note", "a \"quoted\"\nline\tend"), false));
    }

    @Test
    void prettyOutputIsIndented() {
        String json = Json.write(Map.of("code", 1001L), true);
        assertTrue(json.contains("\n  \"code\": 1001"), json);
    }

    @Test
    void parsesRequestBodies() {
        Map<String, Object> body = Json.parseObject("""
                {"username":"admin","password":"Aa123456!","attempts":3,"remember":false,"tags":["a","b"]}
                """);

        assertEquals("admin", body.get("username"));
        assertEquals("Aa123456!", body.get("password"));
        assertEquals(new BigDecimal("3"), body.get("attempts"));
        assertEquals(Boolean.FALSE, body.get("remember"));
        assertEquals(List.of("a", "b"), body.get("tags"));
    }

    @Test
    void roundTripsThroughParseAndWrite() {
        String original = "{\"a\":[1,{\"b\":\"x\\ny\"},null,true],\"c\":{}}";
        assertEquals(original, Json.write(Json.parse(original), false));
    }

    @Test
    void rejectsMalformedDocuments() {
        assertThrows(JsonException.class, () -> Json.parse("{\"a\":}"));
        assertThrows(JsonException.class, () -> Json.parse(""));
        assertThrows(JsonException.class, () -> Json.parse("{\"a\":1} trailing"));
        assertThrows(JsonException.class, () -> Json.parseObject("[1,2]"));
    }
}
