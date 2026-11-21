package com.ragagent.knowledge.wiki.api;

import com.ragagent.knowledge.api.wiki.WikiException;


public class WikiPageNotFoundException extends WikiException {

    public WikiPageNotFoundException() {
        super("wiki page not found");
    }
}
