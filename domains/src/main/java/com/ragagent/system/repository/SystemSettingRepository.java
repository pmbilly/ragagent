package com.ragagent.system.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ragagent.system.domain.SystemSetting;
import com.ragagent.system.mapper.SystemSettingMapper;
import java.util.List;
import org.springframework.stereotype.Repository;

/** 系统设置仓储门面：全量读取、按键读写与删除。 */
@Repository
public class SystemSettingRepository {

    private final SystemSettingMapper mapper;

    public SystemSettingRepository(SystemSettingMapper mapper) {
        this.mapper = mapper;
    }

    /** 全部行（无过滤；调用方按注册表键集投影）。 */
    public List<SystemSetting> listAll() {
        return mapper.selectList(new LambdaQueryWrapper<>());
    }

    /** 按键取一条（{@code LIMIT 1}）。 */
    public SystemSetting findByKey(String key) {
        return mapper.selectOne(new LambdaQueryWrapper<SystemSetting>()
                .eq(SystemSetting::getKey, key)
                .last("LIMIT 1"));
    }

    public void insert(SystemSetting row) {
        mapper.insert(row);
    }

    public void updateById(SystemSetting row) {
        mapper.updateById(row);
    }

    /** 按键删除，返回受影响行数。 */
    public int deleteByKey(String key) {
        return mapper.delete(new LambdaQueryWrapper<SystemSetting>()
                .eq(SystemSetting::getKey, key));
    }
}
