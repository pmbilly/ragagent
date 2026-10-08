package com.ragagent.common.mybatis;

import com.baomidou.mybatisplus.core.exceptions.MybatisPlusException;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.update.Update;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;

/**
 * 全表 UPDATE/DELETE 防护——持久层规约「严禁无条件的全局更新/删除」的机器执行面。
 *
 * <p>没用 MP 自带的 {@code BlockAttackInnerInterceptor}：它对解析失败的 SQL 直接抛异常，
 * 而本仓 Mapper 注解里有大量 PG 方言 SQL（jsonb 运算符、向量操作符、{@code $n} 占位），
 * JSqlParser 未必都吃得下——误伤就是运行期炸合法写路径。这里改为 fail-open：解析得了的
 * 按规则拦，解析不了的放行。放行侧的安全性由架构规约兜底——复杂/方言 SQL 只允许住在
 * Mapper 注解或裸 JDBC 白名单 Repository（见 {@code ArchitectureRulesTest} R7），而
 * wrapper 生成的 SQL 形态固定、必然可解析：防护恰好覆盖全表改删的主攻面。</p>
 *
 * <p>byId 族（updateById / deleteById）跳过：自带主键 WHERE，无需解析。</p>
 *
 * <p><b>显式登记的全表写</b>：业务上确需无边界 UPDATE/DELETE 的，必须具名成 Mapper
 * 方法并在 {@link #FULL_TABLE_ALLOWED} 按语句 id 登记（附理由），PR 里过目；
 * {@code update(null, 无条件 wrapper)} 这种匿名全表写一律拦截。</p>
 */
public class FullTableWriteGuard implements InnerInterceptor {

    /** 登记过的全表写语句（语句 id → 业务理由）。新增须 PR 论证。 */
    private static final java.util.Map<String, String> FULL_TABLE_ALLOWED = java.util.Map.of(
            "com.ragagent.common.tenant.mapper.TenantMapper.applyDefaultStorageQuota",
            "默认存储配额统一应用到全部租户（管理员运维操作）");

    @Override
    public void beforeUpdate(Executor executor, MappedStatement ms, Object parameter) {
        SqlCommandType type = ms.getSqlCommandType();
        if (type != SqlCommandType.UPDATE && type != SqlCommandType.DELETE) {
            return;
        }
        String id = ms.getId();
        if (id.endsWith(".updateById") || id.endsWith(".deleteById")) {
            return;
        }
        if (FULL_TABLE_ALLOWED.containsKey(id)) {
            return;
        }
        String sql = ms.getBoundSql(parameter).getSql();
        net.sf.jsqlparser.statement.Statement statement;
        try {
            statement = CCJSqlParserUtil.parse(sql);
        } catch (Exception e) {
            return; // fail-open：方言/动态 SQL 解析不了，交由白名单纪律管辖（理由见类注释）
        }
        if (statement instanceof Delete delete && delete.getWhere() == null) {
            throw new MybatisPlusException("禁止全表 DELETE（无 WHERE）：" + id);
        }
        if (statement instanceof Update update && update.getWhere() == null) {
            throw new MybatisPlusException("禁止全表 UPDATE（无 WHERE）：" + id);
        }
    }
}
