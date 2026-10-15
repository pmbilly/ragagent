package com.ragagent.common.storage.fileserve;

import java.io.IOException;
import java.io.InputStream;
import com.ragagent.platform.storage.fileserve.FileTransport;

/**
 * 租户云 provider 的存储能力端口（L1）：解析"该租户用哪个 provider 服务"，并给出一组文件操作。
 *
 * <p><b>为什么是端口</b>：知识域的租户文件门面（{@code TenantFileStorage}）需要"云 provider 写/读/删"，
 * 但 provider 解析与客户端构造属存储域实现（依赖 provider 工厂与租户存储配置）。本端口把这一面收成
 * 契约，调用方只做"本地 / 云"的分流判断，不 import 存储域类型。</p>
 *
 * <p><b>三态语义（与迁移前逐字一致）</b>：</p>
 * <ul>
 *   <li>{@link Resolution#none()} —— 无租户 / 租户无存储行：<b>当作没有云配置</b>，调用方走本地契约；</li>
 *   <li>{@code error} 非空 —— 配了云但解析失败：**不能静默退回本地**，调用方按各自姿态抛错；</li>
 *   <li>{@link Resolution#ok()} —— 解析出可用 provider 服务，经 {@link Ops} 操作。</li>
 * </ul>
 */
public interface ProviderFileStorePort {

    /**
     * 按租户解析 provider 服务。
     *
     * @param tenantId 租户 id（{@code <= 0} ⇒ 无租户）
     * @param provider 引用里的 provider scheme；空串 ⇒ 用租户默认
     */
    Resolution resolve(long tenantId, String provider);

    /**
     * provider 解析结果。
     *
     * @param provider 命中的 provider 名（无 ⇒ 空串）
     * @param error    解析失败原因（{@code null} 表示未失败）
     * @param ops      可用的操作面（未解析出 ⇒ {@code null}）
     */
    record Resolution(String provider, String error, Ops ops) {

        /** 无租户 / 租户无存储行：调用方走本地契约。 */
        public static Resolution none() {
            return new Resolution("", null, null);
        }

        /** 是否解析出可用服务。 */
        public boolean ok() {
            return error == null && ops != null;
        }
    }

    /** 单个 provider 服务上的文件操作。 */
    interface Ops {

        /** 保存内容 → 落库的 file_path（provider 引用形态）。 */
        String saveFile(byte[] content, String fileName, long tenantId, String knowledgeId);

        /** 保存字节 → 引用（{@code temp=true} ⇒ 云上临时桶）。 */
        String saveBytes(byte[] data, long tenantId, String fileName, boolean temp);

        /** 引用 → 可直接加载的 URL。 */
        String getFileURL(String filePath);

        /** 读为输入流（调用方负责关闭）。 */
        InputStream getFile(String filePath);

        /** 打开为流式对象（可 seek / 顺序读）。 */
        FileTransport.OpenedFile open(String filePath) throws IOException;

        /** 删除对象。 */
        void deleteFile(String filePath);
    }
}
