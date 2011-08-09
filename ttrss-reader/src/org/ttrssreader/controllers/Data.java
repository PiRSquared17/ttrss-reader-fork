/*
 * ttrss-reader-fork for Android
 * 
 * Copyright (C) 2010 N. Braden.
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

package org.ttrssreader.controllers;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.ttrssreader.R;
import org.ttrssreader.model.pojos.Category;
import org.ttrssreader.model.pojos.Feed;
import org.ttrssreader.utils.Utils;
import android.content.Context;
import android.net.ConnectivityManager;

public class Data {
    
    public static final int VCAT_UNCAT = 0;
    public static final int VCAT_STAR = -1;
    public static final int VCAT_PUB = -2;
    public static final int VCAT_FRESH = -3;
    public static final int VCAT_ALL = -4;
    
    private static Data instance = null;
    private Context context;
    
    private long countersUpdated = 0;
    private Map<Integer, Long> articlesUpdated = new HashMap<Integer, Long>();
    private Map<Integer, Long> feedsUpdated = new HashMap<Integer, Long>();
    private long virtCategoriesUpdated = 0;
    private long categoriesUpdated = 0;
    
    private ConnectivityManager cm;
    
    // Singleton
    private Data() {
    }
    
    public static Data getInstance() {
        if (instance == null) {
            synchronized (Data.class) {
                if (instance == null)
                    instance = new Data();
            }
        }
        return instance;
    }
    
    public synchronized void checkAndInitializeData(final Context context) {
        this.context = context;
        if (context != null)
            cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    }
    
    // *** COUNTERS *********************************************************************
    
    public void resetTime(int id, boolean isCat, boolean isFeed, boolean isArticle) {
        if (isCat) { // id doesn't matter
            virtCategoriesUpdated = 0;
            categoriesUpdated = 0;
            countersUpdated = 0;
        }
        if (isFeed)
            feedsUpdated.put(id, new Long(0)); // id == categoryId
        if (isArticle)
            articlesUpdated.put(id, new Long(0)); // id == feedId
    }
    
    public void updateCounters(boolean overrideOffline) {
        if (countersUpdated > System.currentTimeMillis() - Utils.HALF_UPDATE_TIME) { // Update counters more often..
            return;
        } else if (Utils.isConnected(cm) || overrideOffline) {
            try {
                Controller.getInstance().getConnector().getCounters();
                countersUpdated = System.currentTimeMillis();
            } catch (NotInitializedException e) {
            }
        }
    }
    
    // *** ARTICLES *********************************************************************
    
    public void updateArticles(int feedId, boolean displayOnlyUnread, boolean isCategory) {
        updateArticles(feedId, displayOnlyUnread, isCategory, false);
    }
    
    public void updateArticles(int feedId, boolean displayOnlyUnread, boolean isCategory, boolean overrideOffline) {
        
        // Check if unread-count and actual number of unread articles match, if not do a seperate call with
        // displayOnlyUnread=true
        boolean needUnreadUpdate = false;
        if (!isCategory && !displayOnlyUnread) {
            int unreadCount = DBHelper.getInstance().getUnreadCount(feedId, false);
            int actualUnread = DBHelper.getInstance().getUnreadArticles(feedId).size();
            if (unreadCount > actualUnread) {
                needUnreadUpdate = true;
                articlesUpdated.put(feedId, System.currentTimeMillis() - Utils.UPDATE_TIME - 1000);
            }
        }
        
        Long time = articlesUpdated.get(feedId);
        if (time == null)
            time = new Long(0);
        
        if (time > System.currentTimeMillis() - Utils.UPDATE_TIME) {
            return;
        } else if (Utils.isConnected(cm) || (overrideOffline && Utils.checkConnected(cm))) {
            int limit = 50;
            
            switch (feedId) {
                case VCAT_STAR: // Starred
                case VCAT_PUB: // Published
                    limit = 300;
                    displayOnlyUnread = false;
                    break;
                
                case VCAT_FRESH: // Fresh
                    limit = DBHelper.getInstance().getUnreadCount(feedId, true);
                    break;
                
                case VCAT_ALL: // All Articles
                    limit = DBHelper.getInstance().getUnreadCount(feedId, true);
                    break;
                
                default: // Normal categories
                    limit = DBHelper.getInstance().getUnreadCount(feedId, isCategory);
            }
            
            if (limit <= 0 && displayOnlyUnread)
                limit = 50; // No unread articles, fetch some stuff
            else if (limit <= 0)
                limit = 100; // No unread, fetch some to make sure we are at least a bit up-to-date
            else if (limit > 300)
                limit = 300; // Lots of unread articles, fetch the first 300
                
            if (limit < 300) {
                if (isCategory)
                    limit = limit + 50; // Add some so we have a chance of getting not only the newest and possibly read
                                        // articles but also older ones.
                else
                    limit = limit + 15; // Less on feed, more on category...
            }
            
            try {
                String viewMode = (displayOnlyUnread ? "unread" : "all_articles");
                
                // Set<Integer> ids = // <-- Not needed
                Controller.getInstance().getConnector().getHeadlinesToDatabase(feedId, limit, viewMode, isCategory);
                
                // If necessary and not displaying only unread articles: Refresh unread articles to get them too.
                if (needUnreadUpdate && !displayOnlyUnread)
                    Controller.getInstance().getConnector().getHeadlinesToDatabase(feedId, limit, "unread", isCategory);
                
            } catch (NotInitializedException e) {
                return;
            }
            
            // Store requested feed-/category-id and ids of all feeds in db for this category if a category was
            // requested
            articlesUpdated.put(feedId, System.currentTimeMillis());
            if (isCategory) {
                for (Feed f : DBHelper.getInstance().getFeeds(feedId)) {
                    articlesUpdated.put(f.id, System.currentTimeMillis());
                }
            }
        }
    }
    
    // *** FEEDS ************************************************************************
    
    public Set<Feed> updateFeeds(int categoryId, boolean overrideOffline) {
        
        Long time = feedsUpdated.get(categoryId);
        if (time == null)
            time = new Long(0);
        
        if (time > System.currentTimeMillis() - Utils.UPDATE_TIME) {
            return null;
        } else if (Utils.isConnected(cm) || (overrideOffline && Utils.checkConnected(cm))) {
            try {
                Set<Feed> feeds = Controller.getInstance().getConnector().getFeeds();
                
                // Only delete feeds if we got new feeds...
                if (!feeds.isEmpty())
                    DBHelper.getInstance().deleteFeeds();
                
                Set<Feed> ret = new LinkedHashSet<Feed>();
                for (Feed f : feeds) {
                    if (categoryId == VCAT_ALL || f.categoryId == categoryId)
                        ret.add(f);
                }
                DBHelper.getInstance().insertFeeds(feeds);
                
                // Store requested category-id and ids of all received feeds
                feedsUpdated.put(categoryId, System.currentTimeMillis());
                for (Feed f : feeds) {
                    feedsUpdated.put(f.categoryId, System.currentTimeMillis());
                }
                
                return ret;
            } catch (NotInitializedException e) {
            }
        }
        return null;
    }
    
    // *** CATEGORIES *******************************************************************
    
    public Set<Category> updateVirtualCategories() {
        if (virtCategoriesUpdated > System.currentTimeMillis() - Utils.UPDATE_TIME)
            return null;
        
        String vCatAllArticles = "";
        String vCatFreshArticles = "";
        String vCatPublishedArticles = "";
        String vCatStarredArticles = "";
        String uncatFeeds = "";
        
        if (context != null) {
            vCatAllArticles = (String) context.getText(R.string.VCategory_AllArticles);
            vCatFreshArticles = (String) context.getText(R.string.VCategory_FreshArticles);
            vCatPublishedArticles = (String) context.getText(R.string.VCategory_PublishedArticles);
            vCatStarredArticles = (String) context.getText(R.string.VCategory_StarredArticles);
            uncatFeeds = (String) context.getText(R.string.Feed_UncategorizedFeeds);
        }
        
        Set<Category> vCats = new LinkedHashSet<Category>();
        vCats.add(new Category(VCAT_ALL, vCatAllArticles, DBHelper.getInstance().getUnreadCount(VCAT_ALL, true)));
        vCats.add(new Category(VCAT_FRESH, vCatFreshArticles, DBHelper.getInstance().getUnreadCount(VCAT_FRESH, true)));
        vCats.add(new Category(VCAT_PUB, vCatPublishedArticles, DBHelper.getInstance().getUnreadCount(VCAT_PUB, true)));
        vCats.add(new Category(VCAT_STAR, vCatStarredArticles, DBHelper.getInstance().getUnreadCount(VCAT_STAR, true)));
        vCats.add(new Category(VCAT_UNCAT, uncatFeeds, DBHelper.getInstance().getUnreadCount(VCAT_UNCAT, true)));
        
        DBHelper.getInstance().insertCategories(vCats);
        virtCategoriesUpdated = System.currentTimeMillis();
        
        return vCats;
    }
    
    public Set<Category> updateCategories(boolean overrideOffline) {
        if (categoriesUpdated > System.currentTimeMillis() - Utils.UPDATE_TIME) {
            return null;
        } else if (Utils.isConnected(cm) || overrideOffline) {
            try {
                Set<Category> categories = Controller.getInstance().getConnector().getCategories();
                DBHelper.getInstance().deleteCategories(false);
                DBHelper.getInstance().insertCategories(categories);
                categoriesUpdated = System.currentTimeMillis();
                
                return categories;
            } catch (NotInitializedException e) {
            }
        }
        return null;
    }
    
    // *** STATUS *******************************************************************
    
    public void setArticleRead(Set<Integer> ids, int articleState) {
        boolean erg = false;
        if (Utils.isConnected(cm))
            try {
                erg = Controller.getInstance().getConnector().setArticleRead(ids, articleState);
            } catch (NotInitializedException e) {
                return;
            }
        
        if (!erg)
            DBHelper.getInstance().markUnsynchronizedStates(ids, DBHelper.MARK_READ, articleState);
    }
    
    public void setArticleStarred(int articleIds, int articleState) {
        boolean erg = false;
        Set<Integer> ids = new HashSet<Integer>();
        ids.add(articleIds);
        
        if (Utils.isConnected(cm))
            try {
                erg = Controller.getInstance().getConnector().setArticleStarred(ids, articleState);
            } catch (NotInitializedException e) {
                return;
            }
        
        if (!erg)
            DBHelper.getInstance().markUnsynchronizedStates(ids, DBHelper.MARK_STAR, articleState);
    }
    
    public void setArticlePublished(int articleIds, int articleState) {
        boolean erg = false;
        Set<Integer> ids = new HashSet<Integer>();
        ids.add(articleIds);
        
        if (Utils.isConnected(cm))
            try {
                erg = Controller.getInstance().getConnector().setArticlePublished(ids, articleState);
            } catch (NotInitializedException e) {
                return;
            }
        
        if (!erg)
            DBHelper.getInstance().markUnsynchronizedStates(ids, DBHelper.MARK_PUBLISH, articleState);
    }
    
    public void setRead(int id, boolean isCategory) {
        boolean erg = false;
        if (Utils.isConnected(cm)) {
            try {
                erg = Controller.getInstance().getConnector().setRead(id, isCategory);
            } catch (NotInitializedException e) {
                return;
            }
        }
        
        if (isCategory || id < 0) {
            
            if (!erg)
                DBHelper.getInstance().markUnsynchronizedStatesCategory(id);
            DBHelper.getInstance().markCategoryRead(id);
            
        } else {
            
            if (!erg)
                DBHelper.getInstance().markUnsynchronizedStatesFeed(id);
            DBHelper.getInstance().markFeedRead(id);
            
        }
    }
    
    public String getPref(String pref) {
        if (Utils.isConnected(cm))
            try {
                return Controller.getInstance().getConnector().getPref(pref);
            } catch (NotInitializedException e) {
                return null;
            }
        return null;
    }
    
    public int getVersion() {
        if (Utils.isConnected(cm))
            try {
                return Controller.getInstance().getConnector().getVersion();
            } catch (NotInitializedException e) {
                return -1;
            }
        return -1;
    }
    
    public void synchronizeStatus() {
        if (!Utils.isConnected(cm))
            return;
        
        String[] marks = new String[] { DBHelper.MARK_READ, DBHelper.MARK_STAR, DBHelper.MARK_PUBLISH };
        for (String mark : marks) {
            Set<Integer> idsMark = DBHelper.getInstance().getMarked(mark, 1);
            Set<Integer> idsUnmark = DBHelper.getInstance().getMarked(mark, 0);
            
            try {
                if (DBHelper.MARK_READ.equals(mark)) {
                    if (Controller.getInstance().getConnector().setArticleRead(idsMark, 1))
                        DBHelper.getInstance().setMarked(idsMark, mark);
                    
                    if (Controller.getInstance().getConnector().setArticleRead(idsUnmark, 0))
                        DBHelper.getInstance().setMarked(idsUnmark, mark);
                }
                if (DBHelper.MARK_STAR.equals(mark)) {
                    if (Controller.getInstance().getConnector().setArticleStarred(idsMark, 1))
                        DBHelper.getInstance().setMarked(idsMark, mark);
                    
                    if (Controller.getInstance().getConnector().setArticleStarred(idsUnmark, 0))
                        DBHelper.getInstance().setMarked(idsUnmark, mark);
                }
                if (DBHelper.MARK_PUBLISH.equals(mark)) {
                    if (Controller.getInstance().getConnector().setArticlePublished(idsMark, 1))
                        DBHelper.getInstance().setMarked(idsMark, mark);
                    
                    if (Controller.getInstance().getConnector().setArticlePublished(idsUnmark, 0))
                        DBHelper.getInstance().setMarked(idsUnmark, mark);
                }
            } catch (NotInitializedException e) {
                return;
            }
        }
    }
}
