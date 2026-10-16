package com.ragagent.platform.stream;

import java.util.concurrent.SynchronousQueue;

import com.ragagent.contract.model.ResponseType;
import com.ragagent.contract.model.ChatStream;
import com.ragagent.contract.model.StreamResponse;

/**
 * 无缓冲（{@code SynchronousQueue}）承载的流句柄：并发闸门装饰器用。
 *
 * <p>刻意保持无缓冲语义——"消费者不读则转发 offer 失败"正是放弃检测的依据，
 * 换成有界队列会让放弃检测永不触发。关闭时投终态帧只在**有等待消费者**时成功，
 * 正好把阻塞的消费者解下来。</p>
 */
public final class SyncChatStream extends SynchronousQueue<StreamResponse> implements ChatStream {

    private static final long serialVersionUID = 1L;

    private final StreamLifecycle life;

    public SyncChatStream(Runnable onClose) {
        this.life = new StreamLifecycle(onClose);
    }

    @Override
    public void close() {
        if (life.closeOnce()) {
            super.offer(StreamResponse.of(ResponseType.ANSWER, "", true));
        }
    }

    @Override
    public boolean isClosed() {
        return life.isClosed();
    }
}
