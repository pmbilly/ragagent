package com.ragagent.agent.management.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ragagent.agent.management.mapper.AgentQuestionMapper;
import java.lang.reflect.Method;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 推荐问题（suggested-questions）的键名守卫。
 *
 * <p><b>实锤过的故障（B68）</b>：生成问题写在 {@code chunks.metadata.generatedQuestions}
 * （camel——写入侧是 Java 域类型 {@code DocumentChunkMetadata}，Jackson 默认 camel，
 * 且从未有过改名迁移、全库实测 0 行 snake），而推荐问题的读取侧（本包的
 * {@code firstGeneratedQuestion} 与 {@code AgentQuestionMapper} 的 {@code LIKE} 过滤）
 * 却按 snake {@code generated_questions} 取 → SQL 恒命中 0 行、解析恒 null
 * ⇒ 智能体「推荐问题」永远为空（真机：端点返回 {@code []}，修复后返回库里的问题）。</p>
 *
 * <p>这里同时钉住两处：SQL 过滤用 camel 键（并容忍 snake 存量行）、解析 camel 优先。</p>
 */
class AgentSuggestedQuestionsTest {

    @Test
    @DisplayName("解析：camel 键优先，snake 存量行回落；畸形/空值不抛错")
    void firstGeneratedQuestionReadsCamelAndToleratesSnake() {
        String camel = "{\"generatedQuestions\":[{\"id\":\"q1\",\"question\":\"camel 问题\","
                + "\"contentRevision\":0}],\"generatedQuestionsRevision\":0}";
        assertThat(AgentSuggestedQuestions.firstGeneratedQuestion(camel)).isEqualTo("camel 问题");

        // Go 期存量行可能是 snake：仍要能读出来
        String snake = "{\"generated_questions\":[{\"question\":\"snake 问题\"}]}";
        assertThat(AgentSuggestedQuestions.firstGeneratedQuestion(snake)).isEqualTo("snake 问题");

        // 纯字符串数组元素（兼容旧形态）
        String plain = "{\"generatedQuestions\":[\"纯字符串问题\"]}";
        assertThat(AgentSuggestedQuestions.firstGeneratedQuestion(plain)).isEqualTo("纯字符串问题");

        // 空数组 / 缺键 / null / 畸形 JSON → null（不得抛错）
        assertThat(AgentSuggestedQuestions.firstGeneratedQuestion("{\"generatedQuestions\":[]}")).isNull();
        assertThat(AgentSuggestedQuestions.firstGeneratedQuestion("{\"other\":1}")).isNull();
        assertThat(AgentSuggestedQuestions.firstGeneratedQuestion(null)).isNull();
        assertThat(AgentSuggestedQuestions.firstGeneratedQuestion("")).isNull();
        assertThat(AgentSuggestedQuestions.firstGeneratedQuestion("{不是 json")).isNull();
    }

    @Test
    @DisplayName("SQL 过滤：必须用 camel 键（%generatedQuestions%）——写错键名会让推荐问题恒空")
    void recommendedChunksQueryFiltersOnCamelKey() throws Exception {
        // 逐条扫：凡是按生成问题键过滤的查询，都必须用 camel 主键（并容忍 snake 存量行）。
        // 注意别只盯某一个方法——本映射里还有 FAQ 面的查询（按 chunk_type='faq' 过滤，
        // 与生成问题无关），钉错方法会得到假红/假绿。
        int checked = 0;
        for (Method m : AgentQuestionMapper.class.getDeclaredMethods()) {
            Select select = m.getAnnotation(Select.class);
            if (select == null) {
                continue;
            }
            String sql = String.join(" ", select.value());
            if (!sql.contains("generatedQuestions") && !sql.contains("generated_questions")
                    && !sql.contains("generatedQuestion")) {
                continue;
            }
            checked++;
            assertThat(sql)
                    .as("%s：键名以写入侧为准（camel generatedQuestions）", m.getName())
                    .contains("'%generatedQuestions%'");
            assertThat(sql)
                    .as("%s：容忍 Go 期存量 snake 行", m.getName())
                    .contains("'%generated_questions%'");
        }
        assertThat(checked)
                .as("应至少有一条按生成问题键过滤的查询（否则本守卫形同虚设）")
                .isGreaterThan(0);
    }
}
