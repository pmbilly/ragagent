package com.ragagent.knowledge.storage;

import com.ragagent.contract.error.AppError;
import com.ragagent.contract.error.BizException;
import com.ragagent.contract.storage.fileserve.OpenedFile;
import com.ragagent.contract.storage.fileserve.StoragePathGuard;
import com.ragagent.knowledge.support.KnowledgeHashes;
import com.ragagent.platform.storage.config.UploadLimits;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 本地存储引擎。
 * 文件路径契约：对外暴露 resource://{key} 不透明串（契约样例 已锁定此前缀），
 * preview/download 等回读路径走同一 service。
 * 落盘布局：{LOCAL_STORAGE_BASE_DIR}/{tenantId}/{knowledgeId}/{fileName}
 */
@Service
public class LocalStorageService {

    private static final Logger log = LoggerFactory.getLogger(LocalStorageService.class);

    private final Path baseDir;

    public LocalStorageService(
            @Value("${weknora.storage.local-base-dir:${LOCAL_STORAGE_BASE_DIR:/data/files}}") String baseDir) {
        this.baseDir = Path.of(baseDir);
    }

    /** 测试种子用：本地落盘根目录（绝对化 + 规范化，防止相对路径下 startsWith 误判）。 */
    public Path baseDir() {
        return baseDir.toAbsolutePath().normalize();
    }

    /** 保存文件内容，返回 resource:// 路径 */
    public String save(long tenantId, String knowledgeId, String fileName, byte[] content) {
        try {
            Path dir = baseDir.resolve(String.valueOf(tenantId)).resolve(knowledgeId);
            Files.createDirectories(dir);
            Path target = dir.resolve(sanitize(fileName));
            Files.write(target, content);
            String key = tenantId + "/" + knowledgeId + "/" + sanitize(fileName);
            return "resource://" + key;
        } catch (IOException e) {
            throw new IllegalStateException("failed to create directory or write file: " + e.getMessage(), e);
        }
    }

    /**
     * 保存到租户 {@code exports} 区（知识库文件代理唯一放行的命名空间），
     * 返回 {@code local://{tenantId}/exports/{唯一名}}。
     */
    public String saveExport(long tenantId, String fileName, byte[] content) {
        String safeName = sanitize(fileName);
        int dot = safeName.lastIndexOf('.');
        String ext = dot < 0 ? "" : safeName.substring(dot);
        String baseName = dot < 0 ? safeName : safeName.substring(0, dot);
        String unique = baseName + "_" + System.nanoTime() + ext;
        try {
            Path dir = baseDir.resolve(String.valueOf(tenantId)).resolve("exports");
            Files.createDirectories(dir);
            Files.write(dir.resolve(unique), content);
            return "local://" + tenantId + "/exports/" + unique;
        } catch (IOException e) {
            throw new IllegalStateException("failed to create directory or write file: " + e.getMessage(), e);
        }
    }

    /**
     * 删除 {@code local://} 导出文件（逃逸守卫同读面）；不存在视为成功。
     */
    public void deleteExport(String localPath) {
        if (localPath == null || !localPath.startsWith("local://")) {
            return;
        }
        try {
            Files.deleteIfExists(resolveUnderBase(localPath));
        } catch (IOException e) {
            log.warn("delete export {} failed: {}", localPath, e.toString());
        }
    }

    /** 按 resource:// 路径读回字节 */
    public byte[] read(String resourcePath) {
        if (resourcePath == null || !resourcePath.startsWith("resource://")) {
            throw new IllegalArgumentException("invalid resource path");
        }
        try {
            return Files.readAllBytes(baseDir.resolve(resourcePath.substring("resource://".length())));
        } catch (IOException e) {
            throw new IllegalStateException("failed to read file: " + e.getMessage(), e);
        }
    }

    /**
     * （service/file/local + utils/security）。支持
     * 空路径/读失败由调用方转成 "Failed to retrieve file" 信封。
     */
    public byte[] readChecked(String filePath) {
        Path resolved = resolveUnderBase(filePath);
        try {
            return Files.readAllBytes(resolved);
        } catch (IOException e) {
            throw new BizException(AppError.internal("Failed to retrieve file")
                    .withDetails("failed to open file: " + e.getMessage()));
        }
    }

    /**
     * 解析 + 逃逸守卫：三态 scheme 解析与 base 包含判定都走
     * {@link StoragePathGuard}（③ 去重，单一份实现）；本支保留 BizException 信封错误通道
     * （{@code local://} + {@code resource://} 的解析顺序敏感，委托后语义不变）。
     */
    /**
     * （ServeContent：{@code Accept-Ranges: bytes} + Range/206）。错误通道与
     * {@link #readChecked} 一致（{@code Failed to retrieve file} 信封，守护卫先行）。
     * {@code os.Open} 失败同口径（404），而不是读一半才炸。</p>
     */
    public OpenedFile openChecked(String filePath) {
        Path resolved = resolveUnderBase(filePath);
        try {
            return OpenedFile.ofSeekable(resolved, Files.size(resolved));
        } catch (IOException e) {
            throw new BizException(AppError.internal("Failed to retrieve file")
                    .withDetails("failed to open file: " + e.getMessage()));
        }
    }

    private Path resolveUnderBase(String filePath) {
        String candidate = StoragePathGuard.stripKnownScheme(filePath);
        Path joined = Path.of(candidate).isAbsolute() ? Path.of(candidate) : baseDir.resolve(candidate);
        try {
            return Path.of(StoragePathGuard.safePathUnderBase(baseDir.toString(), joined.toString()));
        } catch (IOException e) {
            throw new BizException(AppError.internal("Failed to retrieve file")
                    .withDetails(e.getMessage()));
        }
    }

    public boolean exists(String resourcePath) {
        if (resourcePath == null || !resourcePath.startsWith("resource://")) {
            return false;
        }
        return Files.exists(baseDir.resolve(resourcePath.substring("resource://".length())));
    }

    public void deleteTree(long tenantId, String knowledgeId) {
        Path dir = baseDir.resolve(String.valueOf(tenantId)).resolve(knowledgeId);
        if (!Files.exists(dir)) {
            return;
        }
        try (var stream = Files.walk(dir)) {
            stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException e) {
                    log.warn("delete {} failed: {}", p, e.toString());
                }
            });
        } catch (IOException e) {
            log.warn("delete tree {} failed: {}", dir, e.toString());
        }
    }

    /** 契约样例 为 32 位小写十六进制（md5） */
    /** 内容 MD5（实现已下沉 L1 {@code KnowledgeHashes}，保留同名入口）。 */
    public static String md5Hex(byte[] content) {
        return KnowledgeHashes.md5Hex(content);
    }

    /** 上传大小上限（MB）：实现即 L1 {@code UploadLimits} 转发。 */
    public static long maxFileSizeMb() {
        return UploadLimits.maxFileSizeMb();
    }

    /** 上传大小上限（字节）：实现即 L1 {@code UploadLimits} 转发。 */
    public static long maxFileSizeBytes() {
        return UploadLimits.maxFileSizeBytes();
    }

    private static String sanitize(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "unnamed";
        }
        // 防路径穿越
        return fileName.replace("/", "_").replace("\\", "_").replace("..", "__");
    }

    public static byte[] readAll(InputStream in) throws IOException {
        try (in) {
            return in.readAllBytes();
        }
    }

    public static void copy(InputStream in, OutputStream out) throws IOException {
        in.transferTo(out);
    }
}
