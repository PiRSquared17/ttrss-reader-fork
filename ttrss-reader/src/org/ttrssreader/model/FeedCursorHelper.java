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

import org.ttrssreader.controllers.Controller;
import org.ttrssreader.controllers.DBHelper;
import org.ttrssreader.utils.Utils;
import android.content.Context;
import android.database.Cursor;

public class FeedCursorHelper extends MainCursorHelper {
    
    public FeedCursorHelper(Context context, int categoryId) {
        super(context);
        this.categoryId = categoryId;
    }
    
    @Override
    public Cursor createCursor(boolean overrideDisplayUnread, boolean buildSafeQuery) {
        
        StringBuilder query = new StringBuilder();
        
        String lastOpenedFeedsList = Utils.separateItems(Controller.getInstance().lastOpenedFeeds, ",");
        
        boolean displayUnread = Controller.getInstance().onlyUnread();
        boolean invertSortFeedCats = Controller.getInstance().invertSortFeedscats();
        
        if (overrideDisplayUnread)
            displayUnread = false;
        
        if (lastOpenedFeedsList.length() > 0 && !buildSafeQuery) {
            query.append("SELECT _id,title,unread FROM (");
        }
        
        query.append("SELECT _id,title,unread FROM ");
        query.append(DBHelper.TABLE_FEEDS);
        query.append(" WHERE categoryId=");
        query.append(categoryId);
        query.append(displayUnread ? " AND unread>0" : "");
        
        if (lastOpenedFeedsList.length() > 0 && !buildSafeQuery) {
            query.append(" UNION SELECT _id,title,unread");
            query.append(" FROM feeds WHERE _id IN (");
            query.append(lastOpenedFeedsList);
            query.append(" ))");
        }
        
        query.append(" ORDER BY UPPER(title) ");
        query.append(invertSortFeedCats ? "DESC" : "ASC");
        query.append(buildSafeQuery ? " LIMIT 200" : " LIMIT 600");
        
        return DBHelper.getInstance().query(query.toString(), null);
    }
}
