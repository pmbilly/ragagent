package com.ragagent.session.controller.vo;

/** 置顶 / 取消置顶的 HTTP 响应体：动作之后的线上值。 */
public record SessionPinRespVo(boolean pinned) {
}
