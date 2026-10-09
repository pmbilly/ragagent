package com.ragagent.memory.mapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ragagent.memory.domain.MemoryItemEmbedding;
import com.ragagent.memory.domain.MemoryScope;
import com.ragagent.memory.domain.MemoryVectorHit;
import com.ragagent.memory.domain.MemoryVectorQuery;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Locale;
import com.ragagent.common.memory.MemoryKinds;
import com.ragagent.common.web.ZeroTimeSerializer;
import com.ragagent.memory.domain.MemoryItem;
import com.ragagent.memory.domain.MemoryVectors;

/**
 * 记忆**向量面**（item_embeddings 表 + pgvector 列）：写入/删除嵌入、按向量检索、
 * pgvector 列就绪探测与列缺失回退、库内/进程内两套排序，以及列存在性探测（JDBC 元数据）。
 *
 * <p>B130 自 {@link MemoryIndexStore} 外提（逐字搬迁）。复核判定：原「规模例外」的理由
 * （"六段同属索引侧读写一个关注点"）是**层次**论点而非**内聚**论点——向量面与话题统计、
 * 文档亲和、抽取进度分属**不同表家族**，天然接缝（本例印证：它是唯一带 JDBC 元数据探测的簇）。
 * 持有 {@link MemoryRepository} 回引以访问各 mapper 与方言判定；本类不得独立实例化。</p>
 */
final class MemoryVectorStore {

    private static final Logger log = LoggerFactory.getLogger(MemoryVectorStore.class);

    private final MemoryRepository repo;

    MemoryVectorStore(MemoryRepository repo) {
        this.repo = repo;
    }

    /**
     * 写向量，并在同一个事务里把它同步进
     * 数据库自己的 vector 列。
     *
     * <p>三项前置短路：{@code embedding == null}、{@code itemID == ""}、
     * {@code vector} 为 null 或长度为 0。</p>
     *
     * <p><b>输入快照</b>：{@code source_content} 非空时会先核对条目现在的
     * content/topic 是否仍是当初输入的那份——不是就**放弃写入**，
     * 免得一个慢的 embedding 调用覆盖掉更新的编辑。</p>
     */
    public void upsertItemEmbedding(MemoryScope scope, MemoryItemEmbedding embedding) {
        if (embedding == null || embedding.getItemId().isEmpty()
                || embedding.getVector() == null || embedding.getVector().length == 0) {
            return;
        }
        embedding.setTenantId(scope.tenantId());
        embedding.setSubjectId(scope.subjectId());
        OffsetDateTime now = OffsetDateTime.now();
        embedding.setUpdatedAt(now);
        if (ZeroTimeSerializer.isZeroValue(embedding.getCreatedAt())) {
            embedding.setCreatedAt(now);
        }

        repo.tx.withSubject(scope, subject -> {
            if (!embedding.getSourceContent().isEmpty()) {
                MemoryItem current = repo.itemMapper.selectScoped(scope.tenantId(), scope.subjectId(),
                        embedding.getItemId());
                if (current == null) {
                    return null;
                }
                if (!current.getContent().equals(embedding.getSourceContent())
                        || !current.getTopic().equals(embedding.getSourceTopic())) {
                    return null;
                }
            }
            if (repo.postgres) {
                repo.embeddingMapper.upsertPostgres(embedding);
            } else {
                // H2 走"先删后插"。为对齐 PG 的 DO UPDATE **不重置 created_at**
                // （它的列集里没有 created_at），把已存在的那行的值读出来带上。
                MemoryItemEmbedding existing =
                        repo.embeddingMapper.selectByItemId(embedding.getItemId());
                if (existing != null) {
                    embedding.setCreatedAt(existing.getCreatedAt());
                }
                repo.embeddingMapper.deleteByItemId(scope.tenantId(), scope.subjectId(), embedding.getItemId());
                repo.embeddingMapper.insertRow(embedding);
            }
            // 上面那个 blob 是每个部署都会读的；这一份是同一个向量的"数据库能排序"的形态。
            // 同一事务里写，所以一行永远不会以"可搜索但向量是旧的"状态存在。
            writeVectorColumn(embedding.getItemId(), embedding.getVector());
            return null;
        });
    }

    /**
     * 删掉一条记忆的向量，好让补扫重建它。
     *
     * <p>一条记忆该嵌入什么，在写下之后是可能变的（一条兴趣嵌入的是
     * 它主题的其它说法），删掉向量就是"请既有补扫重建"的表达方式。</p>
     */
    public void deleteItemEmbedding(MemoryScope scope, String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            return;
        }
        repo.embeddingMapper.deleteByItemId(scope.tenantId(), scope.subjectId(), itemId);
    }

    /**
     * 按 item id 取向量。
     *
     * <p>只返回 {@code modelId} 产出的向量——不同模型的向量不可比，
     * 混在一起算出来的就是胡说。</p>
     *
     * <p>⚠️ 解码后长度为 0 的行被**跳过**，
     * 不会以空数组的形式出现在返回值里。</p>
     */
    public Map<String, float[]> itemEmbeddings(MemoryScope scope, List<String> itemIds, String modelId) {
        if (itemIds == null || itemIds.isEmpty() || modelId == null || modelId.isEmpty()) {
            return null;
        }
        List<MemoryItemEmbedding> rows = repo.embeddingMapper.selectByItemIds(scope.tenantId(),
                scope.subjectId(), itemIds, modelId);
        Map<String, float[]> vectors = new HashMap<>();
        for (MemoryItemEmbedding row : rows) {
            float[] vector = MemoryVectors.decodeEmbedding(row.getVector());
            if (vector != null && vector.length > 0) {
                vectors.put(row.getItemId(), vector);
            }
        }
        return vectors;
    }

    /**
     * 把一个主体**全部**向量对着一次查询排序。
     *
     * <p>候选集是这个主体拥有的每一个向量，不是其中一个窗口。这个区别是关键：
     * 上一版实现按重要度列出条目、再给列出来的那些打分——于是相关性只能重排
     * 重要度已经选好的东西，窗口之外一条匹配的记忆根本够不到。</p>
     *
     * <p>条目是**另外查一次**而不是 join 进排名查询：这样两条路径都不必重复
     * {@code memory_items} 的列清单，排名查询也窄到能一眼读完。</p>
     */
    public List<MemoryVectorHit> searchItemsByVector(MemoryScope scope, MemoryVectorQuery query) {
        if (!scope.valid() || query.unusable()) {
            return null;
        }
        int limit = query.effectiveLimit();

        List<VectorHitRow> rows = vectorColumnReady()
                ? rankInDatabase(scope, query, limit)
                : rankInProcess(scope, query, limit);
        if (rows == null || rows.isEmpty()) {
            return null;
        }

        List<String> ids = new ArrayList<>();
        for (VectorHitRow row : rows) {
            if (row.getScore() >= query.minScore()) {
                ids.add(row.getItemId());
            }
        }
        if (ids.isEmpty()) {
            return null;
        }

        List<MemoryItem> items = repo.itemMapper.selectByIds(scope.tenantId(), scope.subjectId(), ids);
        Map<String, MemoryItem> byId = new HashMap<>();
        for (MemoryItem item : items) {
            byId.put(item.getId(), item);
        }

        List<MemoryVectorHit> hits = new ArrayList<>(ids.size());
        for (VectorHitRow row : rows) {
            MemoryItem item = byId.get(row.getItemId());
            if (item == null) {
                continue;
            }
            hits.add(new MemoryVectorHit(item, row.getScore()));
        }
        return hits;
    }

    /**
     * 向量列回填：把早于迁移 000095 写下的行搬进数据库的 vector 类型。
     *
     * <p>一条 SQL 排名看不见的行，就是一条语义召回找不到的记忆——所以这件事必须自己排干，
     * 不能等下次调用 embedding 模型。**没有任何模型调用**：向量已经存在，
     * 落后的只是它的表示形式。</p>
     *
     * <p>{@code writeVectorColumn} 失败时**只记日志并继续**，
     * 所以返回的 moved 可能小于实际扫描到的行数。</p>
     */
    public int syncVectorColumn(MemoryScope scope, int limit) {
        if (!vectorColumnReady() || !scope.valid()) {
            return 0;
        }
        int effectiveLimit = limit <= 0 ? 500 : limit;
        List<MemoryItemEmbedding> rows = repo.embeddingMapper.selectPendingVectorColumn(
                scope.tenantId(), scope.subjectId(), effectiveLimit);
        int moved = 0;
        for (MemoryItemEmbedding row : rows) {
            try {
                writeVectorColumn(row.getItemId(), row.getVector());
            } catch (RuntimeException e) {
                log.warn("memory: sync vector column failed for {}: {}", row.getItemId(), e.getMessage());
                continue;
            }
            moved++;
        }
        return moved;
    }

    /**
     * 数据库能不能自己做距离运算。
     *
     * <p>只探一次并缓存。没有装 pgvector 的 PostgreSQL 部署上是 false，
     * 两条路径都仍然可用，只是每个向量都要过一遍网络——这也正是它必须被缓存的原因。</p>
     *
     * <p>判据：方言必须是 postgres，**且** {@code memory_item_embeddings}
     * 上有 {@code embedding} 列（迁移 000095 是条件执行的，光看方言决定不了）。</p>
     */
    boolean vectorColumnReady() {
        if (!repo.vectorProbed) {
            synchronized (this) {
                if (!repo.vectorProbed) {
                    repo.vectorColumn = repo.postgres && columnExists("memory_item_embeddings", "embedding");
                    repo.vectorProbed = true;
                }
            }
        }
        return repo.vectorColumn;
    }

    /**
     * 让数据库自己的 vector 类型跟上那个 blob。
     *
     * <p>在调用方的事务里尽力而为：**blob 才是源真值**，vector 列落后的行
     * 会被 {@link #syncVectorColumn} 捡回来，不会丢。</p>
     */
    private void writeVectorColumn(String itemId, byte[] raw) {
        if (!vectorColumnReady() || itemId == null || itemId.isEmpty()) {
            return;
        }
        String literal = MemoryVectors.formatEmbeddingLiteral(MemoryVectors.decodeEmbedding(raw));
        if (literal.isEmpty()) {
            return;
        }
        repo.embeddingMapper.writeVectorColumn(itemId, literal);
    }

    private List<VectorHitRow> rankInDatabase(MemoryScope scope, MemoryVectorQuery query, int limit) {
        String literal = MemoryVectors.formatEmbeddingLiteral(query.vector());
        if (literal.isEmpty()) {
            return List.of();
        }
        return repo.embeddingMapper.rankInDatabase(scope.tenantId(), scope.subjectId(), query.modelId(),
                query.vector().length, query.kinds(), MemoryKinds.STATUS_ACTIVE,
                OffsetDateTime.now(), literal, limit);
    }

    /**
     * 不依赖 pgvector 的那条路——把这个主体的向量读出来、在这里打分。
     *
     * <p>它受与主体本身相同的容量上限约束，所以**仍然看得到全部**，
     * 只是要付传输的代价。</p>
     */
    private List<VectorHitRow> rankInProcess(MemoryScope scope, MemoryVectorQuery query, int limit) {
        List<MemoryItemEmbedding> stored = repo.embeddingMapper.selectScopedVectors(scope.tenantId(),
                scope.subjectId(), query.modelId(), query.vector().length, query.kinds(),
                MemoryKinds.STATUS_ACTIVE, OffsetDateTime.now(), MemoryRepository.FALLBACK_VECTOR_SCAN_CAP);

        List<VectorHitRow> rows = new ArrayList<>(stored.size());
        for (MemoryItemEmbedding row : stored) {
            float[] vector = MemoryVectors.decodeEmbedding(row.getVector());
            if (vector == null || vector.length == 0) {
                continue;
            }
            VectorHitRow hit = new VectorHitRow();
            hit.setItemId(row.getItemId());
            hit.setScore(MemoryVectors.cosineSimilarity(query.vector(), vector));
            rows.add(hit);
        }
        sortVectorHitsDesc(rows);
        if (rows.size() > limit) {
            return new ArrayList<>(rows.subList(0, limit));
        }
        return rows;
    }

    /**
     * 按 score 降序的**稳定**排序。
     *
     * <p>稳定排序保证相同分数的相对次序不变。
     * （NaN 会让"严格弱序"不成立；实际不可达——{@code cosineSimilarity}
     * 在所有退化输入上都提前回 0，而 NaN 需要输入向量里本身含 NaN。）</p>
     */
    private static void sortVectorHitsDesc(List<VectorHitRow> rows) {
        rows.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
    }

    /**
     * JDBC 元数据探列。
     *
     * <p>PG 的 {@code getColumns} 要小写表名、H2 要大写——两轮都试一遍，
     * 都比调用方的方言猜测可靠。探测失败记日志并按"没有这一列"处理（保守：
     * 退到内存兜底排名，功能不丢，只是慢）。</p>
     */
    private boolean columnExists(String table, String column) {
        for (String[] pair : new String[][]{
                {table.toLowerCase(Locale.ROOT), column.toLowerCase(Locale.ROOT)},
                {table.toUpperCase(Locale.ROOT), column.toUpperCase(Locale.ROOT)}}) {
            try (Connection c = repo.dataSource.getConnection();
                 ResultSet rs = c.getMetaData().getColumns(null, null, pair[0], pair[1])) {
                if (rs.next()) {
                    return true;
                }
            } catch (SQLException e) {
                log.warn("memory: failed to probe column {}.{}: {}", table, column, e.getMessage());
            }
        }
        return false;
    }
}
