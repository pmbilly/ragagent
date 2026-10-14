package com.ragagent.wiki.domain.page;

import com.ragagent.wiki.domain.WikiException;

public class WikiPageNotFoundException extends WikiException {

    public WikiPageNotFoundException() {
        super("wiki page not found");
    }
}
