package com.ragagent.storage.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import com.ragagent.contract.storage.support.FileUrlPort;
import com.ragagent.common.storage.support.Mode;
import com.ragagent.common.storage.support.Rewriter;
import com.ragagent.common.tenant.TenantConfigLookup.TenantStorageView;
import com.ragagent.common.storage.support.StorageBackendResolver;

/**
 * {@code Rewriter.forRequest} 与存储域解析器（{@link FileServiceResolver}）的接线用例。
 *
 * <p><b>为什么在本域</b>：这些用例断言的是"按请求组装出的解析器能把 provider 引用解析成 URL"
 * ——组装动作由本域的 {@link FileResolverFactoryAdapter} 承担，故覆盖随实现留在存储域；
 * {@link Rewriter} 自身的纯逻辑用例留在 common 侧（{@code common.storage.support.RewriterTest}）。</p>
 */
class RewriterForRequestTest {

    /** 只关心 {@code getFileURL} 的 FileUrlPort 替身。 */
    static final class StubFileService implements FileUrlPort {
        private final Function<String, String> behavior;
        final AtomicInteger calls = new AtomicInteger();

        StubFileService() {
            this(filePath -> "https://cdn.example.com/" + filePath);
        }

        StubFileService(Function<String, String> behavior) {
            this.behavior = behavior;
        }

        @Override
        public String getFileURL(String filePath) {
            calls.incrementAndGet();
            return behavior.apply(filePath);
        }
    }

    /** 默认模式不得解析任何东西（解析器不参与）。 */
    @Test
    void forRequestHandleModeIsDisabled() {
        Rewriter w = Rewriter.forRequest(Mode.HANDLE, null);
        assertThat(w.enabled()).isFalse();
    }

    @Test
    void forRequestPublicModeIsEnabled() {
        Rewriter w = Rewriter.forRequest(Mode.PUBLIC, new FileServiceResolver(null, new StubFileService(), null));
        assertThat(w.enabled()).isTrue();
        assertThat(w.rewriteRef("resource://xifDo7NTSL300Lp1goVutw"))
                .isEqualTo("https://cdn.example.com/resource://xifDo7NTSL300Lp1goVutw");
    }

    /**
     * <b>未接线的存储后端在此处显形</b>：既没有 provider 级 FileUrlPort、
     * 也没有进程级默认服务时，{@code minio://} 之类引用解析不出 HTTP URL，
     * 于是被原样保留成 handle——正是 {@link Rewriter} "绝不发出取不到的 URL"的降级。
     * 与未配置 {@code APP_EXTERNAL_URL} 的部署表现一致。
     */
    @Test
    void unresolvableProviderReferenceIsLeftAsHandle() {
        Rewriter w = Rewriter.forRequest(Mode.PUBLIC, new FileServiceResolver(null, null, null));
        String in = "![a](minio://bucket/10000/exports/a.png)";
        assertThat(w.rewrite(in)).isEqualTo(in);
    }

    /**
     * 有进程级默认服务时，provider 引用**会**走它——provider 级服务缺位
     * （或建不出来）时回落到进程级默认的既定分支。
     * 注意 {@code resource://} 手柄也走同一个默认服务。
     */
    @Test
    void providerReferenceFallsBackToProcessDefaultService() {
        Rewriter w = Rewriter.forRequest(Mode.PUBLIC, new FileServiceResolver(null, new StubFileService(), null));
        assertThat(w.rewrite("![a](minio://bucket/10000/exports/a.png)"))
                .isEqualTo("![a](https://cdn.example.com/minio://bucket/10000/exports/a.png)");
    }

    @Test
    void resourceHandleResolvesThroughDefaultService() {
        StubFileService defaultSvc = new StubFileService();
        FileServiceResolver resolver = new FileServiceResolver(null, defaultSvc, null);
        assertThat(resolver.resolveFileService("resource://xifDo7NTSL300Lp1goVutw")).isSameAs(defaultSvc);
    }

    /** 22 位以外的 handler 不是合法 {@code resource://} 手柄，走不到默认服务。 */
    @Test
    void malformedResourceHandleIsNotATransportHandle() {
        StubFileService defaultSvc = new StubFileService();
        FileServiceResolver resolver = new FileServiceResolver(null, defaultSvc, null);
        assertThat(resolver.resolveFileService("resource://short")).isNull();
    }

    /** 适配器：把 L1 的组装函数落到域内解析器（等价于调用方内联建解析器）。 */
    @Test
    void adapterBuildsDomainResolver() {
        Rewriter w = Rewriter.forRequest(Mode.PUBLIC,
                new FileResolverFactoryAdapter().create(null, new StubFileService(), null));
        assertThat(w.rewrite("![a](minio://bucket/10000/exports/a.png)"))
                .isEqualTo("![a](https://cdn.example.com/minio://bucket/10000/exports/a.png)");
    }

    @Test
    void providerSchemeWinsOverTenantDefault() {
        StubFileService defaultSvc = new StubFileService();
        FileServiceResolver resolver = new FileServiceResolver(null, defaultSvc, null);
        assertThat(resolver.resolveFileService("minio://bucket/10000/a.png")).isSameAs(defaultSvc);
    }

    @Test
    void storageBackendPrefixIsStrippedBeforeProviderDetection() {
        StubFileService defaultSvc = new StubFileService();
        FileServiceResolver resolver = new FileServiceResolver(null, defaultSvc, null);
        assertThat(resolver.resolveFileService("storage://backend-a/cos://bucket/ap/10000/c.png"))
                .isSameAs(defaultSvc);
    }

    /** 没有 scheme、租户也没配默认 provider → 认不出来。 */
    @Test
    void unknownSchemeWithoutTenantConfigIsNull() {
        FileServiceResolver resolver = new FileServiceResolver(null, new StubFileService(), null);
        assertThat(resolver.resolveFileService("/var/data/files/a.png")).isNull();
    }

    /** 同一个 (backend, provider) 只解析一次。 */
    @Test
    void fileServiceIsCachedPerBackendAndProvider() {
        AtomicInteger resolved = new AtomicInteger();
        StorageBackendResolver backendResolver = (tenantId, backendId, provider, baseDir) -> {
            resolved.incrementAndGet();
            return new StorageBackendResolver.Resolved(new StubFileService());
        };
        TenantStorageView tenant = new TenantStorageView(10002L, null, null);

        FileServiceResolver resolver = new FileServiceResolver(tenant, null, backendResolver);
        FileUrlPort first = resolver.resolveFileService("minio://bucket/10000/a.png");
        FileUrlPort second = resolver.resolveFileService("minio://bucket/10000/b.png");

        assertThat(second).isSameAs(first);
        assertThat(resolved.get()).isEqualTo(1);
    }
}
