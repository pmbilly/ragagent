package com.ragagent.config;

import org.springframework.context.annotation.Configuration;

import com.ragagent.im.runtime.ImTypes;
import com.ragagent.im.service.ImService;
import com.ragagent.im.slack.SlackAdapterFactory;
import com.ragagent.im.telegram.TelegramAdapterFactory;
import com.ragagent.common.security.SsrfGuard;
import com.ragagent.im.dingtalk.DingtalkAdapterFactory;
import com.ragagent.im.feishu.FeishuAdapterFactory;
import com.ragagent.im.feishu.FeishuRegion;
import com.ragagent.im.mattermost.MattermostAdapterFactory;
import com.ragagent.im.qqbot.QqBotAdapterFactory;
import com.ragagent.im.wechat.WechatAdapterFactory;
import com.ragagent.im.wecom.WecomAdapterFactory;
import com.ragagent.im.yunzhijia.YunzhijiaAdapterFactory;
import com.ragagent.stream.StreamManager;

/**
 * IM 适配器工厂的装配。
 *
 * <p>九个渠道的出站客户端逐个落地，每落地一支在这里注册一行。当前已注册：
 * Telegram（webhook + 长轮询）。未注册的平台 {@code ImService.startChannel} 会打
 * WARN（"no adapter factory for platform"）并保持渠道未启动，绝不静默假装成功。</p>
 */
@Configuration
public class ImAdapterWiringConfig {

    /**
     * 构造器里注册（工厂是 {@code @Component}，不由本类定义——否则
     * "配置类的构造器依赖自己 @Bean 方法定义的 bean"会触发 BeanCurrentlyInCreation）。
     */
    public ImAdapterWiringConfig(ImService imService,
                                 TelegramAdapterFactory telegramAdapterFactory,
                                 SlackAdapterFactory slackAdapterFactory,
                                 QqBotAdapterFactory qqBotAdapterFactory,
                                 WecomAdapterFactory wecomAdapterFactory,
                                 org.springframework.beans.factory.ObjectProvider<
                                         SsrfGuard> ssrfGuard,
                                 StreamManager streamManager) {
        // IM 的跨实例 /stop 要写 stop 事件到 StreamManager（延迟接：装配层注入）
        imService.setStreamManager(streamManager);
        imService.registerAdapterFactory(ImTypes.PLATFORM_TELEGRAM, telegramAdapterFactory);
        imService.registerAdapterFactory(ImTypes.PLATFORM_SLACK, slackAdapterFactory);
        imService.registerAdapterFactory(ImTypes.PLATFORM_QQBOT, qqBotAdapterFactory);
        imService.registerAdapterFactory(ImTypes.PLATFORM_WECOM, wecomAdapterFactory);
        // 飞书与 Lark 是同一产品两朵隔离云：同一实现、两个平台名
        SsrfGuard guard = ssrfGuard.getIfAvailable();
        imService.registerAdapterFactory(ImTypes.PLATFORM_FEISHU,
                new FeishuAdapterFactory(
                        FeishuRegion.FEISHU, guard));
        imService.registerAdapterFactory(ImTypes.PLATFORM_LARK,
                new FeishuAdapterFactory(
                        FeishuRegion.LARK, guard));
        imService.registerAdapterFactory(ImTypes.PLATFORM_DINGTALK,
                new DingtalkAdapterFactory(guard));
        imService.registerAdapterFactory(ImTypes.PLATFORM_WECHAT,
                new WechatAdapterFactory(guard));
        imService.registerAdapterFactory(ImTypes.PLATFORM_MATTERMOST,
                new MattermostAdapterFactory(guard));
        imService.registerAdapterFactory(ImTypes.PLATFORM_YUNZHIJIA,
                new YunzhijiaAdapterFactory(guard));
    }
}
