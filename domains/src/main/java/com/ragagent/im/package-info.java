/**
 * 即时通讯渠道域：九渠道适配（feishu/wechat/wecom/dingtalk/slack/mattermost/telegram/qqbot/yunzhijia，
 * 一渠道一子包）与运行时（会话映射、工具展示、入站消息派发）。{@code ImTurnOrchestrator}
 * 做渠道无关的编排。
 *
 * <p><b>种类</b>：集成适配<br>
 * <b>入口</b>：{@code ImChannelController}、{@code ImCallbackController}<br>
 * <b>对外契约面</b>：无（会话问答走 {@code SessionQaPort} 等契约）<br>
 * <b>依赖</b>：{@code contract}、{@code platform}。</p>
 */
package com.ragagent.im;
