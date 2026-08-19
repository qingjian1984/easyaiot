package com.basiclab.iot.node.service.collector.config;

import com.basiclab.iot.node.domain.collector.config.CollectorConfigAgentStatus;
import com.basiclab.iot.node.domain.collector.config.CollectorConfigGetResponseDTO;
import com.basiclab.iot.node.domain.collector.config.CollectorConfigPutRequestDTO;
import com.basiclab.iot.node.domain.collector.config.CollectorConfigPutResponseDTO;
import com.basiclab.iot.node.security.NodeAgentRequestSigner;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * NODE collector 专用配置客户端。
 *
 * <p>该客户端只知道两个固定路径，禁用重定向并把响应体限制在 1 MiB。请求签名使用与实际
 * HttpRequest 完全相同的 UTF-8 bytes；它只提供本合同规定的配置读写能力。</p>
 */
public final class CollectorAgentClient implements CollectorAgentPort {

    public static final String PUT_PATH = "/workload/collector/config";
    public static final String GET_PATH_PREFIX = "/workload/collector/";
    public static final int MAX_RESPONSE_BYTES = 1024 * 1024;

    private static final Set<String> ENVELOPE_FIELDS = Set.of("code", "msg", "data");
    private static final Set<String> PUT_DATA_FIELDS =
            Set.of("status", "workloadId", "configVersion", "payloadSha256");
    private static final Set<String> GET_DATA_FIELDS = Set.of("workloadId", "desired", "active", "observed");
    private static final Set<String> STATE_FIELDS =
            Set.of("present", "schemaVersion", "configVersion", "payloadSha256", "canonicalLengthBytes");
    private static final Set<String> OBSERVED_FIELDS =
            Set.of("workloadId", "status", "configVersion", "payloadSha256", "observedAt", "errorCode");

    private final ObjectMapper mapper;
    private final NodeAgentRequestSigner signer;
    private final HttpClient httpClient;
    private final Duration requestTimeout;

    public CollectorAgentClient(ObjectMapper mapper,
                                NodeAgentRequestSigner signer,
                                Duration connectTimeout,
                                Duration requestTimeout) {
        if (mapper == null || signer == null) {
            throw new IllegalArgumentException("Agent client dependencies are required");
        }
        requirePositive(connectTimeout, "connectTimeout");
        requirePositive(requestTimeout, "requestTimeout");
        this.mapper = strictMapper(mapper);
        this.signer = signer;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        this.requestTimeout = requestTimeout;
    }

    public CollectorAgentClient(NodeAgentRequestSigner signer) {
        this(new ObjectMapper(), signer, Duration.ofSeconds(3), Duration.ofSeconds(10));
    }

    /** 向固定 collector 配置端点发送闭合 PUT。 */
    @Override
    public CollectorConfigPutResponseDTO putConfig(CollectorNodeEndpoint node,
                                                   CollectorConfigPutRequestDTO request) {
        if (request == null || request.getWorkloadId() == null || request.getConfigVersion() == null
                || request.getPayloadCanonical() == null || request.getPayloadSha256() == null
                || request.getCanonicalLengthBytes() == null) {
            throw new CollectorAgentException(Kind.PROTOCOL, -1, "COLLECTOR_CONFIG_REQUEST_INVALID");
        }
        byte[] body;
        try {
            body = mapper.writeValueAsBytes(request);
        } catch (IOException error) {
            throw new CollectorAgentException(Kind.PROTOCOL, -1, "COLLECTOR_CONFIG_REQUEST_INVALID");
        }
        return exchange(node, "PUT", PUT_PATH, body, CollectorConfigPutResponseDTO.class);
    }

    /** 从节点权威地址查询固定 collector 状态端点。 */
    @Override
    public CollectorConfigGetResponseDTO getConfig(CollectorNodeEndpoint node, String workloadId) {
        if (workloadId == null || workloadId.isBlank()) {
            throw new CollectorAgentException(Kind.PROTOCOL, -1, "COLLECTOR_WORKLOAD_NOT_FOUND");
        }
        String encoded = encodePathSegment(workloadId);
        return exchange(node, "GET", GET_PATH_PREFIX + encoded, new byte[0],
                CollectorConfigGetResponseDTO.class);
    }

    private <T> T exchange(CollectorNodeEndpoint node,
                           String method,
                           String path,
                           byte[] body,
                           Class<T> type) {
        URI uri = endpointUri(node, path);
        Map<String, String> headers;
        try {
            headers = signer.sign(node.getNodeId(), method, path, body);
        } catch (RuntimeException error) {
            throw new CollectorAgentException(Kind.RETRYABLE, -1, "AGENT_SIGNING_KEY_UNAVAILABLE");
        }
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(requestTimeout)
                .header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofByteArray(body));
        headers.forEach(builder::header);
        HttpResponse<InputStream> response;
        try {
            response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
        } catch (IOException error) {
            throw new CollectorAgentException(Kind.RETRYABLE, -1, "AGENT_CONNECTION_FAILED");
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new CollectorAgentException(Kind.RETRYABLE, -1, "AGENT_REQUEST_INTERRUPTED");
        } catch (RuntimeException error) {
            throw new CollectorAgentException(Kind.RETRYABLE, -1, "AGENT_CONNECTION_FAILED");
        }
        byte[] responseBody;
        try (InputStream responseStream = response.body()) {
            responseBody = readLimitedResponseBody(responseStream);
        } catch (ResponseBodyTooLargeException error) {
            throw new CollectorAgentException(Kind.RETRYABLE, response.statusCode(), "AGENT_RESPONSE_TOO_LARGE");
        } catch (IOException error) {
            throw new CollectorAgentException(Kind.RETRYABLE, response.statusCode(), "AGENT_CONNECTION_FAILED");
        }
        JsonNode envelope = readEnvelope(responseBody);
        int status = response.statusCode();
        if (status < 200 || status > 299) {
            String errorCode = text(envelope, "msg");
            if (errorCode == null || errorCode.isBlank()) {
                errorCode = "AGENT_HTTP_" + status;
            }
            Kind kind = status >= 400 && status < 500 && status != 401 && status != 403
                    ? Kind.DETERMINISTIC : Kind.RETRYABLE;
            if (status == 404 && path.startsWith(GET_PATH_PREFIX)) {
                kind = Kind.RETRYABLE;
            }
            throw new CollectorAgentException(kind, status, errorCode);
        }
        if (!envelope.path("code").isInt() || envelope.path("code").asInt() != 0) {
            throw new CollectorAgentException(Kind.RETRYABLE, status, "AGENT_RESPONSE_INVALID");
        }
        JsonNode data = envelope.get("data");
        if (data == null || !data.isObject()) {
            throw new CollectorAgentException(Kind.RETRYABLE, status, "AGENT_RESPONSE_INVALID");
        }
        try {
            if (type == CollectorConfigPutResponseDTO.class) {
                assertClosed(data, PUT_DATA_FIELDS);
                CollectorConfigPutResponseDTO value = mapper.treeToValue(
                        data, CollectorConfigPutResponseDTO.class);
                validatePutResponse(value);
                return type.cast(value);
            }
            assertClosed(data, GET_DATA_FIELDS);
            validateGetShape(data);
            return mapper.treeToValue(data, type);
        } catch (IOException | IllegalArgumentException error) {
            throw new CollectorAgentException(Kind.RETRYABLE, status, "AGENT_RESPONSE_INVALID");
        }
    }

    /** 只探测并保留最多 MAX_RESPONSE_BYTES + 1 个字节，调用方负责关闭流。 */
    private static byte[] readLimitedResponseBody(InputStream responseStream) throws IOException {
        if (responseStream == null) {
            return new byte[0];
        }
        byte[] limited = new byte[MAX_RESPONSE_BYTES + 1];
        int total = 0;
        while (total < limited.length) {
            int count = responseStream.read(limited, total, limited.length - total);
            if (count < 0) {
                return Arrays.copyOf(limited, total);
            }
            if (count == 0) {
                continue;
            }
            total += count;
        }
        throw new ResponseBodyTooLargeException();
    }

    private JsonNode readEnvelope(byte[] raw) {
        try {
            JsonNode value = mapper.readTree(raw);
            if (value == null || !value.isObject()) {
                throw new IOException("not object");
            }
            assertClosed(value, ENVELOPE_FIELDS);
            if (!value.has("code") || !value.has("msg") || !value.has("data")) {
                throw new IOException("missing wrapper field");
            }
            if (!value.get("msg").isTextual()) {
                throw new IOException("invalid wrapper message");
            }
            return value;
        } catch (IOException | IllegalArgumentException error) {
            throw new CollectorAgentException(Kind.RETRYABLE, -1, "AGENT_RESPONSE_INVALID");
        }
    }

    private static void validatePutResponse(CollectorConfigPutResponseDTO value) {
        if (value == null || value.getStatus() == null
                || (value.getStatus() != CollectorConfigAgentStatus.ACCEPTED
                && value.getStatus() != CollectorConfigAgentStatus.IDEMPOTENT)
                || value.getWorkloadId() == null || value.getConfigVersion() == null
                || value.getPayloadSha256() == null
                || !value.getPayloadSha256().matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("invalid collector put response");
        }
    }

    private static void validateGetShape(JsonNode data) {
        if (!data.has("workloadId") || !data.get("workloadId").isTextual()
                || data.get("workloadId").textValue().isBlank()) {
            throw new IllegalArgumentException("invalid workloadId");
        }
        for (String name : new String[]{"desired", "active"}) {
            JsonNode state = data.get(name);
            if (state != null && !state.isNull()) {
                assertClosed(state, STATE_FIELDS);
                validateStateTypes(state);
            }
        }
        JsonNode observed = data.get("observed");
        if (observed != null && !observed.isNull()) {
            assertAllowed(observed, OBSERVED_FIELDS);
            if (!observed.isObject()) {
                throw new IllegalArgumentException("invalid observed");
            }
            if (!observed.has("workloadId") || !observed.has("status") || !observed.has("observedAt")
                    || !observed.get("workloadId").isTextual() || !observed.get("status").isTextual()
                    || !observed.get("observedAt").isTextual()
                    || observed.get("workloadId").textValue().isBlank()
                    || observed.get("status").textValue().isBlank()
                    || observed.get("observedAt").textValue().isBlank()
                    || !safeRedactedText(observed.get("observedAt").textValue(), 64)) {
                throw new IllegalArgumentException("invalid observed identity");
            }
            validateOptionalObservedTypes(observed);
        }
    }

    private static void validateStateTypes(JsonNode state) {
        if (!state.get("present").isBoolean()
                || !state.get("schemaVersion").isTextual()
                || !state.get("configVersion").canConvertToLong()
                || !state.get("configVersion").isIntegralNumber()
                || !state.get("payloadSha256").isTextual()
                || !state.get("payloadSha256").textValue().matches("[0-9a-f]{64}")
                || !state.get("canonicalLengthBytes").isIntegralNumber()
                || !state.get("canonicalLengthBytes").canConvertToLong()) {
            throw new IllegalArgumentException("invalid state types");
        }
    }

    private static void validateOptionalObservedTypes(JsonNode observed) {
        JsonNode configVersion = observed.get("configVersion");
        if (configVersion != null && !configVersion.isNull()
                && (!configVersion.isIntegralNumber() || !configVersion.canConvertToLong())) {
            throw new IllegalArgumentException("invalid observed version");
        }
        JsonNode payloadSha256 = observed.get("payloadSha256");
        if (payloadSha256 != null && !payloadSha256.isNull()
                && (!payloadSha256.isTextual() || !payloadSha256.textValue().matches("[0-9a-f]{64}"))) {
            throw new IllegalArgumentException("invalid observed hash");
        }
        JsonNode errorCode = observed.get("errorCode");
        if (errorCode != null && !errorCode.isNull() && !errorCode.isTextual()) {
            throw new IllegalArgumentException("invalid observed error");
        }
        if (errorCode != null && errorCode.isTextual()
                && !safeRedactedText(errorCode.textValue(), 64)) {
            throw new IllegalArgumentException("invalid observed error");
        }
    }

    private static boolean safeRedactedText(String value, int maxLength) {
        if (value == null || value.length() > maxLength) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (Character.isISOControl(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static void assertClosed(JsonNode value, Set<String> expected) {
        if (value == null || !value.isObject()) {
            throw new IllegalArgumentException("closed object required");
        }
        Set<String> names = new HashSet<>();
        value.fieldNames().forEachRemaining(names::add);
        if (!names.equals(expected)) {
            throw new IllegalArgumentException("unknown or missing response field");
        }
    }

    private static void assertAllowed(JsonNode value, Set<String> allowed) {
        if (value == null || !value.isObject()) {
            throw new IllegalArgumentException("object required");
        }
        Set<String> names = new HashSet<>();
        value.fieldNames().forEachRemaining(names::add);
        if (!allowed.containsAll(names)) {
            throw new IllegalArgumentException("unknown response field");
        }
    }

    private static String text(JsonNode object, String name) {
        JsonNode value = object.get(name);
        return value == null || value.isNull() || !value.isTextual() ? null : value.textValue();
    }

    private static ObjectMapper strictMapper(ObjectMapper source) {
        return source.copy()
                .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, false)
                .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
    }

    private static URI endpointUri(CollectorNodeEndpoint node, String path) {
        if (node == null || node.getHost() == null || node.getHost().isBlank()
                || node.getAgentPort() < 1 || node.getAgentPort() > 65535
                || path == null || !path.startsWith("/")) {
            throw new CollectorAgentException(Kind.RETRYABLE, -1, "NODE_ENDPOINT_INVALID");
        }
        String host = node.getHost().trim();
        if (containsUnsafeHost(host)) {
            throw new CollectorAgentException(Kind.RETRYABLE, -1, "NODE_ENDPOINT_INVALID");
        }
        String authority = host.contains(":") && !host.startsWith("[") ? "[" + host + "]" : host;
        try {
            URI uri = new URI("http://" + authority + ":" + node.getAgentPort() + path);
            if (!"http".equals(uri.getScheme()) || !path.equals(uri.getRawPath())
                    || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || uri.getUserInfo() != null) {
                throw new URISyntaxException(uri.toString(), "unsafe endpoint");
            }
            return uri;
        } catch (URISyntaxException error) {
            throw new CollectorAgentException(Kind.RETRYABLE, -1, "NODE_ENDPOINT_INVALID");
        }
    }

    private static boolean containsUnsafeHost(String host) {
        if (host.indexOf('\u0000') >= 0 || host.indexOf('/') >= 0 || host.indexOf('\\') >= 0
                || host.indexOf('@') >= 0 || host.indexOf('?') >= 0 || host.indexOf('#') >= 0
                || host.contains("://")) {
            return true;
        }
        for (int i = 0; i < host.length(); i++) {
            if (Character.isISOControl(host.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static String encodePathSegment(String value) {
        if (value.indexOf('/') >= 0 || value.indexOf('\\') >= 0 || value.contains("..")
                || value.indexOf('?') >= 0 || value.indexOf('#') >= 0
                || value.indexOf('\u0000') >= 0) {
            throw new CollectorAgentException(Kind.DETERMINISTIC, -1, "COLLECTOR_CONFIG_PATH_FORBIDDEN");
        }
        byte[] utf8;
        try {
            ByteBuffer encoded = StandardCharsets.UTF_8.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(CharBuffer.wrap(value));
            utf8 = new byte[encoded.remaining()];
            encoded.get(utf8);
        } catch (CharacterCodingException error) {
            throw new CollectorAgentException(Kind.DETERMINISTIC, -1,
                    "COLLECTOR_CONFIG_PATH_FORBIDDEN");
        }
        StringBuilder result = new StringBuilder(value.length());
        for (byte current : utf8) {
            int c = current & 0xff;
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || c == '-' || c == '_' || c == '.' || c == '~') {
                result.append((char) c);
            } else {
                result.append('%');
                result.append(Character.toUpperCase(Character.forDigit((c >>> 4) & 0xf, 16)));
                result.append(Character.toUpperCase(Character.forDigit(c & 0xf, 16)));
            }
        }
        return result.toString();
    }

    private static void requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    public enum Kind {
        RETRYABLE,
        DETERMINISTIC,
        PROTOCOL
    }

    /** 不携带异常文本、响应正文或 URL 的稳定 Agent 错误。 */
    public static final class CollectorAgentException extends RuntimeException {
        private final Kind kind;
        private final int httpStatus;
        private final String stableCode;

        public CollectorAgentException(Kind kind, int httpStatus, String stableCode) {
            super(stableCode);
            this.kind = kind;
            this.httpStatus = httpStatus;
            this.stableCode = stableCode;
        }

        public Kind getKind() { return kind; }

        public int getHttpStatus() { return httpStatus; }

        public String getStableCode() { return stableCode; }
    }

    private static final class ResponseBodyTooLargeException extends RuntimeException {
    }
}
