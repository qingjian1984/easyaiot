package com.basiclab.iot.device.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** TD-005 1.0.26：HMAC secret 不进入仓库、环境检查或容器元数据的静态合同。 */
class PowerModelSecretMountContractTest {

    @Test
    void composeOverlayUsesConfigTreeAndResetsPlainEnvironmentSecret() throws Exception {
        Path root = root();
        String application = Files.readString(root.resolve(
                "DEVICE/iot-device/iot-device-biz/src/main/resources/application.yaml"));
        String overlay = Files.readString(root.resolve("DEVICE/docker-compose.power-model-secret.yml"));
        String preflight = Files.readString(root.resolve(
                ".scripts/docker/power_model_activation_preflight.ps1"));
        assertTrue(application.contains("optional:configtree:/run/secrets/"));
        assertTrue(application.contains(
                "${easyaiot.power-model.idempotency-hmac-secret-file-content:${EASYAIOT_POWER_MODEL_IDEMPOTENCY_HMAC_SECRET:}}"));
        assertTrue(overlay.contains("EASYAIOT_POWER_MODEL_IDEMPOTENCY_HMAC_SECRET: !reset null"));
        assertTrue(overlay.contains("EASYAIOT_POWER_MODEL_HMAC_SECRET_FILE:?"));
        assertTrue(overlay.contains("target: easyaiot.power-model.idempotency-hmac-secret-file-content"));
        assertFalse(overlay.matches("(?s).*idempotency-hmac-secret-file-content:\\s*[A-Za-z0-9+/=]{32,}.*"));
        assertTrue(preflight.contains("source=$source"));
        assertTrue(preflight.contains("configtree-file"));
        assertTrue(preflight.contains("@('events', 'template-api', 'api')"));
    }

    @Test
    void hostSecretPreflightIsReadOnlyFailClosedAndNeverPrintsSecretMaterial() throws Exception {
        String script = Files.readString(root().resolve(
                ".scripts/docker/power_model_secret_file_preflight.ps1"));
        assertTrue(script.contains("[IO.Path]::IsPathRooted"));
        assertTrue(script.contains("OUTSIDE_REPOSITORY"));
        assertTrue(script.contains("[IO.FileAttributes]::ReparsePoint"));
        assertTrue(script.contains("[Text.UTF8Encoding]::new($false, $true)"));
        assertTrue(script.contains("$bytes -contains 0x0A"));
        assertTrue(script.contains("utf8BytesGe32=true"));
        assertTrue(script.contains("broadReadPrincipalsPresent=true"));
        assertFalse(script.contains("Get-Content"));
        assertFalse(script.contains("Get-FileHash"));
        assertFalse(script.contains("WriteAllBytes"));
    }

    @Test
    void injectionWindowDefaultsToReadOnlyAndScopesApplyAndRollbackToIotDevice() throws Exception {
        String script = Files.readString(root().resolve(
                ".scripts/docker/power_model_secret_injection_window.ps1"));
        assertTrue(script.contains("USER-APPROVAL-20260811-TD005-HMAC-SECRET-INJECTION"));
        assertTrue(script.contains("if (-not $Execute)"));
        assertTrue(script.contains("WINDOW_RESULT=READY_ONLY execute=false runtimeChanged=false"));
        assertTrue(script.contains("-Stage events -ExpectedProfile full"));
        assertTrue(script.contains("-ExpectedPartitions 6"));
        assertTrue(script.contains("-ExpectedReplicationFactor 1"));
        assertTrue(script.contains("-ExpectedRetentionMs 2592000000"));
        assertTrue(script.contains("$stageText | Write-Host"));
        assertTrue(script.contains("return ($code -eq 0)"));
        assertTrue(script.contains("'up', '-d', '--no-deps', '--force-recreate', 'iot-device'"));
        assertTrue(script.contains("ROLLBACK_STARTED target=iot-device overlay=false"));
        assertTrue(script.contains("plainEnvironmentBytes=0"));
        assertFalse(script.contains("compose', 'down"));
        assertFalse(script.contains("Get-FileHash"));
        assertFalse(script.contains("WriteAllBytes"));
    }

    @Test
    void injectionWaitsForKafkaRejoinAndKeepsRollbackResultVisible() throws Exception {
        String script = Files.readString(root().resolve(
                ".scripts/docker/power_model_secret_injection_window.ps1"));
        assertTrue(script.contains("function Wait-Stage2Baseline"));
        assertTrue(script.contains("param([int]$Attempts = 6, [int]$IntervalSeconds = 5)"));
        assertTrue(script.contains("Start-Sleep -Seconds $IntervalSeconds"));
        assertTrue(script.contains("if (-not (Wait-Stage2Baseline))"));
        assertTrue(script.contains("ROLLBACK_RESULT=PASS target=iot-device healthy=true stage2=true"));
        assertFalse(script.contains("[void](Invoke-BaseRollback)"));
    }

    @Test
    void templateApiWindowDefaultsToReadOnlyAndKeepsBindingApiClosed() throws Exception {
        Path root = root();
        String overlay = Files.readString(root.resolve(
                "DEVICE/docker-compose.power-model-template-api.yml"));
        String script = Files.readString(root.resolve(
                ".scripts/docker/power_model_template_api_activation_window.ps1"));
        assertTrue(overlay.contains("EASYAIOT_POWER_MODEL_TEMPLATE_API_ENABLED: \"true\""));
        assertTrue(overlay.contains("EASYAIOT_POWER_MODEL_BINDING_APPLY_API_ENABLED: \"false\""));
        assertTrue(script.contains("USER-APPROVAL-20260811-TD005-TEMPLATE-API-ACTIVATION"));
        assertTrue(script.contains("if (-not $Execute)"));
        assertTrue(script.contains("WINDOW_RESULT=READY_ONLY execute=false runtimeChanged=false targetStage=template-api"));
        assertTrue(script.contains("-Stage template-api"));
        assertTrue(script.contains("bindingApi=false"));
        assertTrue(script.contains("canaryWritten=false"));
        assertFalse(script.contains("compose', 'down"));
        assertFalse(script.contains("Invoke-WebRequest"));
        assertFalse(script.contains("Invoke-RestMethod"));
    }

    @Test
    void templateApiRollbackPreservesSecretAndRevalidatesStageTwo() throws Exception {
        String script = Files.readString(root().resolve(
                ".scripts/docker/power_model_template_api_activation_window.ps1"));
        assertTrue(script.contains("function Invoke-SafeRollback"));
        assertTrue(script.contains("templateApi=false preserveSecret=true"));
        assertTrue(script.contains("'-f', $secretCompose"));
        assertTrue(script.contains("Wait-ActivationStage -Stage events"));
        assertTrue(script.contains("Test-RuntimeInvariants -ExpectedTemplateApi $false"));
        assertTrue(script.contains("Test-CanaryReadiness"));
        assertFalse(script.contains("[void](Invoke-SafeRollback)"));
    }

    private static Path root() {
        Path current = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            if (Files.isDirectory(current.resolve(".scripts/docker"))
                    && Files.isDirectory(current.resolve("DEVICE"))) return current;
            current = current.getParent();
        }
        throw new IllegalStateException("repository root not found");
    }
}
