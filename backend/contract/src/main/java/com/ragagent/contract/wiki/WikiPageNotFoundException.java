package com.ragagent.contract.wiki;


public class WikiPageNotFoundException extends WikiException {

    public WikiPageNotFoundException() {
        super("wiki page not found");
    }
}
