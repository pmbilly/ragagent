package com.ragagent.im.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * IM 平台标识 → 知识库 channel 值映射（附件异步入库时写入）。
 *
 * <p>B126 随「删重复实现」从 {@code ImService.imPlatformToChannel} 迁到
 * {@link ImFormat#imPlatformToChannel}——两处实现功能等价（`ImService` 版用字面量、
 * `ImFormat` 版用 {@code ImTypes.CHANNEL_*} 常量，取值逐字相同），保留知识域常量化
 * 的那份，并把这张逐平台映射表钉在这里（golden 契约只覆盖 2 例，本测覆盖 11 例）。</p>
 */
class ImFormatPlatformChannelTest {

    @Test
    @DisplayName("平台映射表与 Go 侧一致（含别名、未知平台与大小写归一）")
    void mappingMatchesGoTable() {
        assertEquals("wechat", ImFormat.imPlatformToChannel("wechat"));
        assertEquals("wecom", ImFormat.imPlatformToChannel("wecom"));
        assertEquals("wecom", ImFormat.imPlatformToChannel("wxwork"));
        assertEquals("feishu", ImFormat.imPlatformToChannel("feishu"));
        assertEquals("feishu", ImFormat.imPlatformToChannel("lark"));
        assertEquals("dingtalk", ImFormat.imPlatformToChannel("dingtalk"));
        assertEquals("slack", ImFormat.imPlatformToChannel("slack"));
        assertEquals("im", ImFormat.imPlatformToChannel("telegram"));
        assertEquals("im", ImFormat.imPlatformToChannel("mattermost"));
        assertEquals("im", ImFormat.imPlatformToChannel(null));
        assertEquals("wecom", ImFormat.imPlatformToChannel("WeCom"), "大小写归一");
    }
}
