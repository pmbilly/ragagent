package com.ragagent.wiki.domain.folder;

import com.ragagent.wiki.domain.WikiException;

public class WikiFolderNotFoundException extends WikiException {

    public WikiFolderNotFoundException() {
        super("wiki folder not found");
    }
}
