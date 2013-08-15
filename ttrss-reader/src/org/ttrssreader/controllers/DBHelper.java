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

import java.io.File;
import java.io.FilenameFilter;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import org.ttrssreader.model.FeedHeadlineAdapter;
import org.ttrssreader.model.pojos.Article;
import org.ttrssreader.model.pojos.Category;
import org.ttrssreader.model.pojos.Feed;
import org.ttrssreader.model.pojos.Label;
import org.ttrssreader.utils.FileDateComparator;
import org.ttrssreader.utils.StringSupport;
import org.ttrssreader.utils.Utils;
import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;
import android.text.Html;
import android.util.Log;

public class DBHelper {
    
    private static DBHelper instance = null;
    private volatile boolean initialized = false;
    private volatile boolean vacuumDone = false;
    
    /*
     * We use two locks here to avoid too much locking for reading and be able to completely lock everything up when we
     * need to close the DB. In this case we lock both locks with write-access to avoid any other Threads holding the
     * locks while we use only read-locking on the "dbReadLock" for normal accesss. The "dbWriteLock" is always locked
     * on one Thread since concurrent write-access is not possible.
     */
    // private static final ReentrantReadWriteLock dbReadLock = new ReentrantReadWriteLock();
    // private static final ReentrantLock dbWriteLock = new ReentrantLock();
    
    public static final String DATABASE_NAME = "ttrss.db";
    public static final String DATABASE_BACKUP_NAME = "_backup_";
    public static final int DATABASE_VERSION = 52;
    
    public static final String TABLE_CATEGORIES = "categories";
    public static final String TABLE_FEEDS = "feeds";
    public static final String TABLE_ARTICLES = "articles";
    public static final String TABLE_ARTICLES2LABELS = "articles2labels";
    public static final String TABLE_LABELS = "labels";
    public static final String TABLE_MARK = "marked";
    
    public static final String MARK_READ = "isUnread";
    public static final String MARK_STAR = "isStarred";
    public static final String MARK_PUBLISH = "isPublished";
    public static final String MARK_NOTE = "note";
    
    // @formatter:off
    private static final String INSERT_CATEGORY = 
        "REPLACE INTO "
        + TABLE_CATEGORIES
        + " (id, title, unread)"
        + " VALUES (?, ?, ?)";
    
    private static final String INSERT_FEED = 
        "REPLACE INTO "
        + TABLE_FEEDS
        + " (id, categoryId, title, url, unread)"
        + " VALUES (?, ?, ?, ?, ?)";
    
    private static final String INSERT_ARTICLE = 
        "INSERT OR REPLACE INTO "
        + TABLE_ARTICLES
        + " (id, feedId, title, isUnread, articleUrl, articleCommentUrl, updateDate, content, attachments, isStarred, isPublished, cachedImages, articleLabels)" 
        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, coalesce((SELECT cachedImages FROM " + TABLE_ARTICLES + " WHERE id=?), 0), ?)";
    // This should insert new values or replace existing values but should always keep an already inserted value for "cachedImages".
    // When inserting it is set to the default value which is 0 (not "NULL").
    
    private static final String INSERT_LABEL = 
        "REPLACE INTO "
        + TABLE_ARTICLES2LABELS
        + " (articleId, labelId)"
        + " VALUES (?, ?)";
    // @formatter:on
    
    private Context context;
    public SQLiteDatabase db;
    
    private SQLiteStatement insertCategory;
    private SQLiteStatement insertFeed;
    private SQLiteStatement insertArticle;
    private SQLiteStatement insertLabel;
    
    // Singleton
    private DBHelper() {
    }
    
    public static DBHelper getInstance() {
        if (instance == null) {
            synchronized (DBHelper.class) {
                if (instance == null) {
                    instance = new DBHelper();
                }
            }
        }
        return instance;
    }
    
    public synchronized void checkAndInitializeDB(final Context context) {
        this.context = context;
        
        // Check if deleteDB is scheduled or if DeleteOnStartup is set
        if (Controller.getInstance().isDeleteDBScheduled()) {
            if (deleteDB()) {
                Controller.getInstance().setDeleteDBScheduled(false);
                initializeDBHelper();
                return; // Don't need to check if DB is corrupted, it is NEW!
            }
        }
        
        // Initialize DB
        if (!initialized) {
            initializeDBHelper();
        } else if (db == null || !db.isOpen()) {
            initializeDBHelper();
        } else {
            return; // DB was already initialized, no need to check anything.
        }
        
        // Test if DB is accessible, backup and delete if not
        if (initialized) {
            Cursor c = null;
            try {
                // Try to access the DB
                c = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_CATEGORIES, null);
                c.getCount();
                if (c.moveToFirst())
                    c.getInt(0);
                
            } catch (Exception e) {
                Log.e(Utils.TAG, "Database was corrupted, creating a new one...");
                
                closeDB();
                backupAndRemoveDB();
                
                // Initialize again...
                initializeDBHelper();
            } finally {
                if (c != null && !c.isClosed())
                    c.close();
            }
        }
    }
    
    private synchronized void backupAndRemoveDB() {
        // Move DB-File to backup
        File f = context.getDatabasePath(DATABASE_NAME);
        f.renameTo(new File(f.getAbsolutePath() + DATABASE_BACKUP_NAME + System.currentTimeMillis()));
        
        // Find setReadble method in old api
        try {
            Class<?> cls = SharedPreferences.Editor.class;
            Method m = cls.getMethod("setReadble");
            m.invoke(f, true, false);
        } catch (Exception e1) {
        }
        
        // Check if there are too many old backups
        FilenameFilter fnf = new FilenameFilter() {
            @Override
            public boolean accept(File dir, String filename) {
                if (filename.contains(DATABASE_BACKUP_NAME))
                    return true;
                return false;
            }
        };
        File[] backups = f.getParentFile().listFiles(fnf);
        if (backups != null && backups.length > 3) {
            // Sort list of files by last access date
            List<File> list = Arrays.asList(backups);
            Collections.sort(list, new FileDateComparator());
            
            for (int i = list.size(); i > 2; i++) {
                // Delete all except the 2 newest backups
                list.get(i).delete();
            }
        }
    }
    
    @SuppressWarnings("deprecation")
    private synchronized boolean initializeDBHelper() {
        
        if (context == null) {
            Log.e(Utils.TAG, "Can't handle internal DB without Context-Object.");
            return false;
        }
        
        if (db != null)
            closeDB();
        
        OpenHelper openHelper = new OpenHelper(context);
        db = openHelper.getWritableDatabase();
        db.setLockingEnabled(true);
        
        insertCategory = db.compileStatement(INSERT_CATEGORY);
        insertFeed = db.compileStatement(INSERT_FEED);
        insertArticle = db.compileStatement(INSERT_ARTICLE);
        insertLabel = db.compileStatement(INSERT_LABEL);
        
        db.acquireReference();
        initialized = true;
        return true;
    }
    
    private boolean deleteDB() {
        if (context == null)
            return false;
        
        Log.i(Utils.TAG, "Deleting Database as requested by preferences.");
        File f = context.getDatabasePath(DATABASE_NAME);
        if (f.exists()) {
            if (db != null) {
                closeDB();
            }
            return f.delete();
        }
        
        return false;
    }
    
    private void closeDB() {
        db.releaseReference();
        db.close();
        db = null;
    }
    
    private boolean isDBAvailable() {
        if (db != null && db.isOpen()) {
            return true;
        } else if (db != null) {
            
            synchronized (this) {
                if (db != null) {
                    OpenHelper openHelper = new OpenHelper(context);
                    db = openHelper.getWritableDatabase();
                    initialized = db.isOpen();
                    return initialized;
                }
            }
            
        } else {
            Log.i(Utils.TAG, "Controller not initialized, trying to do that now...");
            initializeDBHelper();
            return true;
        }
        
        return false;
    }
    
    private static class OpenHelper extends SQLiteOpenHelper {
        
        OpenHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }
        
        /**
         * @see android.database.sqlite.SQLiteOpenHelper#onCreate(android.database.sqlite.SQLiteDatabase)
         */
        @Override
        public void onCreate(SQLiteDatabase db) {
            // @formatter:off
            db.execSQL(
                    "CREATE TABLE "
                    + TABLE_CATEGORIES 
                    + " (id INTEGER PRIMARY KEY," 
                    + " title TEXT," 
                    + " unread INTEGER)");
            
            db.execSQL(
                    "CREATE TABLE "
                    + TABLE_FEEDS
                    + " (id INTEGER PRIMARY KEY," 
                    + " categoryId INTEGER," 
                    + " title TEXT," 
                    + " url TEXT," 
                    + " unread INTEGER)");
            
            db.execSQL(
                    "CREATE TABLE "
                    + TABLE_ARTICLES
                    + " (id INTEGER PRIMARY KEY," 
                    + " feedId INTEGER," 
                    + " title TEXT," 
                    + " isUnread INTEGER," 
                    + " articleUrl TEXT," 
                    + " articleCommentUrl TEXT,"
                    + " updateDate INTEGER,"
                    + " content TEXT,"
                    + " attachments TEXT,"
                    + " isStarred INTEGER,"
                    + " isPublished INTEGER,"
                    + " cachedImages INTEGER DEFAULT 0,"
                    + " articleLabels TEXT)");
            
            db.execSQL(
                    "CREATE TABLE " 
                    + TABLE_ARTICLES2LABELS
                    + " (articleId INTEGER," 
                    + " labelId INTEGER, PRIMARY KEY(articleId, labelId))");
            
            db.execSQL("CREATE TABLE "
                    + TABLE_MARK
                    + " (id INTEGER,"
                    + " type INTEGER,"
                    + " " + MARK_READ + " INTEGER,"
                    + " " + MARK_STAR + " INTEGER,"
                    + " " + MARK_PUBLISH + " INTEGER,"
                    + " " + MARK_NOTE + " TEXT,"
                    + " PRIMARY KEY(id, type))");
            // @formatter:on
        }
        
        /**
         * @see android.database.sqlite.SQLiteOpenHelper#onUpgrade(android.database.sqlite.SQLiteDatabase, int, int)
         */
        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            boolean didUpgrade = false;
            
            if (oldVersion < 40) {
                String sql = "ALTER TABLE " + TABLE_ARTICLES + " ADD COLUMN isStarred INTEGER";
                
                Log.i(Utils.TAG, String.format("Upgrading database from %s to 40.", oldVersion));
                Log.i(Utils.TAG, String.format(" (Executing: %s", sql));
                
                db.execSQL(sql);
                didUpgrade = true;
            }
            
            if (oldVersion < 42) {
                String sql = "ALTER TABLE " + TABLE_ARTICLES + " ADD COLUMN isPublished INTEGER";
                
                Log.i(Utils.TAG, String.format("Upgrading database from %s to 42.", oldVersion));
                Log.i(Utils.TAG, String.format(" (Executing: %s", sql));
                
                db.execSQL(sql);
                didUpgrade = true;
            }
            
            if (oldVersion < 45) {
                // @formatter:off
                String sql = "CREATE TABLE IF NOT EXISTS "
                    + TABLE_MARK
                    + " (id INTEGER,"
                    + " type INTEGER,"
                    + " " + MARK_READ + " INTEGER,"
                    + " " + MARK_STAR + " INTEGER,"
                    + " " + MARK_PUBLISH + " INTEGER,"
                    + " PRIMARY KEY(id, type))";
                // @formatter:on
                
                Log.i(Utils.TAG, String.format("Upgrading database from %s to 45.", oldVersion));
                Log.i(Utils.TAG, String.format(" (Executing: %s", sql));
                
                db.execSQL(sql);
                didUpgrade = true;
            }
            
            if (oldVersion < 46) {
                
                // @formatter:off
                String sql = "DROP TABLE IF EXISTS "
                    + TABLE_MARK;
                String sql2 = "CREATE TABLE IF NOT EXISTS "
                    + TABLE_MARK
                    + " (id INTEGER PRIMARY KEY,"
                    + " " + MARK_READ + " INTEGER,"
                    + " " + MARK_STAR + " INTEGER,"
                    + " " + MARK_PUBLISH + " INTEGER)";
                // @formatter:on
                
                Log.i(Utils.TAG, String.format("Upgrading database from %s to 46.", oldVersion));
                Log.i(Utils.TAG, String.format(" (Executing: %s", sql));
                Log.i(Utils.TAG, String.format(" (Executing: %s", sql2));
                
                db.execSQL(sql);
                db.execSQL(sql2);
                didUpgrade = true;
            }
            
            if (oldVersion < 47) {
                String sql = "ALTER TABLE " + TABLE_ARTICLES + " ADD COLUMN cachedImages INTEGER DEFAULT 0";
                
                Log.i(Utils.TAG, String.format("Upgrading database from %s to 47.", oldVersion));
                Log.i(Utils.TAG, String.format(" (Executing: %s", sql));
                
                db.execSQL(sql);
                didUpgrade = true;
            }
            
            if (oldVersion < 48) {
                // @formatter:off
                String sql = "CREATE TABLE IF NOT EXISTS "
                        + TABLE_MARK
                        + " (id INTEGER,"
                        + " type INTEGER,"
                        + " " + MARK_READ + " INTEGER,"
                        + " " + MARK_STAR + " INTEGER,"
                        + " " + MARK_PUBLISH + " INTEGER,"
                        + " PRIMARY KEY(id, type))";
                // @formatter:on
                
                Log.i(Utils.TAG, String.format("Upgrading database from %s to 48.", oldVersion));
                Log.i(Utils.TAG, String.format(" (Executing: %s", sql));
                
                db.execSQL(sql);
                didUpgrade = true;
            }
            
            if (oldVersion < 49) {
                // @formatter:off
                String sql = "CREATE TABLE " 
                        + TABLE_ARTICLES2LABELS
                        + " (articleId INTEGER," 
                        + " labelId INTEGER, PRIMARY KEY(articleId, labelId))";
                // @formatter:on
                
                Log.i(Utils.TAG, String.format("Upgrading database from %s to 49.", oldVersion));
                Log.i(Utils.TAG, String.format(" (Executing: %s", sql));
                
                db.execSQL(sql);
                didUpgrade = true;
            }
            
            if (oldVersion < 50) {
                Log.i(Utils.TAG, String.format("Upgrading database from %s to 50.", oldVersion));
                ContentValues cv = new ContentValues();
                cv.put("cachedImages", 0);
                db.update(TABLE_ARTICLES, cv, "cachedImages IS null", null);
                didUpgrade = true;
            }
            
            if (oldVersion < 51) {
                // @formatter:off
                String sql = "DROP TABLE IF EXISTS "
                    + TABLE_MARK;
                String sql2 = "CREATE TABLE "
                    + TABLE_MARK
                    + " (id INTEGER,"
                    + " type INTEGER,"
                    + " " + MARK_READ + " INTEGER,"
                    + " " + MARK_STAR + " INTEGER,"
                    + " " + MARK_PUBLISH + " INTEGER,"
                    + " " + MARK_NOTE + " TEXT,"
                    + " PRIMARY KEY(id, type))";
                // @formatter:on
                
                Log.i(Utils.TAG, String.format("Upgrading database from %s to 51.", oldVersion));
                Log.i(Utils.TAG, String.format(" (Executing: %s", sql));
                Log.i(Utils.TAG, String.format(" (Executing: %s", sql2));
                
                db.execSQL(sql);
                db.execSQL(sql2);
                didUpgrade = true;
            }
            
            if (oldVersion < 52) {
                // @formatter:off
                String sql = "ALTER TABLE " + TABLE_ARTICLES + " ADD COLUMN articleLabels TEXT";
                // @formatter:on
                
                Log.i(Utils.TAG, String.format("Upgrading database from %s to 52.", oldVersion));
                Log.i(Utils.TAG, String.format(" (Executing: %s", sql));
                
                db.execSQL(sql);
                didUpgrade = true;
            }
            
            if (didUpgrade == false) {
                Log.i(Utils.TAG, "Upgrading database, this will drop tables and recreate.");
                db.execSQL("DROP TABLE IF EXISTS " + TABLE_CATEGORIES);
                db.execSQL("DROP TABLE IF EXISTS " + TABLE_FEEDS);
                db.execSQL("DROP TABLE IF EXISTS " + TABLE_ARTICLES);
                db.execSQL("DROP TABLE IF EXISTS " + TABLE_MARK);
                onCreate(db);
            }
            
        }
    }
    
    /**
     * Used by SubscribeActivity to directly access the DB and get a cursor for the List of Categories.<br>
     * PLEASE NOTE: Don't forget to close the received cursor!
     * 
     * @see android.database.sqlite.SQLiteDatabase#rawQuery(String, String[])
     */
    @Deprecated
    public Cursor query(String sql, String[] selectionArgs) {
        if (!isDBAvailable())
            return null;
        
        Cursor cursor = db.rawQuery(sql, selectionArgs);
        return cursor;
    }
    
    // *******| INSERT |*******************************************************************
    
    private void insertCategory(int id, String title, int unread) {
        if (title == null)
            title = "";
        
        synchronized (insertCategory) {
            insertCategory.bindLong(1, id);
            insertCategory.bindString(2, title);
            insertCategory.bindLong(3, unread);
            insertCategory.execute();
        }
    }
    
    public void insertCategories(Set<Category> set) {
        if (!isDBAvailable() || set == null)
            return;
        
        db.beginTransaction();
        try {
            for (Category c : set) {
                insertCategory(c.id, c.title, c.unread);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }
    
    private void insertFeed(int id, int categoryId, String title, String url, int unread) {
        if (title == null)
            title = "";
        if (url == null)
            url = "";
        
        synchronized (insertFeed) {
            insertFeed.bindLong(1, Integer.valueOf(id).longValue());
            insertFeed.bindLong(2, Integer.valueOf(categoryId).longValue());
            insertFeed.bindString(3, title);
            insertFeed.bindString(4, url);
            insertFeed.bindLong(5, unread);
            insertFeed.execute();
        }
    }
    
    public void insertFeeds(Set<Feed> set) {
        if (!isDBAvailable() || set == null)
            return;
        
        db.beginTransaction();
        try {
            for (Feed f : set) {
                insertFeed(f.id, f.categoryId, f.title, f.url, f.unread);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }
    
    private void insertArticleIntern(Article a) {
        if (a.title == null)
            a.title = "";
        if (a.content == null)
            a.content = "";
        if (a.url == null)
            a.url = "";
        if (a.commentUrl == null)
            a.commentUrl = "";
        if (a.updated == null)
            a.updated = new Date();
        if (a.attachments == null)
            a.attachments = new LinkedHashSet<String>();
        if (a.labels == null)
            a.labels = new LinkedHashSet<Label>();
        
        // articleLabels
        long retId = -1;
        synchronized (insertArticle) {
            insertArticle.bindLong(1, a.id);
            insertArticle.bindLong(2, a.feedId);
            insertArticle.bindString(3, Html.fromHtml(a.title).toString());
            insertArticle.bindLong(4, (a.isUnread ? 1 : 0));
            insertArticle.bindString(5, a.url);
            insertArticle.bindString(6, a.commentUrl);
            insertArticle.bindLong(7, a.updated.getTime());
            insertArticle.bindString(8, a.content);
            insertArticle.bindString(9, Utils.separateItems(a.attachments, ";"));
            insertArticle.bindLong(10, (a.isStarred ? 1 : 0));
            insertArticle.bindLong(11, (a.isPublished ? 1 : 0));
            insertArticle.bindLong(12, a.id); // ID again for the where-clause
            insertArticle.bindString(13, Utils.separateItems(a.labels, "---"));
            retId = insertArticle.executeInsert();
        }
        
        if (retId != -1)
            insertLabel(a.id, a.labelId);
    }
    
    public void insertArticle(Article a) {
        if (!isDBAvailable())
            return;
        
        insertArticleIntern(a);
    }
    
    public void insertArticles(Collection<Article> articles) {
        if (!isDBAvailable() || articles == null || articles.isEmpty())
            return;
        
        db.beginTransaction();
        try {
            for (Article a : articles) {
                insertArticleIntern(a);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }
    
    public static Object[] prepareArticleArray(int id, int feedId, String title, boolean isUnread, String articleUrl, String articleCommentUrl, Date updateDate, String content, Set<String> attachments, boolean isStarred, boolean isPublished, int label, Set<Label> labels) {
        Object[] ret = new Object[12];
        
        ret[0] = id;
        ret[1] = feedId;
        ret[2] = (title == null ? "" : title);
        ret[3] = (isUnread ? 1 : 0);
        ret[4] = (articleUrl == null ? "" : articleUrl);
        ret[5] = (articleCommentUrl == null ? "" : articleCommentUrl);
        ret[6] = updateDate.getTime();
        ret[7] = (content == null ? "" : content);
        ret[8] = Utils.separateItems(attachments, ";");
        ret[9] = (isStarred ? 1 : 0);
        ret[10] = (isPublished ? 1 : 0);
        ret[11] = labels;
        
        return ret;
    }
    
    /**
     * New method of inserting many articles into the DB at once. Doesn't seem to run faster then the old way so i'll
     * just leave this code here for future reference and ignore it until it proves to be useful.
     * 
     * @param input
     *            Object-Array with the fields of an article-object.
     */
    public void bulkInsertArticles(List<Object[]> input) {
        if (!isDBAvailable() || input == null || input.isEmpty())
            return;
        
        StringBuilder stmt = new StringBuilder();
        stmt.append("INSERT OR REPLACE INTO " + TABLE_ARTICLES);
        
        Object[] entry = input.get(0);
        stmt.append(" SELECT ");
        
        stmt.append(entry[0] + " AS id, ");
        stmt.append(entry[1] + " AS feedId, ");
        stmt.append(DatabaseUtils.sqlEscapeString(entry[2] + "") + " AS title, ");
        stmt.append(entry[3] + " AS isUnread, ");
        stmt.append("'" + entry[4] + "' AS articleUrl, ");
        stmt.append("'" + entry[5] + "' AS articleCommentUrl, ");
        stmt.append(entry[6] + " AS updateDate, ");
        stmt.append(DatabaseUtils.sqlEscapeString(entry[7] + "") + " AS content, ");
        stmt.append("'" + entry[8] + "' AS attachments, ");
        stmt.append(entry[9] + " AS isStarred, ");
        stmt.append(entry[10] + " AS isPublished, ");
        stmt.append("coalesce((SELECT cachedImages FROM articles WHERE id=" + entry[0] + "), 0) AS cachedImages UNION");
        
        for (int i = 1; i < input.size(); i++) {
            entry = input.get(i);
            
            stmt.append(" SELECT ");
            for (int j = 0; j < entry.length; j++) {
                
                if (j == 2 || j == 7) {
                    // Escape and enquote Content and Title, they can contain quotes
                    stmt.append(DatabaseUtils.sqlEscapeString(entry[j] + ""));
                } else if (j == 4 || j == 5 || j == 8) {
                    // Just enquote Text-Fields
                    stmt.append("'" + entry[j] + "'");
                } else {
                    // Leave numbers..
                    stmt.append(entry[j]);
                }
                
                if (j < (entry.length - 1))
                    stmt.append(", ");
                if (j == (entry.length - 1))
                    stmt.append(", coalesce((SELECT cachedImages FROM articles WHERE id=" + entry[0] + "), 0)");
            }
            if (i < input.size() - 1)
                stmt.append(" UNION ");
            
        }
        
        db.execSQL(stmt.toString());
    }
    
    public void insertLabel(int articleId, int labelId) {
        if (!isDBAvailable())
            return;
        
        if (labelId < -10) {
            synchronized (insertLabel) {
                insertLabel.bindLong(1, articleId);
                insertLabel.bindLong(2, labelId);
                insertLabel.executeInsert();
            }
        }
    }
    
    public void removeLabel(int articleId, int labelId) {
        if (!isDBAvailable())
            return;
        
        if (labelId < -10) {
            String[] args = new String[] { articleId + "", labelId + "" };
            db.delete(TABLE_ARTICLES2LABELS, "articleId=? AND labelId=?", args);
        }
    }
    
    public void insertLabels(Set<Integer> articleIds, int label, boolean assign) {
        if (!isDBAvailable())
            return;
        
        for (Integer articleId : articleIds) {
            if (assign)
                insertLabel(articleId, label);
            else
                removeLabel(articleId, label);
        }
    }
    
    // *******| UPDATE |*******************************************************************
    
    public Collection<Integer> markAllRead() {
        return markRead(-1, false, true);
    }
    
    public Collection<Integer> markRead(int id, boolean isCategory) {
        return markRead(id, isCategory, false);
    }
    
    /**
     * set read status in DB for given category/feed
     * 
     * @param id
     *            category/feed ID
     * @param isCategory
     *            if set to {@code true}, then given id is category
     *            ID, otherwise - feed ID
     * 
     * @return collection of article IDs, which was marked as read or {@code null} if nothing was changed
     */
    public Collection<Integer> markRead(int id, boolean isCategory, boolean markAllRead) {
        Collection<Integer> markedIds = null;
        if (!isDBAvailable())
            return markedIds;
        
        db.beginTransaction();
        try {
            String where = "";
            
            if (!markAllRead) {
                String feedIds;
                if (isCategory || id < 0)
                    feedIds = "select id from " + TABLE_FEEDS + " where categoryId=" + id;
                else
                    feedIds = String.valueOf(id);
                
                where = "feedId in (" + feedIds + ") and isUnread>0";
            }
            
            Cursor c = null;
            try {
                // select id from articles where categoryId in (...)
                c = db.query(TABLE_ARTICLES, new String[] { "id" }, where, null, null, null, null);
                
                int count = c.getCount();
                
                if (count > 0) {
                    markedIds = new Vector<Integer>(count);
                    
                    while (c.moveToNext()) {
                        markedIds.add(c.getInt(0));
                    }
                    
                    ContentValues cv = new ContentValues();
                    cv.put("isUnread", 0);
                    db.update(TABLE_ARTICLES, cv, where, null);
                    
                    calculateCounters();
                }
            } finally {
                if (c != null && !c.isClosed())
                    c.close();
            }
            
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        
        return markedIds;
    }
    
    public void markLabelRead(int labelId) {
        if (!isDBAvailable())
            return;
        
        ContentValues cv = new ContentValues();
        cv.put("isUnread", 0);
        String idList = "SELECT id FROM " + TABLE_ARTICLES + " AS a, " + TABLE_ARTICLES2LABELS
                + " as l WHERE a.id=l.articleId AND l.labelId=" + labelId;
        
        db.update(TABLE_ARTICLES, cv, "isUnread>0 AND id IN(" + idList + ")", null);
    }
    
    /**
     * mark given property of given articles with given state
     * 
     * @param idList
     *            set of article IDs, which should be processed
     * @param mark
     *            mark to be set
     * @param state
     *            value for the mark
     */
    public void markArticles(Set<Integer> idList, String mark, int state) {
        if (!isDBAvailable() || idList.isEmpty())
            return;
        
        db.beginTransaction();
        try {
            for (String ids : StringSupport.convertListToString(idList, 100)) {
                markArticles(ids, mark, state);
            }
            
            calculateCounters();
            
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }
    
    /**
     * mark given property of given article with given state
     * 
     * @param id
     *            set of article IDs, which should be processed
     * @param mark
     *            mark to be set
     * @param state
     *            value for the mark
     */
    public void markArticle(int id, String mark, int state) {
        if (!isDBAvailable())
            return;
        
        db.beginTransaction();
        try {
            markArticles("" + id, mark, state);
            
            calculateCounters();
            
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }
    
    /**
     * mark given property of given articles with given state
     * 
     * @param idList
     *            set of article IDs, which should be processed
     * @param mark
     *            mark to be set
     * @param state
     *            value for the mark
     */
    public void markArticles(String idList, String mark, int state) {
        if (!isDBAvailable())
            return;
        
        String sql = String.format("UPDATE %s SET %s=%s WHERE id IN (%s)", TABLE_ARTICLES, mark, state, idList);
        db.execSQL(sql);
    }
    
    public void markUnsynchronizedStates(Collection<Integer> ids, String mark, int state) {
        if (!isDBAvailable())
            return;
        
        // Disabled until further testing and proper SQL has been built. Tries to do the UPDATE and INSERT without
        // looping over the ids but instead with a list of ids:
        // Set<String> idList = StringSupport.convertListToString(ids);
        // for (String s : idList) {
        // db.execSQL(String.format("UPDATE %s SET %s=%s WHERE id in %s", TABLE_MARK, mark, state, s));
        // db.execSQL(String.format("INSERT OR IGNORE INTO %s (id, %s) VALUES (%s, %s)", TABLE_MARK, mark, id, state));
        // <- WRONG!
        // }
        
        db.beginTransaction();
        try {
            for (Integer id : ids) {
                // First update, then insert. If row exists it gets updated and second call ignores it, else the second
                // call inserts it.
                db.execSQL(String.format("UPDATE %s SET %s=%s WHERE id=%s", TABLE_MARK, mark, state, id));
                db.execSQL(String.format("INSERT OR IGNORE INTO %s (id, %s) VALUES (%s, %s)", TABLE_MARK, mark, id,
                        state));
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }
    
    // Special treatment for notes since the method markUnsynchronizedStates(...) doesn't support inserting any
    // additional data.
    public void markUnsynchronizedNotes(Map<Integer, String> ids, String markPublish) {
        if (!isDBAvailable())
            return;
        
        db.beginTransaction();
        try {
            for (Integer id : ids.keySet()) {
                String note = ids.get(id);
                if (note == null || note.equals(""))
                    continue;
                
                ContentValues cv = new ContentValues();
                cv.put(MARK_NOTE, note);
                db.update(TABLE_MARK, cv, "id=" + id, null);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }
    
    /**
     * set unread counters for feeds and categories according to real amount
     * of unread articles
     * 
     * @return {@code true} if counters was successfully updated, {@code false} otherwise
     */
    public void calculateCounters() {
        if (!isDBAvailable())
            return;
        
        int total = 0;
        Cursor c = null;
        ContentValues cv = null;
        
        db.beginTransaction();
        try {
            @SuppressWarnings("unused")
            int updateCount = 0;
            
            cv = new ContentValues();
            cv.put("unread", 0);
            updateCount = db.update(TABLE_FEEDS, cv, null, null);
            updateCount = db.update(TABLE_CATEGORIES, cv, null, null);
            
            try {
                // select feedId, count(*) from articles where isUnread>0 group by feedId
                c = db.query(TABLE_ARTICLES, new String[] { "feedId", "count(*)" }, "isUnread>0", null, "feedId", null,
                        null, null);
                
                // update feeds
                while (c.moveToNext()) {
                    int feedId = c.getInt(0);
                    int unreadCount = c.getInt(1);
                    
                    total += unreadCount;
                    
                    cv = new ContentValues();
                    cv.put("unread", unreadCount);
                    updateCount = db.update(TABLE_FEEDS, cv, "id=" + feedId, null);
                }
            } finally {
                if (c != null && !c.isClosed())
                    c.close();
            }
            
            try {
                // select categoryId, sum(unread) from feeds where categoryId >= 0 group by categoryId
                c = db.query(TABLE_FEEDS, new String[] { "categoryId", "sum(unread)" }, "categoryId>=0", null,
                        "categoryId", null, null, null);
                
                // update real categories
                while (c.moveToNext()) {
                    int categoryId = c.getInt(0);
                    int unreadCount = c.getInt(1);
                    
                    cv = new ContentValues();
                    cv.put("unread", unreadCount);
                    updateCount = db.update(TABLE_CATEGORIES, cv, "id=" + categoryId, null);
                }
            } finally {
                if (c != null && !c.isClosed())
                    c.close();
            }
            
            cv = new ContentValues();
            
            cv.put("unread", total);
            db.update(TABLE_CATEGORIES, cv, "id=" + Data.VCAT_ALL, null);
            
            cv.put("unread", getUnreadCount(Data.VCAT_FRESH, true));
            db.update(TABLE_CATEGORIES, cv, "id=" + Data.VCAT_FRESH, null);
            
            cv.put("unread", getUnreadCount(Data.VCAT_PUB, true));
            db.update(TABLE_CATEGORIES, cv, "id=" + Data.VCAT_PUB, null);
            
            cv.put("unread", getUnreadCount(Data.VCAT_STAR, true));
            db.update(TABLE_CATEGORIES, cv, "id=" + Data.VCAT_STAR, null);
            
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        
        Log.i(Utils.TAG, "Fixed counters, total unread: " + total);
    }
    
    public void updateAllArticlesCachedImages(boolean isCachedImages) {
        if (!isDBAvailable())
            return;
        
        ContentValues cv = new ContentValues();
        cv.put("cachedImages", isCachedImages);
        db.update(TABLE_ARTICLES, cv, "cachedImages=0", null); // Only apply if not yet applied
    }
    
    public void updateArticleCachedImages(int id, boolean isCachedImages) {
        if (!isDBAvailable())
            return;
        
        ContentValues cv = new ContentValues();
        cv.put("cachedImages", isCachedImages ? 1 : 0);
        db.update(TABLE_ARTICLES, cv, "cachedImages=0 AND id=" + id, null); // Only apply if not yet applied and ID
    }
    
    public void deleteCategories(boolean withVirtualCategories) {
        if (!isDBAvailable())
            return;
        
        String wherePart = "";
        if (!withVirtualCategories)
            wherePart = "id > 0";
        db.delete(TABLE_CATEGORIES, wherePart, null);
    }
    
    /**
     * delete all rows from feeds table
     */
    public void deleteFeeds() {
        if (!isDBAvailable())
            return;
        
        db.delete(TABLE_FEEDS, null, null);
    }
    
    /**
     * Deletes articles until the configured number of articles is matched. Published and Starred articles are ignored
     * so the configured limit is not an exact upper limit to the number of articles in the database.
     */
    public void purgeArticlesNumber() {
        if (!isDBAvailable())
            return;
        
        String idList = "SELECT id FROM " + TABLE_ARTICLES
                + " WHERE isPublished=0 AND isStarred=0 ORDER BY updateDate DESC LIMIT -1 OFFSET "
                + Utils.ARTICLE_LIMIT;
        
        db.delete(TABLE_ARTICLES, "id in(" + idList + ")", null);
        purgeLabels();
    }
    
    /**
     * delete given amount of last articles from DB
     * 
     * @param amountToPurge
     *            amount of articles to be purged
     */
    public void purgeLastArticles(int amountToPurge) {
        if (!isDBAvailable())
            return;
        
        String idList = "SELECT id FROM " + TABLE_ARTICLES
                + " WHERE isPublished=0 AND isStarred=0 ORDER BY updateDate DESC LIMIT -1 OFFSET "
                + (Utils.ARTICLE_LIMIT - amountToPurge);
        
        db.delete(TABLE_ARTICLES, "id in(" + idList + ")", null);
        purgeLabels();
    }
    
    /**
     * delete articles, which belongs to given IDs
     * 
     * @param feedIds
     *            IDs of removed feeds
     */
    public void purgeFeedsArticles(Collection<Integer> feedIds) {
        if (!isDBAvailable())
            return;
        
        db.delete(TABLE_ARTICLES, "feedId IN (" + feedIds + ")", null);
        purgeLabels();
    }
    
    /**
     * delete articles, which belongs to non-existent feeds
     */
    public void purgeOrphanedArticles() {
        if (!isDBAvailable())
            return;
        
        db.delete(TABLE_ARTICLES, "feedId NOT IN (SELECT id FROM " + TABLE_FEEDS + ")", null);
        purgeLabels();
    }
    
    private void purgeLabels() {
        // @formatter:off
        String idsArticles = "SELECT a2l.articleId FROM "
            + TABLE_ARTICLES2LABELS + " AS a2l LEFT OUTER JOIN "
            + TABLE_ARTICLES + " AS a"
            + " ON a2l.articleId = a.id WHERE a.id IS null";

        String idsFeeds = "SELECT a2l.labelId FROM "
            + TABLE_ARTICLES2LABELS + " AS a2l LEFT OUTER JOIN "
            + TABLE_FEEDS + " AS f"
            + " ON a2l.labelId = f.id WHERE f.id IS null";
        // @formatter:on
        db.delete(TABLE_ARTICLES2LABELS, "articleId IN(" + idsArticles + ")", null);
        db.delete(TABLE_ARTICLES2LABELS, "labelId IN(" + idsFeeds + ")", null);
    }
    
    public synchronized void vacuum() {
        if (vacuumDone)
            return;
        
        try {
            long time = System.currentTimeMillis();
            db.execSQL("VACUUM");
            vacuumDone = true;
            Log.i(Utils.TAG, "SQLite VACUUM took " + (System.currentTimeMillis() - time) + " ms.");
        } catch (SQLException e) {
            Log.e(Utils.TAG, "SQLite VACUUM failed: " + e.getMessage() + " " + e.getCause());
        }
    }
    
    // *******| SELECT |*******************************************************************
    
    /**
     * get minimal ID of unread article, stored in DB
     * 
     * @return minimal ID of unread article
     */
    public int getMinUnreadId() {
        int ret = 0;
        if (!isDBAvailable())
            return ret;
        
        Cursor c = null;
        try {
            
            c = db.query(TABLE_ARTICLES, new String[] { "min(id)" }, "isUnread>0", null, null, null, null, null);
            if (c.moveToFirst())
                ret = c.getInt(0);
            else
                return 0;
            
        } finally {
            if (c != null && !c.isClosed())
                c.close();
        }
        
        return ret;
    }
    
    /**
     * get amount of articles stored in the DB
     * 
     * @return amount of articles stored in the DB
     */
    public int countArticles() {
        int ret = -1;
        if (!isDBAvailable())
            return ret;
        
        Cursor c = null;
        try {
            
            c = db.query(TABLE_ARTICLES, new String[] { "count(*)" }, null, null, null, null, null, null);
            if (c.moveToFirst())
                ret = c.getInt(0);
            
        } finally {
            if (c != null && !c.isClosed())
                c.close();
        }
        
        return ret;
    }
    
    // Takes about 2 to 6 ms on Motorola Milestone
    public Article getArticle(int id) {
        Article ret = null;
        if (!isDBAvailable())
            return ret;
        
        Cursor c = null;
        try {
            
            c = db.query(TABLE_ARTICLES, null, "id=?", new String[] { id + "" }, null, null, null, null);
            if (c.moveToFirst())
                ret = handleArticleCursor(c);
            
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (c != null && !c.isClosed())
                c.close();
        }
        
        return ret;
    }
    
    public Set<Label> getLabelsForArticle(int articleId) {
        if (!isDBAvailable())
            return new HashSet<Label>();
        
        Cursor c = null;
        try {
            // @formatter:off
            String sql =      "SELECT f.id, f.title, 0 checked FROM " + TABLE_FEEDS + " f "
                      		+ "     WHERE f.id <= -11 AND"
                    		+ "     NOT EXISTS (SELECT * FROM " + TABLE_ARTICLES2LABELS + " a2l where f.id = a2l.labelId AND a2l.articleId = " + articleId + ")"
                            + " UNION"
                            + " SELECT f.id, f.title, 1 checked FROM " + TABLE_FEEDS + " f, " + TABLE_ARTICLES2LABELS + " a2l "
                            + "     WHERE f.id <= -11 AND f.id = a2l.labelId AND a2l.articleId = " + articleId;
            // @formatter:on
            c = db.rawQuery(sql, null);
            Set<Label> ret = new HashSet<Label>(c.getCount());
            while (c.moveToNext()) {
                Label label = new Label();
                label.setInternalId(c.getInt(0));
                label.caption = c.getString(1);
                label.checked = c.getInt(2) == 1;
                ret.add(label);
            }
            return ret;
            
        } finally {
            if (c != null && !c.isClosed())
                c.close();
        }
    }
    
    public Feed getFeed(int id) {
        Feed ret = new Feed();
        if (!isDBAvailable())
            return ret;
        
        Cursor c = null;
        try {
            
            c = db.query(TABLE_FEEDS, null, "id=?", new String[] { id + "" }, null, null, null, null);
            if (c.moveToFirst())
                ret = handleFeedCursor(c);
            
        } finally {
            if (c != null && !c.isClosed())
                c.close();
        }
        
        return ret;
    }
    
    public Category getCategory(int id) {
        Category ret = new Category();
        if (!isDBAvailable())
            return ret;
        
        Cursor c = null;
        try {
            
            c = db.query(TABLE_CATEGORIES, null, "id=?", new String[] { id + "" }, null, null, null, null);
            if (c.moveToFirst())
                ret = handleCategoryCursor(c);
            
        } finally {
            if (c != null && !c.isClosed())
                c.close();
        }
        
        return ret;
    }
    
    public Set<Article> getUnreadArticles(int feedId) {
        if (!isDBAvailable())
            return new LinkedHashSet<Article>();
        
        Cursor c = null;
        try {
            
            c = db.query(TABLE_ARTICLES, null, "feedId=? AND isUnread>0", new String[] { feedId + "" }, null, null,
                    null, null);
            Set<Article> ret = new LinkedHashSet<Article>(c.getCount());
            while (c.moveToNext()) {
                ret.add(handleArticleCursor(c));
            }
            return ret;
            
        } finally {
            if (c != null && !c.isClosed())
                c.close();
        }
    }
    
    /**
     * 0 - Uncategorized
     * -1 - Special (e.g. Starred, Published, Archived, etc.) <- these are categories here o.O
     * -2 - Labels
     * -3 - All feeds, excluding virtual feeds (e.g. Labels and such)
     * -4 - All feeds, including virtual feeds
     * 
     * @param categoryId
     * @return
     */
    public Set<Feed> getFeeds(int categoryId) {
        
        if (!isDBAvailable())
            return new LinkedHashSet<Feed>();
        
        Cursor c = null;
        try {
            String where = null; // categoryId = 0
            
            if (categoryId >= 0)
                where = "categoryId=" + categoryId;
            
            switch (categoryId) {
                case -1:
                    where = "id IN (0, -2, -3)";
                    break;
                case -2:
                    where = "id < -10";
                    break;
                case -3:
                    where = "categoryId >= 0";
                    break;
                case -4:
                    where = null;
                    break;
            }
            
            c = db.query(TABLE_FEEDS, null, where, null, null, null, "UPPER(title) ASC");
            Set<Feed> ret = new LinkedHashSet<Feed>(c.getCount());
            while (c.moveToNext()) {
                ret.add(handleFeedCursor(c));
            }
            return ret;
            
        } finally {
            if (c != null && !c.isClosed())
                c.close();
        }
    }
    
    public Set<Category> getVirtualCategories() {
        if (!isDBAvailable())
            return new LinkedHashSet<Category>();
        
        Cursor c = null;
        try {
            
            c = db.query(TABLE_CATEGORIES, null, "id<1", null, null, null, "id ASC");
            
            Set<Category> ret = new LinkedHashSet<Category>(c.getCount());
            while (c.moveToNext()) {
                ret.add(handleCategoryCursor(c));
            }
            return ret;
            
        } finally {
            if (c != null && !c.isClosed())
                c.close();
        }
    }
    
    public Set<Category> getAllCategories() {
        if (!isDBAvailable())
            return new LinkedHashSet<Category>();
        
        Cursor c = null;
        try {
            
            c = db.query(TABLE_CATEGORIES, null, "id>=0", null, null, null, "title ASC");
            Set<Category> ret = new LinkedHashSet<Category>(c.getCount());
            while (c.moveToNext()) {
                ret.add(handleCategoryCursor(c));
            }
            return ret;
            
        } finally {
            if (c != null && !c.isClosed())
                c.close();
        }
    }
    
    public int getUnreadCountOld(int id, boolean isCat) {
        int ret = 0;
        if (!isDBAvailable())
            return ret;
        
        if (isCat && id >= 0) { // Only do this for real categories for now
        
            for (Feed f : getFeeds(id)) {
                // Recurse into all feeds of this category and add the unread-count
                ret += getUnreadCount(f.id, false);
            }
            
        } else {
            // Read count for given feed
            Cursor c = null;
            try {
                
                c = db.query(isCat ? TABLE_CATEGORIES : TABLE_FEEDS, new String[] { "unread" }, "id=" + id, null, null,
                        null, null, null);
                if (c.moveToFirst())
                    ret = c.getInt(0);
                
            } finally {
                if (c != null && !c.isClosed())
                    c.close();
            }
        }
        
        return ret;
    }
    
    public int getUnreadCount(int id, boolean isCat) {
        int ret = 0;
        if (!isDBAvailable())
            return ret;
        
        StringBuilder selection = new StringBuilder("isUnread>0");
        String[] selectionArgs = new String[] { String.valueOf(id) };
        
        if (isCat && id >= 0) {
            // real categories
            selection.append(" and feedId in (select id from feeds where categoryId=?)");
        } else {
            if (id < 0) {
                // virtual categories
                switch (id) {
                // All Articles
                    case Data.VCAT_ALL:
                        selectionArgs = null;
                        break;
                    
                    // Fresh Articles
                    case Data.VCAT_FRESH:
                        selection.append(" and updateDate>?");
                        selectionArgs = new String[] { String.valueOf(new Date().getTime()
                                - Controller.getInstance().getFreshArticleMaxAge()) };
                        break;
                    
                    // Published Articles
                    case Data.VCAT_PUB:
                        selection.append(" and isPublished>0");
                        selectionArgs = null;
                        break;
                    
                    // Starred Articles
                    case Data.VCAT_STAR:
                        selection.append(" and isStarred>0");
                        selectionArgs = null;
                        break;
                    
                    default:
                        // Probably a label...
                        selection.append(" and feedId=?");
                }
            } else {
                // feeds
                selection.append(" and feedId=?");
            }
        }
        
        // Read count for given feed
        Cursor c = null;
        try {
            c = db.query(TABLE_ARTICLES, new String[] { "count(*)" }, selection.toString(), selectionArgs, null, null,
                    null, null);
            
            if (c.moveToFirst())
                ret = c.getInt(0);
            
        } finally {
            if (c != null && !c.isClosed())
                c.close();
        }
        
        return ret;
    }
    
    @SuppressLint("UseSparseArrays")
    public Map<Integer, String> getMarked(String mark, int status) {
        if (!isDBAvailable())
            return new HashMap<Integer, String>();
        
        Cursor c = null;
        try {
            c = db.query(TABLE_MARK, new String[] { "id", MARK_NOTE }, mark + "=" + status, null, null, null, null,
                    null);
            Map<Integer, String> ret = new HashMap<Integer, String>(c.getCount());
            while (c.moveToNext()) {
                ret.put(c.getInt(0), c.getString(1));
            }
            return ret;
            
        } finally {
            if (c != null && !c.isClosed())
                c.close();
        }
    }
    
    /**
     * remove specified mark in the temporary mark table for specified
     * articles and then cleanup this table
     * 
     * @param ids
     *            article IDs, which mark should be reseted
     * @param mark
     *            article mark to be reseted
     */
    public void setMarked(Map<Integer, String> ids, String mark) {
        if (!isDBAvailable())
            return;
        
        db.beginTransaction();
        try {
            for (String idList : StringSupport.convertListToString(ids.keySet(), 1000)) {
                ContentValues cv = new ContentValues();
                cv.putNull(mark);
                db.update(TABLE_MARK, cv, "id IN(" + idList + ")", null);
                db.delete(TABLE_MARK, "isUnread IS null AND isStarred IS null AND isPublished IS null", null);
            }
            
            // Insert notes afterwards and only if given note is not null
            for (Integer id : ids.keySet()) {
                String note = ids.get(id);
                if (note == null || note.equals(""))
                    continue;
                
                ContentValues cv = new ContentValues();
                cv.put(MARK_NOTE, note);
                db.update(TABLE_MARK, cv, "id=" + id, null);
            }
            
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }
    
    // *******************************************
    
    private static Article handleArticleCursor(Cursor c) {
        // @formatter:off
        Article ret = new Article(
                c.getInt(0),                        // id
                c.getInt(1),                        // feedId
                c.getString(2),                     // title
                (c.getInt(3) != 0),                 // isUnread
                c.getString(4),                     // articleUrl
                c.getString(5),                     // articleCommentUrl
                new Date(c.getLong(6)),             // updateDate
                c.getString(7),                     // content
                parseAttachments(c.getString(8)),   // attachments
                (c.getInt(9) != 0),                 // isStarred
                (c.getInt(10) != 0),                // isPublished
                c.getInt(11),                       // Label-ID
                parseArticleLabels(c.getString(12)) // Labels
        );
        ret.cachedImages = (c.getInt(11) != 0);
        // @formatter:on
        return ret;
    }
    
    private static Feed handleFeedCursor(Cursor c) {
        // @formatter:off
        Feed ret = new Feed(
                c.getInt(0),            // id
                c.getInt(1),            // categoryId
                c.getString(2),         // title
                c.getString(3),         // url
                c.getInt(4));           // unread
        // @formatter:on
        return ret;
    }
    
    private static Category handleCategoryCursor(Cursor c) {
        // @formatter:off
        Category ret = new Category(
                c.getInt(0),            // id
                c.getString(1),         // title
                c.getInt(2));           // unread
        // @formatter:on
        return ret;
    }
    
    private static Set<String> parseAttachments(String att) {
        Set<String> ret = new LinkedHashSet<String>();
        if (att == null)
            return ret;
        
        for (String s : att.split(";")) {
            ret.add(s);
        }
        
        return ret;
    }
    
    /*
     * Parse labels from string of the form "label;;label;;...;;label" where each label is of the following format:
     * "caption;forground;background"
     */
    private static Set<Label> parseArticleLabels(String labelStr) {
        Set<Label> ret = new LinkedHashSet<Label>();
        if (labelStr == null)
            return ret;
        
        int i = 0;
        for (String s : labelStr.split("---")) {
            String[] l = s.split(";");
            if (l.length > 0) {
                i++;
                Label label = new Label();
                label.setId(i);
                label.checked = true;
                label.caption = l[0];
                if (l.length > 1 && l[1].startsWith("#"))
                    label.foregroundColor = l[1];
                if (l.length > 2 && l[1].startsWith("#"))
                    label.backgroundColor = l[2];
                ret.add(label);
            }
        }
        
        return ret;
    }
    
    public ArrayList<Object[]> queryArticles(String query) {
        if (!isDBAvailable())
            return null;
        
        Cursor c = null;
        try {
            c = db.rawQuery(query, null);
            
            ArrayList<Object[]> ret = new ArrayList<Object[]>(c.getCount());
            while (c.moveToNext()) {
                Object[] o = new Object[7];
                o[FeedHeadlineAdapter.POS_ID] = c.getInt(0);
                o[FeedHeadlineAdapter.POS_FEEDID] = c.getInt(1);
                o[FeedHeadlineAdapter.POS_TITLE] = c.getString(2);
                o[FeedHeadlineAdapter.POS_UNREAD] = Boolean.valueOf(c.getInt(3) != 0);
                o[FeedHeadlineAdapter.POS_UPDATED] = new Date(c.getLong(4));
                o[FeedHeadlineAdapter.POS_STARRED] = Boolean.valueOf(c.getInt(5) != 0);
                o[FeedHeadlineAdapter.POS_PUBLISHED] = Boolean.valueOf(c.getInt(6) != 0);
                ret.add(o);
            }
            return ret;
            
        } finally {
            if (c != null && !c.isClosed())
                c.close();
        }
    }
    
    public ArrayList<Article> queryArticles() {
        if (!isDBAvailable())
            return null;
        
        Cursor c = null;
        try {
            c = db.query(TABLE_ARTICLES, new String[] { "id", "content", "attachments" },
                    "cachedImages=0 AND isUnread>0", null, null, null, null, "LIMIT 1000");
            
            ArrayList<Article> ret = new ArrayList<Article>(c.getCount());
            while (c.moveToNext()) {
                Article a = new Article();
                a.id = c.getInt(0);
                a.content = c.getString(1);
                a.attachments = parseAttachments(c.getString(2));
                ret.add(a);
            }
            return ret;
            
        } finally {
            if (c != null && !c.isClosed())
                c.close();
        }
    }
    
    public ArrayList<Object[]> queryFeeds(String query) {
        if (!isDBAvailable())
            return null;
        
        Cursor c = null;
        try {
            c = db.rawQuery(query, null);
            
            ArrayList<Object[]> ret = new ArrayList<Object[]>(c.getCount());
            while (c.moveToNext()) {
                Object[] o = new Object[7];
                o[FeedHeadlineAdapter.POS_ID] = c.getInt(0);
                o[FeedHeadlineAdapter.POS_TITLE] = c.getString(1);
                o[FeedHeadlineAdapter.POS_UNREAD] = c.getInt(2);
                ret.add(o);
            }
            return ret;
            
        } finally {
            if (c != null && !c.isClosed())
                c.close();
        }
    }
    
    public void queryCategories(final ArrayList<Object[]> list, String query) {
        if (!isDBAvailable())
            return;
        
        Cursor c = null;
        try {
            c = db.rawQuery(query, null);
            
            while (c.moveToNext()) {
                Object[] o = new Object[7];
                o[FeedHeadlineAdapter.POS_ID] = c.getInt(0);
                o[FeedHeadlineAdapter.POS_TITLE] = c.getString(1);
                o[FeedHeadlineAdapter.POS_UNREAD] = c.getInt(2);
                list.add(o);
            }
            
        } finally {
            if (c != null && !c.isClosed())
                c.close();
        }
    }
    
}
