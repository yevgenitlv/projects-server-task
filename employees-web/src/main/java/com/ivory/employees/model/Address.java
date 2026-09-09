package com.ivory.employees.model;

import com.ivory.employees.json.Json;

import java.util.Map;

/** Employee address, mapped from the ADDRESS_* columns of EMPLOYEES. */
public record Address(String street, String number, String city, String country) {

    public Map<String, Object> toJson() {
        Map<String, Object> json = Json.object();
        json.put("street", street);
        json.put("number", number);
        json.put("city", city);
        json.put("country", country);
        return json;
    }
}
