package com.ragagent.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ragagent.system.domain.SystemSetting;
import org.apache.ibatis.annotations.Mapper;

/**
 * system_settings 表 Mapper。
 *
 * Upsert 语义（冲突时全列覆盖）在 service 层以「先 selectByKey 再 insert/updateById」
 * 两步实现（单实例 SystemAdmin 操作无并发窗口）。
 */
@Mapper
public interface SystemSettingMapper extends BaseMapper<SystemSetting> {
}
