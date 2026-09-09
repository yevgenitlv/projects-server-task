package com.ivory.employees.web;

import com.ivory.employees.json.Json;
import com.ivory.employees.service.AuthService;
import com.ivory.employees.util.ApiException;
import com.ivory.employees.util.RequestContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Map;

/**
 * The LOGIN service.
 *
 * <ul>
 *   <li>{@code POST /api/login} - body {@code {"username":"...","password":"..."}}, answers a token</li>
 *   <li>{@code POST /api/logout} - ends the session carried by the token</li>
 *   <li>{@code GET  /api/session} - describes the session carried by the token</li>
 * </ul>
 *
 * <p>The token is sent back on every other call as {@code Authorization: Bearer <token>}
 * (or {@code X-Auth-Token: <token>}).
 */
public class AuthServlet extends ApiServlet {

    public AuthServlet(Services services) {
        super(services);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        switch (path(request)) {
            case "/api/login" -> login(request, response);
            case "/api/logout" -> logout(request, response);
            default -> throw ApiException.notFound("Unknown service " + path(request));
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!"/api/session".equals(path(request))) {
            throw ApiException.notFound("Unknown service " + path(request));
        }
        AuthService.Session session = AuthFilter.session(request)
                .orElseThrow(() -> ApiException.unauthorized("No active session"));
        writeJson(response, 200, Map.of("session", describe(session)));
    }

    private void login(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Map<String, Object> body = readJsonBody(request);
        String username = requiredString(body, "username");
        RequestContext.user(username);
        AuthService.Session session = services.auth().login(username, requiredString(body, "password"));

        Map<String, Object> answer = Json.object();
        answer.put("token", session.token());
        answer.put("tokenType", "Bearer");
        answer.put("session", describe(session));
        answer.put("usage", "send 'Authorization: Bearer " + session.token() + "' on the other services");
        writeJson(response, 200, answer);
    }

    private void logout(HttpServletRequest request, HttpServletResponse response) throws IOException {
        services.auth().logout(AuthFilter.token(request).orElse(null));
        writeJson(response, 200, Map.of("loggedOut", true));
    }

    private static Map<String, Object> describe(AuthService.Session session) {
        Map<String, Object> json = Json.object();
        json.put("username", session.username());
        json.put("issuedAt", session.issuedAt().toString());
        json.put("expiresAt", session.expiresAt().toString());
        return json;
    }

    private static String path(HttpServletRequest request) {
        return request.getServletPath() + (request.getPathInfo() == null ? "" : request.getPathInfo());
    }
}
