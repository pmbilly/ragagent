package com.ragagent.knowledge.storage;

import com.ragagent.contract.storage.fileserve.OpenedFile;
import java.io.IOException;
import java.io.InputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.ragagent.contract.storage.fileserve.ProviderFileStorePort;
import com.ragagent.platform.storage.fileserve.StoragePaths;
import com.ragagent.contract.error.BizException;
import com.ragagent.contract.error.AppError;

/**
 * 租户感知的文件存储门面：本地卷（{@link LocalStorageService}）与云 provider 的分流。
 *
 * <p><b>跨域口径</b>：云 provider 面经 L1 端口 {@link ProviderFileStorePort}（解析 + 写/读/删操作），
 * 本类不再 import 存储域类型；"本地 / 云"分流与错误信封口径<b>逐字保留</b>：</p>
 * <ul>
 *   <li>无租户 / 租户无云配置（{@code none}）⇒ 本地契约（{@code resource://…} 既定形态，契约样例锁定）；</li>
 *   <li>配了云但解析失败（{@code error} 非空）⇒ **不静默退回本地**：写面抛、读面折成
 *       {@code Failed to retrieve file} 信封、删除只记 warn；</li>
 *   <li>引用带 {@code local://} scheme ⇒ 归本地盘（Go local provider 的原生形态）。</li>
 * </ul>
 */
@Service
public class TenantFileStorage {

    private static final Logger log = LoggerFactory.getLogger(TenantFileStorage.class);

    private final LocalStorageService local;
    private final ProviderFileStorePort providerStore;

    public TenantFileStorage(LocalStorageService local,
                             ProviderFileStorePort providerStore) {
        this.local = local;
        this.providerStore = providerStore;
    }

    // ── 写 ──────────────────────────────────────────────────────────────────

    /**
     * 保存知识文件→ 落库的 file_path。
     */
    public String save(long tenantId, String knowledgeId, String fileName, byte[] content) {
        ProviderFileStorePort.Resolution resolved = providerStore.resolve(tenantId, null);
        if (resolved.ok()) {
            String path = resolved.ops().saveFile(content, fileName, tenantId, knowledgeId);
            log.info("stored knowledge file on provider {}: knowledge_id={} path={}",
                    resolved.provider(), knowledgeId, path);
            return path;
        }
        if (resolved.error() != null) {
            throw new IllegalStateException(
                    "resolve storage provider failed: " + resolved.error());
        }
        return local.save(tenantId, knowledgeId, fileName, content);
    }

    /**
     * 导出结果：{@code handled=false} = "租户走本地存储，调用方自便"；
     * {@code handled=true} 且 {@code url=null} = "云上导出失败，调用方按失败处理"。
     */
    public record Exported(String url, boolean handled) {
    }

    /**
     * 保存导出字节并取 URL（知识 FAQ 导入的失败明细 CSV 走这条，temp=true → 云上落临时桶）。
     * <p>本地租户返回 {@code handled=false}：调用方保留既有本地落盘与 {@code local://} 引用
     * （那是契约样例锁定的形态）。</p>
     */
    public Exported saveExportedBytesToUrl(long tenantId, String fileName, byte[] data, boolean temp) {
        ProviderFileStorePort.Resolution resolved = providerStore.resolve(tenantId, null);
        if (resolved.ok()) {
            String ref = resolved.ops().saveBytes(data, tenantId, fileName, temp);
            String url = resolved.ops().getFileURL(ref);
            log.info("exported bytes on provider {}: ref={}", resolved.provider(), ref);
            return new Exported(url, true);
        }
        if (resolved.error() != null) {
            log.warn("export to provider failed: {}", resolved.error());
            return new Exported(null, true);
        }
        return new Exported(null, false);
    }

    // ── 读 ──────────────────────────────────────────────────────────────────

    /**
     * 读回字节。本地引用保持
     * {@link LocalStorageService#read(String)} 的既有语义（只认 {@code resource://}）。
     */
    public byte[] read(long tenantId, String filePath) {
        String provider = StoragePaths.parseProviderScheme(filePath);
        if (provider.isEmpty()) {
            return local.read(filePath);
        }
        if (isLocalScheme(provider)) {
            // LocalStorageService.readChecked 认得它，read 只认 resource://。
            return local.readChecked(filePath);
        }
        return readFromProvider(tenantId, provider, filePath);
    }

    /**
     * 读回字节。非 provider 引用走
     * {@link LocalStorageService#readChecked(String)}（多 scheme 容忍 + 路径穿越守卫 +
     * {@code Failed to retrieve file} 信封）；provider 引用失败折成同一个信封。
     */
    public byte[] readChecked(long tenantId, String filePath) {
        String provider = StoragePaths.parseProviderScheme(filePath);
        if (provider.isEmpty() || isLocalScheme(provider)) {
            return local.readChecked(filePath);
        }
        try {
            return readFromProvider(tenantId, provider, filePath);
        } catch (RuntimeException e) {
            String detail = e.getMessage() == null ? e.toString() : e.getMessage();
            throw new BizException(AppError.internal("Failed to retrieve file")
                    .withDetails(detail));
        }
    }

    /**
     * provider 引用打开为流式对象（minio 可 seek / aws-sdk 族流式）——复用文件代理面的同一适配器
     * （端口实现内的 {@code ProviderFileContentService}），不重复一套分流逻辑。
     * <p>错误折叠成与 {@link #readChecked} 同一个 {@code Failed to retrieve file} 信封
     * （契约样例 {@code kg-download-404/traversal} 锁的就是它）。</p>
     */
    public OpenedFile open(long tenantId, String filePath) {
        String provider = StoragePaths.parseProviderScheme(filePath);
        if (provider.isEmpty() || isLocalScheme(provider)) {
            return local.openChecked(filePath);
        }
        try {
            ProviderFileStorePort.Resolution resolved = providerStore.resolve(tenantId, provider);
            if (!resolved.ok()) {
                throw new IllegalStateException("read from provider \"" + provider + "\" failed: "
                        + resolved.error());
            }
            return resolved.ops().open(filePath);
        } catch (BizException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            String detail = e.getMessage() == null ? e.toString() : e.getMessage();
            throw new BizException(AppError.internal("Failed to retrieve file").withDetails(detail));
        }
    }

    // ── 删 ──────────────────────────────────────────────────────────────────

    /**
     * 删除知识的所有文件：
     * 本地目录树恒清（既有语义），{@code filePath} 是 provider 引用时额外删对象。
     */
    public void delete(long tenantId, String knowledgeId, String filePath) {
        local.deleteTree(tenantId, knowledgeId);
        String provider = StoragePaths.parseProviderScheme(filePath);
        if (provider.isEmpty() || isLocalScheme(provider)) {
            return;
        }
        try {
            ProviderFileStorePort.Resolution resolved = providerStore.resolve(tenantId, provider);
            if (!resolved.ok()) {
                log.warn("delete provider object skipped: provider={} err={}",
                        provider, resolved.error());
                return;
            }
            resolved.ops().deleteFile(filePath);
            log.info("deleted provider object: provider={} path={}", provider, filePath);
        } catch (RuntimeException e) {
            log.warn("delete provider object failed: path={} err={}", filePath, e.toString());
        }
    }

    // ── 内部 ────────────────────────────────────────────────────────────────

    private static boolean isLocalScheme(String provider) {
        return "local".equalsIgnoreCase(provider);
    }

    private byte[] readFromProvider(long tenantId, String provider, String filePath) {
        ProviderFileStorePort.Resolution resolved = providerStore.resolve(tenantId, provider);
        if (!resolved.ok()) {
            throw new IllegalStateException("read from provider \"" + provider + "\" failed: "
                    + resolved.error());
        }
        try (InputStream in = resolved.ops().getFile(filePath)) {
            return in == null ? new byte[0] : in.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("failed to read file: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            throw new IllegalStateException(e.getMessage() == null ? e.toString() : e.getMessage(), e);
        }
    }
}
