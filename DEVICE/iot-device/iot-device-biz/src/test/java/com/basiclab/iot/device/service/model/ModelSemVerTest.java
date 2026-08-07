package com.basiclab.iot.device.service.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TD-005 §5.1/§7.1：SemVer 解析、生产绑定禁止 prerelease、服务端最小版本增量。
 */
class ModelSemVerTest {

    @Test
    void parseAcceptsStrictSemVerAndSplitsComponents() {
        ModelSemVer version = ModelSemVer.parse("1.2.3");
        assertEquals(1, version.major());
        assertEquals(2, version.minor());
        assertEquals(3, version.patch());
        assertEquals("", version.prerelease());

        ModelSemVer pilot = ModelSemVer.parse("2.0.0-rc.1");
        assertTrue(pilot.isPrerelease());
    }

    @Test
    void parseRejectsMalformedVersions() {
        for (String raw : new String[]{"", "1.0", "v1.0.0", "1.0.0.0", "1.01.0", "1.0.0-"}) {
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> ModelSemVer.parse(raw), "必须拒绝非法 SemVer: " + raw);
            assertTrue(error.getMessage().startsWith("MODEL_TEMPLATE_SEMVER_INVALID"),
                    "非法 SemVer 必须使用稳定错误码前缀: " + raw);
        }
    }

    @Test
    void orderingFollowsSemVerPrecedence() {
        assertTrue(ModelSemVer.parse("1.0.0").compareTo(ModelSemVer.parse("1.0.1")) < 0);
        assertTrue(ModelSemVer.parse("1.2.0").compareTo(ModelSemVer.parse("1.10.0")) < 0);
        assertTrue(ModelSemVer.parse("2.0.0-rc.1").compareTo(ModelSemVer.parse("2.0.0")) < 0,
                "prerelease 先于正式版本");
        assertEquals(0, ModelSemVer.parse("1.0.0").compareTo(ModelSemVer.parse("1.0.0")));
    }

    @Test
    void productionBindingRejectsPrerelease() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> ModelSemVer.requireProductionBindable(ModelSemVer.parse("1.0.0-beta.1")));
        assertTrue(error.getMessage().startsWith("MODEL_TEMPLATE_SEMVER_PRERELEASE_FORBIDDEN"));
        assertDoesNotThrow(() -> ModelSemVer.requireProductionBindable(ModelSemVer.parse("1.0.0")));
    }

    @Test
    void minimumBumpMustBeRespected() {
        ModelSemVer base = ModelSemVer.parse("1.2.3");
        // 高于最低增量：允许
        assertDoesNotThrow(() -> ModelSemVer.requireAllowedBump(base,
                ModelSemVer.parse("2.0.0"), ModelSemVer.Bump.MINOR));
        // 等于最低增量：允许
        assertDoesNotThrow(() -> ModelSemVer.requireAllowedBump(base,
                ModelSemVer.parse("1.2.4"), ModelSemVer.Bump.PATCH));
        // 低于最低增量：阻止
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> ModelSemVer.requireAllowedBump(base,
                        ModelSemVer.parse("1.3.0"), ModelSemVer.Bump.MAJOR));
        assertTrue(error.getMessage().startsWith("MODEL_TEMPLATE_SEMVER_BUMP_TOO_LOW"));
        // 未前进：阻止
        assertThrows(IllegalArgumentException.class,
                () -> ModelSemVer.requireAllowedBump(base, base, ModelSemVer.Bump.PATCH));
        // 服务端不允许调用方通过 PATCH 标记绕过结构判断：回退版本同样阻止
        assertThrows(IllegalArgumentException.class,
                () -> ModelSemVer.requireAllowedBump(base,
                        ModelSemVer.parse("1.2.2"), ModelSemVer.Bump.PATCH));
    }
}
