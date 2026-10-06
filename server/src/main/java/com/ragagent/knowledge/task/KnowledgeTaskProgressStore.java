package com.ragagent.knowledge.task;

import com.ragagent.knowledge.dto.kb.KBCloneProgress;
import com.ragagent.knowledge.dto.doc.KnowledgeMoveProgress;

/**
 * move / clone 任务的进度端口（轮询端点读取）。
 *
 * <p><b>两种实现</b>：进程内（{@link InProcessKnowledgeTaskProgressStore}，
 * 缺省/单实例语义）；Redis（{@link RedisKnowledgeTaskProgressStore}，
 * {@code knowledge.redis-enabled=true} 时以 {@code @Primary} 生效——进度跨实例可见，
 * 轮询被负载均衡路由到任意副本都能读到）。</p>
 *
 * <p><b>两条写入口的语义</b>（契约样例锁定，两种实现都必须保真）：</p>
 * <ul>
 *   <li>{@code save*Initial}（handler 准入时）= Redis {@code SETNX} → {@code putIfAbsent}；
 *       只在键不存在时落「Task queued, waiting to start...」的初始进度；</li>
 *   <li>{@code save*}（worker 每步）= Redis {@code SET} → {@code put}，无条件覆写。
 *       响应里的 {@code created_at} 变成 0——这是源码行为（契约样例锁定），别"修好"。</li>
 * </ul>
 */
public interface KnowledgeTaskProgressStore {

    /** 准入时的初始 pending 进度（只在不存在时落）。 */
    void saveMoveInitial(KnowledgeMoveProgress progress);

    /** worker 每步覆写。 */
    void saveMove(KnowledgeMoveProgress progress);

    /** 过期 → null（调用方转 404）。 */
    KnowledgeMoveProgress getMove(String taskId);

    /** 准入时的初始 pending 进度（只在不存在时落）。 */
    void saveCloneInitial(KBCloneProgress progress);

    /** worker 每步覆写。 */
    void saveClone(KBCloneProgress progress);

    /** 过期 → null。 */
    KBCloneProgress getClone(String taskId);
}
