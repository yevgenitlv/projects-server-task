package com.ivory.employees.web;

import com.ivory.employees.json.Json;
import com.ivory.employees.json.JsonException;
import com.ivory.employees.util.ApiException;
import com.ivory.employees.util.RequestContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Base servlet: turns every answer into JSON, and every failure into a JSON error body with a
 * matching HTTP status.
 */
public abstract class ApiServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(ApiServlet.class.getName());
    private static final int MAX_BODY_BYTES = 64 * 1024;

    protected final transient Services services;

    protected ApiServlet(Services services) {
        this.services = services;
    }

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            super.service(request, response);
        } catch (ApiException e) {
            LOG.log(Level.FINE, e, () -> "Request rejected with status " + e.status() + ": " + e.getMessage());
            writeError(response, e.status(), e.getMessage());
        } catch (JsonException e) {
            LOG.log(Level.FINE, e, () -> "Malformed request body: " + e.getMessage());
            writeError(response, 400, "Malformed JSON body: " + e.getMessage());
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "Unhandled failure while serving " + request.getRequestURI(), e);
            writeError(response, 500, "Internal server error");
        }
    }

    protected void writeJson(HttpServletResponse response, int status, Object body) throws IOException {
        String json = Json.write(body, services.config().prettyJson());
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(json);
        LOG.fine(() -> "Answer body (" + status + "): " + json);
    }

    protected void writeError(HttpServletResponse response, int status, String message) throws IOException {
        if (response.isCommitted()) {
            LOG.warning(() -> "Cannot report error '" + message + "': the answer is already committed");
            return;
        }
        response.reset();
        Map<String, Object> error = Json.object();
        error.put("status", status);
        error.put("message", message);
        error.put("requestId", RequestContext.requestId());
        writeJson(response, status, Map.of("error", error));
    }

    /** Reads and parses a JSON request body. */
    protected Map<String, Object> readJsonBody(HttpServletRequest request) throws IOException {
        StringBuilder body = new StringBuilder();
        try (BufferedReader reader = request.getReader()) {
            char[] buffer = new char[1024];
            int read;
            while ((read = reader.read(buffer)) >= 0) {
                body.append(buffer, 0, read);
                if (body.length() > MAX_BODY_BYTES) {
                    throw ApiException.badRequest("Request body is larger than " + MAX_BODY_BYTES + " bytes");
                }
            }
        }
        if (body.isEmpty()) {
            throw ApiException.badRequest("A JSON request body is required");
        }
        return Json.parseObject(body.toString());
    }

    protected static String requiredString(Map<String, Object> body, String field) {
        Object value = body.get(field);
        if (!(value instanceof String text) || text.isBlank()) {
            throw ApiException.badRequest("'" + field + "' is required and must be a non-empty string");
        }
        return text;
    }

    /** Reads a boolean query parameter; absent means {@code defaultValue}, bare presence means true. */
    protected static boolean booleanParam(HttpServletRequest request, String name, boolean defaultValue) {
        String raw = request.getParameter(name);
        if (raw == null) {
            return defaultValue;
        }
        if (raw.isBlank()) {
            return true;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "true", "1", "yes", "y" -> true;
            case "false", "0", "no", "n" -> false;
            default -> throw ApiException.badRequest("'" + name + "' must be true or false but was '" + raw + "'");
        };
    }

    protected static long longParam(HttpServletRequest request, String name, long defaultValue) {
        String raw = request.getParameter(name);
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            throw ApiException.badRequest("'" + name + "' must be a whole number but was '" + raw + "'");
        }
    }

    /** Reads an enum query parameter, accepting any case and both {@code annualIncome} spellings. */
    protected static <E extends Enum<E>> E enumParam(HttpServletRequest request, String name,
                                                     Class<E> type, E defaultValue) {
        String raw = request.getParameter(name);
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        String normalised = raw.trim()
                .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .replace('-', '_')
                .toUpperCase(Locale.ROOT);
        for (E constant : type.getEnumConstants()) {
            if (constant.name().equals(normalised)) {
                return constant;
            }
        }
        throw ApiException.badRequest("'" + name + "' must be one of "
                + java.util.Arrays.stream(type.getEnumConstants()).map(ApiServlet::displayName).toList()
                + " but was '" + raw + "'");
    }

    /** Renders an enum constant the way the API documents it: {@code ANNUAL_INCOME -> annualIncome}. */
    private static String displayName(Enum<?> constant) {
        String[] words = constant.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder name = new StringBuilder(words[0]);
        for (int i = 1; i < words.length; i++) {
            name.append(Character.toUpperCase(words[i].charAt(0))).append(words[i].substring(1));
        }
        return name.toString();
    }
}
