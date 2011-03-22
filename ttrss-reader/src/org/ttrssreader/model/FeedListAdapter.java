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

package org.ttrssreader.model;

import org.ttrssreader.R;
import org.ttrssreader.controllers.DBHelper;
import org.ttrssreader.controllers.Data;
import org.ttrssreader.model.pojos.FeedItem;
import android.content.Context;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class FeedListAdapter extends MainAdapter {
    
    public FeedListAdapter(Context context, int categoryId) {
        super(context);
        this.categoryId = categoryId;
    }
    
    @Override
    public Object getItem(int position) {
        if (cursor.isClosed()) {
            return null;
        }
        
        FeedItem ret = null;
        if (cursor.getCount() >= position) {
            if (cursor.moveToPosition(position)) {
                ret = new FeedItem();
                ret.id = cursor.getInt(0);
                ret.title = cursor.getString(1);
                ret.unread = cursor.getInt(2);
            }
        }
        return ret;
    }
    
    private int getImage(boolean unread) {
        if (unread) {
            return R.drawable.feedheadlinesunread48;
        } else {
            return R.drawable.feedheadlinesread48;
        }
    }
    
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (position >= getCount() || position < 0)
            return new View(context);
        
        FeedItem f = (FeedItem) getItem(position);
        
        final LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        LinearLayout layout = null;
        if (convertView == null) {
            layout = (LinearLayout) inflater.inflate(R.layout.feeditem, null);
        } else {
            if (convertView instanceof LinearLayout) {
                layout = (LinearLayout) convertView;
            }
        }
        
        ImageView icon = (ImageView) layout.findViewById(R.id.icon);
        icon.setImageResource(getImage(f.unread > 0));
        
        TextView title = (TextView) layout.findViewById(R.id.title);
        title.setText(super.formatTitle(f.title, f.unread));
        if (f.unread > 0) {
            title.setTypeface(Typeface.DEFAULT_BOLD, 1);
        } else {
            title.setTypeface(Typeface.DEFAULT, 0);
        }
        
        return layout;
    }
    
    protected String buildQuery() {
        StringBuilder query = new StringBuilder();
        
        query.append("SELECT id,title,unread FROM ");
        query.append(DBHelper.TABLE_FEEDS);
        query.append(" WHERE categoryId=");
        query.append(categoryId);
        
        if (displayOnlyUnread) {
            query.append(" AND unread>0");
        }
        
        query.append(" ORDER BY UPPER(title) ");
        
        if (invertSortFeedCats) {
            query.append("DESC");
        } else {
            query.append("ASC");
        }
        
        return query.toString();
    }
    
    @Override
    public void update() {
        Data.getInstance().updateFeeds(categoryId, false);
        unreadCount = DBHelper.getInstance().getUnreadCount(categoryId, true);
    }
    
}
