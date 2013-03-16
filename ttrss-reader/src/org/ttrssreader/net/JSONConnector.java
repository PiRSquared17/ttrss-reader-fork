/*
 * ttrss-reader-fork for Android
 * 
 * Copyright (C) 2010 N. Braden.
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

package org.ttrssreader.net;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONException;
import org.json.JSONObject;
import org.ttrssreader.controllers.Controller;
import org.ttrssreader.controllers.DBHelper;
import org.ttrssreader.controllers.Data;
import org.ttrssreader.model.pojos.Article;
import org.ttrssreader.model.pojos.Category;
import org.ttrssreader.model.pojos.Feed;
import org.ttrssreader.model.pojos.Label;
import org.ttrssreader.utils.Base64;
import org.ttrssreader.utils.StringSupport;
import org.ttrssreader.utils.Utils;
import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

public abstract class JSONConnector {
    
    protected static String lastError = "";
    protected static boolean hasLastError = false;
    
    protected static final String PARAM_OP = "op";
    protected static final String PARAM_USER = "user";
    protected static final String PARAM_PW = "password";
    protected static final String PARAM_CAT_ID = "cat_id";
    protected static final String PARAM_FEED_ID = "feed_id";
    protected static final String PARAM_ARTICLE_ID = "article_id";
    protected static final String PARAM_ARTICLE_IDS = "article_ids";
    protected static final String PARAM_LIMIT = "limit";
    protected static final int PARAM_LIMIT_MAX_VALUE = 60;
    protected static final String PARAM_VIEWMODE = "view_mode";
    protected static final String PARAM_SHOW_CONTENT = "show_content";
    protected static final String PARAM_INC_ATTACHMENTS = "include_attachments"; // include_attachments available since
                                                                                 // 1.5.3 but is ignored on older
                                                                                 // versions
    protected static final String PARAM_SINCE_ID = "since_id";
    protected static final String PARAM_SKIP = "skip";
    protected static final String PARAM_MODE = "mode";
    protected static final String PARAM_FIELD = "field"; // 0-starred, 1-published, 2-unread, 3-article note (since api
                                                         // level 1)
    protected static final String PARAM_DATA = "data"; // optional data parameter when setting note field
    protected static final String PARAM_IS_CAT = "is_cat";
    protected static final String PARAM_PREF = "pref_name";
    protected static final String PARAM_OUTPUT_MODE = "output_mode"; // output_mode (default: flc) - what kind of
                                                                     // information to return (f-feeds, l-labels,
                                                                     // c-categories, t-tags)
    
    protected static final String VALUE_LOGIN = "login";
    protected static final String VALUE_GET_CATEGORIES = "getCategories";
    protected static final String VALUE_GET_FEEDS = "getFeeds";
    protected static final String VALUE_GET_HEADLINES = "getHeadlines";
    protected static final String VALUE_UPDATE_ARTICLE = "updateArticle";
    protected static final String VALUE_CATCHUP = "catchupFeed";
    protected static final String VALUE_UPDATE_FEED = "updateFeed";
    protected static final String VALUE_GET_PREF = "getPref";
    protected static final String VALUE_GET_VERSION = "getVersion";
    protected static final String VALUE_GET_LABELS = "getLabels";
    protected static final String VALUE_SET_LABELS = "setArticleLabel";
    protected static final String VALUE_SHARE_TO_PUBLISHED = "shareToPublished";
    
    protected static final String VALUE_LABEL_ID = "label_id";
    protected static final String VALUE_ASSIGN = "assign";
    protected static final String VALUE_API_LEVEL = "getApiLevel";
    protected static final String VALUE_GET_COUNTERS = "getCounters";
    protected static final String VALUE_OUTPUT_MODE = "flc"; // f - feeds, l - labels, c - categories, t - tags
    
    protected static final String ERROR = "error";
    protected static final String ERROR_TEXT = "Error: ";
    protected static final String NOT_LOGGED_IN = "NOT_LOGGED_IN";
    protected static final String UNKNOWN_METHOD = "UNKNOWN_METHOD";
    protected static final String NOT_LOGGED_IN_MESSAGE = "Couldn't login to your account, please check your credentials.";
    protected static final String API_DISABLED = "API_DISABLED";
    protected static final String API_DISABLED_MESSAGE = "Please enable API for the user \"%s\" in the preferences of this user on the Server.";
    protected static final String STATUS = "status";
    protected static final String API_LEVEL = "api_level";
    
    protected static final String SESSION_ID = "session_id"; // session id as an OUT parameter
    protected static final String SID_Test = "sid"; // session id as an IN parameter
    protected static final String ID = "id";
    protected static final String TITLE = "title";
    protected static final String UNREAD = "unread";
    protected static final String CAT_ID = "cat_id";
    protected static final String FEED_ID = "feed_id";
    protected static final String UPDATED = "updated";
    protected static final String CONTENT = "content";
    protected static final String URL = "link";
    protected static final String URL_SHARE = "url";
    protected static final String FEED_URL = "feed_url";
    protected static final String COMMENT_URL = "comments";
    protected static final String ATTACHMENTS = "attachments";
    protected static final String CONTENT_URL = "content_url";
    protected static final String STARRED = "marked";
    protected static final String PUBLISHED = "published";
    protected static final String VALUE = "value";
    protected static final String VERSION = "version";
    protected static final String LEVEL = "level";
    protected static final String CAPTION = "caption";
    protected static final String CHECKED = "checked";
    
    protected static final String COUNTER_KIND = "kind";
    protected static final String COUNTER_CAT = "cat";
    protected static final String COUNTER_ID = "id";
    protected static final String COUNTER_COUNTER = "counter";
    
    protected static final int MAX_ID_LIST_LENGTH = 100;
    
    protected String httpUsername;
    protected String httpPassword;
    
    protected String sessionId = null;
    protected String loginLock = "";
    protected DefaultHttpClient client;
    protected Context context;
    private int apiLevel = -1;
    
    public JSONConnector(Context context) {
        refreshHTTPAuth();
        this.context = context;
    }
    
    protected abstract InputStream doRequest(Map<String, String> params);
    
    protected boolean refreshHTTPAuth() {
        if (!Controller.getInstance().useHttpAuth())
            return false;
        
        boolean refreshNeeded = false;
        
        if (httpUsername == null || !httpUsername.equals(Controller.getInstance().httpUsername()))
            refreshNeeded = true;
        
        if (httpPassword == null || !httpPassword.equals(Controller.getInstance().httpPassword()))
            refreshNeeded = true;
        
        if (!refreshNeeded)
            return false;
        
        // Refresh data
        httpUsername = Controller.getInstance().httpUsername();
        httpPassword = Controller.getInstance().httpPassword();
        
        return true;
    }
    
    protected void logRequest(final JSONObject json) throws JSONException {
        if (Controller.getInstance().logSensitiveData()) {
            Log.i(Utils.TAG, json.toString());
        } else {
            // Filter password and session-id
            Object paramPw = json.remove(PARAM_PW);
            Object paramSID = json.remove(SID_Test);
            Log.i(Utils.TAG, json.toString());
            json.put(PARAM_PW, paramPw);
            json.put(SID_Test, paramSID);
        }
    }
    
    private String readResult(Map<String, String> params, boolean login) throws IOException {
        InputStream in = doRequest(params);
        if (in == null)
            return null;
        
        JsonReader reader = null;
        String ret = "";
        try {
            reader = new JsonReader(new InputStreamReader(in, "UTF-8"));
            // Check if content contains array or object, array indicates login-response or error, object is content
            
            reader.beginObject();
            while (reader.hasNext()) {
                String name = reader.nextName();
                if (name.equals("content")) {
                    JsonToken t = reader.peek();
                    
                    if (t.equals(JsonToken.BEGIN_OBJECT)) {
                        
                        JsonObject object = new JsonObject();
                        reader.beginObject();
                        while (reader.hasNext()) {
                            object.addProperty(reader.nextName(), reader.nextString());
                        }
                        reader.endObject();
                        
                        if (object.get(SESSION_ID) != null) {
                            ret = object.get(SESSION_ID).getAsString();
                        }
                        if (object.get(STATUS) != null) {
                            ret = object.get(STATUS).getAsString();
                        }
                        if (object.get(API_LEVEL) != null) {
                            this.apiLevel = object.get(API_LEVEL).getAsInt();
                        }
                        if (object.get(VALUE) != null) {
                            ret = object.get(VALUE).getAsString();
                        }
                        if (object.get(ERROR) != null) {
                            String message = object.get(ERROR).getAsString();
                            
                            if (message.contains(NOT_LOGGED_IN)) {
                                lastError = NOT_LOGGED_IN;
                                if (!login)
                                    return readResult(params, false); // Just do the same request again
                                else
                                    return null;
                            }
                            
                            if (message.contains(API_DISABLED)) {
                                hasLastError = true;
                                lastError = String.format(API_DISABLED_MESSAGE, Controller.getInstance().username());
                                return null;
                            }
                            
                            // Any other error
                            hasLastError = true;
                            lastError = ERROR_TEXT + message;
                            return null;
                        }
                    }
                    
                } else {
                    reader.skipValue();
                }
            }
        } finally {
            if (reader != null)
                reader.close();
        }
        if (ret.startsWith("\""))
            ret = ret.substring(1, ret.length());
        if (ret.endsWith("\""))
            ret = ret.substring(0, ret.length() - 1);
        
        return ret;
    }
    
    private JsonReader prepareReader(Map<String, String> params) throws IOException {
        InputStream in = doRequest(params);
        if (in == null)
            return null;
        JsonReader reader = new JsonReader(new InputStreamReader(in, "UTF-8"));
        
        // Check if content contains array or object, array indicates login-response or error, object is content
        try {
            reader.beginObject();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        while (reader.hasNext()) {
            String name = reader.nextName();
            if (name.equals("content")) {
                JsonToken t = reader.peek();
                
                if (t.equals(JsonToken.BEGIN_OBJECT)) {
                    // Handle error
                    JsonObject object = new JsonObject();
                    reader.beginObject();
                    while (reader.hasNext()) {
                        object.addProperty(reader.nextName(), reader.nextString());
                    }
                    reader.endObject();
                    
                    if (object.get(ERROR) != null) {
                        String message = object.get(ERROR).toString();
                        
                        if (message.contains(NOT_LOGGED_IN)) {
                            lastError = NOT_LOGGED_IN;
                            if (login())
                                return prepareReader(params); // Just do the same request again
                            else
                                return null;
                        }
                        
                        if (message.contains(API_DISABLED)) {
                            hasLastError = true;
                            lastError = String.format(API_DISABLED_MESSAGE, Controller.getInstance().username());
                            return null;
                        }
                        
                        // Any other error
                        hasLastError = true;
                        lastError = ERROR_TEXT + message;
                    }
                } else if (t.equals(JsonToken.BEGIN_ARRAY)) {
                    return reader;
                }
                
            } else {
                reader.skipValue();
            }
        }
        return null;
    }
    
    public boolean sessionAlive() {
        // Make sure we are logged in
        if (sessionId == null || lastError.equals(NOT_LOGGED_IN))
            if (!login())
                return false;
        if (hasLastError)
            return false;
        return true;
    }
    
    /**
     * Does an API-Call and ignores the result.
     * 
     * @param params
     * @return true if the call was successful.
     */
    private boolean doRequestNoAnswer(Map<String, String> params) {
        if (!sessionAlive())
            return false;
        
        try {
            String result = readResult(params, false);
            // Log.d(Utils.TAG, "Result: " + result);
            if ("OK".equals(result))
                return true;
            else
                return false;
        } catch (IOException e) {
            e.printStackTrace();
            if (!hasLastError) {
                hasLastError = true;
                lastError = ERROR_TEXT + formatException(e);
            }
        }
        
        return false;
    }
    
    /**
     * Tries to login to the ttrss-server with the base64-encoded password.
     * 
     * @return true on success, false otherwise
     */
    private boolean login() {
        long time = System.currentTimeMillis();
        
        // Just login once, check if already logged in after acquiring the lock on mSessionId
        if (sessionId != null && !lastError.equals(NOT_LOGGED_IN))
            return true;
        
        synchronized (loginLock) {
            if (sessionId != null && !lastError.equals(NOT_LOGGED_IN))
                return true; // Login done while we were waiting for the lock
                
            Map<String, String> params = new HashMap<String, String>();
            params.put(PARAM_OP, VALUE_LOGIN);
            params.put(PARAM_USER, Controller.getInstance().username());
            params.put(PARAM_PW, Base64.encodeBytes(Controller.getInstance().password().getBytes()));
            
            try {
                sessionId = readResult(params, true);
                if (sessionId != null) {
                    Log.d(Utils.TAG, "login: " + (System.currentTimeMillis() - time) + "ms");
                    return true;
                }
            } catch (IOException e) {
                e.printStackTrace();
                if (!hasLastError) {
                    hasLastError = true;
                    lastError = ERROR_TEXT + formatException(e);
                }
            }
            
            if (!hasLastError) {
                // Login didnt succeed, write message
                hasLastError = true;
                lastError = NOT_LOGGED_IN_MESSAGE;
            }
            return false;
        }
    }
    
    // ***************** Helper-Methods **************************************************
    
    private void parseCounter(JsonReader reader) {
        
        final int isCat = 0;
        final int id = 1;
        final int unreadCount = 2;
        
        long time = System.currentTimeMillis();
        
        List<int[]> list = new ArrayList<int[]>();
        try {
            reader.beginArray();
            while (reader.hasNext()) {
                
                int[] values = new int[] { 0, Integer.MAX_VALUE, 0 };
                
                reader.beginObject();
                while (reader.hasNext()) {
                    
                    try {
                        String name = reader.nextName();
                        
                        if (name.equals(COUNTER_KIND)) {
                            values[isCat] = reader.nextString().equals(COUNTER_CAT) ? 1 : 0;
                        } else if (name.equals(COUNTER_ID)) {
                            String value = reader.nextString();
                            // Check if id is a string, then it would be a global counter
                            if (value.equals("global-unread") || value.equals("subscribed-feeds"))
                                continue;
                            values[id] = Integer.parseInt(value);
                        } else if (name.equals(COUNTER_COUNTER)) {
                            String value = reader.nextString();
                            // Check if null because of an API-bug
                            if (!value.equals("null"))
                                values[unreadCount] = Integer.parseInt(value);
                        } else {
                            reader.skipValue();
                        }
                    } catch (IllegalArgumentException e) {
                        e.printStackTrace();
                        reader.skipValue();
                        continue;
                    }
                    
                }
                reader.endObject();
                list.add(values);
                
            }
            reader.endArray();
        } catch (IllegalStateException ise) {
        } catch (IOException e) {
        }
        
        Log.d(Utils.TAG, "Counters: parsing took " + (System.currentTimeMillis() - time) + "ms");
        time = System.currentTimeMillis();
        
        SQLiteDatabase db = DBHelper.getInstance().db;
        try {
            db.beginTransaction();
            
            for (int[] values : list) { // TODO: Optimize!!!
                if (values[id] == Integer.MAX_VALUE)
                    continue;
                
                ContentValues cv = new ContentValues();
                cv.put("unread", values[unreadCount]);
                
                if (values[isCat] > 0 && values[id] >= 0) {
                    // Category
                    db.update(DBHelper.TABLE_CATEGORIES, cv, "id=?", new String[] { values[id] + "" });
                } else if (values[isCat] == 0 && values[id] < 0 && values[id] >= -4) {
                    // Virtual Category
                    db.update(DBHelper.TABLE_CATEGORIES, cv, "id=?", new String[] { values[id] + "" });
                } else if (values[isCat] == 0 && values[id] > 0) {
                    // Feed
                    db.update(DBHelper.TABLE_FEEDS, cv, "id=?", new String[] { values[id] + "" });
                } else if (values[isCat] == 0 && values[id] < -10) {
                    // Label
                    db.update(DBHelper.TABLE_FEEDS, cv, "id=?", new String[] { values[id] + "" });
                }
            }
            
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
            DBHelper.getInstance().purgeArticlesNumber();
        }
        
        Log.d(Utils.TAG, "Counters: inserting took " + (System.currentTimeMillis() - time) + "ms");
    }
    
    private Set<String> parseAttachments(JsonReader reader) throws IOException {
        Set<String> ret = new HashSet<String>();
        reader.beginArray();
        while (reader.hasNext()) {
            
            String attId = null;
            String attUrl = null;
            
            reader.beginObject();
            while (reader.hasNext()) {
                
                try {
                    String name = reader.nextName();
                    if (name.equals(CONTENT_URL)) {
                        attUrl = reader.nextString();
                    } else if (name.equals(ID)) {
                        attId = reader.nextString();
                    } else {
                        reader.skipValue();
                    }
                } catch (IllegalArgumentException e) {
                    e.printStackTrace();
                    reader.skipValue();
                    continue;
                }
                
            }
            reader.endObject();
            
            if (attId != null && attUrl != null)
                ret.add(attUrl);
        }
        reader.endArray();
        return ret;
    }
    
    private int parseArticleArray(final Set<Article> articles, JsonReader reader, int labelId, int id, boolean isCategory) {
        long time = System.currentTimeMillis();
        int count = 0;
        
        try {
            reader.beginArray();
            while (reader.hasNext()) {
                int articleId = -1;
                String title = null;
                boolean isUnread = false;
                Date updated = null;
                int feedId = 0;
                String content = null;
                String articleUrl = null;
                String articleCommentUrl = null;
                Set<String> attachments = null;
                boolean isStarred = false;
                boolean isPublished = false;
                
                reader.beginObject();
                while (reader.hasNext() && reader.peek().equals(JsonToken.NAME)) {
                    String name = reader.nextName();
                    
                    try {
                        if (name.equals(ID)) {
                            articleId = reader.nextInt();
                        } else if (name.equals(TITLE)) {
                            title = reader.nextString();
                        } else if (name.equals(UNREAD)) {
                            isUnread = reader.nextBoolean();
                        } else if (name.equals(UPDATED)) {
                            updated = new Date(Long.valueOf(reader.nextString() + "000"));
                        } else if (name.equals(FEED_ID)) {
                            feedId = reader.nextInt();
                        } else if (name.equals(CONTENT)) {
                            content = reader.nextString();
                        } else if (name.equals(URL)) {
                            articleUrl = reader.nextString();
                        } else if (name.equals(COMMENT_URL)) {
                            articleCommentUrl = reader.nextString();
                        } else if (name.equals(ATTACHMENTS)) {
                            attachments = parseAttachments(reader);
                        } else if (name.equals(STARRED)) {
                            isStarred = reader.nextBoolean();
                        } else if (name.equals(PUBLISHED)) {
                            isPublished = reader.nextBoolean();
                        } else {
                            reader.skipValue();
                        }
                    } catch (IllegalArgumentException e) {
                        Log.w(Utils.TAG, "Result contained illegal value for entry \"" + name + "\".");
                        reader.skipValue();
                        continue;
                    }
                    
                }
                reader.endObject();
                
                if (articleId != -1 && title != null) {
                    articles.add(new Article(articleId, feedId, title, isUnread, articleUrl, articleCommentUrl,
                            updated, content, attachments, isStarred, isPublished, labelId));
                    count++;
                }
            }
            reader.endArray();
        } catch (Exception e) {
            Log.e(Utils.TAG, "Input data could not be read: " + e.getMessage() + " (" + e.getCause() + ")", e);
        }
        
        Log.d(Utils.TAG, "parseArticleArray: parsing " + count + " articles took "
                + (System.currentTimeMillis() - time) + "ms");
        return count;
    }
    
    // ***************** Retrieve-Data-Methods **************************************************
    
    /**
     * Retrieves a set of maps which map strings to the information, e.g. "id" -> 42, containing the counters for every
     * category and feed. The retrieved information is directly inserted into the database.
     * 
     * @return true if the request succeeded.
     */
    public boolean getCounters() {
        boolean ret = true;
        makeLazyServerWork(); // otherwise the unread counters may be outdated
        
        if (!sessionAlive())
            return false;
        
        long time = System.currentTimeMillis();
        Map<String, String> params = new HashMap<String, String>();
        params.put(PARAM_OP, VALUE_GET_COUNTERS);
        params.put(PARAM_OUTPUT_MODE, VALUE_OUTPUT_MODE);
        
        JsonReader reader = null;
        try {
            reader = prepareReader(params);
            if (reader == null)
                return false;
            
            parseCounter(reader);
            
        } catch (IOException e) {
            e.printStackTrace();
            ret = false;
        } finally {
            if (reader != null)
                try {
                    reader.close();
                } catch (IOException e1) {
                }
        }
        
        Log.d(Utils.TAG, "getCounters: " + (System.currentTimeMillis() - time) + "ms");
        return ret;
    }
    
    /**
     * Retrieves all categories.
     * 
     * @return a list of categories.
     */
    public Set<Category> getCategories() {
        long time = System.currentTimeMillis();
        Set<Category> ret = new LinkedHashSet<Category>();
        if (!sessionAlive())
            return ret;
        
        Map<String, String> params = new HashMap<String, String>();
        params.put(PARAM_OP, VALUE_GET_CATEGORIES);
        
        JsonReader reader = null;
        try {
            reader = prepareReader(params);
            
            if (reader == null)
                return ret;
            
            reader.beginArray();
            while (reader.hasNext()) {
                
                int id = -1;
                String title = null;
                int unread = 0;
                
                reader.beginObject();
                while (reader.hasNext()) {
                    
                    try {
                        String name = reader.nextName();
                        
                        if (name.equals(ID)) {
                            id = reader.nextInt();
                        } else if (name.equals(TITLE)) {
                            title = reader.nextString();
                        } else if (name.equals(UNREAD)) {
                            unread = reader.nextInt();
                        } else {
                            reader.skipValue();
                        }
                    } catch (IllegalArgumentException e) {
                        e.printStackTrace();
                        reader.skipValue();
                        continue;
                    }
                    
                }
                reader.endObject();
                
                // Don't handle categories with an id below 1, we already have them in the DB from
                // Data.updateVirtualCategories()
                if (id > 0 && title != null)
                    ret.add(new Category(id, title, unread));
            }
            reader.endArray();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (reader != null)
                try {
                    reader.close();
                } catch (IOException e1) {
                }
        }
        
        Log.d(Utils.TAG, "getCategories: " + (System.currentTimeMillis() - time) + "ms");
        return ret;
    }
    
    private Set<Feed> getFeeds(boolean tolerateWrongUnreadInformation) {
        long time = System.currentTimeMillis();
        Set<Feed> ret = new LinkedHashSet<Feed>();
        if (!sessionAlive())
            return ret;
        
        if (!tolerateWrongUnreadInformation) {
            makeLazyServerWork();
        }
        
        Map<String, String> params = new HashMap<String, String>();
        params.put(PARAM_OP, VALUE_GET_FEEDS);
        params.put(PARAM_CAT_ID, Data.VCAT_ALL + ""); // Hardcoded -4 fetches all feeds. See
                                                      // http://tt-rss.org/redmine/wiki/tt-rss/JsonApiReference#getFeeds
        
        JsonReader reader = null;
        try {
            reader = prepareReader(params);
            
            if (reader == null)
                return ret;
            
            reader.beginArray();
            while (reader.hasNext()) {
                
                int categoryId = -1;
                int id = 0;
                String title = null;
                String feedUrl = null;
                int unread = 0;
                
                reader.beginObject();
                while (reader.hasNext()) {
                    
                    try {
                        String name = reader.nextName();
                        
                        if (name.equals(ID)) {
                            id = reader.nextInt();
                        } else if (name.equals(CAT_ID)) {
                            categoryId = reader.nextInt();
                        } else if (name.equals(TITLE)) {
                            title = reader.nextString();
                        } else if (name.equals(FEED_URL)) {
                            feedUrl = reader.nextString();
                        } else if (name.equals(UNREAD)) {
                            unread = reader.nextInt();
                        } else {
                            reader.skipValue();
                        }
                    } catch (IllegalArgumentException e) {
                        e.printStackTrace();
                        reader.skipValue();
                        continue;
                    }
                    
                }
                reader.endObject();
                
                if (id != -1 || categoryId == -2) // normal feed (>0) or label (-2)
                    if (title != null) // Dont like complicated if-statements..
                        ret.add(new Feed(id, categoryId, title, feedUrl, unread));
                
            }
            reader.endArray();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (reader != null)
                try {
                    reader.close();
                } catch (IOException e1) {
                }
        }
        
        Log.d(Utils.TAG, "getFeeds: " + (System.currentTimeMillis() - time) + "ms");
        return ret;
    }
    
    /**
     * Retrieves all feeds, mapped to their categories.
     * 
     * @return a map of all feeds mapped to the categories.
     */
    public Set<Feed> getFeeds() {
        return getFeeds(false);
    }
    
    private boolean makeLazyServerWork(Integer feedId) {
        if (Controller.getInstance().lazyServer()) {
            Map<String, String> taskParams = new HashMap<String, String>();
            taskParams.put(PARAM_OP, VALUE_UPDATE_FEED);
            taskParams.put(PARAM_FEED_ID, feedId + "");
            return doRequestNoAnswer(taskParams);
        }
        return true;
    }
    
    private long noTaskUntil = 0;
    final static private long minTaskIntervall = 10 * Utils.MINUTE;
    
    private boolean makeLazyServerWork() {
        boolean ret = true;
        final long time = System.currentTimeMillis();
        if (Controller.getInstance().lazyServer() && (noTaskUntil < time)) {
            noTaskUntil = time + minTaskIntervall;
            Set<Feed> feedset = getFeeds(true);
            Iterator<Feed> feeds = feedset.iterator();
            while (feeds.hasNext()) {
                final Feed f = feeds.next();
                ret = ret && makeLazyServerWork(f.id);
            }
        }
        return ret;
    }
    
    /**
     * @see #getHeadlines(Integer, int, String, boolean, int, int)
     */
    public void getHeadlines(final Set<Article> articles, Integer id, int limit, String viewMode, boolean isCategory) {
        getHeadlines(articles, id, limit, viewMode, isCategory, 0);
    }
    
    /**
     * Retrieves the specified articles.
     * 
     * @param id
     *            the id of the feed/category
     * @param limit
     *            the maximum number of articles to be fetched
     * @param viewMode
     *            indicates wether only unread articles should be included (Possible values: all_articles, unread,
     *            adaptive, marked, updated)
     * @param isCategory
     *            indicates if we are dealing with a category or a feed
     * @param sinceId
     *            the first ArticleId which is to be retrieved.
     * @return the number of fetched articles.
     */
    public void getHeadlines(final Set<Article> articles, Integer id, int limit, String viewMode, boolean isCategory, int sinceId) {
        long time = System.currentTimeMillis();
        int offset = 0;
        int maxSize = articles.size() + limit;
        
        if (!sessionAlive())
            return;
        
        makeLazyServerWork(id);
        
        while (articles.size() < maxSize) {
            
            Map<String, String> params = new HashMap<String, String>();
            params.put(PARAM_OP, VALUE_GET_HEADLINES);
            params.put(PARAM_FEED_ID, id + "");
            params.put(PARAM_LIMIT, PARAM_LIMIT_MAX_VALUE + "");
            params.put(PARAM_SKIP, offset + "");
            params.put(PARAM_VIEWMODE, viewMode);
            params.put(PARAM_SHOW_CONTENT, "1");
            params.put(PARAM_INC_ATTACHMENTS, "1");
            params.put(PARAM_IS_CAT, (isCategory ? "1" : "0"));
            if (sinceId > 0)
                params.put(PARAM_SINCE_ID, sinceId + "");
            
            if (id == Data.VCAT_STAR && !isCategory) // We set isCategory=false for starred/published articles...
                DBHelper.getInstance().purgeStarredArticles();
            
            if (id == Data.VCAT_PUB && !isCategory)
                DBHelper.getInstance().purgePublishedArticles();
            
            JsonReader reader = null;
            try {
                reader = prepareReader(params);
                if (reader == null)
                    continue;
                
                int count = parseArticleArray(articles, reader, (!isCategory && id < -10 ? id : -1), id, isCategory);
                if (count < PARAM_LIMIT_MAX_VALUE)
                    break;
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (IOException e1) {
                    }
                }
            }
            offset = offset + PARAM_LIMIT_MAX_VALUE;
        }
        
        Log.d(Utils.TAG, "getHeadlines: " + (System.currentTimeMillis() - time) + "ms");
    }
    
    /**
     * Marks the given list of article-Ids as read/unread depending on int articleState.
     * 
     * @param articlesIds
     *            a list of article-ids.
     * @param articleState
     *            the new state of the article (0 -> mark as read; 1 -> mark as unread).
     */
    public boolean setArticleRead(Set<Integer> ids, int articleState) {
        boolean ret = true;
        if (ids.size() == 0)
            return ret;
        
        for (String idList : StringSupport.convertListToString(ids, MAX_ID_LIST_LENGTH)) {
            Map<String, String> params = new HashMap<String, String>();
            params.put(PARAM_OP, VALUE_UPDATE_ARTICLE);
            params.put(PARAM_ARTICLE_IDS, idList);
            params.put(PARAM_MODE, articleState + "");
            params.put(PARAM_FIELD, "2");
            ret = ret && doRequestNoAnswer(params);
        }
        return ret;
    }
    
    /**
     * Marks the given Article as "starred"/"not starred" depending on int articleState.
     * 
     * @param ids
     *            a list of article-ids.
     * @param articleState
     *            the new state of the article (0 -> not starred; 1 -> starred; 2 -> toggle).
     * @return true if the operation succeeded.
     */
    public boolean setArticleStarred(Set<Integer> ids, int articleState) {
        boolean ret = true;
        if (ids.size() == 0)
            return ret;
        
        for (String idList : StringSupport.convertListToString(ids, MAX_ID_LIST_LENGTH)) {
            Map<String, String> params = new HashMap<String, String>();
            params.put(PARAM_OP, VALUE_UPDATE_ARTICLE);
            params.put(PARAM_ARTICLE_IDS, idList);
            params.put(PARAM_MODE, articleState + "");
            params.put(PARAM_FIELD, "0");
            ret = ret && doRequestNoAnswer(params);
        }
        return ret;
    }
    
    /**
     * Marks the given Articles as "published"/"not published" depending on articleState.
     * 
     * @param ids
     *            a list of article-ids with corresponding notes (may be null).
     * @param articleState
     *            the new state of the articles (0 -> not published; 1 -> published; 2 -> toggle).
     * @return true if the operation succeeded.
     */
    public boolean setArticlePublished(Map<Integer, String> ids, int articleState) {
        boolean ret = true;
        if (ids.size() == 0)
            return ret;
        
        for (String idList : StringSupport.convertListToString(ids.keySet(), MAX_ID_LIST_LENGTH)) {
            Map<String, String> params = new HashMap<String, String>();
            params.put(PARAM_OP, VALUE_UPDATE_ARTICLE);
            params.put(PARAM_ARTICLE_IDS, idList);
            params.put(PARAM_MODE, articleState + "");
            params.put(PARAM_FIELD, "1");
            ret = ret && doRequestNoAnswer(params);
            
            // Add a note to the article(s)
            
            for (Integer id : ids.keySet()) {
                String note = ids.get(id);
                if (note == null || note.equals(""))
                    continue;
                
                params.put(PARAM_FIELD, "3"); // Field 3 is the "Add note" field
                params.put(PARAM_DATA, note);
                ret = ret && doRequestNoAnswer(params);
            }
        }
        
        return ret;
    }
    
    /**
     * Marks a feed or a category with all its feeds as read.
     * 
     * @param id
     *            the feed-id/category-id.
     * @param isCategory
     *            indicates whether id refers to a feed or a category.
     * @return true if the operation succeeded.
     */
    public boolean setRead(int id, boolean isCategory) {
        Map<String, String> params = new HashMap<String, String>();
        params.put(PARAM_OP, VALUE_CATCHUP);
        params.put(PARAM_FEED_ID, id + "");
        params.put(PARAM_IS_CAT, (isCategory ? "1" : "0"));
        return doRequestNoAnswer(params);
    }
    
    /**
     * Returns the value for the given preference-name as a string.
     * 
     * @param pref
     *            the preferences name
     * @return the value of the preference or null if it ist not set or unknown
     */
    public String getPref(String pref) {
        if (!sessionAlive())
            return null;
        
        Map<String, String> params = new HashMap<String, String>();
        params.put(PARAM_OP, VALUE_GET_PREF);
        params.put(PARAM_PREF, pref);
        
        try {
            String ret = readResult(params, false);
            return ret;
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        return null;
    }
    
    /**
     * Returns the version of the server-installation as integer (version-string without dots)
     * 
     * @return the version
     */
    public int getVersion() {
        int ret = -1;
        if (!sessionAlive())
            return ret;
        
        Map<String, String> params = new HashMap<String, String>();
        params.put(PARAM_OP, VALUE_GET_VERSION);
        
        String response = "";
        JsonReader reader = null;
        try {
            reader = prepareReader(params);
            if (reader == null)
                return ret;
            
            reader.beginArray();
            while (reader.hasNext()) {
                try {
                    
                    reader.beginObject();
                    while (reader.hasNext()) {
                        String name = reader.nextName();
                        
                        if (name.equals(VERSION)) {
                            response = reader.nextString();
                        } else {
                            reader.skipValue();
                        }
                        
                    }
                    
                } catch (IllegalArgumentException e) {
                    e.printStackTrace();
                }
            }
            
            // Replace dots, parse integer
            ret = Integer.parseInt(response.replace(".", ""));
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (reader != null)
                try {
                    reader.close();
                } catch (IOException e1) {
                }
        }
        
        return ret;
    }
    
    /**
     * Returns a Set of all existing labels. If some of the labels are checked for the given article the property
     * "checked" is true.
     * 
     * @return a set of labels.
     */
    public Set<Label> getLabels(Integer articleId) {
        Set<Label> ret = new HashSet<Label>();
        if (!sessionAlive())
            return ret;
        
        Map<String, String> params = new HashMap<String, String>();
        params.put(PARAM_OP, VALUE_GET_LABELS);
        params.put(PARAM_ARTICLE_ID, articleId.toString());
        
        JsonReader reader = null;
        Label label = new Label();;
        try {
            reader = prepareReader(params);
            if (reader == null)
                return ret;
            
            reader.beginArray();
            while (reader.hasNext()) {
                try {
                    
                    reader.beginObject();
                    label = new Label();
                    while (reader.hasNext()) {
                        String name = reader.nextName();
                        if (ID.equals(name)) {
                            label.setId(reader.nextInt());
                        } else if (CAPTION.equals(name)) {
                            label.caption = reader.nextString();
                        } else if (CHECKED.equals(name)) {
                            label.checked = reader.nextBoolean();
                        } else {
                            reader.skipValue();
                        }
                    }
                    
                } catch (IllegalArgumentException e) {
                    e.printStackTrace();
                }
            }
            reader.endArray();
            ret.add(label);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (reader != null)
                try {
                    reader.close();
                } catch (IOException e1) {
                }
        }
        
        return ret;
    }
    
    public boolean setArticleLabel(Set<Integer> articleIds, int labelId, boolean assign) {
        boolean ret = true;
        if (articleIds.size() == 0)
            return ret;
        
        for (String idList : StringSupport.convertListToString(articleIds, MAX_ID_LIST_LENGTH)) {
            Map<String, String> params = new HashMap<String, String>();
            params.put(PARAM_OP, VALUE_SET_LABELS);
            params.put(PARAM_ARTICLE_IDS, idList);
            params.put(VALUE_LABEL_ID, labelId + "");
            params.put(VALUE_ASSIGN, (assign ? "1" : "0"));
            ret = ret && doRequestNoAnswer(params);
        }
        
        return ret;
    }
    
    public boolean shareToPublished(String title, String url, String content) {
        Map<String, String> params = new HashMap<String, String>();
        params.put(PARAM_OP, VALUE_SHARE_TO_PUBLISHED);
        params.put(TITLE, title);
        params.put(URL_SHARE, url);
        params.put(CONTENT, content);
        return doRequestNoAnswer(params);
    }
    
    /**
     * Retrieves the API-Level of the currently used server-installation.
     * 
     * @return the API-Level of the server-installation
     */
    public int getApiLevel() {
        // Directly return api_level which was retrieved with the login, only for 1.6 and above
        if (apiLevel > -1)
            return apiLevel;
        
        int ret = -1;
        if (!sessionAlive())
            return ret;
        
        Map<String, String> params = new HashMap<String, String>();
        params.put(PARAM_OP, VALUE_API_LEVEL);
        
        String response = "";
        JsonReader reader = null;
        try {
            reader = prepareReader(params);
            if (reader == null)
                return ret;
            
            reader.beginArray();
            while (reader.hasNext()) {
                try {
                    
                    reader.beginObject();
                    while (reader.hasNext()) {
                        String name = reader.nextName();
                        
                        if (name.equals(LEVEL)) {
                            response = reader.nextString();
                        } else {
                            reader.skipValue();
                        }
                        
                    }
                    
                } catch (IllegalArgumentException e) {
                    e.printStackTrace();
                }
            }
            
            if (response.contains(UNKNOWN_METHOD)) {
                ret = 0; // Assume Api-Level 0
            } else {
                ret = Integer.parseInt(response);
            }
            
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (reader != null)
                try {
                    reader.close();
                } catch (IOException e1) {
                }
        }
        
        return ret;
    }
    
    /**
     * Returns true if there was an error.
     * 
     * @return true if there was an error.
     */
    public boolean hasLastError() {
        return hasLastError;
    }
    
    /**
     * Returns the last error-message and resets the error-state of the connector.
     * 
     * @return a string with the last error-message.
     */
    public String pullLastError() {
        String ret = new String(lastError);
        lastError = "";
        hasLastError = false;
        return ret;
    }
    
    protected static String formatException(Exception e) {
        return e.getMessage() + (e.getCause() != null ? "(" + e.getCause() + ")" : "");
    }
    
}
