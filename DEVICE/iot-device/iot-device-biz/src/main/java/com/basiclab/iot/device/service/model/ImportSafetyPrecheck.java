package com.basiclab.iot.device.service.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * TD-005 §11.3：导入安全预检（进入 EasyExcel / Schema 校验之前）。
 * OOXML 只接受无宏 .xlsx：ZIP 结构、容量与恶意内容逐项拒绝；解析器不计算
 * 公式、不访问外部网络或资源。JSON 要求严格 UTF-8 无 BOM、有界嵌套深度、
 * $schema 引用必须指向可信前缀。限额为候选值（冻结前需评审）。Java 8 兼容。
 */
public final class ImportSafetyPrecheck {

    // 候选限额（§11.3 要求限制，具体值待 DBA/架构评审冻结）
    private static final long MAX_RAW_BYTES = 20L * 1024 * 1024;
    private static final long MAX_EXPANDED_BYTES = 100L * 1024 * 1024;
    private static final long MAX_ENTRIES = 1000;
    private static final long MAX_COMPRESSION_RATIO = 100;
    private static final long RATIO_CHECK_MIN_EXPANDED = 1024 * 1024;
    private static final long MAX_JSON_BYTES = 10L * 1024 * 1024;
    private static final int MAX_JSON_DEPTH = 64;
    private static final String TRUSTED_SCHEMA_PREFIX = "https://easyaiot.local/schemas/";

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ---------- OOXML ----------

    public void scanXlsx(byte[] content) {
        if (content == null || content.length < 4
                || content[0] != 'P' || content[1] != 'K' || content[2] != 3 || content[3] != 4) {
            throw unsafe("不是合法的 OOXML ZIP 工件");
        }
        if (content.length > MAX_RAW_BYTES) {
            throw unsafe("原始大小超过上限");
        }
        long totalExpanded = 0;
        long entries = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > MAX_ENTRIES) {
                    throw unsafe("ZIP entry 数超过上限");
                }
                checkEntryName(entry.getName());
                byte[] data = readBounded(zip, entry.getName());
                totalExpanded += data.length;
                if (totalExpanded > MAX_EXPANDED_BYTES) {
                    throw unsafe("展开大小超过上限");
                }
                scanEntryContent(entry.getName(), data);
            }
        } catch (IOException e) {
            throw unsafe("ZIP 结构损坏: " + e.getClass().getSimpleName());
        }
        if (totalExpanded > RATIO_CHECK_MIN_EXPANDED
                && totalExpanded / Math.max(1, content.length) > MAX_COMPRESSION_RATIO) {
            throw unsafe("压缩比异常（疑似 ZIP 炸弹）");
        }
    }

    private void checkEntryName(String rawName) {
        String name = rawName.replace('\\', '/');
        if (name.startsWith("/") || name.matches("^[A-Za-z]:/.*") || name.contains("../")
                || name.equals("..") || name.contains("/..")) {
            throw unsafe("非法 entry 路径: " + rawName);
        }
        if (name.endsWith("vbaProject.bin")) {
            throw unsafe("包含宏工程 " + name);
        }
        if (name.startsWith("xl/embeddings/") || name.contains("oleObject")) {
            throw unsafe("包含 OLE 嵌入 " + name);
        }
        if (name.startsWith("xl/externalLinks/")) {
            throw unsafe("包含外部链接 " + name);
        }
        if (name.equals("xl/connections.xml") || name.contains("queryTable")) {
            throw unsafe("包含数据连接/查询表 " + name);
        }
        if (name.contains("pivotCache") || name.startsWith("xl/pivotTables/")) {
            throw unsafe("包含 PivotTable/PivotCache " + name);
        }
        if (name.startsWith("xl/activeX/") || name.contains("ctrlProp")) {
            throw unsafe("包含 ActiveX 控件 " + name);
        }
        if (name.equals("xl/calcChain.xml")) {
            throw formula("包含 calcChain（公式链）");
        }
    }

    private void scanEntryContent(String rawName, byte[] data) {
        String name = rawName.replace('\\', '/');
        if (name.endsWith(".rels")) {
            String text = new String(data, StandardCharsets.UTF_8);
            if (text.contains("TargetMode=\"External\"")) {
                throw unsafe("包含外部关系引用 " + name);
            }
        }
        if (name.startsWith("xl/worksheets/") && name.endsWith(".xml")) {
            String text = new String(data, StandardCharsets.UTF_8);
            if (text.contains("<f>") || text.contains("<f ")) {
                throw formula("工作表包含公式单元格 " + name);
            }
        }
    }

    private byte[] readBounded(ZipInputStream zip, String name) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int read;
        while ((read = zip.read(chunk)) != -1) {
            buffer.write(chunk, 0, read);
            if (buffer.size() > MAX_EXPANDED_BYTES) {
                throw unsafe("entry 展开大小超过上限: " + name);
            }
        }
        return buffer.toByteArray();
    }

    // ---------- JSON ----------

    public void scanJson(byte[] content) {
        if (content == null || content.length == 0) {
            throw malformed("空内容");
        }
        if (content.length > MAX_JSON_BYTES) {
            throw malformed("大小超过上限");
        }
        if (content.length >= 3 && (content[0] & 0xFF) == 0xEF
                && (content[1] & 0xFF) == 0xBB && (content[2] & 0xFF) == 0xBF) {
            throw malformed("包含 UTF-8 BOM（§15 要求无 BOM）");
        }
        String text = strictUtf8(content);
        JsonNode tree = parse(text);
        requireBoundedDepth(tree, 1);
        JsonNode schemaRef = tree.path("$schema");
        if (schemaRef.isTextual() && !schemaRef.textValue().startsWith(TRUSTED_SCHEMA_PREFIX)) {
            throw new IllegalArgumentException(
                    "MODEL_IMPORT_UNTRUSTED_SCHEMA_REFERENCE: 不可信 $schema 引用 " + schemaRef.textValue());
        }
    }

    private static String strictUtf8(byte[] content) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content)).toString();
        } catch (CharacterCodingException e) {
            throw malformed("不是严格 UTF-8");
        }
    }

    private JsonNode parse(String text) {
        try {
            return objectMapper.readTree(text);
        } catch (IOException e) {
            throw malformed("JSON 解析失败");
        }
    }

    private static void requireBoundedDepth(JsonNode node, int depth) {
        if (depth > MAX_JSON_DEPTH) {
            throw malformed("嵌套深度超过上限 " + MAX_JSON_DEPTH);
        }
        if (node.isObject()) {
            node.forEach(child -> requireBoundedDepth(child, depth + 1));
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                requireBoundedDepth(child, depth + 1);
            }
        }
    }

    private static IllegalArgumentException unsafe(String reason) {
        return new IllegalArgumentException("MODEL_IMPORT_UNSAFE_WORKBOOK: " + reason);
    }

    private static IllegalArgumentException formula(String reason) {
        return new IllegalArgumentException("MODEL_IMPORT_FORMULA_NOT_ALLOWED: " + reason);
    }

    private static IllegalArgumentException malformed(String reason) {
        return new IllegalArgumentException("MODEL_IMPORT_JSON_MALFORMED: " + reason);
    }
}
