package com.chargewise.security;

import com.chargewise.dto.ApiErrorResponse;
import com.chargewise.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import tools.jackson.databind.json.JsonMapper;

/** Writes the D8 error body for 401 and 403 raised by the security filter chain, before any controller runs. */
public class JsonSecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final JsonMapper mapper;

    public JsonSecurityErrorHandler(JsonMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        write(request, response, HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED,
                "Authentication is required: send a valid bearer token");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        write(request, response, HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN,
                "You do not have permission to perform this action");
    }

    private void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status, ErrorCode code,
            String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(), new ApiErrorResponse(status.value(), code.name(), message,
                List.of(), Instant.now(), request.getRequestURI()));
    }
}
