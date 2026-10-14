package com.ragagent.common.mybatis;

/**
 * SQL {@code LIKE} 参数转义：把 {@code \} {@code %} {@code _} 变成字面量（PG 的 LIKE 默认
 * {@code ESCAPE} 就是反斜杠，故拼 {@code "%" + escape(kw) + "%"} 后 {@code %} 不再退化成"匹配一切"）。
 *
 * <p><b>为什么集中到一处</b>：此前本仓库有**三份逐字相同**的私有实现——
 * {@code auth/service/TenantService}、{@code session/repository/SessionRepository}
 * （并被 {@code session/repository/MessageRepository} 借用）、{@code knowledge/repository/KnowledgeRepository}
 * （由检索查询搬入）。三份完全一样却各自演化，属"同一政策多副本"的经典坏味道 ⇒ 合一，
 * 由本类承载；调用方一律 {@code LikeEscapes.escape(...)}。</p>
 *
 * <p><b>契约</b>：{@code value} 必须非 {@code null}（与三处旧实现一致——它们直接调 {@code replace}）；
 * 出参仍是"可直接拼进 LIKE 模式串"的裸转义文本。</p>
 */
public final class LikeEscapes {

    private LikeEscapes() {}

    /** 转义 LIKE 元字符（非空入参；见类注释的契约说明）。 */
    public static String escape(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
