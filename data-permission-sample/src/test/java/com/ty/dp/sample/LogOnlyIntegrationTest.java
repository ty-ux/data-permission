package com.ty.dp.sample;

import com.ty.dp.core.subject.DataPermissionContext;
import com.ty.dp.core.subject.SimpleDataPermissionSubject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "data-permission.mode=LOG_ONLY")
class LogOnlyIntegrationTest {

    @Autowired
    private DemoProjectMapper mapper;

    @AfterEach
    void tearDown() {
        DataPermissionContext.clear();
    }

    @Test
    void logOnlyDoesNotFilter() {
        DataPermissionContext.setSubject(SimpleDataPermissionSubject.builder()
                .subjectId("tester")
                .roleId("10")
                .field("deptId", 100L)
                .field("userNo", "u1")
                .build());

        assertEquals(3, mapper.selectAll().size());
    }
}
