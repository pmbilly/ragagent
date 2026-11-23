package com.ragagent.session.controller.vo;

import java.util.List;

/** 消息搜索的 HTTP 响应体：{@code items} 恒输出数组（空结果是 []）。 */
public record MessageSearchRespVo(
        List<MessageSearchGroupItemVo> items,
        int total) {
}
