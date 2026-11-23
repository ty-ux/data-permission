package com.ty.dp.starter.admin;

import com.ty.dp.starter.admin.DataPermissionAdminAuthorizer.AdminAction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DataPermissionAdminAuthorizationInterceptorTest {

    private final DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
    private final DataPermissionAdminAuthorizationInterceptor interceptor =
            new DataPermissionAdminAuthorizationInterceptor(beans.getBeanProvider(DataPermissionAdminAuthorizer.class));
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @ParameterizedTest
    @CsvSource({"GET,LIST", "POST,SAVE", "PUT,EDIT", "DELETE,REMOVE"})
    void mapsMethodAndAllowsAuthorizedRequest(String method, AdminAction action) throws Exception {
        DataPermissionAdminAuthorizer authorizer = mock(DataPermissionAdminAuthorizer.class);
        beans.registerSingleton("authorizer", authorizer);
        when(authorizer.isAllowed(action)).thenReturn(true);

        assertTrue(invoke(method));
        verify(authorizer).isAllowed(action);
        verifyNoMoreInteractions(authorizer);
        assertEquals(200, response.getStatus());
        assertEquals("", response.getContentAsString());
    }

    @Test
    void optionsDoesNotResolveOrCallAuthorizer() throws Exception {
        // Even broken bean creation must not affect CORS preflight.
        beans.registerBeanDefinition("broken", new RootBeanDefinition(BrokenAuthorizer.class));
        assertTrue(invoke("OPTIONS"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "POST", "PUT", "DELETE"})
    void absentBeanKeepsLegacyBehavior(String method) throws Exception {
        assertTrue(invoke(method));
    }

    @ParameterizedTest
    @ValueSource(strings = {"HEAD", "PATCH", "TRACE", "CONNECT"})
    void unsupportedMethodsAreDeniedEvenWithoutBean(String method) throws Exception {
        assertFalse(invoke(method));
        assertForbidden();
    }

    @Test
    void denyingBeanReturnsJson403() throws Exception {
        beans.registerSingleton("authorizer", (DataPermissionAdminAuthorizer) action -> false);
        assertFalse(invoke("GET"));
        assertForbidden();
    }

    @Test
    void throwingBeanFailsClosedWithoutLeakingException() throws Exception {
        beans.registerSingleton("authorizer", (DataPermissionAdminAuthorizer) action -> {
            throw new IllegalStateException("sensitive host detail");
        });
        assertFalse(invoke("POST"));
        assertForbidden();
    }

    @Test
    void ambiguousBeansFailClosed() throws Exception {
        beans.registerSingleton("first", (DataPermissionAdminAuthorizer) action -> true);
        beans.registerSingleton("second", (DataPermissionAdminAuthorizer) action -> true);
        assertFalse(invoke("GET"));
        assertForbidden();
    }

    @Test
    void beanCreationFailureFailsClosed() throws Exception {
        beans.registerBeanDefinition("broken", new RootBeanDefinition(BrokenAuthorizer.class));
        assertFalse(invoke("GET"));
        assertForbidden();
    }

    private boolean invoke(String method) throws Exception {
        return interceptor.preHandle(new MockHttpServletRequest(method, "/data-permission/admin/rules"),
                response, new Object());
    }

    private void assertForbidden() throws Exception {
        assertEquals(403, response.getStatus());
        assertEquals("application/json;charset=UTF-8", response.getContentType());
        assertEquals("{\"error\":\"forbidden\"}", response.getContentAsString());
    }

    public static class BrokenAuthorizer implements DataPermissionAdminAuthorizer {
        public BrokenAuthorizer() {
            throw new IllegalStateException("broken provider");
        }

        @Override
        public boolean isAllowed(AdminAction action) {
            return true;
        }
    }
}
