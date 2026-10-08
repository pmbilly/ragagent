package com.ragagent.common.tenant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ragagent.common.tenant.Tenant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface TenantMapper extends BaseMapper<Tenant> {

    /**
     * 全表 UPDATE：默认存储配额统一应用到所有租户（管理员运维操作）。
     * <p>这是持久层规约「禁止无条件全局更新」的<b>显式登记例外</b>——全表写必须
     * 具名成 Mapper 方法并在 {@code FullTableWriteGuard.FULL_TABLE_ALLOWED} 登记，
     * 禁止再用 {@code update(null, 无条件 wrapper)} 匿名发起。</p>
     */
    @Update("UPDATE tenants SET storage_quota = #{quotaBytes}")
    int applyDefaultStorageQuota(@Param("quotaBytes") long quotaBytes);
}
