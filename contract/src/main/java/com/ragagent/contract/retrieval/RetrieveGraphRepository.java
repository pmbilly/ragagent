package com.ragagent.contract.retrieval;

import com.ragagent.contract.graph.GraphData;
import com.ragagent.contract.graph.NameSpace;
import java.util.List;

/**
 * 图库的写（AddGraph）/删（DelGraph）/读（SearchNode）端口。
 *
 * <p>由 {@code retrieval-engine} 的 {@code graph.Neo4jGraphRepository} 提供真实实现
 * （NEO4J_ENABLE=true 才建驱动；否则 driver 为 null，三个操作都告警并静默）。
 * 流水线只用 {@link #searchNode}；写/删由抽取与清理链路调用。</p>
 */
public interface RetrieveGraphRepository {
    /**
     * 仓储的真实驱动是否已建（缺省 false）——供健康/状态面读，避免调用方
     * {@code instanceof} 具体实现。
     */
    default boolean enabled() {
        return false;
    }

    /** 把一批图写进仓库（driver 缺失 → 告警 + 静默返回）。 */
    void addGraph(NameSpace namespace, List<GraphData> graphs);

    /** 按命名空间删除（driver 缺失 → 告警 + 静默返回）。 */
    void delGraph(List<NameSpace> namespaces);

    /** 按节点名（CONTAINS）匹配并取回一跳子图。 */
    GraphData searchNode(NameSpace namespace, List<String> nodes);
}
