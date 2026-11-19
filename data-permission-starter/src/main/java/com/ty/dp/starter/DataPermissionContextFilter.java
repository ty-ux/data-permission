package com.ty.dp.starter;

import com.ty.dp.core.subject.DataPermissionContext;
import com.ty.dp.core.subject.DataPermissionSubject;
import com.ty.dp.core.subject.DataPermissionSubjectProvider;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Resolves the subject once per request and clears the thread local afterwards.
 * The interceptor also resolves lazily, so this filter is an optimisation, not
 * a hard requirement.
 */
public class DataPermissionContextFilter extends OncePerRequestFilter {

    private final DataPermissionSubjectProvider subjectProvider;

    public DataPermissionContextFilter(DataPermissionSubjectProvider subjectProvider) {
        this.subjectProvider = subjectProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            if (DataPermissionContext.getSubject() == null && subjectProvider != null) {
                DataPermissionSubject subject = subjectProvider.getCurrent();
                if (subject != null) {
                    DataPermissionContext.setSubject(subject);
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            DataPermissionContext.clear();
        }
    }
}
