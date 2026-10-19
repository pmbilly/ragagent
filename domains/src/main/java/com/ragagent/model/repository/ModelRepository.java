package com.ragagent.model.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ragagent.model.domain.Model;
import com.ragagent.model.mapper.ModelMapper;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Repository;

/** 模型仓储门面：可见性查询、写入、软删与内置模型的桶内默认清理。 */
@Repository
public class ModelRepository {

    private final ModelMapper mapper;

    public ModelRepository(ModelMapper mapper) {
        this.mapper = mapper;
    }

    public void insert(Model model) {
        mapper.insert(model);
    }

    public void updateById(Model model) {
        mapper.updateById(model);
    }

    /** 按 id 取（**不过滤软删**；内置模型对账需要看到已软删的手工行）。 */
    public Model byIdIncludingDeleted(String id) {
        return mapper.selectById(id);
    }

    /** 租户可见：本租户行 + 内置行。 */
    public Model findVisible(long tenantId, String id) {
        return mapper.selectOne(new LambdaQueryWrapper<Model>()
                .eq(Model::getId, id)
                .and(w -> w.eq(Model::getTenantId, tenantId).or().eq(Model::isIsBuiltin, true))
                .isNull(Model::getDeletedAt)
                .last("LIMIT 1"));
    }

    /** 租户可见列表：本租户行 + 内置行。 */
    public List<Model> listVisible(long tenantId) {
        return mapper.selectList(new LambdaQueryWrapper<Model>()
                .and(w -> w.eq(Model::getTenantId, tenantId).or().eq(Model::isIsBuiltin, true))
                .isNull(Model::getDeletedAt));
    }

    /** 软删指定租户下的模型。 */
    public void softDelete(String id, long tenantId, OffsetDateTime now) {
        mapper.update(null, new LambdaUpdateWrapper<Model>()
                .eq(Model::getId, id)
                .eq(Model::getTenantId, tenantId)
                .set(Model::getDeletedAt, now));
    }

    /** 软删单个 id（内置模型漂移清扫）。 */
    public void softDeleteById(String id, OffsetDateTime now) {
        mapper.update(null, new LambdaUpdateWrapper<Model>()
                .eq(Model::getId, id)
                .set(Model::getDeletedAt, now));
    }

    /** YAML 托管且未软删的行（漂移清扫的对照集）。 */
    public List<Model> listManagedBy(String managedBy) {
        return mapper.selectList(new LambdaQueryWrapper<Model>()
                .eq(Model::getManagedBy, managedBy)
                .isNull(Model::getDeletedAt));
    }

    /** 同 (tenant, type) 桶内清掉其他行的默认标记。 */
    public void clearDefaultForBucket(long tenantId, String type, String exceptId) {
        mapper.update(null, new LambdaUpdateWrapper<Model>()
                .eq(Model::getTenantId, tenantId)
                .eq(Model::getType, type)
                .ne(Model::getId, exceptId)
                .eq(Model::isIsDefault, true)
                .set(Model::isIsDefault, false));
    }

    /**
     * 按主键整行更新（非空字段全量 SET）。
     *
     * <p>调用方需先把 {@code createdAt} 置 null——更新列集不含它，已存在行的创建时间
     * 才不会被每次启动改写。</p>
     */
    public int updateByIdNonNull(Model model) {
        return mapper.update(model, new LambdaUpdateWrapper<Model>().eq(Model::getId, model.getId()));
    }
}
