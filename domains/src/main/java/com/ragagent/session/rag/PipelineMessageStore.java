package com.ragagent.session.rag;

import com.ragagent.session.dto.PipelineMessageImageView;
import com.ragagent.session.dto.PipelineMessageView;

import java.util.List;

/**
 * chat 管线的消息读写面：端口两端都是会话域内类型。
 *
 * <p>此前作为 {@code PipelineMessageStore} 放在 contract，是 chatpipeline 独立成模块时的
 * 跨域契约；管线归位会话域后，实现与调用都在本域，留在 contract 只会让消息视图无法还乡。</p>
 */
public interface PipelineMessageStore {

    /** 找不到返回 null 或抛异常。 */
    PipelineMessageView getMessage(String sessionId, String messageId);

    List<PipelineMessageView> getRecentMessagesBySession(String sessionId, int limit);

    void updateMessageImages(String sessionId, String messageId,
            List<PipelineMessageImageView> images);

    void updateMessageRenderedContent(String sessionId, String messageId, String renderedContent);
}
