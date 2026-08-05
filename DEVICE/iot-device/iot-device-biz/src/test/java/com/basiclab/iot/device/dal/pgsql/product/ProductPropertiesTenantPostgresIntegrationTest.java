package com.basiclab.iot.device.dal.pgsql.product;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.basiclab.iot.common.config.TenantProperties;
import com.basiclab.iot.common.core.context.TenantContextHolder;
import com.basiclab.iot.common.core.db.TenantDatabaseInterceptor;
import com.basiclab.iot.device.dal.dataobject.product.ProductPropertyDO;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * TD-005 root-property tenant contract against a real PostgreSQL transaction.
 *
 * <p>The suite is opt-in because it requires the local integration instance. Every test uses a
 * unique fixture and rolls the transaction back; it never changes constraints or existing rows.</p>
 */
class ProductPropertiesTenantPostgresIntegrationTest {

    private static final String MAPPER_RESOURCE = "mapper/product/ProductPropertiesMapper.xml";
    private static final long TENANT_ONE = 910_005_001L;
    private static final long TENANT_TWO = 910_005_002L;

    private SqlSession session;
    private ProductPropertiesMapper mapper;

    @BeforeEach
    void openPostgresTransaction() throws Exception {
        assumeTrue(Boolean.parseBoolean(System.getenv("TD005_PG_ENABLED")),
                "Set TD005_PG_ENABLED=true to run the PostgreSQL tenant contract");
        String password = System.getenv("TD005_PG_PASSWORD");
        assumeTrue(password != null && !password.isBlank(),
                "Set TD005_PG_PASSWORD without committing credentials");

        String url = environmentOrDefault("TD005_PG_URL", "jdbc:postgresql://localhost:5432/iot-device20");
        String username = environmentOrDefault("TD005_PG_USERNAME", "postgres");
        PooledDataSource dataSource = new PooledDataSource("org.postgresql.Driver", url, username, password);

        Environment environment = new Environment("td005-postgres", new JdbcTransactionFactory(), dataSource);
        Configuration configuration = new Configuration(environment);
        configuration.setCacheEnabled(false);

        TenantProperties tenantProperties = new TenantProperties();
        tenantProperties.setIgnoreTables(Collections.emptySet());
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(
                new TenantDatabaseInterceptor(tenantProperties)));
        configuration.addInterceptor(interceptor);

        try (InputStream input = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(MAPPER_RESOURCE)) {
            assertNotNull(input, "Missing mapper resource: " + MAPPER_RESOURCE);
            new XMLMapperBuilder(input, configuration, MAPPER_RESOURCE,
                    configuration.getSqlFragments()).parse();
        }

        SqlSessionFactory factory = new SqlSessionFactoryBuilder().build(configuration);
        session = factory.openSession(false);
        mapper = session.getMapper(ProductPropertiesMapper.class);
    }

    @AfterEach
    void rollbackAndClearTenant() {
        TenantContextHolder.clear();
        if (session != null) {
            session.rollback(true);
            session.close();
        }
    }

    @Test
    void ten001ContextMustOverrideUntrustedTenantOnInsert() throws Exception {
        TenantContextHolder.setTenantId(TENANT_ONE);
        ProductPropertyDO property = fixture("ten001");
        property.setTenantId(TENANT_TWO);

        assertEquals(1, mapper.insert(property));
        assertNotNull(property.getId());
        assertEquals(TENANT_ONE, rawTenantId(property.getId()));
        assertEquals(TENANT_ONE, mapper.selectByPrimaryKey(property.getId()).getTenantId());
    }

    @Test
    void ten002AndTen003CrossTenantCrudMustNotRevealOrModifyRow() {
        TenantContextHolder.setTenantId(TENANT_ONE);
        ProductPropertyDO property = fixture("ten002-003");
        assertEquals(1, mapper.insert(property));

        TenantContextHolder.setTenantId(TENANT_TWO);
        session.clearCache();
        assertNull(mapper.selectByPrimaryKey(property.getId()));

        ProductPropertyDO update = ProductPropertyDO.builder()
                .id(property.getId())
                .propertyName("cross-tenant-update-must-not-apply")
                .build();
        assertEquals(0, mapper.updateByPrimaryKeySelective(update));
        assertEquals(0, mapper.deleteByPrimaryKey(property.getId()));

        TenantContextHolder.setTenantId(TENANT_ONE);
        session.clearCache();
        ProductPropertyDO unchanged = mapper.selectByPrimaryKey(property.getId());
        assertNotNull(unchanged);
        assertEquals(property.getPropertyName(), unchanged.getPropertyName());
    }

    @Test
    void ten004MixedTenantBatchDeleteMustBeAtomic() {
        TenantContextHolder.setTenantId(TENANT_ONE);
        ProductPropertyDO own = fixture("ten004-own");
        assertEquals(1, mapper.insert(own));

        TenantContextHolder.setTenantId(TENANT_TWO);
        ProductPropertyDO foreign = fixture("ten004-foreign");
        assertEquals(1, mapper.insert(foreign));

        TenantContextHolder.setTenantId(TENANT_ONE);
        session.clearCache();
        assertEquals(0, mapper.deleteProductPropertiesByIds(new Long[]{own.getId(), foreign.getId()}));
        session.clearCache();
        assertNotNull(mapper.selectByPrimaryKey(own.getId()));

        TenantContextHolder.setTenantId(TENANT_TWO);
        session.clearCache();
        assertNotNull(mapper.selectByPrimaryKey(foreign.getId()));
    }

    @Test
    void ten006MissingTenantContextMustFailClosedBeforeInsert() {
        TenantContextHolder.clear();
        RuntimeException error = assertThrows(RuntimeException.class,
                () -> mapper.insert(fixture("ten006")));
        assertTrue(hasCause(error, NullPointerException.class),
                "missing tenant context must fail through TenantContextHolder.getRequiredTenantId()");
    }

    private ProductPropertyDO fixture(String suffix) {
        String token = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return ProductPropertyDO.builder()
                .propertyName("TD005 " + suffix)
                .propertyCode("td005_" + token)
                .datatype("DOUBLE")
                .description("transactional integration fixture")
                .required(1)
                .step(1)
                .unit("unit")
                .productIdentification("td005-postgres-" + token)
                .build();
    }

    private Long rawTenantId(Long id) throws Exception {
        try (PreparedStatement statement = session.getConnection().prepareStatement(
                "SELECT tenant_id FROM product_properties WHERE id = ?")) {
            statement.setLong(1, id);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getLong(1);
            }
        }
    }

    private static String environmentOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static boolean hasCause(Throwable error, Class<? extends Throwable> type) {
        Throwable current = error;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
