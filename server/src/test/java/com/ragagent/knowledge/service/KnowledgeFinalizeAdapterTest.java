package com.ragagent.knowledge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.ragagent.common.knowledge.KnowledgeFinalizePort;
import com.ragagent.knowledge.domain.Knowledge;
import com.ragagent.knowledge.mapper.KnowledgeMapper;

/**
 * {@link KnowledgeFinalizeAdapter} 的行为测试（B98/C2：由 wiki 侧
 * {@code WikiKnowledgeFinalizerTest} 随实现搬来）。
 *
 * <p><b>为什么值得单测</b>：没有它，文档会永远停在 {@code finalizing}。它的正确性完全体现在
 * <b>两条 UPDATE 的 WHERE 子句</b>上：递减必须钳在零、晋升必须"无 SELECT、无条件尝试"——
 * 先 SELECT 再决定曾导致 pending_subtasks_count 卡死。</p>
 *
 * <p><b>为什么必须自预热 lambda 缓存</b>（2026-10-10 补，修的是本仓的存量红 ✗）：
 * {@code Knowledge::getId} 这类 lambda 由 MyBatis-Plus 的<b>静态</b>缓存解析（{@code LambdaUtils}），
 * 而该缓存的初始化发生在 MyBatis 启动装配里 ⇒ 这是**纯单测**（无 Spring 上下文）时无人预热，
 * {@code new LambdaUpdateWrapper<>()} 会抛 {@code can not find lambda cache for this entity}；
 * 又恰好被实现的 {@code catch (RuntimeException)} 吞掉 ⇒ 表现为"zero interactions"（6 例全红 ✓）。
 * 此前它偶发能绿，只因同 fork 里先跑过 Spring 测试 ⇒ **顺序依赖的假绿** ✗（{@code maxParallelForks=4}
 * 后更容易落在未预热的 fork）。故在 {@link BeforeAll} 里显式初始化 TableInfo，测试自给自足。</p>
 */
class KnowledgeFinalizeAdapterTest {

    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 10, 8, 0, 0, 0, 0, ZoneOffset.UTC);

    /**
     * MyBatis-Plus 的 lambda → 列名解析依赖<b>全局静态</b> TableInfo 缓存（生产里由 MyBatis 启动装配
     * 填充）。纯单测须自行初始化，否则 {@code new LambdaUpdateWrapper<>()} 抛
     * {@code can not find lambda cache for this entity}。
     */
    @BeforeAll
    static void initLambdaCache() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Knowledge.class);
    }

    private final KnowledgeMapper knowledgeMapper = mock(KnowledgeMapper.class);
    private final KnowledgeFinalizeAdapter adapter = new KnowledgeFinalizeAdapter(knowledgeMapper);

    /**
     * 两次写入的形状：先递减（{@code pending_subtasks_count > 0} 守卫），
     * 再无条件的带守卫晋升（{@code parse_status = finalizing AND count = 0}），
     * 每次都刷新 {@code updated_at}。
     */
    @Test
    @DisplayName("递减 + 带守卫晋升（对照 Go FinalizeSubtask）")
    void decrementThenPromote() {
        when(knowledgeMapper.update(isNull(), any())).thenReturn(1);

        KnowledgeFinalizePort.Result result = adapter.finalizeSubtask("kid-1", NOW);

        assertThat(result.decremented()).isTrue();
        assertThat(result.promoted()).isTrue();

        ArgumentCaptor<Wrapper<Knowledge>> captor = ArgumentCaptor.captor();
        verify(knowledgeMapper, times(2)).update(isNull(), captor.capture());
        String decrementSql = captor.getAllValues().get(0).getSqlSegment();
        assertThat(decrementSql)
                .as("递减语句必须钳在零：%s", decrementSql)
                .contains("pending_subtasks_count >");
        assertThat(captor.getAllValues().get(0).getSqlSet())
                .contains("pending_subtasks_count = pending_subtasks_count - 1");

        String promoteSql = captor.getAllValues().get(1).getSqlSegment();
        assertThat(promoteSql)
                .as("晋升必须同时守 parse_status 与计数归零：%s", promoteSql)
                .contains("parse_status")
                .contains("pending_subtasks_count =");
        assertThat(captor.getAllValues().get(1).getSqlSet())
                .contains("parse_status")
                .contains("error_message")
                .contains("processed_at");
    }

    /**
     * 行已不在 {@code finalizing}（或计数未归零）时晋升影响 0 行——
     * 这是<b>预期</b>，不是错误：只有真正把计数减到零的调用方能匹配上。
     */
    @Test
    @DisplayName("晋升未命中不是错误")
    void promoteMissIsNotAnError() {
        when(knowledgeMapper.update(isNull(), any()))
                .thenReturn(1)   // 递减命中
                .thenReturn(0);  // 晋升未命中

        KnowledgeFinalizePort.Result result = adapter.finalizeSubtask("kid-1", NOW);

        assertThat(result.decremented()).isTrue();
        assertThat(result.promoted()).isFalse();
    }

    /**
     * 计数已为 0（或行不存在）时递减影响 0 行；晋升仍<b>照常尝试</b>——绝不先 SELECT 再决定。
     */
    @Test
    @DisplayName("递减未命中仍无条件尝试晋升")
    void alwaysAttemptsPromote() {
        when(knowledgeMapper.update(isNull(), any()))
                .thenReturn(0)   // 递减未命中
                .thenReturn(0);

        KnowledgeFinalizePort.Result result = adapter.finalizeSubtask("kid-1", NOW);

        assertThat(result.decremented()).isFalse();
        assertThat(result.promoted()).isFalse();
        verify(knowledgeMapper, times(2)).update(isNull(), any());
    }

    /** 递减报错时提前返回，不再尝试晋升；失败只记日志（方法不抛） */
    @Test
    @DisplayName("递减报错时提前返回且不抛")
    void decrementFailureShortCircuits() {
        when(knowledgeMapper.update(isNull(), any()))
                .thenThrow(new IllegalStateException("db down"));

        KnowledgeFinalizePort.Result result = adapter.finalizeSubtask("kid-1", NOW);

        assertThat(result.decremented()).isFalse();
        assertThat(result.promoted()).isFalse();
        verify(knowledgeMapper, times(1)).update(isNull(), any());
    }

    /** 晋升报错时保留递减结果；同样不抛 */
    @Test
    @DisplayName("晋升报错时保留递减结果")
    void promoteFailureKeepsDecrement() {
        when(knowledgeMapper.update(isNull(), any()))
                .thenReturn(1)
                .thenThrow(new IllegalStateException("db down"));

        KnowledgeFinalizePort.Result result = adapter.finalizeSubtask("kid-1", NOW);

        assertThat(result.decremented()).isTrue();
        assertThat(result.promoted()).isFalse();
    }

    /** 空 id 是安全 no-op（直接短路，不碰仓储） */
    @Test
    @DisplayName("空 id 短路")
    void emptyIdShortCircuits() {
        assertThat(adapter.finalizeSubtask("", NOW).decremented()).isFalse();
        assertThat(adapter.finalizeSubtask(null, NOW).decremented()).isFalse();
        verify(knowledgeMapper, org.mockito.Mockito.never()).update(any(), any());
    }

    /** 晋升条件里的常量取自领域类型 */
    @Test
    @DisplayName("晋升条件使用领域常量")
    void promoteUsesDomainConstants() {
        when(knowledgeMapper.update(isNull(), any())).thenReturn(1);
        adapter.finalizeSubtask("kid-1", NOW);

        ArgumentCaptor<Wrapper<Knowledge>> captor = ArgumentCaptor.captor();
        verify(knowledgeMapper, times(2)).update(isNull(), captor.capture());
        assertThat(Knowledge.PARSE_FINALIZING).isEqualTo("finalizing");
        assertThat(Knowledge.PARSE_COMPLETED).isEqualTo("completed");
        // MyBatis-Plus 把值参数化了（#{ew.paramNameValuePairs.MPGENVALn}），
        // 因此断言参数表里有 completed，以及 WHERE 里的 finalizing。
        com.baomidou.mybatisplus.core.conditions.AbstractWrapper<?, ?, ?> promoted =
                (com.baomidou.mybatisplus.core.conditions.AbstractWrapper<?, ?, ?>)
                        captor.getAllValues().get(1);
        assertThat(promoted.getParamNameValuePairs().values())
                .as("晋升的 SET 里必须有 completed")
                .contains(Knowledge.PARSE_COMPLETED);
        assertThat(promoted.getSqlSegment())
                .contains("parse_status")
                .contains("pending_subtasks_count");
    }
}
