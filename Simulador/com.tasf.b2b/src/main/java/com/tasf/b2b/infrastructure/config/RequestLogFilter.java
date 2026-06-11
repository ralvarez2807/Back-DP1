package com.tasf.b2b.infrastructure.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Filtro de diagnóstico — log antes de Spring Security.
 * Eliminar cuando se resuelva el problema de routing.
 */
@Component
@Order(1)
public class RequestLogFilter implements Filter {

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest http = (HttpServletRequest) req;
        System.out.printf("[TOMCAT] %s %s%n", http.getMethod(), http.getRequestURI());
        chain.doFilter(req, res);
    }
}
