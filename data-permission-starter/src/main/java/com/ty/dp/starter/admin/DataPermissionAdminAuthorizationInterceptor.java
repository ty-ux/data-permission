package com.ty.dp.starter.admin;

import com.ty.dp.starter.admin.DataPermissionAdminAuthorizer.AdminAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/** Authorizes management requests without coupling the library to a host security framework. */
public class DataPermissionAdminAuthorizationInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(DataPermissionAdminAuthorizationInterceptor.class);

    private final ObjectProvider<DataPermissionAdminAuthorizer> authorizers;

    public DataPermissionAdminAuthorizationInterceptor(ObjectProvider<DataPermissionAdminAuthorizer> authorizers) {
        this.authorizers = authorizers;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        String method = request.getMethod();
        if ("OPTIONS".equals(method)) {
            return true;
        }

        AdminAction action;
        switch (method) {
            case "GET": action = AdminAction.LIST; break;
            case "POST": action = AdminAction.SAVE; break;
            case "PUT": action = AdminAction.EDIT; break;
            case "DELETE": action = AdminAction.REMOVE; break;
            default:
                log.warn("Management API request denied: unsupported HTTP method {}", method);
                return forbidden(response);
        }

        try {
            // getIfAvailable deliberately rejects ambiguous beans instead of silently allowing access.
            DataPermissionAdminAuthorizer authorizer = authorizers.getIfAvailable();
            if (authorizer == null || authorizer.isAllowed(action)) {
                return true;
            }
            log.warn("Management API authorization denied for action {}", action);
        } catch (Exception ex) {
            log.warn("Management API authorization failed for action {}; denying access", action, ex);
        }
        return forbidden(response);
    }

    private boolean forbidden(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"error\":\"forbidden\"}");
        return false;
    }
}
