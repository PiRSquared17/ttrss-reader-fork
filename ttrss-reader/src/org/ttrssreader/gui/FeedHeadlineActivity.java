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

package org.ttrssreader.gui;

import org.ttrssreader.R;
import org.ttrssreader.controllers.Controller;
import org.ttrssreader.controllers.Data;
import org.ttrssreader.gui.dialogs.ArticleLabelDialog;
import org.ttrssreader.gui.dialogs.FeedUnsubscribeDialog;
import org.ttrssreader.gui.fragments.ArticleFragment;
import org.ttrssreader.gui.fragments.FeedHeadlineListFragment;
import org.ttrssreader.gui.fragments.MainListFragment;
import org.ttrssreader.gui.interfaces.TextInputAlertCallback;
import org.ttrssreader.model.pojos.Article;
import org.ttrssreader.model.updaters.PublishedStateUpdater;
import org.ttrssreader.model.updaters.ReadStateUpdater;
import org.ttrssreader.model.updaters.StarredStateUpdater;
import org.ttrssreader.model.updaters.Updater;
import org.ttrssreader.utils.AsyncTask;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.view.KeyEvent;
import android.widget.Toast;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;

public class FeedHeadlineActivity extends MenuActivity implements TextInputAlertCallback {
    
    public static final int FEED_NO_ID = 37846914;
    private static final String FRAGMENT = "HEADLINE_FRAGMENT";
    
    private int categoryId = Integer.MIN_VALUE;
    private boolean selectArticlesForCategory = false;
    
    private FeedHeadlineUpdater headlineUpdater = null;
    
    private static final String SELECTED = "SELECTED";
    private int selectedArticleId = Integer.MIN_VALUE;
    private static final String ARTICLE_FRAME = "ARTICLE_FRAME";
    private int articleFrame = -1;
    
    private FeedHeadlineListFragment headlineFragment;
    private ArticleFragment articleFragment;
    
    @Override
    protected void onCreate(Bundle instance) {
        super.onCreate(instance);
        setContentView(R.layout.feedheadlinelist);
        super.initTabletLayout();
        
        int feedId = Integer.MIN_VALUE;
        int articleId = Integer.MIN_VALUE;
        
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            categoryId = extras.getInt(FeedHeadlineListFragment.FEED_CAT_ID);
            feedId = extras.getInt(FeedHeadlineListFragment.FEED_ID);
            articleId = extras.getInt(FeedHeadlineListFragment.ARTICLE_ID);
            selectArticlesForCategory = extras.getBoolean(FeedHeadlineListFragment.FEED_SELECT_ARTICLES);
            selectedArticleId = extras.getInt(SELECTED, Integer.MIN_VALUE);
            articleFrame = extras.getInt(ARTICLE_FRAME, -1);
        } else if (instance != null) {
            categoryId = instance.getInt(FeedHeadlineListFragment.FEED_CAT_ID);
            feedId = instance.getInt(FeedHeadlineListFragment.FEED_ID);
            articleId = instance.getInt(FeedHeadlineListFragment.ARTICLE_ID);
            selectArticlesForCategory = instance.getBoolean(FeedHeadlineListFragment.FEED_SELECT_ARTICLES);
            selectedArticleId = instance.getInt(SELECTED, Integer.MIN_VALUE);
            articleFrame = instance.getInt(ARTICLE_FRAME, -1);
        }
        
        headlineFragment = (FeedHeadlineListFragment) getSupportFragmentManager().findFragmentByTag(FRAGMENT);
        if (headlineFragment == null) {
            headlineFragment = FeedHeadlineListFragment.newInstance(feedId, categoryId, selectArticlesForCategory,
                    articleId);
            
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            transaction.add(R.id.frame_left, headlineFragment, FRAGMENT);
            
            // Display article in right frame:
            if (Controller.isTablet) {
                int id = articleId;
                if (id == Integer.MIN_VALUE)
                    id = selectedArticleId;
                
                if (id != Integer.MIN_VALUE) {
                    selectedArticleId = id;
                    articleFragment = ArticleFragment.newInstance(id, feedId, categoryId, selectArticlesForCategory,
                            ArticleFragment.ARTICLE_MOVE_DEFAULT);
                    transaction.add(R.id.frame_right, articleFragment);
                }
            }
            
            transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
            transaction.commit();
        }
    }
    
    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putInt(FeedHeadlineListFragment.FEED_CAT_ID, categoryId);
        outState.putBoolean(FeedHeadlineListFragment.FEED_SELECT_ARTICLES, selectArticlesForCategory);
        outState.putInt(SELECTED, selectedArticleId);
        outState.putInt(ARTICLE_FRAME, articleFrame);
        super.onSaveInstanceState(outState);
    }
    
    @Override
    protected void onRestoreInstanceState(Bundle instance) {
        categoryId = instance.getInt(FeedHeadlineListFragment.FEED_CAT_ID);
        selectArticlesForCategory = instance.getBoolean(FeedHeadlineListFragment.FEED_SELECT_ARTICLES);
        selectedArticleId = instance.getInt(SELECTED, Integer.MIN_VALUE);
        articleFrame = instance.getInt(ARTICLE_FRAME, -1);
        super.onRestoreInstanceState(instance);
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        refreshAndUpdate();
    }
    
    @Override
    protected void doRefresh() {
        super.doRefresh();
        headlineFragment.doRefresh();
        refreshTitleAndUnread();
    }
    
    public void refreshTitleAndUnread() {
        if (!Controller.isTablet && articleFragment != null) {
            String title = headlineFragment.getTitle();
            title = title + " " + articleFragment.getTitle();
            setTitle(title);
            setUnread(0);
        } else {
            setTitle(headlineFragment.getTitle());
            setUnread(headlineFragment.getUnread());
        }
    }
    
    @Override
    protected void doUpdate(boolean forceUpdate) {
        // Only update if no headlineUpdater already running
        if (headlineUpdater != null) {
            if (headlineUpdater.getStatus().equals(AsyncTask.Status.FINISHED)) {
                headlineUpdater = null;
            } else {
                return;
            }
        }
        
        if (!isCacherRunning()) {
            headlineUpdater = new FeedHeadlineUpdater(forceUpdate);
            headlineUpdater.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (selectedArticleId != Integer.MIN_VALUE) {
            getSupportMenuInflater().inflate(R.menu.article, menu);
            if (Controller.isTablet)
                super.onCreateOptionsMenu(menu);
        } else {
            super.onCreateOptionsMenu(menu);
        }
        menu.removeItem(R.id.Menu_MarkAllRead);
        menu.removeItem(R.id.Menu_MarkFeedsRead);
        return true;
    }
    
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        boolean ret = super.onPrepareOptionsMenu(menu);
        if (selectedArticleId != Integer.MIN_VALUE)
            prepareArticleMenu(menu, getArticle());
        return ret;
    }
    
    public void prepareArticleMenu(final Menu menu, final Article article) {
        if (article != null) {
            MenuItem read = menu.findItem(R.id.Article_Menu_MarkRead);
            if (article.isUnread) {
                read.setTitle(getString(R.string.Commons_MarkRead));
                read.setIcon(R.drawable.ic_menu_mark);
            } else {
                read.setTitle(getString(R.string.Commons_MarkUnread));
                read.setIcon(R.drawable.ic_menu_clear_playlist);
            }
            
            MenuItem publish = menu.findItem(R.id.Article_Menu_MarkPublish);
            if (article.isPublished) {
                publish.setTitle(getString(R.string.Commons_MarkUnpublish));
                publish.setIcon(R.drawable.menu_published);
            } else {
                publish.setTitle(getString(R.string.Commons_MarkPublish));
                publish.setIcon(R.drawable.menu_publish);
            }
            
            MenuItem star = menu.findItem(R.id.Article_Menu_MarkStar);
            if (article.isStarred) {
                star.setTitle(getString(R.string.Commons_MarkUnstar));
                star.setIcon(R.drawable.menu_starred);
            } else {
                star.setTitle(getString(R.string.Commons_MarkStar));
                star.setIcon(R.drawable.ic_menu_star);
            }
        }
    }
    
    @Override
    public final boolean onOptionsItemSelected(final MenuItem item) {
        if (super.onOptionsItemSelected(item))
            return true;
        
        Article article = getArticle();
        switch (item.getItemId()) {
            case R.id.Menu_Refresh:
                doUpdate(true);
                return true;
            case R.id.Menu_MarkFeedRead:
                if (selectArticlesForCategory) {
                    new Updater(this, new ReadStateUpdater(categoryId)).exec();
                } else {
                    new Updater(this, new ReadStateUpdater(headlineFragment.getFeedId(), 42)).exec();
                }
                
                if (Controller.getInstance().goBackAfterMakeAllRead())
                    onBackPressed();
                
                return true;
            case R.id.Menu_FeedUnsubscribe:
                FeedUnsubscribeDialog.getInstance(this, headlineFragment.getFeedId()).show(getSupportFragmentManager(),
                        FeedUnsubscribeDialog.DIALOG_UNSUBSCRIBE);
            case R.id.Article_Menu_MarkRead:
                new Updater(this, new ReadStateUpdater(article, article.feedId, article.isUnread ? 0 : 1)).exec();
                return true;
            case R.id.Article_Menu_MarkStar:
                new Updater(this, new StarredStateUpdater(article, article.isStarred ? 0 : 1)).exec();
                return true;
            case R.id.Article_Menu_MarkPublish:
                new Updater(this, new PublishedStateUpdater(article, article.isPublished ? 0 : 1)).exec();
                return true;
            case R.id.Article_Menu_MarkPublishNote:
                new TextInputAlert(this, article).show(this);
                return true;
            case R.id.Article_Menu_AddArticleLabel:
                DialogFragment dialog = ArticleLabelDialog.newInstance(article.id);
                dialog.show(getSupportFragmentManager(), "Edit Labels");
                return true;
            case R.id.Article_Menu_ShareLink:
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("text/plain");
                i.putExtra(Intent.EXTRA_TEXT, article.url);
                i.putExtra(Intent.EXTRA_SUBJECT, article.title);
                startActivity(Intent.createChooser(i, (String) getText(R.string.ArticleActivity_ShareTitle)));
                return true;
            case R.id.Article_Menu_OpenInBrowser:
                Intent j = new Intent(Intent.ACTION_VIEW, Uri.parse(article.url));
                startActivity(Intent.createChooser(j, (String) getText(R.string.Commons_OpenInBrowser)));
                return true;
            default:
                return false;
        }
        
    }
    
    private Article getArticle() {
        Fragment fragment = getSupportFragmentManager().findFragmentById(articleFrame);
        if (fragment instanceof ArticleFragment)
            return ((ArticleFragment) fragment).getArticle();
        return null;
    }
    
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (Controller.getInstance().useVolumeKeys()) {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_N) {
                openNextFragment(-1);
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_B) {
                openNextFragment(1);
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }
    
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (Controller.getInstance().useVolumeKeys()) {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP
                    || keyCode == KeyEvent.KEYCODE_N || keyCode == KeyEvent.KEYCODE_B) {
                return true;
            }
        }
        return super.onKeyUp(keyCode, event);
    }
    
    private void openNextFragment(int direction) {
        if (selectedArticleId != Integer.MIN_VALUE) {
            // Open next article
            Fragment fragment = getSupportFragmentManager().findFragmentById(articleFrame);
            if (fragment instanceof ArticleFragment)
                ((ArticleFragment) fragment).openNextArticle(direction);
        } else {
            // Open next Feed
            headlineFragment.openNextFeed(direction);
        }
    }
    
    /**
     * Updates all articles from the selected feed.
     */
    public class FeedHeadlineUpdater extends ActivityUpdater {
        private static final int DEFAULT_TASK_COUNT = 2;
        
        public FeedHeadlineUpdater(boolean forceUpdate) {
            super(forceUpdate);
        }
        
        @Override
        protected Void doInBackground(Void... params) {
            taskCount = DEFAULT_TASK_COUNT;
            
            int progress = 0;
            boolean displayUnread = Controller.getInstance().onlyUnread();
            
            publishProgress(++progress); // Move progress forward
            if (selectArticlesForCategory) {
                Data.getInstance().updateArticles(categoryId, displayUnread, true, false, forceUpdate);
            } else {
                Data.getInstance().updateArticles(headlineFragment.getFeedId(), displayUnread, false, false,
                        forceUpdate);
            }
            publishProgress(taskCount); // Move progress forward to 100%
            return null;
        }
    }
    
    @Override
    public void itemSelected(MainListFragment source, int selectedIndex, int selectedId) {
        switch (source.getType()) {
            case FEEDHEADLINE:
                //
                displayArticle(selectedId);
                break;
            default:
                Toast.makeText(this, "Invalid request!", Toast.LENGTH_SHORT).show();
                break;
        }
    }
    
    private void displayArticle(int articleId) {
        selectedArticleId = articleId;
        
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        articleFrame = R.id.frame_right;
        if (!Controller.isTablet) {
            articleFrame = R.id.frame_left;
            ft.addToBackStack(null);
        }
        
        ft.replace(articleFrame, ArticleFragment.newInstance(articleId, headlineFragment.getFeedId(), categoryId,
                selectArticlesForCategory, ArticleFragment.ARTICLE_MOVE_DEFAULT));
        ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
        ft.commit();
    }
    
    @Override
    public void onPublishNoteResult(Article a, String note) {
        new Updater(this, new PublishedStateUpdater(a, a.isPublished ? 0 : 1, note)).exec();
    }
    
    @Override
    public void onBackPressed() {
        selectedArticleId = Integer.MIN_VALUE;
        articleFrame = -1;
        super.onBackPressed();
    }
    
}
