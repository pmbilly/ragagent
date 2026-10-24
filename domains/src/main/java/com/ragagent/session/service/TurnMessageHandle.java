package com.ragagent.session.service;

import com.ragagent.session.domain.message.Message;

/**
 * 本轮 assistant 行的写入句柄：消息实体 + 本轮共享监视器。
 *
 * <p>把两者绑在一起传，避免事件桥只拿到消息、又各自 new 一把锁。</p>
 */
public record TurnMessageHandle(Message message, TurnMessageGate gate) {
}
