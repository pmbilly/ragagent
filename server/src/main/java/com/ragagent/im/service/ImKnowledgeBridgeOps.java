package com.ragagent.im.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;

import com.ragagent.common.context.TenantContext;
import com.ragagent.im.domain.ImChannelEntity;
import com.ragagent.im.runtime.ImCommandSet;
import com.ragagent.im.runtime.ImFormat;
import com.ragagent.knowledge.service.KnowledgeService;
import com.ragagent.knowledge.service.KnowledgeService.DuplicateKnowledgeException;

/**
 * IM 与知识库域的全部接面：命令面的 KB 清单/检索读取，以及附件异步入库。
 *
 * <p>B126 自 {@link ImService} 原样外提（逐字搬迁，仅改依赖取用方式）。四件事共用
 * 同一个依赖（{@code ObjectProvider<KnowledgeService>}，组件缺席时静默跳过），
 * 且都属「IM 怎么够到知识域」这一个关注点——与渠道生命周期、消息入口闸门无关。</p>
 *
 * <p><b>待办登记（搬迁时核对发现，本批只搬不修）</b>：{@link #kbLister()} 与
 * {@link #knowledgeSearcher()} 目前是**返回空列表的桩**，而 {@code KnowledgeService}
 * 明明已注入——{@link ImCommandSet.InfoCommand}/{@code SearchCommand} 会读到它们，
 * 因此生产上 {@code /info} 与 {@code /search} 的 KB/检索面**恒为空**。这是功能缺口
 * （疑似未接线），不是重构问题，另立批次核查；搬到一个类里正是为了让这处显眼。</p>
 */
final class ImKnowledgeBridgeOps {

    private static final Logger log = LoggerFactory.getLogger(ImKnowledgeBridgeOps.class);

    /** 附件异步入知识库的文件扩展名白名单。 */
    private static final java.util.Set<String> SUPPORTED_KB_FILE_EXTS = java.util.Set.of(
            "pdf", "txt", "docx", "doc", "md", "markdown",
            "png", "jpg", "jpeg", "gif", "csv", "xlsx", "xls", "pptx", "ppt");

    private final ObjectProvider<KnowledgeService> knowledgeServices;

    ImKnowledgeBridgeOps(ObjectProvider<KnowledgeService> knowledgeServices) {
        this.knowledgeServices = knowledgeServices;
    }

    // ── 命令的依赖面（cmd_info/cmd_search 的 KB/检索读取） ────────────────

    /** 命令注册用（门面在构造期调用）；**当前为返回空列表的桩**，见类注释待办。 */
    ImCommandSet.KnowledgeBaseLister kbLister() {
        return new ImCommandSet.KnowledgeBaseLister() {
            @Override
            public List<KbView> listKnowledgeBases() {
                return List.of();
            }

            @Override
            public List<KbView> listKnowledgeBasesByTenantId(long tenantId) {
                return List.of();
            }
        };
    }

    /** 命令注册用（门面在构造期调用）；**当前为返回空列表的桩**，见类注释待办。 */
    ImCommandSet.KnowledgeSearcher knowledgeSearcher() {
        return (kbIds, knowledgeIds, documentIds, query) -> List.of();
    }

    // ── 附件异步入库 ─────────────────────────────────────────────────────

    /**
     * 附件异步入渠道绑定的知识库：
     * 无用户可见通知——原始文件消息照常收到 QA 回复，入库是后台工作；
     * 渠道未配 KB / 知识服务缺席 / 类型不在白名单 → 直接跳过。
     */
    void ingestAttachmentToKnowledgeBase(ImChannelEntity channel,
            ImAttachmentPreparer.Prepared prepared) {
        String kbId = channel.getKnowledgeBaseId();
        if (kbId == null || kbId.isEmpty() || prepared == null || prepared.raw() == null) {
            return;
        }
        ImAttachmentPreparer.RawFile raw = prepared.raw();
        long tenantId = channel.getTenantId();
        Thread.ofVirtual().name("im-kb-ingest-" + channel.getId()).start(() -> {
            // 后台线程无认证上下文：显式绑渠道租户（知识库写入要求租户在上下文）
            TenantContext.set(tenantId, new TenantContext.Principal(
                    TenantContext.PrincipalTypes.IM_USER, "system-" + tenantId), "viewer",
                    false, "system-" + tenantId, false);
            try {
                ingestAttachmentInner(kbId, channel, raw);
            } finally {
                TenantContext.clear();
            }
        });
    }

    private void ingestAttachmentInner(String kbId, ImChannelEntity channel,
            ImAttachmentPreparer.RawFile raw) {
        String ext = ImAttachmentPreparer.extensionOf(raw.fileName());
        if (!SUPPORTED_KB_FILE_EXTS.contains(ext)) {
            log.info("[IM] Unsupported file type after download: {} (file={})", ext, raw.fileName());
            return;
        }
        KnowledgeService service = knowledgeServices.getIfAvailable();
        if (service == null) {
            return;
        }
        try {
            var knowledge = service.createFromFile(kbId, raw.content(), raw.fileName(),
                    raw.fileName(), null, ImFormat.imPlatformToChannel(channel.getPlatform()));
            log.info("[IM] File saved to knowledge base: kb={} knowledge={} file={}",
                    kbId, knowledge.getId(), raw.fileName());
        } catch (DuplicateKnowledgeException e) {
            log.info("[IM] File already exists in knowledge base: {}", raw.fileName());
        } catch (RuntimeException e) {
            log.error("[IM] Failed to create knowledge from file: {}", e.getMessage());
        }
    }
}
