package com.ragagent.contract.doc;

import java.util.List;
import java.util.Map;

/**
 * 解析引擎目录：本地静态注册表 + docreader 远端发现的合并结果。
 *
 * <p>管理面（{@code /system/parser-engines*}）只消费"有哪些引擎、是否可用、支持什么类型"，
 * 不关心 8 个内置引擎的定义与合并顺序——这些属知识域的解析能力，实现在
 * {@code knowledge.service.parser.ParserEngineCatalog}。</p>
 *
 * <p>载体复用 {@link DocReaderAdminPort.RemoteEngineView}：本地引擎与远端引擎的形状本就相同
 * （name / description / fileTypes / available / unavailableReason），不再建第二个近重复类型。</p>
 */
public interface ParserEngineCatalogPort {

    /**
     * 本地静态引擎 + 远端引擎合并。
     *
     * @param docreaderConnected docreader 服务是否可达
     * @param overrides          租户解析引擎覆盖配置
     * @param remoteEngines      远端清单（未连接/失败时为 null）
     */
    List<DocReaderAdminPort.RemoteEngineView> listAll(
            boolean docreaderConnected,
            Map<String, String> overrides,
            List<DocReaderAdminPort.RemoteEngineView> remoteEngines);

    /**
     * 远端引擎清单：未连接 → null（回落本地）；RPC 失败 → 记日志 + null。
     */
    List<DocReaderAdminPort.RemoteEngineView> fetchRemote(
            boolean docreaderConnected, Map<String, String> overrides);
}
