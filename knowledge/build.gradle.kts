// 知识库模块：`com.ragagent.knowledge`（241 件主源码 + 22 件测试）。
//
// 依赖方向：knowledge → platform → contract。**对 domains 主码 0 出边**；
// 测试期有 4 处借用 domains 的存储/租户实现做夹具（单向，不构成循环）。
//
// 含文档解析 gRPC 客户端（`docreader` proto 由 ../docreader 生成）。

plugins {
    `java-library`
    id("com.google.protobuf")
    id("ragagent.java-conventions")
    id("ragagent.test-conventions")
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

dependencies {
    api(project(":platform"))
    api(project(":contract"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation(libs.mybatis.plus.starter)
    implementation(libs.protobuf.java)
    implementation(libs.grpc.client.starter)
    compileOnly(libs.javax.annotation.api)   // grpc 生成代码的 @Generated 注解
    compileOnly(libs.swagger.annotations.jakarta)   // OpenAPI @Tag（运行期由 :boot 提供）
    implementation("org.postgresql:postgresql")

    testImplementation(project(":domains"))
    testImplementation(project(":model-runtime"))   // 测试用 llm.extract 做真实抽取
    testImplementation(testFixtures(project(":platform")))
    testImplementation(testFixtures(project(":domains")))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:${libs.versions.protobuf.get()}"
    }
    plugins {
        create("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:${libs.versions.grpc.get()}"
        }
    }
    generateProtoTasks {
        all().forEach { task ->
            task.plugins { create("grpc") }
        }
    }
}

sourceSets["main"].proto {
    srcDir("../docreader")
}

// protobuf 插件与 testFixtures 的隐式任务冲突（同 domains）：本模块夹具不用 proto ⇒ 关掉。
tasks.matching { it.name.contains("TestProto") || it.name.contains("TestFixturesProto") }.configureEach {
    enabled = false
}
