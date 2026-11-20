package com.ragagent.platform.stream;

import com.ragagent.contract.model.ChatStream;
import com.ragagent.contract.model.ResponseType;
import com.ragagent.contract.model.StreamResponse;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * 无界队列承载的流句柄（{@code RemoteApiChat} 与 langfuse 转发层用）。
 *
 * <p>关闭时：跑钩子（关 provider 连接 / 中断转发线程）→ 投一个终态帧，让仍在
 * {@code take()} 的消费者/转发线程立刻收尾，而不是等 120s 放弃阈值。</p>
 */
public class QueuedChatStream extends LinkedBlockingQueue<StreamResponse> implements ChatStream {

    private static final long serialVersionUID = 1L;

    private final StreamLifecycle life;

    public QueuedChatStream(Runnable onClose) {
        this.life = new StreamLifecycle(onClose);
    }

    @Override
    public void close() {
        if (life.closeOnce()) {
            // 无界队列必成功；终态帧只用于解阻塞，消费者已收尾时它被丢弃。
            super.offer(StreamResponse.of(ResponseType.ANSWER, "", true));
        }
    }

    @Override
    public boolean isClosed() {
        return life.isClosed();
    }
}
