/*
 * ttrss-reader-fork for Android
 * 
 * Copyright (C) 2010 Nils Braden
 * Copyright (C) 2009-2010 J. Devauchelle.
 * 
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * version 3 as published by the Free Software Foundation.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 */

package org.ttrssreader.model.pojos;

import java.util.Date;
import java.util.Set;

public class Article implements Comparable<Article> {
    
    public int id;
    public String title;
    public int feedId;
    public volatile boolean isUnread;
    public String url;
    public String commentUrl;
    public Date updated;
    public String content;
    public Set<String> attachments;
    public boolean isStarred;
    public boolean isPublished;
    public Set<Label> labels;
    public String author;
    
    public Article() {
        id = -1;
        title = null;
        isUnread = false;
        updated = null;
        feedId = 0;
        content = null;
        url = null;
        commentUrl = null;
        attachments = null;
        labels = null;
        isStarred = false;
        isPublished = false;
        author = null;
    }
    
    public Article(int id, int feedId, String title, boolean isUnread, String articleUrl, String articleCommentUrl,
            Date updateDate, String content, Set<String> attachments, boolean isStarred, boolean isPublished,
            Set<Label> labels, String author) {
        this.id = id;
        this.title = title;
        this.feedId = feedId;
        this.isUnread = isUnread;
        this.updated = updateDate;
        this.url = articleUrl.trim();
        this.commentUrl = articleCommentUrl;
        if (content == null || content.equals("null")) {
            this.content = null;
        } else {
            this.content = content;
        }
        this.attachments = attachments;
        this.isStarred = isStarred;
        this.isPublished = isPublished;
        this.labels = labels;
        this.author = author;
    }
    
    @Override
    public int compareTo(Article ai) {
        return ai.updated.compareTo(this.updated);
    }
    
    @Override
    public boolean equals(Object o) {
        if (o == null)
            return false;
        if (o instanceof Article) {
            Article ac = (Article) o;
            return id == ac.id;
        }
        return false;
    }
    
    @Override
    public int hashCode() {
        return id;
    }
    
    public enum ArticleField {
        id, title, unread, updated, feed_id, content, link, comments, attachments, marked, published, labels,
        is_updated, tags, feed_title, comments_count, comments_link, always_display_attachments, author, score, lang,
        note
    }
    
}