package com.ragagent.framework.html;

import static org.assertj.core.api.Assertions.assertThat;

import com.ragagent.framework.text.TextConv;
import org.junit.jupiter.api.Test;

/**
 * 繁转简转换器 + FAQ 归一化纯函数的语料测试。期望值全部钉死在常量里
 * （SHA-256 校验过的词典 + 贪心最长匹配）。
 *
 * <p>词典数据文件以 SHA-256 核对（记录见 data/README.md）——词典更新会改变
 * FAQ 归一化与 content_hash，更新前先更新这里的语料。</p>
 */
class TextConvTest {

    private static final String[] CONV_INPUTS = {
            "怎麼綁定手機？",
            "軟體怎麼下載",
            "簡體轉換：環境變量設置，人才招聘，皇后於是",
            "乾乾淨淨的乾隆年間",
            "面髮與頭髮",
    };

    private static final String[] CONV_EXPECTED = {
            "怎么绑定手机？",
            "软体怎么下载",
            "简体转换：环境变量设置，人才招聘，皇后于是",
            "干干净净的乾隆年间",
            "面发与头发",
    };

    /** NORM 语料：{输入 → normalizeQuestion 输出}。 */

    @Test
    void toSimplified_matchesGoCorpus() {
        for (int i = 0; i < CONV_INPUTS.length; i++) {
            assertThat(TextConv.toSimplified(CONV_INPUTS[i]))
                    .as("ToSimplified(%s)", CONV_INPUTS[i])
                    .isEqualTo(CONV_EXPECTED[i]);
        }
    }
}
