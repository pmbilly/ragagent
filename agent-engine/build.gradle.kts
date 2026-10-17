// agent 引擎模块：`com.ragagent.agent`（80 件主源码 + 33 件测试 + 3 件夹具）。
//
// 依赖方向：agent-engine → platform → contract（**对其它域出边 = 0**：实测只 import
// contract 139 / platform 51 / modelcontext 3 —— 后者是 agent-engine → model-runtime 的
// 合法终态边（工具注册表与流解码属模型侧的 agent 词汇））。
//
// 技术栈按实测 import 面声明：Jackson（databind/node/annotations）· jtokkit（token 计数）·
// MyBatis-Plus（技能目录的行映射注解）· snakeyaml（技能清单装载）· spring-context/jdbc。
//
// 过渡说明：domains 现有 55 个消费文件（agentmanagement/agenttools/session）仍直引本模块
// ⇒ domains 显式声明 `implementation(project(":agent-engine"))`（**过渡边**），
// 后续按 `contract.agent` 端口化逐点收窄。
plugins {
    `java-library`
    `java-test-fixtures`   // 共享测试基建（GoRecording*/契约夹具：随 agent 树自 domains 迁来）
    id("ragagent.java-conventions")
    id("ragagent.test-conventions")
}

dependencies {
    // 底座：agent 面签名大量使用契约词汇（AgentState/AgentTool/ToolResult…）与平台件 ⇒ api 传递
    api(project(":platform"))
    api(project(":contract"))

    // 模型侧 agent 词汇（工具注册表 / 流解码）——按方案：agent-engine → model-runtime 合法终态边
    implementation(project(":model-runtime"))

    // 依实测 import 面声明（jackson 28 处 / jtokkit 5 / mybatis-plus 3 / snakeyaml 3）
    api("com.fasterxml.jackson.core:jackson-databind")
    api("com.fasterxml.jackson.core:jackson-annotations")
    api("org.springframework:spring-context")
    api("org.springframework:spring-jdbc")
    api("org.slf4j:slf4j-api")
    implementation(libs.jtokkit)
    implementation(libs.mybatis.plus.starter)

    testFixturesApi(project(":contract"))
    testFixturesApi(project(":platform"))
    // 录制金片加载器在 platform testFixtures（JSON 外置 + SHA-256 校验）
    testFixturesApi(testFixtures(project(":platform")))

    testImplementation(testFixtures(project(":platform")))   // EmbeddedRedis 等平台测试基座
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
