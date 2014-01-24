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

package org.ttrssreader.gui.dialogs;

import org.ttrssreader.R;
import org.ttrssreader.model.updaters.IUpdatable;
import org.ttrssreader.model.updaters.Updater;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;

public class YesNoUpdaterDialog extends DialogFragment {
    
    public static final String DIALOG = "yesnodialog";
    
    private Activity parent;
    private IUpdatable updater;
    private int titleRes;
    private int msgRes;
    private boolean backAfterUpdate;
    
    public static YesNoUpdaterDialog getInstance(Activity parent, IUpdatable updater, int titleRes, int msgRes) {
        return getInstance(parent, updater, titleRes, msgRes, false);
    }
    
    public static YesNoUpdaterDialog getInstance(Activity parent, IUpdatable updater, int titleRes, int msgRes, boolean backAfterUpdate) {
        YesNoUpdaterDialog fragment = new YesNoUpdaterDialog();
        fragment.parent = parent;
        fragment.updater = updater;
        fragment.titleRes = titleRes;
        fragment.msgRes = msgRes;
        fragment.backAfterUpdate = backAfterUpdate;
        return fragment;
    }
    
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setIcon(android.R.drawable.ic_dialog_info);
        
        builder.setTitle(getResources().getString(titleRes));
        builder.setMessage(getResources().getString(msgRes));
        builder.setPositiveButton(getResources().getString(R.string.Yes), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(final DialogInterface d, final int which) {
                new Updater(parent, updater, backAfterUpdate).exec();
                d.dismiss();
            }
        });
        builder.setNegativeButton(getResources().getString(R.string.No), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(final DialogInterface d, final int which) {
                d.dismiss();
            }
        });
        
        return builder.create();
    }
    
}
