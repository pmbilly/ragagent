package com.ragagent.knowledge.wiki.domain.folder;

import com.ragagent.shared.wiki.WikiException;

public class WikiFolderNotFoundException extends WikiException {

    public WikiFolderNotFoundException() {
        super("wiki folder not found");
    }
}
