package com.ragagent.auth.repository;

import com.ragagent.auth.domain.Tenant;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;

/**
 * 租户管理端口（域侧）：租户行的查询、写入与软删。
 *
 * <p>与 {@link TenantLookup} 同属"域侧端口"面——返回<b>实体</b>，只允许 L3 业务域
 * （auth 的租户管理面）使用。持久层细节（MyBatis-Plus 条件构造、分页插件）由实现独占。</p>
 */
public interface TenantAdmin {

    /** 按 id 取未软删的行（不存在返回 null）。 */
    Tenant byId(long id);

    /** 按 id 取行，**不过滤软删**（存储桶唯一性校验的旧值读取用）。 */
    Tenant byIdIncludingDeleted(long id);

    /** 批量取未软删的行。 */
    List<Tenant> byIds(Collection<Long> ids);

    /** 全量未软删，created_at DESC。 */
    List<Tenant> listAll();

    /** 全量未软删（不排序；唯一性校验遍历用）。 */
    List<Tenant> listLive();

    /** 搜索命中数；关键字已由调用方转义。 */
    long count(String escapedKeyword, long tenantId);

    /** 搜索并分页（page&lt;=0 表示不分页）；created_at DESC。 */
    List<Tenant> search(String escapedKeyword, long tenantId, int page, int pageSize);

    /** 插入并回填主键。 */
    void insert(Tenant tenant);

    /** 整行按主键写回（null 字段跳过）。 */
    void updateById(Tenant tenant);

    /** 物理删除（创建失败回滚用）。 */
    void deleteById(long id);

    /** 软删：{@code deleted_at = now}（仅未软删行）。 */
    void softDeleteById(long id, OffsetDateTime now);
}
