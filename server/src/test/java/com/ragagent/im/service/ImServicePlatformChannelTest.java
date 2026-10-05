package com.ragagent.im.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * IM 平台标识 → 知识库 channel 值映射（与 Go {@code imPlatformToChannel} 同表；
 * 附件异步入库时写入）。
 */
class ImServicePlatformChannelTest {

    @Test
    void mappingMatchesGoTable() {
        assertEquals("wechat", ImService.imPlatformToChannel("wechat"));
        assertEquals("wecom", ImService.imPlatformToChannel("wecom"));
        assertEquals("wecom", ImService.imPlatformToChannel("wxwork"));
        assertEquals("feishu", ImService.imPlatformToChannel("feishu"));
        assertEquals("feishu", ImService.imPlatformToChannel("lark"));
        assertEquals("dingtalk", ImService.imPlatformToChannel("dingtalk"));
        assertEquals("slack", ImService.imPlatformToChannel("slack"));
        assertEquals("im", ImService.imPlatformToChannel("telegram"));
        assertEquals("im", ImService.imPlatformToChannel("mattermost"));
        assertEquals("im", ImService.imPlatformToChannel(null));
        assertEquals("wecom", ImService.imPlatformToChannel("WeCom"), "大小写归一");
    }
}
