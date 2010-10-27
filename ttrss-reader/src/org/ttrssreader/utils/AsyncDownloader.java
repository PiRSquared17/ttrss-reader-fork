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

package org.ttrssreader.utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;

public class AsyncDownloader extends AsyncTask<URL, Void, String> {
    
    Context context;
    
    public AsyncDownloader(Context context) {
        this.context = context;
    }
    
    protected String doInBackground(URL... urls) {
        if (urls.length > 0) {
            return downloadFile(urls[0]);
        } else {
            return null;
        }
    }
    
    private String downloadFile(URL url) {
        try {
            // Build name as "download_123801230712", then try to extract a proper name from URL
            String name = "download_" + System.currentTimeMillis();
            if (!url.getFile().equals("")) {
                int pos = url.getFile().lastIndexOf("/");
                if (pos > 0) name = url.getFile().substring(pos+1);
            }
            
            // Path: /sdcard/Android/data/org.ttrssreader/files/
            StringBuilder sb = new StringBuilder();
            sb.append(Environment.getExternalStorageDirectory()).append(File.separator).append(Utils.SDCARD_PATH)
                    .append(File.separator).append(name);
            File file = new File(sb.toString());
            
            HttpURLConnection c = (HttpURLConnection) url.openConnection();
            c.setRequestMethod("GET");
            c.setDoOutput(true);
            c.connect();
            
            FileOutputStream f = new FileOutputStream(file);
            InputStream in = c.getInputStream();
            
            byte[] buffer = new byte[1024];
            int len1 = 0;
            while ((len1 = in.read(buffer)) != -1) {
                f.write(buffer, 0, len1);
            }
            f.close();
            
            return file.getAbsolutePath();
            
        } catch (IOException e) {
            Log.d(Utils.TAG, "Error while downloading: " + e);
        }
        
        return "";
    }
    
}
