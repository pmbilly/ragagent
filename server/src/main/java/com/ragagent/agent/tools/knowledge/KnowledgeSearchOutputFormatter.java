package com.ragagent.agent.tools.knowledge;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import com.fasterxml.jackson.databind.JsonNode;
import com.ragagent.common.llm.ToolResult;
import com.ragagent.agent.tools.DocChunkSupport.ImageInfoView;
import com.ragagent.common.knowledge.FaqChunkMetadata;
import com.ragagent.knowledge.domain.Chunk;

import com.ragagent.agent.tools.knowledge.KnowledgeSearchTool.ResultWithMeta;
import com.ragagent.agent.tools.DocChunkSupport;

import static com.ragagent.agent.tools.knowledge.KnowledgeSearchTool.nz;


final class KnowledgeSearchOutputFormatter {


    private final KnowledgeSearchTool tool;

    KnowledgeSearchOutputFormatter(KnowledgeSearchTool tool) {
        this.tool = tool;
    }

    FaqChunkMetadata getFAQMetadata(String chunkID, Map<String, FaqChunkMetadata> cache) {
        if (chunkID.isEmpty() || tool.chunkBackend == null) {
            return null;
        }
        if (cache.containsKey(chunkID)) {
            return cache.get(chunkID);
        }
        Chunk chunk;
        try {
            chunk = tool.chunkBackend.faqChunkById(chunkID);
        } catch (RuntimeException e) {
            cache.put(chunkID, null);
            return null;
        }
        if (chunk == null) {
            cache.put(chunkID, null);
            return null;
        }
        FaqChunkMetadata meta = FaqSnippet.faqMetadata(chunk);
        cache.put(chunkID, meta);
        return meta;
    }

    /** 文档级 custom metadata 头（每文档一次，仅当非空）。 */
    static void writeKnowledgeMetadataHeader(StringBuilder ob, List<ResultWithMeta> results) {
        Set<String> seen = new LinkedHashSet<>();
        boolean hasMetadata = false;
        StringBuilder documents = new StringBuilder();
        for (ResultWithMeta result : results) {
            if (result == null || result.sr == null || nz(result.sr.knowledgeId).isEmpty()
                    || nz(result.sr.knowledgeCustomMetadata).isEmpty()) {
                continue;
            }
            if (!seen.add(result.sr.knowledgeId)) {
                continue;
            }
            hasMetadata = true;
            documents.append(String.format(Locale.ROOT,
                    "<document knowledgeId=\"%s\" knowledgeBaseId=\"%s\" title=\"%s\">\n",
                    FaqSnippet.xmlEscape(result.sr.knowledgeId),
                    FaqSnippet.xmlEscape(result.sr.knowledgeBaseId),
                    FaqSnippet.xmlEscape(result.sr.knowledgeTitle)));
            documents.append(String.format(Locale.ROOT, "<metadata>%s</metadata>\n",
                    FaqSnippet.xmlEscape(result.sr.knowledgeCustomMetadata)));
            documents.append("</document>\n");
        }
        if (!hasMetadata) {
            return;
        }
        ob.append("<documents>\n");
        ob.append(documents);
        ob.append("</documents>\n");
    }

    /** 工具输出渲染。 */
    ToolResult formatOutput(List<ResultWithMeta> results, List<String> kbsToSearch, List<String> queries) {
        if (results.isEmpty()) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("knowledgeBaseIds", kbsToSearch);
            data.put("results", List.of());
            data.put("count", 0);
            if (!queries.isEmpty()) {
                data.put("queries", queries);
            }
            String output = String.format(Locale.ROOT,
                    "No relevant content found in %d knowledge base(s).\n\n", kbsToSearch.size())
                    + "=== ⚠️ CRITICAL - Next Steps ===\n"
                    + "- ❌ DO NOT use training data or general knowledge to answer\n"
                    + "- ✅ If web_search is enabled: You MUST use web_search to find information\n"
                    + "- ✅ If web_search is disabled: State 'I couldn't find relevant information in the knowledge base'\n"
                    + "- NEVER fabricate or infer answers - ONLY use retrieved content\n";
            ToolResult r = new ToolResult();
            r.setSuccess(true);
            r.setOutput(output);
            r.setData(data);
            return r;
        }

        Map<String, Integer> kbCounts = new LinkedHashMap<>();
        for (ResultWithMeta r : results) {
            kbCounts.merge(nz(r.sr.knowledgeBaseId), 1, Integer::sum);
        }

        StringBuilder ob = new StringBuilder();
        ob.append(String.format(Locale.ROOT, "<searchResults count=\"%d\">\n", results.size()));
        for (String q : queries) {
            ob.append(String.format(Locale.ROOT, "<query>%s</query>\n", FaqSnippet.xmlEscape(q)));
        }
        writeKnowledgeMetadataHeader(ob, results);

        List<Map<String, Object>> formattedResults = new ArrayList<>(results.size());
        Map<String, FaqChunkMetadata> faqMetadataCache = new LinkedHashMap<>();
        Map<String, Set<Integer>> knowledgeChunkMap = new LinkedHashMap<>();
        Map<String, Long> knowledgeTotalMap = new LinkedHashMap<>();
        Map<String, String> knowledgeTitleMap = new LinkedHashMap<>();

        for (int i = 0; i < results.size(); i++) {
            ResultWithMeta result = results.get(i);
            FaqChunkMetadata faqMeta = null;
            if (KnowledgeSearchTool.KB_TYPE_FAQ.equals(result.knowledgeBaseType)) {
                faqMeta = getFAQMetadata(nz(result.sr.id), faqMetadataCache);
            }

            knowledgeChunkMap.computeIfAbsent(nz(result.sr.knowledgeId), k -> new LinkedHashSet<>())
                    .add(result.sr.chunkIndex);
            knowledgeTitleMap.put(nz(result.sr.knowledgeId), nz(result.sr.knowledgeTitle));

            if (!knowledgeTotalMap.containsKey(nz(result.sr.knowledgeId))) {
                long effectiveTenantID = tool.searchTargets == null ? 0
                        : tool.searchTargets.getTenantIdForKb(nz(result.sr.knowledgeBaseId));
                if (effectiveTenantID == 0) {
                    knowledgeTotalMap.put(nz(result.sr.knowledgeId), 0L);
                } else {
                    long total;
                    try {
                        total = tool.chunkBackend.totalChunks(effectiveTenantID, nz(result.sr.knowledgeId));
                    } catch (RuntimeException e) {
                        total = 0;
                    }
                    knowledgeTotalMap.put(nz(result.sr.knowledgeId), total);
                }
            }

            boolean seen = !tool.seenChunks.add(nz(result.sr.id));
            boolean isFAQ = faqMeta != null;

            String sourceQuery = nz(result.sourceQuery);
            if (seen) {
                if (isFAQ) {
                    ob.append(String.format(Locale.ROOT,
                            "<faq rank=\"%d\" faqId=\"%s\" index=\"%d\" knowledgeBaseId=\"%s\" knowledgeTitle=\"%s\" score=\"%.3f\" sourceQuery=\"%s\" already_seen=\"true\">\n",
                            i + 1, FaqSnippet.xmlEscape(nz(result.sr.id)), result.sr.chunkIndex,
                            FaqSnippet.xmlEscape(nz(result.sr.knowledgeBaseId)),
                            FaqSnippet.xmlEscape(nz(result.sr.knowledgeTitle)),
                            result.sr.score, FaqSnippet.xmlEscape(sourceQuery)));
                } else {
                    ob.append(String.format(Locale.ROOT,
                            "<chunk rank=\"%d\" chunkId=\"%s\" chunkIndex=\"%d\" knowledgeId=\"%s\" knowledgeBaseId=\"%s\" knowledgeTitle=\"%s\" score=\"%.3f\" sourceQuery=\"%s\" already_seen=\"true\">\n",
                            i + 1, FaqSnippet.xmlEscape(nz(result.sr.id)), result.sr.chunkIndex,
                            FaqSnippet.xmlEscape(nz(result.sr.knowledgeId)),
                            FaqSnippet.xmlEscape(nz(result.sr.knowledgeBaseId)),
                            FaqSnippet.xmlEscape(nz(result.sr.knowledgeTitle)),
                            result.sr.score, FaqSnippet.xmlEscape(sourceQuery)));
                }
                ob.append("<note>(content omitted, already returned in a previous knowledge_search call this session)</note>\n");
                ob.append(isFAQ ? "</faq>\n" : "</chunk>\n");
            } else {
                if (isFAQ) {
                    ob.append(String.format(Locale.ROOT,
                            "<faq rank=\"%d\" faqId=\"%s\" index=\"%d\" knowledgeBaseId=\"%s\" knowledgeTitle=\"%s\" score=\"%.3f\" sourceQuery=\"%s\">\n",
                            i + 1, FaqSnippet.xmlEscape(nz(result.sr.id)), result.sr.chunkIndex,
                            FaqSnippet.xmlEscape(nz(result.sr.knowledgeBaseId)),
                            FaqSnippet.xmlEscape(nz(result.sr.knowledgeTitle)),
                            result.sr.score, FaqSnippet.xmlEscape(sourceQuery)));
                } else {
                    ob.append(String.format(Locale.ROOT,
                            "<chunk rank=\"%d\" chunkId=\"%s\" chunkIndex=\"%d\" knowledgeId=\"%s\" knowledgeBaseId=\"%s\" knowledgeTitle=\"%s\" score=\"%.3f\" sourceQuery=\"%s\">\n",
                            i + 1, FaqSnippet.xmlEscape(nz(result.sr.id)), result.sr.chunkIndex,
                            FaqSnippet.xmlEscape(nz(result.sr.knowledgeId)),
                            FaqSnippet.xmlEscape(nz(result.sr.knowledgeBaseId)),
                            FaqSnippet.xmlEscape(nz(result.sr.knowledgeTitle)),
                            result.sr.score, FaqSnippet.xmlEscape(sourceQuery)));
                }
                String snippet = "";
                if (faqMeta != null) {
                    snippet = FaqSnippet.faqMatchSnippetFromQueries(faqMeta, queries);
                }
                if (snippet.isEmpty()) {
                    snippet = extractSnippetForQueries(nz(result.sr.content), queries);
                }
                if (!snippet.isEmpty()) {
                    ob.append(String.format(Locale.ROOT, "<matchSnippet>%s</matchSnippet>\n",
                            FaqSnippet.xmlEscape(snippet)));
                }
                // content 原样输出，不做 xmlEscape（输出契约）。
                ob.append(String.format(Locale.ROOT, "<content>%s</content>\n", nz(result.sr.content)));

                if (!nz(result.sr.imageInfo).isEmpty()) {
                    List<ImageInfoView> imageInfos = parseImageInfoList(result.sr.imageInfo);
                    for (ImageInfoView img : imageInfos) {
                        String md = DocChunkSupport.buildImageInfoMarkdownWithURL(img.url(), img);
                        if (!md.isEmpty()) {
                            ob.append(md).append('\n');
                        }
                    }
                }

                if (isFAQ) {
                    FaqSnippet.writeFaqFieldsXml(ob, faqMeta);
                    ob.append("</faq>\n");
                } else {
                    ob.append("</chunk>\n");
                }
            }

            Map<String, Object> formatted = new LinkedHashMap<>();
            formatted.put("resultIndex", i + 1);
            formatted.put("content", nz(result.sr.content));
            formatted.put("knowledgeId", nz(result.sr.knowledgeId));
            formatted.put("knowledgeBaseId", nz(result.sr.knowledgeBaseId));
            formatted.put("knowledgeTitle", nz(result.sr.knowledgeTitle));
            formatted.put("knowledgeMetadata", nz(result.sr.knowledgeCustomMetadata));
            formatted.put("matchType", result.sr.matchType);
            formatted.put("sourceQuery", sourceQuery);
            formatted.put("queryType", nz(result.queryType));
            formatted.put("knowledgeBaseType", nz(result.knowledgeBaseType));
            formattedResults.add(formatted);

            Map<String, Object> last = formatted;

            if (!nz(result.sr.imageInfo).isEmpty()) {
                List<Map<String, String>> imageList = new ArrayList<>();
                for (ImageInfoView img : parseImageInfoList(result.sr.imageInfo)) {
                    Map<String, String> imgData = new LinkedHashMap<>();
                    if (!nz(img.url()).isEmpty()) {
                        imgData.put("url", img.url());
                    }
                    if (!nz(img.caption()).isEmpty()) {
                        imgData.put("caption", img.caption());
                    }
                    if (!nz(img.ocrText()).isEmpty()) {
                        imgData.put("ocrText", img.ocrText());
                    }
                    if (!imgData.isEmpty()) {
                        imageList.add(imgData);
                    }
                }
                if (!imageList.isEmpty()) {
                    last.put("images", imageList);
                }
            }

            if (faqMeta != null) {
                last.put("faqId", nz(result.sr.id));
                last.put("index", result.sr.chunkIndex);
                if (!nz(faqMeta.standardQuestion).isEmpty()) {
                    last.put("faqStandardQuestion", faqMeta.standardQuestion);
                }
                FaqSnippet.appendSimilarQuestionsToChunkData(last, faqMeta.similarQuestions);
                if (faqMeta.answers != null && !faqMeta.answers.isEmpty()) {
                    last.put("faqAnswers", faqMeta.answers);
                }
            } else {
                last.put("chunkId", nz(result.sr.id));
                last.put("chunkIndex", result.sr.chunkIndex);
            }
        }

        ob.append("<retrievalStatistics>\n");
        for (Map.Entry<String, Set<Integer>> e : knowledgeChunkMap.entrySet()) {
            String knowledgeID = e.getKey();
            long totalChunks = knowledgeTotalMap.getOrDefault(knowledgeID, 0L);
            int retrievedCount = e.getValue().size();
            String title = knowledgeTitleMap.getOrDefault(knowledgeID, "");
            if (totalChunks > 0) {
                long remaining = totalChunks - retrievedCount;
                double percentage = retrievedCount / (double) totalChunks * 100;
                ob.append(String.format(Locale.ROOT,
                        "<documentStat knowledgeId=\"%s\" title=\"%s\" totalChunks=\"%d\" retrieved=\"%d\" remaining=\"%d\" coverage=\"%.1f%%\" />\n",
                        FaqSnippet.xmlEscape(knowledgeID), FaqSnippet.xmlEscape(title), totalChunks,
                        retrievedCount, remaining, percentage));
            }
        }
        ob.append("</retrievalStatistics>\n");
        ob.append("</searchResults>");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("knowledgeBaseIds", kbsToSearch);
        data.put("results", formattedResults);
        data.put("count", formattedResults.size());
        data.put("kbCounts", kbCounts);
        data.put("displayType", "search_results");
        if (!queries.isEmpty()) {
            data.put("queries", queries);
        }

        ToolResult r = new ToolResult();
        r.setSuccess(true);
        r.setOutput(ob.toString());
        r.setData(data);
        return r;
    }

    /** ImageInfo JSON 解析（url/caption/ocr_text 数组）。 */
    static List<ImageInfoView> parseImageInfoList(String imageInfoJson) {
        List<ImageInfoView> out = new ArrayList<>();
        JsonNode arr;
        try {
            arr = KnowledgeSearchTool.RecordingSupportHolder.MAPPER.readTree(imageInfoJson);
        } catch (java.io.IOException e) {
            return out;
        }
        if (arr == null || !arr.isArray()) {
            return out;
        }
        for (JsonNode img : arr) {
            out.add(new ImageInfoView(img.path("url").asText(""),
                    img.path("caption").asText(""), img.path("ocr_text").asText("")));
        }
        return out;
    }

    /**
     * query token 最早命中上下文（各 200 runes），
     * 无命中回落前 400 runes + " ..."；单线折叠空格，"... x ..." 包裹。
     */
    static String extractSnippetForQueries(String content, List<String> queries) {
        content = nz(content).trim();
        if (content.isEmpty()) {
            return "";
        }

        List<String> tokens = FaqSnippet.searchQueryTokens(queries);

        String lowered = content.toLowerCase(Locale.ROOT);
        int earliest = -1;
        int earliestEnd = -1;
        for (String tok : tokens) {
            int idx = lowered.indexOf(tok);
            if (idx < 0) {
                continue;
            }
            int end = idx + tok.length();
            if (earliest < 0 || idx < earliest) {
                earliest = idx;
                earliestEnd = end;
            }
        }

        if (earliest < 0) {
            if (content.codePointCount(0, content.length()) > 400) {
                return content.substring(0, content.offsetByCodePoints(0, 400)).trim() + " ...";
            }
            return content;
        }

        String matchStr = content.substring(earliest, earliestEnd);
        String before = content.substring(0, earliest);
        String after = content.substring(earliestEnd);

        String beforeRunes = GrepChunksTool.lastRunes(before, 200);
        String afterRunes = GrepChunksTool.firstRunes(after, 200);

        String snippet = beforeRunes + matchStr + afterRunes;
        snippet = snippet.replace("\n", " ");
        while (snippet.contains("  ")) {
            snippet = snippet.replace("  ", " ");
        }
        return "... " + snippet.trim() + " ...";
    }

    /** 测试侧 RecordingSupport 不可引用主源码，经此 holders 取 mapper。 */
}
