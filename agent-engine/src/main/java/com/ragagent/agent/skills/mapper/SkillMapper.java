package com.ragagent.agent.skills.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ragagent.agent.skills.SkillEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * skills 表仓储（MyBatis-Plus）。
 *
 * <p>读写都走实体：列表/详情/新建/软删；引用检查（agent 配置 jsonb）在
 * {@code SkillCatalogService} 里用 JdbcTemplate 单独表达。</p>
 *
 * <p>包名必须落在 {@code com.ragagent.**.mapper}（{@code @MapperScan} 范围），
 * 否则启动即 bean 缺失。</p>
 */
@Mapper
public interface SkillMapper extends BaseMapper<SkillEntity> {
}
