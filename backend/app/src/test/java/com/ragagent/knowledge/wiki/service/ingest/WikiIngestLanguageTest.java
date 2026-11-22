package com.ragagent.knowledge.wiki.service.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ragagent.framework.text.RequestLanguages;
import com.ragagent.framework.text.Whitespace;
import com.ragagent.knowledge.wiki.domain.TaskPendingOp;
import com.ragagent.knowledge.wiki.domain.ingest.WikiIngestConstants;
import com.ragagent.knowledge.wiki.domain.ingest.WikiPendingOp;
import com.ragagent.knowledge.wiki.prompt.WikiPromptTemplate;
import com.ragagent.knowledge.wiki.prompt.WikiPrompts;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 语言持久化与 prompt 渲染的测试。
 *
 * <p>请求作用域的语言由 {@link RequestLanguages#setCurrentLocale}
 * （线程本地）承载：入队时落定 locale，下游 worker 从 op 载荷读。</p>
 */
class WikiIngestLanguageTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @AfterEach
    void clearLocale() {
        RequestLanguages.clearCurrentLocale();
    }

    // ── 待办 op 的语言持久化 ──

    @Test
    @DisplayName("TestNewWikiIngestPendingOpPersistsResolvedLanguage：请求 locale 被保留")
    void persistsRequestLocale() throws Exception {
        RequestLanguages.setCurrentLocale("en-US");

        TaskPendingOp row = new WikiIngestService(null, null, null, null, null, null,
                null, null, null, null, null, null, null)
                .newWikiIngestPendingOp(7, "kb-1", "knowledge-1");

        WikiPendingOp op = MAPPER.treeToValue(row.getPayload(), WikiPendingOp.class);
        assertThat(op.getLanguage()).isEqualTo("en-US");
        assertThat(row.getTenantId()).isEqualTo(7L);
        assertThat(row.getTaskType()).isEqualTo(WikiIngestConstants.TASK_TYPE);
        assertThat(row.getScope()).isEqualTo(WikiIngestConstants.TASK_SCOPE);
        assertThat(row.getScopeId()).isEqualTo("kb-1");
        assertThat(row.getOp()).isEqualTo(WikiIngestConstants.OP_INGEST);
        assertThat(row.getDedupKey()).isEqualTo("knowledge-1");
    }

    @Test
    @DisplayName("TestNewWikiIngestPendingOpPersistsResolvedLanguage：无语言的后台上下文回落默认值")
    void fallsBackToDefaultLanguage() throws Exception {
        RequestLanguages.clearCurrentLocale();

        TaskPendingOp row = new WikiIngestService(null, null, null, null, null, null,
                null, null, null, null, null, null, null)
                .newWikiIngestPendingOp(7, "kb-1", "knowledge-1");

        WikiPendingOp op = MAPPER.treeToValue(row.getPayload(), WikiPendingOp.class);
        assertThat(op.getLanguage()).isEqualTo(RequestLanguages.defaultLanguage());
        // 关键不变量：绝不持久化空串
        assertThat(op.getLanguage()).isNotEmpty();
    }

    // ── 旧行的语言恢复 ──

    @Test
    @DisplayName("TestResolveLanguageNameRecoversLegacyPendingOp：语言字段为空的旧行仍产出本地化页面")
    void recoversLegacyPendingOp() {
        RequestLanguages.setCurrentLocale("ru-RU");
        // 旧行的 Language 是空串 —— worker 必须拿 ctx + 默认值解析，而不是信载荷
        assertThat(RequestLanguages.resolveLanguageName("")).isEqualTo("Russian");

        RequestLanguages.clearCurrentLocale();
        assertThat(RequestLanguages.resolveLanguageName("")).isNotEmpty();
    }

    @ParameterizedTest(name = "{0} → {1}")
    @DisplayName("locale → 人类可读语言名（对照 Go LanguageLocaleName）")
    @CsvSource({
            "zh-CN, Chinese (Simplified)",
            "zh, Chinese (Simplified)",
            "zh-Hans, Chinese (Simplified)",
            "zh-TW, Chinese (Traditional)",
            "en-US, English",
            "en, English",
            "ru-RU, Russian",
            "fr-FR, French",
            "de-DE, German",
            "es-ES, Spanish",
            "pt-BR, Portuguese",
            // 未知 locale 原样透传
            "xx-YY, xx-YY"
    })
    void localeNameMapping(String locale, String want) {
        assertThat(RequestLanguages.localeName(locale)).isEqualTo(want);
    }

    // ── SlugUpdate 语言解析 ──

    @Test
    @DisplayName("TestResolveSlugUpdateLanguage：4 个子用例逐条对照")
    void resolveSlugUpdateLanguage() {
        RequestLanguages.setCurrentLocale("en-US");

        // 用更新自己携带的语言
        assertThat(RequestLanguages.resolveSlugUpdateLanguage(
                List.of("Korean"))).isEqualTo("Korean");

        // 页面会聚合多篇文档的贡献，第一个不保证带语言
        assertThat(RequestLanguages.resolveSlugUpdateLanguage(
                List.of("", "Korean")))
                .isEqualTo("Korean");

        // 回落到请求语言
        assertThat(RequestLanguages.resolveSlugUpdateLanguage(
                List.of("", "")))
                .isEqualTo("English");

        // 空更新集同样回落
        assertThat(RequestLanguages.resolveSlugUpdateLanguage(null)).isEqualTo("English");
        assertThat(RequestLanguages.resolveSlugUpdateLanguage(List.of())).isEqualTo("English");
    }

    // ── prompt 不渲染空语言 ──

    /**
     * 护栏针对的可观测症状：未解析的语言会把编辑指令渲染成 {@code "Write in ."}，
     * 从而把输出语言交给模型自行决定。
     */
    @Test
    @DisplayName("TestWikiPromptsNeverRenderAnEmptyLanguage：8 个 prompt 都不会渲染出空语言")
    void promptsNeverRenderEmptyLanguage() {
        Map<String, String> prompts = Map.of(
                "WikiPageModifyUserPrompt", WikiPrompts.WIKI_PAGE_MODIFY_USER_PROMPT,
                "WikiSummaryPrompt", WikiPrompts.WIKI_SUMMARY_PROMPT,
                "WikiIndexIntroPrompt", WikiPrompts.WIKI_INDEX_INTRO_PROMPT,
                "WikiIndexIntroUpdatePrompt", WikiPrompts.WIKI_INDEX_INTRO_UPDATE_PROMPT,
                "WikiKnowledgeExtractPrompt", WikiPrompts.WIKI_KNOWLEDGE_EXTRACT_PROMPT,
                "WikiCandidateSlugPrompt", WikiPrompts.WIKI_CANDIDATE_SLUG_PROMPT,
                "WikiChunkCitationPrompt", WikiPrompts.WIKI_CHUNK_CITATION_PROMPT,
                "WikiTaxonomyPlanPrompt", WikiPrompts.WIKI_TAXONOMY_PLAN_PROMPT);

        for (Map.Entry<String, String> e : prompts.entrySet()) {
            String rendered = WikiPromptTemplate.render(e.getValue(), Map.of(
                    "HasAdditions", "1",
                    "Language", RequestLanguages.resolveLanguageName("")));
            assertThat(rendered)
                    .as("%s rendered without a language", e.getKey())
                    .doesNotContain("Write in .")
                    .doesNotContain("in <no value>");
        }
    }

    // ── Java 侧补充：解析链与空白语义 ──

    @Test
    @DisplayName("resolveLanguage：显式 locale 优先于上下文，上下文优先于默认")
    void resolveLanguagePrecedence() {
        RequestLanguages.setCurrentLocale("th-TH");
        assertThat(RequestLanguages.resolveLanguage("ru-RU")).isEqualTo("ru-RU");
        assertThat(RequestLanguages.resolveLanguage("  ")).isEqualTo("th-TH");
        RequestLanguages.clearCurrentLocale();
        assertThat(RequestLanguages.resolveLanguage("")).isEqualTo(RequestLanguages.defaultLanguage());
    }

    @Test
    @DisplayName("Whitespace.trimSpace 覆盖 Unicode White_Space（含 NBSP / 全角空格）")
    void trimCoversGoWhitespace() {
        assertThat(Whitespace.trimSpace("  x  ")).isEqualTo("x");
        assertThat(Whitespace.trimSpace("　x　")).isEqualTo("x");
        assertThat(Whitespace.trimSpace("  x  ")).isEqualTo("x");
        // Java 的 String.strip() 不会裁 NBSP，这正是本工具存在的理由
        assertThat(" x ".strip()).isNotEqualTo("x");
    }
}
