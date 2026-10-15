/**
 * 联网搜索跨域**支撑端口**（最底层，零域依赖）：会话侧解析默认 provider、
 * 删除会话时伴生清理临时搜索状态这两个最小读/清面。消费方注入接口、
 * websearch 域实现，依赖方向是"消费域 → 端口 ← websearch 域"。
 */
package com.ragagent.common.websearch;
