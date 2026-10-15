package com.ragagent.contract.doc;

import java.util.List;
import java.util.Map;

/**
 * 文档解析服务（DocReader）的管理端口：连接态、重连、远端引擎清单与连接配置读取。
 *
 * <p><b>为什么需要它</b>：{@code system} 的系统管理面（解析引擎清单页、重连动作）此前直连
 * 知识域的 {@code knowledge.client.DocReaderClient} 与 {@code knowledge.config.DocReaderProperties}
 * （域实现 + 域配置 ✗）。本端口只暴露管理面真正用到的东西：连接态三元（
 * {@code #isConnected()} / {@code #reconnect(String)} / {@code #listEngines(Map)}）与配置视图
 * （{@code #config()}）；解析面（{@code read}）不在本端口范围内。</p>
 *
 * <p>失败语义与迁移前一致：{@code #listEngines(Map)} 的连接不可用/查询失败以运行时异常表达，
 * 调用方按"无远端引擎"降级。</p>
 */
public interface DocReaderAdminPort {

    /** 连接对象是否存在（配置了地址即已连接——与客户端惰性连接语义一致）。 */
    boolean isConnected();

    /** 换地址重连。 */
    void reconnect(String addr);

    /** 远端解析引擎清单（{@code overrides} 为可选地址覆盖，非 null）。 */
    List<RemoteEngineView> listEngines(Map<String, String> overrides);

    /** 连接配置视图（{@code addr} 为原配置值去空白，{@code transport} 为归一后的传输名）。 */
    DocReaderConfigView config();

    /** 远端解析引擎视图。 */
    record RemoteEngineView(String name, String description, List<String> fileTypes,
                            boolean available, String unavailableReason) {
    }

    /** 连接配置视图。 */
    record DocReaderConfigView(String addr, String transport) {
    }
}
