package com.ragagent.chatpipeline.plugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.ragagent.chatpipeline.ChatManage;
import com.ragagent.chatpipeline.PipelineLog;
import com.ragagent.chatpipeline.support.ImageInfoCollector;
import com.ragagent.common.pipeline.ChunkTypes;
import com.ragagent.common.retrieval.SearchResult;
import com.ragagent.common.knowledge.ChunkFacts;
import com.ragagent.retrieval.support.ImageInfoEnricher;
import com.ragagent.retrieval.support.ChunkSearchUtil;
import com.ragagent.retrieval.support.ImageInfoMatchUtil;

/**
 * PluginMerge 的父块解析与短上下文扩展簇：resolveParentChunks（父子关联）、
 * expandShortContextWithNeighbors（短文本邻居补充）。
 */
final class MergeParentOps {

    private final PluginMerge service;

    MergeParentOps(PluginMerge service) {
        this.service = service;
    }

    // ------------------------------------------------------------------
    // 父块解析（text→parent / image→text→grandparent）
    // ------------------------------------------------------------------

    List<SearchResult> resolveParentChunks(ChatManage chatManage, List<SearchResult> results) {
        if (results.isEmpty() || service.chunkRepo == null) {
            return results;
        }

        long tenantId = chatManage != null ? chatManage.getTenantId() : 0;
        if (tenantId == 0) {
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("reason", "missing_tenant");
            PipelineLog.warn("Merge", "parent_resolve_skip", f);
            return results;
        }

        // 收集去重后的父块 ID（保出现序）
        Map<String, Boolean> parentIds = new LinkedHashMap<>();
        for (SearchResult r : results) {
            if (!r.getParentChunkId().isEmpty()) {
                parentIds.putIfAbsent(r.getParentChunkId(), Boolean.TRUE);
            }
        }
        if (parentIds.isEmpty()) {
            return results;
        }

        List<SearchResult> working = results;
        List<ChunkFacts> parentChunks;
        try {
            parentChunks = service.chunkRepo.listChunksById(tenantId, new ArrayList<>(parentIds.keySet()));
        } catch (RuntimeException e) {
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("error", e.getMessage());
            PipelineLog.warn("Merge", "parent_resolve_failed", f);
            return results;
        }

        Map<String, ChunkFacts> parentMap = new LinkedHashMap<>();
        for (ChunkFacts c : parentChunks) {
            parentMap.put(c.id(), c);
        }

        // 图片命中走 image → text → parent_text 链：只为这些结果取祖父块
        Map<String, Boolean> imageTextParentIds = new LinkedHashMap<>();
        for (SearchResult r : working) {
            if (ChunkTypes.IMAGE_OCR.equals(r.getChunkType())
                    || ChunkTypes.IMAGE_CAPTION.equals(r.getChunkType())) {
                imageTextParentIds.putIfAbsent(r.getParentChunkId(), Boolean.TRUE);
            }
        }
        if (!imageTextParentIds.isEmpty()) {
            List<String> grandparentIds = new ArrayList<>();
            Map<String, Boolean> grandparentSeen = new LinkedHashMap<>();
            for (ChunkFacts parent : parentChunks) {
                if (!imageTextParentIds.containsKey(parent.id())) {
                    continue;
                }
                if (parent.parentChunkId().isEmpty() || !ChunkTypes.TEXT.equals(parent.chunkType())) {
                    continue;
                }
                if (parentMap.containsKey(parent.parentChunkId())) {
                    continue;
                }
                if (grandparentSeen.containsKey(parent.parentChunkId())) {
                    continue;
                }
                grandparentSeen.put(parent.parentChunkId(), Boolean.TRUE);
                grandparentIds.add(parent.parentChunkId());
            }
            if (!grandparentIds.isEmpty()) {
                List<ChunkFacts> grandparents;
                try {
                    grandparents = service.chunkRepo.listChunksById(tenantId, grandparentIds);
                    for (ChunkFacts grandparent : grandparents) {
                        parentMap.put(grandparent.id(), grandparent);
                    }
                } catch (RuntimeException e) {
                    Map<String, Object> f = new LinkedHashMap<>();
                    f.put("error", e.getMessage());
                    PipelineLog.warn("Merge", "grandparent_fetch_failed", f);
                }
            }
        }

        // 批量取 image_info（只取命中的 text 子块）
        List<String> textChildIds = collectScopedTextChildIds(working, parentMap);
        Map<String, String> scopedImageInfo = null;
        if (!textChildIds.isEmpty()) {
            scopedImageInfo = collectImageInfoByChunkIds(tenantId, textChildIds);
        }

        for (SearchResult r : working) {
            if (r.getParentChunkId().isEmpty()) {
                continue;
            }

            if (ChunkTypes.TEXT.equals(r.getChunkType())) {
                // text → parent_text：扩展到全父块给上下文；ImageInfo 只取本子块的
                ChunkFacts parent = parentMap.get(r.getParentChunkId());
                if (parent == null || parent.content().isEmpty()
                        || !ChunkTypes.PARENT_TEXT.equals(parent.chunkType())) {
                    continue;
                }
                Map<String, Object> f = new LinkedHashMap<>();
                f.put("child_id", r.getId());
                f.put("parent_id", r.getParentChunkId());
                f.put("child_len", PluginMerge.runeLen(r.getContent()));
                f.put("parent_len", PluginMerge.runeLen(parent.content()));
                f.put("scoped_img", true);
                PipelineLog.info("Merge", "parent_resolve", f);
                assignScopedImageInfo(r, scopedImageInfo, r.getId());
                String parentContent = ImageInfoMatchUtil.pruneMarkdownImagesByImageInfo(
                        parent.content(), r.getImageInfo());
                r.setContent(ChunkSearchUtil.joinChunkContent(parentContent, r.getContent(), "\n\n"));
                r.setContentRewritten(true);
                if (!PluginMerge.containsId(r.getSubChunkId(), r.getId())) {
                    PluginMerge.appendSubChunkId(r, r.getId());
                }
            } else if (ChunkTypes.IMAGE_OCR.equals(r.getChunkType())
                    || ChunkTypes.IMAGE_CAPTION.equals(r.getChunkType())) {
                ChunkFacts textParent = parentMap.get(r.getParentChunkId());
                if (textParent == null || textParent.content().isEmpty()
                        || !ChunkTypes.TEXT.equals(textParent.chunkType())) {
                    continue;
                }
                String hitImageInfo = r.getImageInfo();
                // 命中块本身携带识别文本（Content 是 OCR/描述），先存后覆写（#3052）
                String childRecognizedContent = r.getContent();
                ChunkFacts contentSource = textParent;
                if (!textParent.parentChunkId().isEmpty()) {
                    ChunkFacts grandparent = parentMap.get(textParent.parentChunkId());
                    if (grandparent != null && ChunkTypes.PARENT_TEXT.equals(grandparent.chunkType())
                            && !grandparent.content().isEmpty()) {
                        contentSource = grandparent;
                    }
                }
                r.setContent(textParent.content());
                r.setChunkIndex(textParent.chunkIndex());
                r.setContentRewritten(true);
                assignScopedImageInfo(r, scopedImageInfo, textParent.id());
                if (r.getImageInfo().isEmpty() && !hitImageInfo.isEmpty()) {
                    r.setImageInfo(ImageInfoMatchUtil.filterImageInfoByContentUrls(
                            textParent.content(), hitImageInfo));
                }
                String textContent = ImageInfoMatchUtil.pruneMarkdownImagesByImageInfo(
                        textParent.content(), r.getImageInfo());
                String parentContent = ImageInfoMatchUtil.pruneMarkdownImagesByImageInfo(
                        contentSource.content(), r.getImageInfo());
                r.setContent(ChunkSearchUtil.joinChunkContent(parentContent, textContent, "\n\n"));
                // 父/祖父 markdown 之后重新接上识别文本（JoinChunkContent 折叠重复）
                r.setContent(ChunkSearchUtil.joinChunkContent(r.getContent(), childRecognizedContent, "\n\n"));
                r.setImageInfo(ImageInfoEnricher.clearImageInfoTextMatchingBody(
                        r.getImageInfo(), childRecognizedContent, r.getChunkType()));
                r.setContentRewritten(true);
                Map<String, Object> f = new LinkedHashMap<>();
                f.put("child_id", r.getId());
                f.put("child_type", r.getChunkType());
                f.put("text_id", textParent.id());
                f.put("parent_id", contentSource.id());
                f.put("match_len", PluginMerge.runeLen(r.getContent()));
                f.put("parent_len", PluginMerge.runeLen(contentSource.content()));
                f.put("scoped", true);
                PipelineLog.info("Merge", "image_parent_resolve", f);
                if (!PluginMerge.containsId(r.getSubChunkId(), r.getId())) {
                    PluginMerge.appendSubChunkId(r, r.getId());
                }
            }
        }

        return working;
    }


    static List<String> collectScopedTextChildIds(List<SearchResult> results, Map<String, ChunkFacts> parentMap) {
        Map<String, Boolean> seen = new LinkedHashMap<>();
        List<String> ids = new ArrayList<>();
        for (SearchResult r : results) {
            if (r.getParentChunkId().isEmpty()) {
                continue;
            }
            switch (r.getChunkType()) {
                case ChunkTypes.TEXT -> {
                    ChunkFacts parent = parentMap.get(r.getParentChunkId());
                    if (parent == null || !ChunkTypes.PARENT_TEXT.equals(parent.chunkType())) {
                        continue;
                    }
                    if (seen.containsKey(r.getId())) {
                        continue;
                    }
                    seen.put(r.getId(), Boolean.TRUE);
                    ids.add(r.getId());
                }
                case ChunkTypes.IMAGE_OCR, ChunkTypes.IMAGE_CAPTION -> {
                    if (seen.containsKey(r.getParentChunkId())) {
                        continue;
                    }
                    seen.put(r.getParentChunkId(), Boolean.TRUE);
                    ids.add(r.getParentChunkId());
                }
                default -> {}
            }
        }
        return ids;
    }


    /** per-child image_info 优先，回落按内容 URL 过滤。 */
    static void assignScopedImageInfo(SearchResult r, Map<String, String> scoped, String textChildId) {
        if (scoped != null) {
            String info = scoped.get(textChildId);
            if (info != null && !info.isEmpty()) {
                r.setImageInfo(info);
                return;
            }
        }
        if (!r.getImageInfo().isEmpty()) {
            r.setImageInfo(ImageInfoMatchUtil.filterImageInfoByContentUrls(r.getContent(), r.getImageInfo()));
        }
    }


    /** 聚合器在 {@link ImageInfoCollector}。 */
    Map<String, String> collectImageInfoByChunkIds(long tenantId, List<String> chunkIds) {
        return ImageInfoCollector.collect(service.chunkRepo, tenantId, chunkIds);
    }


    // ------------------------------------------------------------------
    // 短上下文邻居扩展
    // ------------------------------------------------------------------

    List<SearchResult> expandShortContextWithNeighbors(ChatManage chatManage, List<SearchResult> results) {
        final int minLen = 350;
        final int maxLen = 850;

        if (results.isEmpty() || service.chunkRepo == null) {
            return results;
        }

        long tenantId = chatManage != null ? chatManage.getTenantId() : 0;
        if (tenantId == 0) {
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("reason", "missing_tenant");
            PipelineLog.warn("Merge", "expand_skip", f);
            return results;
        }

        List<SearchResult> targets = new ArrayList<>();
        Map<String, Boolean> baseIdsSet = new LinkedHashMap<>();

        for (SearchResult r : results) {
            if (r == null || r.getId().isEmpty() || r.getContent().isEmpty()) {
                continue;
            }
            if (!ChunkTypes.TEXT.equals(r.getChunkType())) {
                continue;
            }
            if (PluginMerge.runeLen(r.getContent()) >= minLen) {
                continue;
            }
            targets.add(r);
            baseIdsSet.put(r.getId(), Boolean.TRUE);
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("chunk_id", r.getId());
            f.put("content", r.getContent());
            f.put("chunk_type", r.getChunkType());
            f.put("len", PluginMerge.runeLen(r.getContent()));
            PipelineLog.info("Merge", "need_expand", f);
        }

        if (targets.isEmpty()) {
            return results;
        }

        List<String> baseIds = new ArrayList<>(baseIdsSet.keySet());

        Map<String, ChunkFacts> chunkMap = new LinkedHashMap<>();
        List<ChunkFacts> chunks;
        try {
            chunks = service.chunkRepo.listChunksById(tenantId, baseIds);
        } catch (RuntimeException e) {
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("error", e.getMessage());
            PipelineLog.warn("Merge", "expand_list_base_failed", f);
            return results;
        }
        for (ChunkFacts chunk : chunks) {
            chunkMap.put(chunk.id(), chunk);
        }

        Map<String, Boolean> neighborIdsSet = new LinkedHashMap<>();
        for (ChunkFacts chunk : chunkMap.values()) {
            if (chunk == null) {
                continue;
            }
            if (!chunk.preChunkId().isEmpty() && !chunkMap.containsKey(chunk.preChunkId())) {
                neighborIdsSet.put(chunk.preChunkId(), Boolean.TRUE);
            }
            if (!chunk.nextChunkId().isEmpty() && !chunkMap.containsKey(chunk.nextChunkId())) {
                neighborIdsSet.put(chunk.nextChunkId(), Boolean.TRUE);
            }
        }

        if (!neighborIdsSet.isEmpty()) {
            List<String> neighborIDs = new ArrayList<>(neighborIdsSet.keySet());
            try {
                List<ChunkFacts> neighbors = service.chunkRepo.listChunksById(tenantId, neighborIDs);
                for (ChunkFacts chunk : neighbors) {
                    chunkMap.put(chunk.id(), chunk);
                    Map<String, Object> f = new LinkedHashMap<>();
                    f.put("neighbor_chunk_id", chunk.id());
                    f.put("neighbor_content", chunk.content());
                    f.put("neighbor_chunk_type", chunk.chunkType());
                    f.put("neighbor_len", PluginMerge.runeLen(chunk.content()));
                    PipelineLog.info("Merge", "expand_list_neighbor_success", f);
                }
            } catch (RuntimeException e) {
                Map<String, Object> f = new LinkedHashMap<>();
                f.put("error", e.getMessage());
                PipelineLog.warn("Merge", "expand_list_neighbor_failed", f);
            }
        }

        for (SearchResult res : targets) {
            fetchChunksIfMissing(tenantId, chunkMap, res.getId());
            ChunkFacts baseChunk = chunkMap.get(res.getId());
            if (baseChunk == null || baseChunk.content().isEmpty()
                    || !ChunkTypes.TEXT.equals(baseChunk.chunkType())) {
                continue;
            }

            StringBuilder prevContent = new StringBuilder();
            StringBuilder nextContent = new StringBuilder();
            List<String> prevIDs = new ArrayList<>();
            List<String> nextIDs = new ArrayList<>();

            String prevCursor = baseChunk.preChunkId();
            String nextCursor = baseChunk.nextChunkId();

            fetchChunksIfMissing(tenantId, chunkMap, prevCursor, nextCursor);

            if (!prevCursor.isEmpty()) {
                ChunkFacts prevChunk = chunkMap.get(prevCursor);
                if (prevChunk != null && prevChunk.knowledgeId().equals(baseChunk.knowledgeId())) {
                    prevContent.append(prevChunk.content());
                    prevIDs.add(prevChunk.id());
                    prevCursor = prevChunk.preChunkId();
                } else {
                    prevCursor = "";
                }
            }

            if (!nextCursor.isEmpty()) {
                ChunkFacts nextChunk = chunkMap.get(nextCursor);
                if (nextChunk != null && nextChunk.knowledgeId().equals(baseChunk.knowledgeId())) {
                    nextContent.append(nextChunk.content());
                    nextIDs.add(nextChunk.id());
                    nextCursor = nextChunk.nextChunkId();
                } else {
                    nextCursor = "";
                }
            }

            String merged;
            while (true) {
                merged = PluginMerge.mergeOrderedContent(prevContent.toString(), baseChunk.content(),
                        nextContent.toString(), maxLen);
                if (merged.isEmpty()) {
                    break;
                }
                if (PluginMerge.runeLen(merged) >= minLen) {
                    break;
                }
                if (prevCursor.isEmpty() && nextCursor.isEmpty()) {
                    break;
                }

                boolean expanded = false;
                if (!prevCursor.isEmpty()) {
                    fetchChunksIfMissing(tenantId, chunkMap, prevCursor);
                    ChunkFacts prevChunk = chunkMap.get(prevCursor);
                    if (prevChunk != null && prevChunk.knowledgeId().equals(baseChunk.knowledgeId())) {
                        // 前块内容前接（带重叠折叠，\n\n 连接）
                    prevContent = new StringBuilder(
                            ChunkSearchUtil.joinChunkContent(prevChunk.content(), prevContent.toString(), "\n\n"));
                        prevIDs.add(0, prevChunk.id());
                        prevCursor = prevChunk.preChunkId();
                        expanded = true;
                    } else {
                        prevCursor = "";
                    }
                }

                merged = PluginMerge.mergeOrderedContent(prevContent.toString(), baseChunk.content(),
                        nextContent.toString(), maxLen);
                if (PluginMerge.runeLen(merged) >= minLen) {
                    break;
                }

                if (!nextCursor.isEmpty()) {
                    fetchChunksIfMissing(tenantId, chunkMap, nextCursor);
                    ChunkFacts nextChunk = chunkMap.get(nextCursor);
                    if (nextChunk != null && nextChunk.knowledgeId().equals(baseChunk.knowledgeId())) {
                        // 后块内容后接（带重叠折叠，\n\n 连接）
                        nextContent = new StringBuilder(
                                ChunkSearchUtil.joinChunkContent(nextContent.toString(), nextChunk.content(), "\n\n"));
                        nextIDs.add(nextChunk.id());
                        nextCursor = nextChunk.nextChunkId();
                        expanded = true;
                    } else {
                        nextCursor = "";
                    }
                }

                if (!expanded) {
                    break;
                }
            }

            if (merged.isEmpty()) {
                continue;
            }

            int beforeLen = PluginMerge.runeLen(res.getContent());
            res.setContent(merged);
            res.setContentRewritten(true);

            for (String id : prevIDs) {
                if (!id.isEmpty() && !PluginMerge.containsId(res.getSubChunkId(), id)) {
                    PluginMerge.appendSubChunkId(res, id);
                }
            }
            for (String id : nextIDs) {
                if (!id.isEmpty() && !PluginMerge.containsId(res.getSubChunkId(), id)) {
                    PluginMerge.appendSubChunkId(res, id);
                }
            }

            Map<String, Object> f = new LinkedHashMap<>();
            f.put("chunk_id", res.getId());
            f.put("prev_ids", prevIDs);
            f.put("next_ids", nextIDs);
            f.put("before_len", beforeLen);
            f.put("after_len", PluginMerge.runeLen(res.getContent()));
            f.put("base_content", baseChunk.content());
            f.put("after_content", res.getContent());
            f.put("chunk_type", res.getChunkType());
            f.put("remaining_prev", prevCursor);
            f.put("remaining_next", nextCursor);
            PipelineLog.info("Merge", "expand_short_chunk", f);
        }

        return results;
    }



    void fetchChunksIfMissing(long tenantId, Map<String, ChunkFacts> chunkMap, String... chunkIds) {
        List<String> missing = new ArrayList<>(chunkIds.length);
        for (String id : chunkIds) {
            if (id == null || id.isEmpty()) {
                continue;
            }
            if (!chunkMap.containsKey(id)) {
                missing.add(id);
            }
        }
        if (missing.isEmpty()) {
            return;
        }

        List<ChunkFacts> chunks;
        try {
            chunks = service.chunkRepo.listChunksById(tenantId, missing);
        } catch (RuntimeException e) {
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("missing_cnt", missing.size());
            f.put("error", e.getMessage());
            PipelineLog.warn("Merge", "expand_fetch_missing_failed", f);
            chunks = new ArrayList<>();
        }

        Map<String, Boolean> found = new LinkedHashMap<>();
        for (ChunkFacts chunk : chunks) {
            chunkMap.put(chunk.id(), chunk);
            found.put(chunk.id(), Boolean.TRUE);
        }

        for (String id : missing) {
            if (!found.containsKey(id)) {
                chunkMap.put(id, null);
            }
        }
    }


}
