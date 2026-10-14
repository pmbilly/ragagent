package com.ragagent.knowledge.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.ragagent.contract.doc.DocReaderAdminPort;
import com.ragagent.knowledge.client.DocReaderClient;
import com.ragagent.knowledge.config.DocReaderProperties;

/**
 * {@link DocReaderAdminPort} 的 knowledge 侧实现：把管理面四件事转发给
 * {@link DocReaderClient} 与 {@link DocReaderProperties}。
 *
 * <p>配置视图的成形口径与迁移前 {@code SystemController} 的私有辅助方法逐字一致
 * （{@code addr} 原值去空白、{@code transport} 走 {@code transportOrDefault()}）。</p>
 */
@Component
public class DocReaderAdminAdapter implements DocReaderAdminPort {

    private final DocReaderClient client;
    private final DocReaderProperties properties;

    public DocReaderAdminAdapter(DocReaderClient client, DocReaderProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public boolean isConnected() {
        return client.isConnected();
    }

    @Override
    public void reconnect(String addr) {
        client.reconnect(addr);
    }

    @Override
    public List<RemoteEngineView> listEngines(Map<String, String> overrides) {
        List<DocReaderClient.RemoteEngine> engines = client.listEngines(overrides);
        if (engines == null) {
            return null;
        }
        List<RemoteEngineView> out = new ArrayList<>(engines.size());
        for (DocReaderClient.RemoteEngine e : engines) {
            out.add(new RemoteEngineView(e.name(), e.description(), e.fileTypes(),
                    e.available(), e.unavailableReason()));
        }
        return out;
    }

    @Override
    public DocReaderConfigView config() {
        return new DocReaderConfigView(
                properties.addr() == null ? "" : properties.addr().trim(),
                properties.transportOrDefault());
    }
}
