package com.ragagent.datasource.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.ragagent.platform.security.SsrfGuard;
import com.ragagent.common.knowledge.domain.Knowledge;
import com.ragagent.common.knowledge.domain.KnowledgeBase;
import com.ragagent.knowledge.storage.LocalStorageService;
import com.ragagent.common.knowledge.task.KnowledgeProcessingQueue;
import com.sun.net.httpserver.HttpServer;
import com.ragagent.common.knowledge.KnowledgeCatalogPort;

/**
 * 数据源 URL 摄取（整改 K2 /）：下载在同步期完成、与文件行同形。
 *
 * <p>历史缺口：原实现只写 {@code source=url} + {@code filePath=""} 就入队，而 worker 对
 * 非 manual 只读 filePath ⇒ 空路径必被存储层拒绝（整条链是死的）。这里用真实 HTTP 回环
 * 服务 + mock 数据访问面，断言"落行形态 = 文件行"与 SSRF 前置校验。</p>
 */
class MapperKnowledgeBridgeUrlIngestTest {

    private HttpServer server;
    private SsrfGuard.Whitelist snapshot;

    @BeforeEach
    void snapshotWhitelist() {
        snapshot = SsrfGuard.snapshotWhitelist();
    }

    @AfterEach
    void restore() {
        SsrfGuard.restoreWhitelist(snapshot);
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("URL 条目在同步期下载并落成文件行（filePath/hash/size/source 齐备 + 入队）")
    void urlIngestDownloadsAndPersistsFileRow() throws Exception {
        SsrfGuard.installWhitelist("127.0.0.1", "");
        byte[] body = "# hello".getBytes(StandardCharsets.UTF_8);
        int port = startServer("/doc.md", body);
        String url = "http://127.0.0.1:" + port + "/doc.md";

        KnowledgeCatalogPort catalog = mock(KnowledgeCatalogPort.class);
        when(catalog.findKnowledgeBaseRow(anyString()))
                .thenReturn(kb(42L, "kb-1"));
        when(catalog.saveKnowledgeFile(anyLong(), anyString(), anyString(), any(byte[].class)))
                .thenReturn("kb-1/doc.md");
        KnowledgeProcessingQueue worker = mock(KnowledgeProcessingQueue.class);

        Knowledge created = bridge(catalog, worker)
                .createFromUrl(42L, "kb-1", url, null, "标题", List.of(), "ima");

        ArgumentCaptor<Knowledge> captor = ArgumentCaptor.forClass(Knowledge.class);
        verify(catalog).insertKnowledge(captor.capture());
        Knowledge row = captor.getValue();
        assertThat(row.getFilePath()).as("URL 行必须与文件行同形（关键断言）").isEqualTo("kb-1/doc.md");
        assertThat(row.getFileHash()).isEqualTo(LocalStorageService.md5Hex(body));
        assertThat(row.getFileSize()).isEqualTo(body.length);
        assertThat(row.getSource()).isEqualTo(url);
        assertThat(row.getFileName()).isEqualTo("doc.md");
        assertThat(row.getFileType()).isEqualTo("md");
        assertThat(row.getTitle()).isEqualTo("标题");
        assertThat(row.getType()).isEqualTo("file");
        assertThat(row.getParseStatus()).isEqualTo(Knowledge.PARSE_PENDING);
        assertThat(created.getId()).isEqualTo(row.getId());
        verify(worker).enqueue(row.getId());
    }

    @Test
    @DisplayName("SSRF：受限主机在抓取前被拒，不落行、不入队")
    void ssrfRestrictedHostRejectedBeforeInsert() throws Exception {
        SsrfGuard.installWhitelist("", "");
        KnowledgeCatalogPort catalog = mock(KnowledgeCatalogPort.class);
        when(catalog.findKnowledgeBaseRow(anyString()))
                .thenReturn(kb(42L, "kb-1"));
        KnowledgeProcessingQueue worker = mock(KnowledgeProcessingQueue.class);

        MapperKnowledgeBridge bridge = bridge(catalog, worker);

        assertThatThrownBy(() -> bridge.createFromUrl(42L, "kb-1", "http://localhost/doc.md", null,
                null, List.of(), "ima"))
                .isInstanceOf(SsrfGuard.SsrfException.class);

        verify(catalog, never()).insertKnowledge(any(Knowledge.class));
        verifyNoInteractions(worker);
    }

    @Test
    @DisplayName("回归：createFromFile 行为不变（不写 source、metadata 落行）")
    void fileIngestUnchanged() throws Exception {
        KnowledgeCatalogPort catalog = mock(KnowledgeCatalogPort.class);
        when(catalog.findKnowledgeBaseRow(anyString()))
                .thenReturn(kb(42L, "kb-1"));
        when(catalog.saveKnowledgeFile(anyLong(), anyString(), anyString(), any(byte[].class)))
                .thenReturn("kb-1/a.txt");
        KnowledgeProcessingQueue worker = mock(KnowledgeProcessingQueue.class);

        bridge(catalog, worker).createFromFile(42L, "kb-1",
                "abc".getBytes(StandardCharsets.UTF_8), "a.txt", Map.of("externalId", "e1"),
                List.of(), "rss");

        ArgumentCaptor<Knowledge> captor = ArgumentCaptor.forClass(Knowledge.class);
        verify(catalog).insertKnowledge(captor.capture());
        Knowledge row = captor.getValue();
        assertThat(row.getSource()).isEmpty();
        assertThat(row.getFileName()).isEqualTo("a.txt");
        assertThat(row.getFilePath()).isEqualTo("kb-1/a.txt");
        assertThat(row.getMetadata().get("externalId").asText()).isEqualTo("e1");
    }

    // ── 夹具 ────────────────────────────────────────────────────────────────

    private MapperKnowledgeBridge bridge(KnowledgeCatalogPort catalog,
                                         KnowledgeProcessingQueue worker) throws Exception {
        Connection connection = mock(Connection.class);
        DatabaseMetaData meta = mock(DatabaseMetaData.class);
        when(meta.getDatabaseProductName()).thenReturn("H2");
        when(connection.getMetaData()).thenReturn(meta);
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenReturn(connection);
        return new MapperKnowledgeBridge(catalog, worker, new SsrfGuard(), dataSource);
    }

    private int startServer(String path, byte[] body) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(path, exchange -> {
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
        return server.getAddress().getPort();
    }

    private static KnowledgeBase kb(long tenantId, String kbId) {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setId(kbId);
        kb.setTenantId(tenantId);
        kb.setEmbeddingModelId("em-1");
        return kb;
    }
}
