package com.ragagent.common.mybatis;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * 分页请求工厂：统一「不带 count 的翻页/行帽/offset 翻页」构造，替代
 * {@code .last("LIMIT " + n ...)} 拼接。LIMIT/OFFSET 由分页插件按方言生成。
 *
 * <p>总数仍由调用方显式 {@code selectCount} 提供（保持既有 count 查询形状——
 * MP 自动 count 会带 ORDER BY，H2 对部分语句报错，见 TenantInvitationService 注释）。</p>
 *
 * <p>负数防御：MP 对 {@code size < 0} 的语义是「不限行数」（全表扫）；这里一律
 * 钳到 0（LIMIT 0 空结果），防御调用方把未校验的 limit 直传进来。</p>
 */
public final class PageRequests {

    private PageRequests() {
    }

    /** 行帽：只取前 {@code size} 条（LIMIT size，无 OFFSET、无 count）。 */
    public static <T> Page<T> cap(long size) {
        return raw(1, clamp(size));
    }

    /** page/size 翻页（无 count）：OFFSET = (page-1)*size；page&lt;1 归 1。 */
    public static <T> Page<T> range(long page, long size) {
        return raw(page < 1 ? 1 : page, clamp(size));
    }

    /** 任意 offset 翻页（无 count）：上游 API 是 offset/limit 语义而非 page/size 时用。 */
    public static <T> Page<T> atOffset(long offset, long size) {
        return new OffsetPage<>(clamp(offset), clamp(size));
    }

    private static long clamp(long v) {
        return v < 0 ? 0 : v;
    }

    private static <T> Page<T> raw(long page, long size) {
        Page<T> p = Page.of(page, size);
        p.setSearchCount(false);
        return p;
    }

    /** 覆写偏移计算的 Page：分页拦截器从 {@code IPage.offset()} 取偏移。 */
    static final class OffsetPage<T> extends Page<T> {

        private final long fixedOffset;

        OffsetPage(long offset, long size) {
            super(1, size);
            this.fixedOffset = offset;
        }

        @Override
        public long offset() {
            return fixedOffset;
        }
    }
}
