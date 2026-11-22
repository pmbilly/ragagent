package com.ragagent.knowledge.api.faq;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * FAQ 规范化与内容哈希的 Go 语料对拍（随 FaqChunkMetadata 自 framework 迁入）。
 */
class FaqChunkMetadataCorpusTest {

private static final String[][] NORM_CORPUS = {
            {"怎麼綁定手機？", "怎么绑定手机"},
            {"軟體怎麼下載", "软体怎么下载"},
            {"簡體轉換：環境變量設置，人才招聘，皇后於是", "简体转换:环境变量设置,人才招聘,皇后于是"},
            {"乾乾淨淨的乾隆年間", "干干净净的乾隆年间"},
            {"面髮與頭髮", "面发与头发"},
            {"  Hello World  ", "hello world"},
            {"怎麼 綁定 手機", "怎么绑定手机"},
            {"iphone 15 怎麼 激活", "iphone 15怎么激活"},
            {"访问 https://example.com/a?b=1 获取", "访问获取"},
            {"問題？！。，；、！?.,;!:'\"", "问题"},
            {"全角ＡＢＣ１２３：", "全角abc123"},
    };

    @Test
    void normalizeQuestion_matchesGoCorpus() {
        for (String[] pair : NORM_CORPUS) {
            assertThat(FaqChunkMetadata.normalizeQuestion(pair[0]))
                    .as("NormalizeQuestion(%s)", pair[0])
                    .isEqualTo(pair[1]);
        }
    }


    @Test
    void contentHash_matchesGoCorpus() {
        // 语料期望值：calculateContentHash（aa0f3822...）
        FaqChunkMetadata meta =
                new FaqChunkMetadata();
        meta.standardQuestion = "怎么 绑定 手机？";
        meta.similarQuestions = java.util.List.of("如何绑定手机", "How to bind phone");
        meta.negativeQuestions = java.util.List.of("怎么解绑手机");
        meta.answers = java.util.List.of("进入设置，选择设备，点击绑定。");
        meta.answerStrategy = "all";
        meta.version = 1;
        meta.source = "faq";
        assertThat(FaqChunkMetadata.calculateContentHash(meta))
                .isEqualTo("aa0f3822df446266592d86568ac5440c3fcfb388161730e043132f306bbbc44e");

        // 相似问顺序不影响 hash（排序后进串）
        FaqChunkMetadata reordered =
                new FaqChunkMetadata();
        reordered.standardQuestion = "怎么 绑定 手机？";
        reordered.similarQuestions = java.util.List.of("How to bind phone", "如何绑定手机");
        reordered.answers = java.util.List.of("进入设置，选择设备，点击绑定。");
        reordered.answerStrategy = "all";
        reordered.version = 1;
        reordered.source = "faq";
        assertThat(FaqChunkMetadata.calculateContentHash(reordered))
                .isEqualTo("4779365a6b2bd1f57353fee4c5eefd56485d10976062c73ae30aff3ed76fc5da");
    }


    @Test
    void sanitize_matchesGoSemantics() {
        FaqChunkMetadata meta =
                new FaqChunkMetadata();
        meta.standardQuestion = "  x  ";
        meta.similarQuestions = new java.util.ArrayList<>(java.util.List.of(" a ", "", " a ", "b"));
        meta.version = 0;
        meta.sanitize();
        assertThat(meta.standardQuestion).isEqualTo("x");
        assertThat(meta.similarQuestions).containsExactly("a", "b");
        assertThat(meta.version).isEqualTo(1);
    }
}
