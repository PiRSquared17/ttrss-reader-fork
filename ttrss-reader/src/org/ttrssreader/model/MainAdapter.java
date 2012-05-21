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

package org.ttrssreader.model;

import java.util.ArrayList;
import java.util.List;
import org.ttrssreader.utils.Utils;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

public abstract class MainAdapter extends BaseAdapter {
    
    protected Context context;
    protected Cursor cursor;
    private Cursor tempCursor = null;
    protected String poorMansMutex = "poorMansMutex";
    
    protected int categoryId;
    protected int feedId;
    
    protected boolean selectArticlesForCategory;
    
    public MainAdapter(Context context) {
        this.context = context;
        makeQuery();
    }
    
    public MainAdapter(Context context, int categoryId) {
        this.context = context;
        this.categoryId = categoryId;
        makeQuery();
    }
    
    public MainAdapter(Context context, int feedId, int categoryId, boolean selectArticlesForCategory) {
        this.context = context;
        this.feedId = feedId;
        this.categoryId = categoryId;
        this.selectArticlesForCategory = selectArticlesForCategory;
        makeQuery();
    }
    
    public final void closeCursor() {
        if (cursor == null || cursor.isClosed())
            return;
        
        synchronized (poorMansMutex) {
            closeCursor(cursor);
        }
    }
    
    public final void closeCursor(Cursor c) {
        if (c == null || c.isClosed())
            return;
        
        if (c == null || c.isClosed())
            return;
        
        // Catch all SQLiteExceptions to make sure no "unable to close due to unfinalised statements" errors arise
        try {
            c.close();
        } catch (SQLiteException e) {
        }
    }
    
    @Override
    public final int getCount() {
        synchronized (poorMansMutex) {
            if (cursor.isClosed())
                makeQuery();
            
            return cursor.getCount();
        }
    }
    
    @Override
    public final long getItemId(int position) {
        return position;
    }
    
    public final int getId(int position) {
        int ret = 0;
        synchronized (poorMansMutex) {
            if (cursor.isClosed())
                makeQuery();
            
            if (cursor.getCount() >= position)
                if (cursor.moveToPosition(position))
                    ret = cursor.getInt(0);
        }
        return ret;
    }
    
    public final List<Integer> getIds() {
        List<Integer> result = new ArrayList<Integer>();
        synchronized (poorMansMutex) {
            if (cursor.isClosed())
                makeQuery();
            
            if (cursor.moveToFirst()) {
                while (!cursor.isAfterLast()) {
                    result.add(cursor.getInt(0));
                    cursor.move(1);
                }
            }
        }
        return result;
    }
    
    public final String getTitle(int position) {
        String ret = "";
        synchronized (poorMansMutex) {
            if (cursor.isClosed())
                makeQuery();
            
            if (cursor.getCount() >= position)
                if (cursor.moveToPosition(position))
                    ret = cursor.getString(1);
        }
        return ret;
    }
    
    public static final String formatTitle(String title, int unread) {
        if (unread > 0) {
            return title + " (" + unread + ")";
        } else {
            return title;
        }
    }
    
    /**
     * Discards the old cursor and fetches new data in the background.
     */
    public final void refreshQuery() {
        new Thread(new Runnable() {
            public void run() {
                makeQuery(true);
            }
        }).start();
    }
    
    /**
     * Creates a new query if necessary
     */
    public void makeQuery() {
        makeQuery(false);
    }
    
    /**
     * Creates a new query if necessary
     */
    public void makeQuery(boolean force) {
        makeQuery(force, false);
    }
    
    /**
     * Creates a new query if necessary or called with force = true.
     * 
     * @param force
     *            forces the creation of a new query
     */
    public void makeQuery(boolean force, boolean overrideUnreadCheck) {
        if (!force) {
            if (cursor != null && !cursor.isClosed())
                return;
        }
        
        synchronized (poorMansMutex) {
            
            // Check again to reduce the number of unnecessary new cursors
            if (!force) {
                if (cursor != null && !cursor.isClosed())
                    return;
            }
            
            try {
                tempCursor = executeQuery(false, false); // normal query
                
                // Only check for unread articles in normal feeds, published, starred, all, fresh often don't have
                // unread articles so dont check there.
                if (feedId >= 0 && !checkUnread(tempCursor)) {
                    Log.d(Utils.TAG, "Cursor did not contain unread articles...");
                    tempCursor = executeQuery(true, false); // Override unread if query was empty
                }
                
            } catch (Exception e) {
                Log.d(Utils.TAG, "Exception while creating cursor, trying to create a really fail-safe query...");
                tempCursor = executeQuery(false, true); // Fail-safe-query
            }
            
            // Try to almost atomically switch the old for the new cursor and close the old one afterwards
            if (tempCursor != null) {
                // Hold a reference
                Cursor oldCur = cursor;
                
                // Swap cursor
                cursor = tempCursor;
                handler.sendEmptyMessage(0);
                
                // Clean-up
                tempCursor = null;
                closeCursor(oldCur);
            }
        }
    }
    
    /**
     * Tries to find out if the given cursor points to a dataset with unread articles in it, returns true if it does.
     * 
     * @param c
     *            the cursor.
     * @return true if there are unread articles in the dataset, else false.
     */
    private final boolean checkUnread(Cursor c) {
        if (c == null || c.isClosed() || c.getCount() < 1)
            return false; // Check null, closed, empty
            
        int col = c.getColumnIndex("unread");
        if (col == -1 || !c.moveToFirst())
            return false; // Check column, move
            
        do {
            if (c.getInt(col) > 0) {
                c.moveToFirst(); // One unread article found, move to first entry
                return true;
            }
        } while (c.moveToNext());
        
        c.moveToFirst();
        return false;
    }
    
    @Override
    public abstract Object getItem(int position);
    
    @Override
    public abstract View getView(int position, View convertView, ViewGroup parent);
    
    /**
     * Builds the query for this adapter as a string and returns it to be invoked on a database object.
     * 
     * @param overrideDisplayUnread
     *            if true unread articles/feeds/anything won't be filtered as specified by the setting but will be
     *            included in the result.
     * @param buildSafeQuery
     *            indicates that the query should modified to also display unread content even though displayUnread is
     *            disabled, this is used to get a new query when the current query is empty.
     * @param forceRefresh
     *            this indicates that a refresh of the cursor should be forced.
     * @return a valid SQL-Query string for this adapter.
     */
    protected abstract Cursor executeQuery(boolean overrideDisplayUnread, boolean buildSafeQuery);
    
    /**
     * Notifies about changed data since only the original thread that created a view may do this.
     */
    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            notifyDataSetChanged();
        }
    };
}
