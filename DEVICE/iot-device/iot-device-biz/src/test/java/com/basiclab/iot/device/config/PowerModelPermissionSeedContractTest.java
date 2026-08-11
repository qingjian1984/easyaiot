package com.basiclab.iot.device.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** TD-005 §16：系统库权限 seed/rollback 候选的静态安全合同。 */
class PowerModelPermissionSeedContractTest {

    private static final List<String> PERMISSIONS = Arrays.asList(
            "power:model-template:read", "power:model-template:edit",
            "power:model-template:publish", "power:model-template:import",
            "power:model-template:upgrade", "power:model-template:retire",
            "power:system-template:manage");

    @Test
    void applyAndRollbackContainEveryFrozenPermissionAndFailClosedGuards() throws Exception {
        Path directory = root().resolve(".scripts/postgresql/td005-permissions");
        String apply = Files.readString(directory.resolve("apply_power_model_permissions.sql"));
        String rollback = Files.readString(directory.resolve("rollback_power_model_permissions.sql"));
        String preflight = Files.readString(directory.resolve("preflight_power_model_permissions.sql"));
        String verify = Files.readString(directory.resolve("verify_power_model_permissions.sql"));
        for (String permission : PERMISSIONS) {
            assertTrue(apply.contains("'" + permission + "'"));
            assertTrue(rollback.contains("'" + permission + "'"));
            assertTrue(preflight.contains("'" + permission + "'"));
            assertTrue(verify.contains("'" + permission + "'"));
        }
        assertTrue(apply.contains("current_database() <> 'ruoyi-vue-pro20'"));
        assertTrue(apply.contains("TD005_PERMISSION_PARTIAL_OR_DRIFTED"));
        assertTrue(apply.contains("TD005_PERMISSION_ID_OCCUPIED"));
        assertTrue(apply.contains("SET LOCAL lock_timeout = '5s'"));
        assertTrue(apply.contains("LOCK TABLE public.system_menu IN SHARE ROW EXCLUSIVE MODE"));
        assertTrue(apply.contains("TD005_PERMISSION_VERIFY_DUPLICATE"));
        assertTrue(rollback.contains("TD005_PERMISSION_ROLLBACK_ROLE_LINKED"));
        assertTrue(rollback.contains("creator = 'td005-seed'"));
        assertTrue(rollback.contains("LOCK TABLE public.system_role_menu IN SHARE ROW EXCLUSIVE MODE"));
        assertFalse(hasStandaloneCommit(apply));
        assertFalse(hasStandaloneCommit(rollback));
        assertTrue(preflight.contains("BEGIN TRANSACTION READ ONLY;"));
        assertTrue(preflight.contains("TD005_PERMISSION_PARTIAL_OR_DRIFTED"));
        assertTrue(preflight.contains("ROLLBACK;"));
        assertTrue(verify.contains("BEGIN TRANSACTION READ ONLY;"));
        assertTrue(verify.contains("TD005_PERMISSION_UNAPPROVED_ROLE_GRANT"));
        assertTrue(verify.contains("ROLLBACK;"));
        assertFalse(hasStandaloneCommit(preflight));
        assertFalse(hasStandaloneCommit(verify));
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
        return sql.lines()
                .map(String::trim)
                .anyMatch(line -> "COMMIT".equalsIgnoreCase(line)
                        || "COMMIT;".equalsIgnoreCase(line));
    }
}
