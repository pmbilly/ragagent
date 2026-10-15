package com.ragagent.wiki.domain.folder;

import com.ragagent.contract.wiki.WikiException;

public class WikiFolderNotFoundException extends WikiException {

    public WikiFolderNotFoundException() {
        super("wiki folder not found");
    }
}
