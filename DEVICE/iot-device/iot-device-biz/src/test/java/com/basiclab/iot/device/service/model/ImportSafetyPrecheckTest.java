package com.basiclab.iot.device.service.model;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TD-005 §11.3：导入安全预检（Schema 外语义合同）。
 * 恶意 OOXML/JSON fixture 由测试内联构造（ZIP 字节级），保证自描述、可评审。
 */
class ImportSafetyPrecheckTest {

    private final ImportSafetyPrecheck precheck = new ImportSafetyPrecheck();

    // ---------- OOXML ----------

    @Test
    void cleanWorkbookPasses() {
        assertDoesNotThrow(() -> precheck.scanXlsx(workbook(
                entry("[Content_Types].xml", "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"/>"),
                entry("_rels/.rels", "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"/>"),
                entry("xl/workbook.xml", "<workbook/>"),
                entry("xl/worksheets/sheet1.xml", "<worksheet><sheetData><row><c r=\"A1\" t=\"inlineStr\"/></row></sheetData></worksheet>")
        )));
    }

    @Test
    void nonZipContentIsRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> precheck.scanXlsx("not a zip".getBytes(StandardCharsets.UTF_8)));
        assertTrue(error.getMessage().startsWith("MODEL_IMPORT_UNSAFE_WORKBOOK"));
    }

    @Test
    void macroProjectIsRejected() {
        assertUnsafe(workbook(
                entry("[Content_Types].xml", "<Types/>"),
                entry("xl/vbaProject.bin", "macro-bytes")));
    }

    @Test
    void oleEmbeddingIsRejected() {
        assertUnsafe(workbook(
                entry("[Content_Types].xml", "<Types/>"),
                entry("xl/embeddings/oleObject1.bin", "ole")));
    }

    @Test
    void externalLinksAndConnectionsAreRejected() {
        assertUnsafe(workbook(
                entry("[Content_Types].xml", "<Types/>"),
                entry("xl/externalLinks/externalLink1.xml", "<externalLink/>")));
        assertUnsafe(workbook(
                entry("[Content_Types].xml", "<Types/>"),
                entry("xl/connections.xml", "<connections/>")));
        assertUnsafe(workbook(
                entry("[Content_Types].xml", "<Types/>"),
                entry("xl/worksheets/_rels/sheet1.xml.rels",
                        "<Relationships><Relationship Target=\"http://evil.example/x\" TargetMode=\"External\"/></Relationships>")));
    }

    @Test
    void pivotCacheAndQueryTablesAreRejected() {
        assertUnsafe(workbook(
                entry("[Content_Types].xml", "<Types/>"),
                entry("xl/pivotCache/pivotCacheDefinition1.xml", "<pivotCacheDefinition/>")));
        assertUnsafe(workbook(
                entry("[Content_Types].xml", "<Types/>"),
                entry("xl/queryTables/queryTable1.xml", "<queryTable/>")));
    }

    @Test
    void pathTraversalEntryIsRejected() {
        assertUnsafe(workbook(
                entry("[Content_Types].xml", "<Types/>"),
                entry("../../evil.xml", "<evil/>")));
    }

    @Test
    void formulaCellIsRejectedWithDedicatedError() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> precheck.scanXlsx(workbook(
                        entry("[Content_Types].xml", "<Types/>"),
                        entry("xl/worksheets/sheet1.xml",
                                "<worksheet><sheetData><row><c r=\"A1\"><f>SUM(A2:A3)</f><v>3</v></c></row></sheetData></worksheet>"))));
        assertTrue(error.getMessage().startsWith("MODEL_IMPORT_FORMULA_NOT_ALLOWED"));
    }

    @Test
    void calcChainImpliesFormulasAndIsRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> precheck.scanXlsx(workbook(
                        entry("[Content_Types].xml", "<Types/>"),
                        entry("xl/calcChain.xml", "<calcChain/>"))));
        assertTrue(error.getMessage().startsWith("MODEL_IMPORT_FORMULA_NOT_ALLOWED"));
    }

    @Test
    void zipBombRatioIsRejected() {
        StringBuilder huge = new StringBuilder("<worksheet>");
        for (int i = 0; i < 200_000; i++) {
            huge.append("aaaaaaaaaa");
        }
        huge.append("</worksheet>");
        assertUnsafe(workbook(
                entry("[Content_Types].xml", "<Types/>"),
                entry("xl/worksheets/sheet1.xml", huge.toString())));
    }

    @Test
    void tooManyEntriesAreRejected() throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buffer)) {
            zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zip.write("<Types/>".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            for (int i = 0; i < 1100; i++) {
                zip.putNextEntry(new ZipEntry("xl/filler" + i + ".xml"));
                zip.write("<x/>".getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        assertUnsafe(buffer.toByteArray());
    }

    // ---------- JSON ----------

    @Test
    void cleanJsonPasses() {
        assertDoesNotThrow(() -> precheck.scanJson(
                "{\"templateCode\":\"standard-meter\",\"properties\":[]}".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void bomIsRejected() {
        byte[] bom = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, '{', '}'};
        assertMalformedJson(bom);
    }

    @Test
    void invalidUtf8IsRejected() {
        byte[] broken = new byte[]{'{', (byte) 0xC3, 0x28, '}'};
        assertMalformedJson(broken);
    }

    @Test
    void untrustedSchemaReferenceIsRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> precheck.scanJson(
                        "{\"$schema\":\"http://evil.example/schema.json\"}".getBytes(StandardCharsets.UTF_8)));
        assertTrue(error.getMessage().startsWith("MODEL_IMPORT_UNTRUSTED_SCHEMA_REFERENCE"));
    }

    @Test
    void trustedSchemaReferencePasses() {
        assertDoesNotThrow(() -> precheck.scanJson(
                "{\"$schema\":\"https://easyaiot.local/schemas/power-model-template/1.0.0\"}"
                        .getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void excessiveNestingIsRejected() {
        StringBuilder deep = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            deep.append("{\"a\":");
        }
        deep.append("1");
        for (int i = 0; i < 200; i++) {
            deep.append("}");
        }
        assertMalformedJson(deep.toString().getBytes(StandardCharsets.UTF_8));
    }

    // ---------- helpers ----------

    private void assertUnsafe(byte[] workbookBytes) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> precheck.scanXlsx(workbookBytes));
        assertTrue(error.getMessage().startsWith("MODEL_IMPORT_UNSAFE_WORKBOOK"),
                "应为稳定安全错误，实际: " + error.getMessage());
    }

    private void assertMalformedJson(byte[] jsonBytes) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> precheck.scanJson(jsonBytes));
        assertTrue(error.getMessage().startsWith("MODEL_IMPORT_JSON_MALFORMED"),
                "应为稳定安全错误，实际: " + error.getMessage());
    }

    private static Entry entry(String name, String content) {
        return new Entry(name, content);
    }

    private static byte[] workbook(Entry... entries) {
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(buffer)) {
                for (Entry entry : entries) {
                    zip.putNextEntry(new ZipEntry(entry.name));
                    zip.write(entry.content.getBytes(StandardCharsets.UTF_8));
                    zip.closeEntry();
                }
            }
            return buffer.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static final class Entry {
        private final String name;
        private final String content;

        private Entry(String name, String content) {
            this.name = name;
            this.content = content;
        }
    }
}
