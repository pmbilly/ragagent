package com.ragagent.common.wiki;

/** 链接修复结果（content 为修复后正文，changed 表示是否发生了改写）。 */
public record WikiLinkRepairResult(String content, boolean changed) {
}
