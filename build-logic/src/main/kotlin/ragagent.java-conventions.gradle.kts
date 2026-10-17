import com.diffplug.gradle.spotless.SpotlessExtension
import io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension
import org.gradle.api.artifacts.VersionCatalogsExtension

plugins {
    java
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

// 格式口径：ratchet 从 seed 起只检查后续触碰的文件；生成代码不格式化。
pluginManager.apply("com.diffplug.spotless")
configure<SpotlessExtension> {
    java {
        ratchetFrom("seed")
        targetExclude("build/**")
        // import 口径：\# 前缀=static 块置顶，空串=其余单块 ASCII 序
        importOrder("\\#", "")
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
}

// 依赖版本由 Boot BOM 统一（此前每个模块各写一份）。
// 预编译脚本插件没有 `libs` 访问器 ⇒ 从 VersionCatalogsExtension 动态取坐标。
pluginManager.apply("io.spring.dependency-management")
val bootBom = extensions.getByType<VersionCatalogsExtension>()
    .named("libs")
    .findLibrary("spring-boot-dependencies")
    .get()
    .get()
configure<DependencyManagementExtension> {
    imports {
        mavenBom("${bootBom.module}:${bootBom.versionConstraint.requiredVersion}")
    }
}
