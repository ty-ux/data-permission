package com.ty.dp.starter.admin;

import com.ty.dp.core.model.DataPermissionRule;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Minimal rule administration API. It is disabled by default. The host
 * application must provide a {@link DataPermissionAdminAuthorizer} or protect
 * these endpoints itself when enabling {@code data-permission.admin.enabled=true}.
 */
@RestController
@RequestMapping("${data-permission.admin.path:/data-permission/admin}")
public class DataPermissionAdminController {

    private final DataPermissionAdminService adminService;

    public DataPermissionAdminController(DataPermissionAdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/rules")
    public List<DataPermissionRule> list(@RequestParam(required = false) String appPoint,
                                         @RequestParam(required = false) String ruleName) {
        return adminService.listRules(appPoint, ruleName);
    }

    @GetMapping("/rules/{id}")
    public DataPermissionRule get(@PathVariable String id) {
        return adminService.getRule(id);
    }

    @PostMapping("/rules")
    public Map<String, String> save(@RequestBody DataPermissionRule rule) {
        String id = adminService.saveRule(rule);
        return Collections.singletonMap("id", id);
    }

    @DeleteMapping("/rules/{ids}")
    public Map<String, String> delete(@PathVariable String[] ids) {
        adminService.deleteRules(Arrays.asList(ids));
        return Collections.singletonMap("status", "ok");
    }

    @GetMapping("/roles/{roleId}/rules")
    public List<String> roleRules(@PathVariable String roleId) {
        return adminService.listRoleRuleIds(roleId);
    }

    @PutMapping("/roles/{roleId}/rules")
    public Map<String, String> assign(@PathVariable String roleId,
                                      @RequestBody RoleRuleRequest request) {
        adminService.assignRoleRules(roleId, request == null ? null : request.getRuleIds());
        return Collections.singletonMap("status", "ok");
    }

    public static class RoleRuleRequest {
        private List<String> ruleIds;

        public List<String> getRuleIds() {
            return ruleIds;
        }

        public void setRuleIds(List<String> ruleIds) {
            this.ruleIds = ruleIds;
        }
    }
}
