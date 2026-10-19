package com.ragagent.contract.session;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 会话转录读取端口：供记忆蒸馏按游标/时间取消息视图。
 *
 * <p>只暴露蒸馏需要的四字段视图（{@link SessionMessageView}），不把消息实体
 * 交给消费方。</p>
 */
public interface MessageTranscriptPort {

    /** 取游标之后的消息（按创建时间升序），最多 {@code limit} 条。 */
    List<SessionMessageView> listAfterCursor(
            String sessionId, OffsetDateTime afterCreatedAt, String afterId, int limit);

    /** 取指定时间之前的消息（用于补上下文），最多 {@code limit} 条。 */
    List<SessionMessageView> listBeforeTime(String sessionId, OffsetDateTime beforeTime, int limit);
}
