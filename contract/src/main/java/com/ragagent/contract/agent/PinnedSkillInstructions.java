package com.ragagent.contract.agent;

/** 本轮 @ 指定的技能正文：随系统提示词注入，点名场景确定性优先于按需读取。 */
public record PinnedSkillInstructions(String name, String instructions) {
}
