package com.ragagent.knowledge.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ragagent.knowledge.config.DocReaderProperties;

import docreader.DocReaderGrpc;
import docreader.Docreader;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;

/**
 * DocReader 客户端流路径的失败语义（整改方案 K3）。
 *
 * <p>三处缺口（修复前实测红）：① 异步 UNIMPLEMENTED 被 {@code onError} 丢弃 ⇒ 不回退 unary；
 * ② meta 后错误被吞 ⇒ 返回"部分成功"；③ 超时只表现为误导性的 "first frame missing meta"。
 * 测试用真实 Netty 回环端口 + 假 DocReader 服务，覆盖真实客户端路径（不 mock 通道）。</p>
 */
class DocReaderClientTest {

    private Server server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.shutdownNow();
        }
    }

    @Test
    @DisplayName("正常流：首帧 meta → markdown 返回（回归）")
    void streamReturnsMetaMarkdown() throws Exception {
        DocReaderClient client = start(FakeDocReader.normal("stream-markdown"), Duration.ofSeconds(10));

        DocReaderClient.ParseResult result = client.read(new byte[] {1, 2, 3}, "a.pdf", "pdf", "t", null);

        assertThat(result.markdown()).isEqualTo("stream-markdown");
    }

    @Test
    @DisplayName("异步 UNIMPLEMENTED：回退 unary Read（修复前抛 first frame missing meta）")
    void asyncUnimplementedFallsBackToUnary() throws Exception {
        FakeDocReader fake = new FakeDocReader();
        fake.implementStream = false;
        fake.unaryMarkdown = "unary-markdown";
        DocReaderClient client = start(fake, Duration.ofSeconds(10));

        DocReaderClient.ParseResult result = client.read(new byte[] {1, 2, 3}, "a.pdf", "pdf", "t", null);

        assertThat(result.markdown()).isEqualTo("unary-markdown");
    }

    @Test
    @DisplayName("meta 后错误：必须上报，不得返回部分成功（修复前返回成功）")
    void errorAfterMetaPropagates() throws IOException {
        FakeDocReader fake = FakeDocReader.normal("partial-markdown");
        fake.streamError = Status.INTERNAL.withDescription("boom");
        DocReaderClient client = start(fake, Duration.ofSeconds(10));

        assertThatThrownBy(() -> client.read(new byte[] {1, 2, 3}, "a.pdf", "pdf", "t", null))
                .isInstanceOf(StatusRuntimeException.class)
                .hasMessageContaining("boom");
    }

    @Test
    @DisplayName("超时：deadline 到期的真实状态上报（修复前是误导性的 missing meta）")
    void deadlineExceededPropagates() throws IOException {
        FakeDocReader fake = new FakeDocReader();
        fake.hangStream = true;
        DocReaderClient client = start(fake, Duration.ofMillis(300));

        assertThatThrownBy(() -> client.read(new byte[] {1, 2, 3}, "a.pdf", "pdf", "t", null))
                .isInstanceOf(StatusRuntimeException.class)
                .satisfies(e -> assertThat(((StatusRuntimeException) e).getStatus().getCode())
                        .isEqualTo(Status.Code.DEADLINE_EXCEEDED));
    }

    @Test
    @DisplayName("空流（无 meta）：仍按既有语义拒绝（回归）")
    void emptyStreamWithoutMetaRejected() throws IOException {
        DocReaderClient client = start(new FakeDocReader(), Duration.ofSeconds(10));

        assertThatThrownBy(() -> client.read(new byte[] {1, 2, 3}, "a.pdf", "pdf", "t", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("missing meta");
    }

    private DocReaderClient start(FakeDocReader fake, Duration timeout) throws IOException {
        server = NettyServerBuilder.forPort(0).addService(fake).build().start();
        DocReaderProperties properties =
                new DocReaderProperties("localhost:" + server.getPort(), "grpc");
        return new DocReaderClient(properties, timeout);
    }

    /** 假 DocReader：默认空流；normal() 发一张 meta 帧；可配置流错误 / 挂起 / 不实现流。 */
    static final class FakeDocReader extends DocReaderGrpc.DocReaderImplBase {
        final List<Docreader.ReadStreamResponse> frames = new ArrayList<>();
        String unaryMarkdown = "unary-default";
        Status streamError;
        boolean implementStream = true;
        boolean hangStream;

        static FakeDocReader normal(String markdown) {
            FakeDocReader fake = new FakeDocReader();
            fake.frames.add(Docreader.ReadStreamResponse.newBuilder()
                    .setMeta(Docreader.ReadStreamMeta.newBuilder().setMarkdownContent(markdown))
                    .build());
            return fake;
        }

        @Override
        public void read(Docreader.ReadRequest request,
                StreamObserver<Docreader.ReadResponse> responseObserver) {
            responseObserver.onNext(Docreader.ReadResponse.newBuilder()
                    .setMarkdownContent(unaryMarkdown).build());
            responseObserver.onCompleted();
        }

        @Override
        public void readStream(Docreader.ReadRequest request,
                StreamObserver<Docreader.ReadStreamResponse> responseObserver) {
            if (!implementStream) {
                responseObserver.onError(
                        Status.UNIMPLEMENTED.withDescription("stream not supported").asRuntimeException());
                return;
            }
            if (hangStream) {
                return; // 不响应：等 deadline
            }
            for (Docreader.ReadStreamResponse frame : frames) {
                responseObserver.onNext(frame);
            }
            if (streamError != null) {
                responseObserver.onError(streamError.asRuntimeException());
            } else {
                responseObserver.onCompleted();
            }
        }
    }
}
