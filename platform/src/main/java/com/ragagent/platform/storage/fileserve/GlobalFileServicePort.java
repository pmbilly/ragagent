package com.ragagent.platform.storage.fileserve;

/**
 * 进程级文件服务面（L1）：取"本地基座 + 资源目录装饰"的写读删服务。
 *
 * <p><b>为什么是端口</b>：会话域的 agent 抓取页装配（{@code ArtifactCollectorWiring}）需要进程级字节落盘面，
 * 但它的构造属存储域实现（依赖 provider 解析与本地根配置）。本端口只暴露<b>结果服务</b>
 * （{@link WritableFileContentService} 已上提 L1），调用方不 import 存储域类型。</p>
 */
public interface GlobalFileServicePort {

    /**
     * 进程级服务（实现方可懒建：避免装配期碰文件系统）。
     * <p>语义与既有 {@code StorageFileResolver.globalFileService(baseDir)} 一致：
     * {@code resource://} 与 {@code local://} 归一 + 资源目录装饰。</p>
     */
    WritableFileContentService globalService();
}
