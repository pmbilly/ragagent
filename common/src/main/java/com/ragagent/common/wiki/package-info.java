/**
 * wiki 跨域**端口**（最底层，零域依赖）：wiki 页读写的工具面入口。
 * 消费方注入接口、wiki 域实现，依赖方向是"消费域 → 端口 ← wiki 域"；
 * 页/issue/索引实体已上提 {@code common.wiki.domain}，端口直接搬运它们。
 */
package com.ragagent.common.wiki;
