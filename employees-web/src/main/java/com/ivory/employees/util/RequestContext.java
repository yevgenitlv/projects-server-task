package com.ivory.employees.util;

/**
 * Per-request correlation data (request id + authenticated user) kept in a {@link ThreadLocal} so
 * every log line written while handling a request can be tied back to that request.
 */
public final class RequestContext {

    private static final ThreadLocal<String> REQUEST_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> USER = new ThreadLocal<>();

    private RequestContext() {
    }

    public static void begin(String requestId) {
        REQUEST_ID.set(requestId);
    }

    public static void user(String username) {
        USER.set(username);
    }

    public static String requestId() {
        String id = REQUEST_ID.get();
        return id == null ? "-" : id;
    }

    public static String user() {
        String user = USER.get();
        return user == null ? "-" : user;
    }

    public static void clear() {
        REQUEST_ID.remove();
        USER.remove();
    }
}
