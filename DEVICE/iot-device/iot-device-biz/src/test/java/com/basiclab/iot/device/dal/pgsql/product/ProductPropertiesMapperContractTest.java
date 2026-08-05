package com.basiclab.iot.device.dal.pgsql.product;

import com.basiclab.iot.device.dal.dataobject.product.ProductPropertyDO;
import com.basiclab.iot.device.domain.product.vo.request.ProductRootPropertySaveRequest;
import com.basiclab.iot.device.domain.product.vo.response.ProductRootPropertyResponse;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductPropertiesMapperContractTest {

    private static final String RESOURCE = "mapper/product/ProductPropertiesMapper.xml";

    private static final Set<String> APPROVED_COLUMNS = new LinkedHashSet<>(Arrays.asList(
            "id", "property_name", "property_code", "datatype", "description", "enumlist",
            "max", "maxlength", "method", "min", "required", "step", "unit",
            "create_by", "create_time", "update_by", "update_time",
            "template_identification", "product_identification", "tenant_id"));

    @Test
    void mapperMustUseApprovedRootPropertyShape() throws IOException {
        String xml = readResource();

        assertFalse(xml.contains("service_id"), "根属性 Mapper 不得读取或写入 service_id");
        assertFalse(xml.contains("findAllByServiceId"));
        assertFalse(xml.contains("selectPropertiesByServiceIdList"));

        String columnsBlock = extract(xml, "<sql id=\"Base_Column_List\">", "</sql>");
        Set<String> actualColumns = Arrays.stream(columnsBlock
                        .replaceAll("<!--.*?-->", "")
                        .replace("\"", "")
                        .split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        assertEquals(APPROVED_COLUMNS, actualColumns);

        String resultMap = extract(xml, "<resultMap id=\"BaseResultMap\"", "</resultMap>");
        Matcher matcher = Pattern.compile("<(?:id|result)\\s+column=\"([^\"]+)\"").matcher(resultMap);
        Set<String> mappedColumns = new LinkedHashSet<>();
        while (matcher.find()) {
            mappedColumns.add(matcher.group(1));
        }
        assertEquals(APPROVED_COLUMNS, mappedColumns);
        assertTrue(xml.contains("<otherwise>1 = 0</otherwise>"), "空集合查询/删除必须 fail closed");
    }

    @Test
    void persistenceAndNewApiObjectsMustKeepRootAndServiceFactsSeparated() throws Exception {
        assertNotNull(ProductPropertyDO.class.getDeclaredField("tenantId"));
        assertNotNull(ProductPropertyDO.class.getDeclaredField("productIdentification"));
        assertNotNull(ProductPropertyDO.class.getDeclaredField("templateIdentification"));
        assertFalse(hasField(ProductPropertyDO.class, "serviceId"));
        assertFalse(hasField(ProductRootPropertySaveRequest.class, "tenantId"));
        assertFalse(hasField(ProductRootPropertySaveRequest.class, "serviceId"));
        assertFalse(hasField(ProductRootPropertyResponse.class, "serviceId"));

        Set<String> mapperMethods = Arrays.stream(ProductPropertiesMapper.class.getDeclaredMethods())
                .map(Method::getName)
                .collect(Collectors.toSet());
        assertFalse(mapperMethods.contains("findAllByServiceId"));
        assertFalse(mapperMethods.contains("selectPropertiesByServiceIdList"));
    }

    @Test
    void everyPublicMapperMethodMustHaveAParsableStatement() throws IOException {
        Configuration configuration = parseMapper();

        String namespace = ProductPropertiesMapper.class.getName() + ".";
        for (Method method : ProductPropertiesMapper.class.getDeclaredMethods()) {
            assertTrue(configuration.hasStatement(namespace + method.getName()),
                    "Mapper 方法缺少 XML statement: " + method.getName());
        }
    }

    @Test
    void representativeDynamicSqlMustRenderFailClosedAndWithBalancedInsertValues() throws IOException {
        Configuration configuration = parseMapper();
        String namespace = ProductPropertiesMapper.class.getName() + ".";
        ProductPropertyDO entity = ProductPropertyDO.builder()
                .propertyName("环境温度")
                .propertyCode("temperature")
                .datatype("DOUBLE")
                .max("125")
                .min("-40")
                .required(1)
                .step(1)
                .productIdentification("legacy-non-power-demo")
                .build();

        BoundSql insert = configuration.getMappedStatement(namespace + "insert").getBoundSql(entity);
        String insertSql = normalize(insert.getSql());
        assertFalse(insertSql.contains("jdbcType="));
        assertFalse(insertSql.contains("service_id"));
        assertEquals(18, insert.getParameterMappings().size(), "18 个非 ID/tenant 列必须逐一绑定");

        BoundSql emptyIds = configuration.getMappedStatement(namespace + "selectPropertiesByPropertiesIdList")
                .getBoundSql(Collections.singletonMap("propertiesIdList", Collections.emptyList()));
        assertTrue(normalize(emptyIds.getSql()).contains("WHERE 1 = 0"));

        BoundSql mixedTenantDelete = configuration
                .getMappedStatement(namespace + "deleteProductPropertiesByIds")
                .getBoundSql(Collections.singletonMap("array", new Long[]{1L, 2L}));
        String mixedTenantDeleteSql = normalize(mixedTenantDelete.getSql());
        assertTrue(mixedTenantDeleteSql.contains(
                "scoped_properties.tenant_id = product_properties.tenant_id"),
                "批量删除的完整性计数必须显式限定到外层租户");
        assertFalse(mixedTenantDeleteSql.contains("${"), "批量计数不得使用文本替换参数");

        entity.setTemplateIdentification("template-must-not-mix");
        BoundSql mixedScope = configuration.getMappedStatement(namespace + "selectProductPropertiesList")
                .getBoundSql(entity);
        assertTrue(normalize(mixedScope.getSql()).contains("AND 1 = 0"));
    }

    private boolean hasField(Class<?> type, String name) {
        for (Field field : type.getDeclaredFields()) {
            if (field.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    private String readResource() throws IOException {
        try (InputStream input = Thread.currentThread().getContextClassLoader().getResourceAsStream(RESOURCE)) {
            assertNotNull(input, "缺少 Mapper XML: " + RESOURCE);
            byte[] bytes = new byte[8192];
            StringBuilder output = new StringBuilder();
            int count;
            while ((count = input.read(bytes)) != -1) {
                output.append(new String(bytes, 0, count, StandardCharsets.UTF_8));
            }
            return output.toString();
        }
    }

    private Configuration parseMapper() throws IOException {
        Configuration configuration = new Configuration();
        try (InputStream input = Thread.currentThread().getContextClassLoader().getResourceAsStream(RESOURCE)) {
            assertNotNull(input, "缺少 Mapper XML: " + RESOURCE);
            new XMLMapperBuilder(input, configuration, RESOURCE, configuration.getSqlFragments()).parse();
        }
        return configuration;
    }

    private String normalize(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }

    private String extract(String source, String startToken, String endToken) {
        int start = source.indexOf(startToken);
        assertTrue(start >= 0, "缺少片段: " + startToken);
        int contentStart = source.indexOf('>', start) + 1;
        int end = source.indexOf(endToken, contentStart);
        assertTrue(end >= 0, "缺少片段结束: " + endToken);
        return source.substring(contentStart, end);
    }
}
