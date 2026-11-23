package com.ty.dp.sample;

import com.ty.dp.starter.admin.DataPermissionAdminAuthorizer;
import com.ty.dp.starter.admin.DataPermissionAdminAuthorizer.AdminAction;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "data-permission.admin.path=/custom/admin")
@AutoConfigureMockMvc
class AdminAuthorizationIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private DataPermissionAdminAuthorizer authorizer;

    @Test
    void allSixAdminEndpointsAreProtectedAtCustomPath() throws Exception {
        MockHttpServletRequestBuilder[] requests = {
                get("/custom/admin/rules"),
                get("/custom/admin/rules/missing"),
                post("/custom/admin/rules").contentType("application/json").content("{}"),
                delete("/custom/admin/rules/missing"),
                get("/custom/admin/roles/10/rules"),
                put("/custom/admin/roles/10/rules").contentType("application/json").content("{\"ruleIds\":[]}")
        };
        for (MockHttpServletRequestBuilder request : requests) {
            mvc.perform(request).andExpect(status().isForbidden())
                    .andExpect(content().contentType("application/json;charset=UTF-8"))
                    .andExpect(content().json("{\"error\":\"forbidden\"}"));
        }
        verify(authorizer, times(3)).isAllowed(AdminAction.LIST);
        verify(authorizer).isAllowed(AdminAction.SAVE);
        verify(authorizer).isAllowed(AdminAction.REMOVE);
        verify(authorizer).isAllowed(AdminAction.EDIT);
    }

    @Test
    void allowedListKeepsExistingResponseFormat() throws Exception {
        when(authorizer.isAllowed(AdminAction.LIST)).thenReturn(true);
        mvc.perform(get("/custom/admin/rules")).andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void exceptionFailsClosedThroughMvc() throws Exception {
        when(authorizer.isAllowed(AdminAction.LIST)).thenThrow(new IllegalStateException("host failure"));
        mvc.perform(get("/custom/admin/rules")).andExpect(status().isForbidden())
                .andExpect(content().json("{\"error\":\"forbidden\"}"));
    }

    @Test
    void optionsDoesNotInvokeAuthorizer() throws Exception {
        mvc.perform(options("/custom/admin/rules")).andExpect(status().isOk());
        verifyNoInteractions(authorizer);
    }

    @Test
    void oldDefaultPathIsNotExposedWhenCustomized() throws Exception {
        mvc.perform(get("/data-permission/admin/rules")).andExpect(status().isNotFound());
        verifyNoInteractions(authorizer);
    }
}
