package com.basiclab.iot.device.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** TD-005 1.0.25：隔离 canary 角色最小授权资产的静态安全合同。 */
class PowerModelCanaryRoleGrantContractTest {

    private static final List<String> ALLOWED = Arrays.asList(
            "power:model-template:read",
            "power:model-template:edit",
            "power:model-template:publish");

    @Test
    void grantIsTenantBoundMinimalAndReversible() throws Exception {
        Path directory = root().resolve(".scripts/postgresql/td005-canary-role");
        String rolePreflight = Files.readString(directory.resolve("preflight_canary_role.sql"));
        String dataPreflight = Files.readString(directory.resolve("preflight_canary_tenant_data.sql"));
        String apply = Files.readString(directory.resolve("apply_canary_role_grant.sql"));
        String verify = Files.readString(directory.resolve("verify_canary_role_grant.sql"));
        String rollback = Files.readString(directory.resolve("rollback_canary_role_grant.sql"));
        for (String permission : ALLOWED) {
            assertTrue(rolePreflight.contains("'" + permission + "'"));
            assertTrue(apply.contains("'" + permission + "'"));
        }
        assertTrue(rolePreflight.contains("id = 122"));
        assertTrue(rolePreflight.contains("id = 111"));
        assertTrue(dataPreflight.contains("TD005_CANARY_TENANT_NOT_EMPTY"));
        assertTrue(dataPreflight.contains("BEGIN TRANSACTION READ ONLY;"));
        assertTrue(apply.contains("menu_id IN (3903, 3904, 3905, 3906)"));
        assertTrue(apply.contains("TD005_CANARY_ROLE_FORBIDDEN_PERMISSION"));
        assertTrue(apply.contains("SET LOCAL lock_timeout = '5s'"));
        assertTrue(verify.contains("BEGIN TRANSACTION READ ONLY;"));
        assertTrue(rollback.contains("TD005_CANARY_ROLE_ROLLBACK_DRIFT"));
        assertFalse(hasStandaloneCommit(rolePreflight));
        assertFalse(hasStandaloneCommit(dataPreflight));
        assertFalse(hasStandaloneCommit(apply));
        assertFalse(hasStandaloneCommit(verify));
        assertFalse(hasStandaloneCommit(rollback));
    }

    @Test
    void windowsWrapperForcesUtf8AndCanOnlyRunReadOnlyPreflights() throws Exception {
        Path directory = root().resolve(".scripts/postgresql/td005-canary-role");
        String wrapper = Files.readString(directory.resolve("run_readonly_preflight.ps1"));
        assertTrue(wrapper.contains("Get-Content -Raw -Encoding UTF8"));
        assertTrue(wrapper.contains("$OutputEncoding = [Text.UTF8Encoding]::new($false)"));
        assertTrue(wrapper.contains("preflight_canary_role.sql"));
        assertTrue(wrapper.contains("preflight_canary_tenant_data.sql"));
        assertTrue(wrapper.contains("BEGIN TRANSACTION READ ONLY"));
        assertTrue(wrapper.contains("TD005_CANARY_PREFLIGHT_NOT_READ_ONLY"));
        assertFalse(wrapper.contains("apply_canary_role_grant.sql"));
        assertFalse(wrapper.contains("rollback_canary_role_grant.sql"));
        assertFalse(wrapper.contains("docker cp"));
    }

    private static Path root() {
        Path current = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            if (Files.isDirectory(current.resolve(".scripts/postgresql"))
                    && Files.isDirectory(current.resolve("DEVICE"))) return current;
            current = current.getParent();
        }
        throw new IllegalStateException("repository root not found");
    }

    private static boolean hasStandaloneCommit(String sql) {
        return sql.lines().map(String::trim)
                .anyMatch(line -> "COMMIT".equalsIgnoreCase(line)
                        || "COMMIT;".equalsIgnoreCase(line));
    }
}
