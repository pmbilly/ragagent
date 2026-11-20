package com.ragagent.wiki.api;

import com.ragagent.shared.wiki.WikiException;


public class WikiPageNotFoundException extends WikiException {

    public WikiPageNotFoundException() {
        super("wiki page not found");
    }
}
