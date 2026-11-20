package com.ragagent.knowledge.wiki.domain.folder;

import com.ragagent.shared.wiki.WikiException;

/**
 * 同一父目录下已存在同名活跃文件夹。
 */
public class WikiFolderConflictException extends WikiException {

    public WikiFolderConflictException() {
        super("wiki folder name conflict");
    }
}
