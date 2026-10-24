package com.ragagent.retrieval.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ragagent.contract.vectorstore.ConnectionConfig;
import com.ragagent.contract.vectorstore.VectorStoreEngineRules;
import com.ragagent.platform.security.SsrfGuard;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 控制面规则由引擎注册表提供：9 个引擎全覆盖、未知返回 null、qdrant 地址形态保持原样。
 */
class VectorStoreEngineCatalogTest {

    private final VectorStoreEngineCatalog catalog = new VectorStoreEngineCatalog(new SsrfGuard());

    private static final List<String> ENGINES = List.of(
            EngineTypes.ENGINE_ELASTICSEARCH,
            EngineTypes.ENGINE_POSTGRES,
            EngineTypes.ENGINE_SQLITE,
            EngineTypes.ENGINE_OPENSEARCH,
            EngineTypes.ENGINE_DORIS,
            EngineTypes.ENGINE_QDRANT,
            EngineTypes.ENGINE_WEAVIATE,
            EngineTypes.ENGINE_MILVUS,
            EngineTypes.ENGINE_TENCENT_VECTORDB);

    @Test
    void everyDeclaredEngineHasRules() {
        for (String engine : ENGINES) {
            assertNotNull(catalog.rulesFor(engine), engine);
        }
    }

    @Test
    void unknownEngineReturnsNull() {
        assertNull(catalog.rulesFor("nope"));
        assertNull(catalog.rulesFor(null));
    }

    @Test
    void elasticsearchRequiresAddrWithGoldenText() {
        VectorStoreEngineRules rules = catalog.rulesFor(EngineTypes.ENGINE_ELASTICSEARCH);
        assertEquals("addr is required for elasticsearch", rules.requiredFields().get(0).message());
        assertTrue(rules.requiredFields().get(0).satisfied().test(config("http://es:9200")));
    }

    @Test
    void qdrantControlAddressKeepsHostPortForm() {
        ConnectionConfig c = new ConnectionConfig();
        c.host = "qdrant";
        c.port = 6334;
        VectorStoreEngineRules rules = catalog.rulesFor(EngineTypes.ENGINE_QDRANT);
        assertEquals("qdrant:6334", rules.addressFields().get(0).value().apply(c));
    }

    private static ConnectionConfig config(String addr) {
        ConnectionConfig c = new ConnectionConfig();
        c.addr = addr;
        return c;
    }
}
