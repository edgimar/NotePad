/*
 * Copyright (c) 2015 Jonas Kalderstam.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.nononsenseapps.notepad;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ListView;
import android.widget.Toast;

import com.nononsenseapps.helpers.ActivityHelper;
import com.nononsenseapps.helpers.NotificationHelper;
import com.nononsenseapps.notepad.database.LegacyDBHelper;
import com.nononsenseapps.notepad.database.LegacyDBHelper.NotePad;
import com.nononsenseapps.notepad.database.Notification;
import com.nononsenseapps.notepad.database.Task;
import com.nononsenseapps.notepad.database.TaskList;
import com.nononsenseapps.notepad.fragments.DialogConfirmBase;
import com.nononsenseapps.notepad.fragments.DialogEditList.EditListDialogListener;
import com.nononsenseapps.notepad.fragments.DialogEditList_;
import com.nononsenseapps.notepad.fragments.TaskDetailFragment;
import com.nononsenseapps.notepad.fragments.TaskListFragment;
import com.nononsenseapps.notepad.fragments.TaskListViewPagerFragment;
import com.nononsenseapps.notepad.interfaces.MenuStateController;
import com.nononsenseapps.notepad.legacy.DonateMigrator;
import com.nononsenseapps.notepad.legacy.DonateMigrator_;
import com.nononsenseapps.notepad.prefs.MainPrefs;
import com.nononsenseapps.notepad.prefs.PrefsActivity;
import com.nononsenseapps.notepad.sync.orgsync.BackgroundSyncScheduler;
import com.nononsenseapps.notepad.sync.orgsync.OrgSyncService;
import com.nononsenseapps.ui.ExtraTypesCursorAdapter;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.InstanceState;
import org.androidannotations.annotations.SystemService;
import org.androidannotations.annotations.UiThread;
import org.androidannotations.annotations.UiThread.Propagation;
import org.androidannotations.annotations.ViewById;

import java.util.ArrayList;
import java.util.List;

@EActivity(resName = "activity_main")
public class ActivityMain extends AppCompatActivity implements TaskListFragment
        .TaskListCallbacks, MenuStateController, OnSharedPreferenceChangeListener,
        TaskDetailFragment.TaskEditorCallbacks {

    // Intent notification argument
    public static final String NOTIFICATION_CANCEL_ARG = "notification_cancel_arg";
    public static final String NOTIFICATION_DELETE_ARG = "notification_delete_arg";
    // If donate version has been migrated
    public static final String MIGRATED = "donate_inapp_or_oldversion";
    // Set to true in bundle if exits should be animated
    public static final String ANIMATEEXIT = "animateexit";
    // Using tags for test
    public static final String DETAILTAG = "detailfragment";
    public static final String LISTPAGERTAG = "listpagerfragment";
    protected boolean reverseAnimation = false;
    @ViewById(resName = "leftDrawer")
    ListView leftDrawer;

    DrawerLayout mDrawerLayout;
    // Only present on tablets
    @ViewById(resName = "fragment2")
    View fragment2;
    // Shown on tablets on start up. Hide on selection
    @ViewById(resName = "taskHint")
    View taskHint;
    @SystemService
    LayoutInflater layoutInflater;
    @SystemService
    InputMethodManager inputManager;
    // private MenuItem mSyncMenuItem;
    boolean mAnimateExit = false;
    // Changes depending on what we're showing since the started activity can
    // receive new intents
    @InstanceState
    boolean showingEditor = false;
    boolean isDrawerClosed = true;
    // WIll only be the viewpager fragment
    ListOpener listOpener = null;
    private ActionBarDrawerToggle mDrawerToggle;
    // Only not if opening note directly
    private boolean shouldAddToBackStack = true;
    private Bundle state;
    private boolean shouldRestart = false;

    private void setupNavDrawer() {
        // Show icon
        ActionBar ab = getSupportActionBar();
        if (ab != null) {
            ab.setDisplayHomeAsUpEnabled(true);
            ab.setHomeButtonEnabled(true);
        }

        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (mDrawerLayout == null) {
            return;
        }

        /*if (selfItem == NAVDRAWER_ITEM_INVALID) {
            // do not show a nav drawer
            View navDrawer = mDrawerLayout.findViewById(R.id.navdrawer);
            if (navDrawer != null) {
                ((ViewGroup) navDrawer.getParent()).removeView(navDrawer);
            }
            mDrawerLayout = null;
            return;
        }*/

        mDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout, R.string
                .navigation_drawer_open, R.string.navigation_drawer_close) {
            @Override
            public void onDrawerSlide(View drawerView, float slideOffset) {
                super.onDrawerSlide(drawerView, slideOffset);
            }

            @Override
            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);
                isDrawerClosed = false;
                //onNavDrawerStateChanged(true, false);
            }

            @Override
            public void onDrawerClosed(View drawerView) {
                super.onDrawerClosed(drawerView);
                isDrawerClosed = true;
                // creates call to onPrepareOptionsMenu()
                invalidateOptionsMenu();
                //onNavDrawerStateChanged(false, false);
            }

            @Override
            public void onDrawerStateChanged(int newState) {
                super.onDrawerStateChanged(newState);
                /*onNavDrawerStateChanged(isNavDrawerOpen(), newState != DrawerLayout
                        .STATE_IDLE);*/

                if (DrawerLayout.STATE_IDLE != newState) {
                    getActionBar().setTitle(R.string.show_from_all_lists);
                    // Is in motion, hide action items
                    isDrawerClosed = false;
                    invalidateOptionsMenu(); // creates call to
                    // onPrepareOptionsMenu()
                }
            }
        };

        mDrawerToggle.syncState();

        populateNavDrawer();

        // Recycler view stuff
        /*RecyclerView mRecyclerView = (RecyclerView) mDrawerLayout.findViewById(R.id
        .navdrawer_list);
        mRecyclerView.setHasFixedSize(true);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        mNavAdapter = new FeedsAdapter();
        mRecyclerView.setAdapter(mNavAdapter);

        populateNavDrawer();*/
    }

    protected boolean isNavDrawerOpen() {
        return mDrawerLayout != null && mDrawerLayout.isDrawerOpen(GravityCompat.START);
    }

    // Subclasses can override to decide what happens on nav item selection
    protected void onNavigationDrawerItemSelected(long id, String title, String url, String tag) {
        // TODO add default start activity with arguments
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.activity_main, menu);

        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.setGroupVisible(R.id.activity_menu_group, isDrawerClosed);
        menu.setGroupVisible(R.id.activity_reverse_menu_group, !isDrawerClosed);

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Pass the event to ActionBarDrawerToggle, if it returns
        // true, then it has handled the app icon touch event
        if (mDrawerToggle.onOptionsItemSelected(item)) {
            return true;
        }
        // Handle your other action bar items...
        int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            if (showingEditor) {
                // Only true in portrait mode
                final View focusView = ActivityMain.this.getCurrentFocus();
                if (inputManager != null && focusView != null) {
                    inputManager.hideSoftInputFromWindow(focusView.getWindowToken(),
                            InputMethodManager.HIDE_NOT_ALWAYS);
                }

                // Should load the same list again
                // Try getting the list from the original intent
                final long listId = getListId(getIntent());

                final Intent intent = new Intent().setAction(Intent.ACTION_VIEW).setClass
                        (ActivityMain.this, ActivityMain_.class);
                if (listId > 0) {
                    intent.setData(TaskList.getUri(listId));
                }

                // Set the intent before, so we set the correct
                // action bar
                setIntent(intent);
                while (getSupportFragmentManager().popBackStackImmediate()) {
                    // Need to pop the entire stack and then load
                }

                reverseAnimation = true;
                Log.d("nononsenseapps fragment", "starting activity");

                intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            }
            // else
            // Handled by drawer
            return true;
        } else if (itemId == R.id.drawer_menu_createlist) {
            // Show fragment
            DialogEditList_ dialog = DialogEditList_.getInstance();
            dialog.setListener(new EditListDialogListener() {

                @Override
                public void onFinishEditDialog(long id) {
                    openList(id);
                }
            });
            dialog.show(getSupportFragmentManager(), "fragment_create_list");
            return true;
        } else if (itemId == R.id.menu_preferences) {
            Intent intent = new Intent();
            intent.setClass(this, PrefsActivity.class);
            startActivity(intent);
            return true;
        } else if (itemId == R.id.menu_delete) {
            return false;
        } else {
            return false;
        }
    }

    @Override
    public void finish() {
        super.finish();
        // Only animate when specified. Should be when it was animated "in"
        if (mAnimateExit) {
            overridePendingTransition(R.anim.activity_slide_in_right, R.anim
                    .activity_slide_out_right_full);
        }
    }

    /**
     * Returns a list id from an intent if it contains one, either as part of
     * its URI or as an extra
     * <p/>
     * Returns -1 if no id was contained, this includes insert actions
     */
    long getListId(final Intent intent) {
        long retval = -1;
        if (intent != null &&
                intent.getData() != null &&
                (Intent.ACTION_EDIT.equals(intent.getAction()) ||
                        Intent.ACTION_VIEW.equals(intent.getAction()) ||
                        Intent.ACTION_INSERT.equals(intent.getAction()))) {
            if ((intent.getData().getPath().startsWith(NotePad.Lists.PATH_VISIBLE_LISTS) ||
                    intent.getData().getPath().startsWith(NotePad.Lists.PATH_LISTS) ||
                    intent.getData().getPath().startsWith(TaskList.URI.getPath()))) {
                try {
                    retval = Long.parseLong(intent.getData().getLastPathSegment());
                } catch (NumberFormatException e) {
                    retval = -1;
                }
            } else if (-1 != intent.getLongExtra(LegacyDBHelper.NotePad.Notes.COLUMN_NAME_LIST,
                    -1)) {
                retval = intent.getLongExtra(LegacyDBHelper.NotePad.Notes.COLUMN_NAME_LIST, -1);
            } else if (-1 != intent.getLongExtra(TaskDetailFragment.ARG_ITEM_LIST_ID, -1)) {
                retval = intent.getLongExtra(TaskDetailFragment.ARG_ITEM_LIST_ID, -1);
            } else if (-1 != intent.getLongExtra(Task.Columns.DBLIST, -1)) {
                retval = intent.getLongExtra(Task.Columns.DBLIST, -1);
            }
        }
        return retval;
    }

    /**
     * Opens the specified list and closes the left drawer
     */
    void openList(final long id) {
        // Open list
        Intent i = new Intent(ActivityMain.this, ActivityMain_.class);
        i.setAction(Intent.ACTION_VIEW).setData(TaskList.getUri(id)).addFlags(Intent
                .FLAG_ACTIVITY_SINGLE_TOP);

        // If editor is on screen, we need to reload fragments
        if (listOpener == null) {
            while (getSupportFragmentManager().popBackStackImmediate()) {
                // Need to pop the entire stack and then load
            }
            reverseAnimation = true;
            startActivity(i);
        } else {
            // If not popped, then send the call to the fragment
            // directly
            Log.d("nononsenseapps list", "calling listOpener");
            listOpener.openList(id);
        }

        // And then close drawer
        if (mDrawerLayout != null && leftDrawer != null) {
            mDrawerLayout.closeDrawer(leftDrawer);
        }
    }

    @Override
    public void onCreate(Bundle b) {
        // Must do this before super.onCreate
        ActivityHelper.readAndSetSettings(this);
        super.onCreate(b);

        // First load, then don't add to backstack
        shouldAddToBackStack = false;

        // To know if we should animate exits
        if (getIntent() != null && getIntent().getBooleanExtra(ANIMATEEXIT, false)) {
            mAnimateExit = true;
        }

        // If user has donated some other time
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        // To listen on fragment changes
        // TODO probably remove this?
        getSupportFragmentManager().addOnBackStackChangedListener(new FragmentManager
                .OnBackStackChangedListener() {
            public void onBackStackChanged() {
                if (showingEditor && !isNoteIntent(getIntent())) {
                    setHomeAsDrawer(true);
                }
                // Always update menu
                invalidateOptionsMenu();
            }
        });

        if (b != null) {
            Log.d("nononsenseapps list", "Activity Saved not null: " + b);
            this.state = b;
        }

        // Clear possible notifications, schedule future ones
        final Intent intent = getIntent();
        // Clear notification if present
        clearNotification(intent);
        // Schedule notifications
        NotificationHelper.schedule(this);
        // Schedule syncs
        BackgroundSyncScheduler.scheduleSync(this);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        setupNavDrawer();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (mDrawerToggle != null) {
            mDrawerToggle.onConfigurationChanged(newConfig);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        OrgSyncService.stop(this);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if ((keyCode == KeyEvent.KEYCODE_BACK)) {
            // Reset intent so we get proper fragment handling when the stack
            // pops
            if (getSupportFragmentManager().getBackStackEntryCount() <= 1) {
                setIntent(new Intent(this, ActivityMain_.class));
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onPause() {
        super.onPause();
        // Pause sync monitors
        OrgSyncService.pause(this);
    }

    @Override
    public void onNewIntent(Intent intent) {
        setIntent(intent);
        loadFragments();
        // Just to be sure it gets done
        // Clear notification if present
        clearNotification(intent);
    }

    @Override
    public void onResume() {
        if (shouldRestart) {
            restartAndRefresh();
        }
        super.onResume();

        // Sync if appropriate
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        // Do absolutely NOT call super class here. Will bug out the viewpager!
        super.onSaveInstanceState(outState);
    }

    private void restartAndRefresh() {
        shouldRestart = false;
        Intent intent = getIntent();
        overridePendingTransition(0, 0);
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        finish();
        overridePendingTransition(0, 0);
        startActivity(intent);
    }

    void isOldDonateVersionInstalled() {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences
                (ActivityMain.this);
        if (prefs.getBoolean(MIGRATED, false)) {
            // already migrated
            return;
        }
        try {
            PackageManager pm = getPackageManager();
            List<ApplicationInfo> packages = pm.getInstalledApplications(0);
            for (ApplicationInfo packageInfo : packages) {
                if (packageInfo.packageName.equals("com.nononsenseapps.notepad_donate")) {
                    migrateDonateUser();
                    // Don't migrate again
                    prefs.edit().putBoolean(MIGRATED, true).commit();
                    // Stop loop
                    break;
                }
            }
        } catch (Exception e) {
            // Can't allow crashing
        }
    }

    @SuppressLint("ValidFragment")
    @UiThread
    void migrateDonateUser() {
        // migrate user
        if (!DonateMigrator.hasImported(this)) {
            final DialogConfirmBase dialog = new DialogConfirmBase() {

                @Override
                public int getTitle() {
                    return R.string.import_data_question;
                }

                @Override
                public int getMessage() {
                    return R.string.import_data_msg;
                }

                @Override
                public void onOKClick() {
                    startService(new Intent(ActivityMain.this, DonateMigrator_.class));
                }
            };
            dialog.show(getSupportFragmentManager(), "migrate_question");
        }
    }

    @UiThread(propagation = Propagation.REUSE)
    void loadFragments() {
        final Intent intent = getIntent();

        // Mandatory
        Fragment left = null;
        String leftTag = null;
        // Only if fragment2 is not null
        Fragment right = null;

        if (this.state != null) {
            this.state = null;
            if (showingEditor && fragment2 != null) {
                // Should only be true in portrait
                showingEditor = false;
            }

            // Find fragments
            // This is an instance state variable
            if (showingEditor) {
                // Portrait, with editor, modify action bar
                setHomeAsDrawer(false);
                // Done
                return;
            } else {
                // Find the listpager
                left = getSupportFragmentManager().findFragmentByTag(LISTPAGERTAG);
                listOpener = (ListOpener) left;

                if (left != null && fragment2 == null) {
                    // Done
                    return;
                } else if (left != null && fragment2 != null) {
                    right = getSupportFragmentManager().findFragmentByTag(DETAILTAG);
                }

                if (left != null && right != null) {
                    // Done
                    return;
                }
            }
        }

        // Load stuff
        final FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        if (reverseAnimation) {
            reverseAnimation = false;
            transaction.setCustomAnimations(R.anim.slide_in_bottom, R.anim.slide_out_top, R.anim
                    .slide_in_top, R.anim.slide_out_bottom);
        } else {
            transaction.setCustomAnimations(R.anim.slide_in_top, R.anim.slide_out_bottom, R.anim
                    .slide_in_bottom, R.anim.slide_out_top);
        }

		/*
         * If it contains a noteId, load an editor. If also tablet, load the
		 * lists.
		 */
        /*if (fragment2 != null) {
            if (right == null) {
                if (getNoteId(intent) > 0) {
                    right = TaskDetailFragment_.getInstance(getNoteId(intent));
                } else if (isNoteIntent(intent)) {
                    right = TaskDetailFragment_.getInstance(getNoteShareText(intent),
                            TaskListViewPagerFragment.getAShowList(this, getListId(intent)));
                }
            }
        } else if (isNoteIntent(intent)) {
            showingEditor = true;
            listOpener = null;
            leftTag = DETAILTAG;
            if (getNoteId(intent) > 0) {
                left = TaskDetailFragment_.getInstance(getNoteId(intent));
            } else {
                // Get a share text (null safe)
                // In a list (if specified, or default otherwise)
                left = TaskDetailFragment_.getInstance(getNoteShareText(intent),
                        TaskListViewPagerFragment.getARealList(this, getListId(intent)));
            }
            // fucking stack
            while (getSupportFragmentManager().popBackStackImmediate()) {
                // Need to pop the entire stack and then load
            }
            if (shouldAddToBackStack) {
                transaction.addToBackStack(null);
            }

            setHomeAsDrawer(false);
        }*/
        /*
         * Other case, is a list id or a tablet
		 */
        if (!isNoteIntent(intent) || fragment2 != null) {
            // If we're no longer in the editor, reset the action bar
            if (fragment2 == null) {
                setHomeAsDrawer(true);
            }
            // TODO
            showingEditor = false;

            left = TaskListViewPagerFragment.getInstance(getListIdToShow(intent));
            leftTag = LISTPAGERTAG;
            listOpener = (ListOpener) left;
        }

        if (fragment2 != null && right != null) {
            transaction.replace(R.id.fragment2, right, DETAILTAG);
            taskHint.setVisibility(View.GONE);
        }
        transaction.replace(R.id.fragment1, left, leftTag);

        // Commit transaction
        // Allow state loss as workaround for bug
        // https://code.google.com/p/android/issues/detail?id=19917
        transaction.commitAllowingStateLoss();
        // Next go, always add
        shouldAddToBackStack = true;
    }

    /**
     * Returns a note id from an intent if it contains one, either as part of
     * its URI or as an extra
     * <p/>
     * Returns -1 if no id was contained, this includes insert actions
     */
    long getNoteId(final Intent intent) {
        long retval = -1;
        if (intent != null &&
                intent.getData() != null &&
                (Intent.ACTION_EDIT.equals(intent.getAction()) || Intent.ACTION_VIEW.equals
                        (intent.getAction()))) {
            if (intent.getData().getPath().startsWith(TaskList.URI.getPath())) {
                // Find it in the extras. See DashClock extension for an example
                retval = intent.getLongExtra(Task.TABLE_NAME, -1);
            } else if ((intent.getData().getPath().startsWith(LegacyDBHelper.NotePad.Notes
                    .PATH_VISIBLE_NOTES) ||
                    intent.getData().getPath().startsWith(LegacyDBHelper.NotePad.Notes
                            .PATH_NOTES) ||
                    intent.getData().getPath().startsWith(Task.URI.getPath()))) {
                retval = Long.parseLong(intent.getData().getLastPathSegment());
            }
            // else if (null != intent
            // .getStringExtra(TaskDetailFragment.ARG_ITEM_ID)) {
            // retval = Long.parseLong(intent
            // .getStringExtra(TaskDetailFragment.ARG_ITEM_ID));
            // }
        }
        return retval;
    }

    /**
     * Returns the text that has been shared with the app. Does not check
     * anything other than EXTRA_SUBJECT AND EXTRA_TEXT
     * <p/>
     * If it is a Google Now intent, will ignore the subject which is
     * "Note to self"
     */
    String getNoteShareText(final Intent intent) {
        if (intent == null || intent.getExtras() == null) {
            return "";
        }

        StringBuilder retval = new StringBuilder();
        // possible title
        if (intent.getExtras().containsKey(Intent.EXTRA_SUBJECT) && !("com.google.android.gm" + "" +
                ".action.AUTO_SEND").equals(intent.getAction())) {
            retval.append(intent.getExtras().get(Intent.EXTRA_SUBJECT));
        }
        // possible note
        if (intent.getExtras().containsKey(Intent.EXTRA_TEXT)) {
            if (retval.length() > 0) {
                retval.append("\n");
            }
            retval.append(intent.getExtras().get(Intent.EXTRA_TEXT));
        }
        return retval.toString();
    }

    /**
     * If intent contains a list_id, returns that. Else, checks preferences for
     * default list setting. Else, -1.
     */
    long getListIdToShow(final Intent intent) {
        long result = getListId(intent);
        return TaskListViewPagerFragment.getAShowList(this, result);
    }

    /**
     * Returns true the intent URI targets a note. Either an edit/view or
     * insert.
     */
    boolean isNoteIntent(final Intent intent) {
        if (intent == null) {
            return false;
        }
        if (Intent.ACTION_SEND.equals(intent.getAction()) || ("com.google.android.gm.action" + "" +
                ".AUTO_SEND").equals(intent.getAction())) {
            return true;
        }

        return intent.getData() != null &&
                (Intent.ACTION_EDIT.equals(intent.getAction()) ||
                        Intent.ACTION_VIEW.equals(intent.getAction()) ||
                        Intent.ACTION_INSERT.equals(intent.getAction())) &&
                (intent.getData().getPath().startsWith(NotePad.Notes.PATH_VISIBLE_NOTES) ||
                        intent.getData().getPath().startsWith(NotePad.Notes.PATH_NOTES) ||
                        intent.getData().getPath().startsWith(Task.URI.getPath())) &&
                !intent.getData().getPath().startsWith(TaskList.URI.getPath());

    }

    void setHomeAsDrawer(final boolean value) {
        mDrawerToggle.setDrawerIndicatorEnabled(value);
    }

    private void clearNotification(final Intent intent) {
        if (intent != null && intent.getLongExtra(NOTIFICATION_DELETE_ARG, -1) > 0) {
            Notification.deleteOrReschedule(this, Notification.getUri(intent.getLongExtra
                    (NOTIFICATION_DELETE_ARG, -1)));
        }
        if (intent != null && intent.getLongExtra(NOTIFICATION_CANCEL_ARG, -1) > 0) {
            NotificationHelper.cancelNotification(this, (int) intent.getLongExtra
                    (NOTIFICATION_CANCEL_ARG, -1));
        }

    }

    /**
     * Loads the appropriate fragments depending on state and intent.
     */
    @AfterViews
    protected void loadContent() {
        loadFragments();
    }

    /**
     * Load a list of lists in the left
     */
    protected void populateNavDrawer() {
        // Use extra items for All Lists
        final int[] extraIds = new int[]{-1, TaskListFragment.LIST_ID_OVERDUE, TaskListFragment
                .LIST_ID_TODAY, TaskListFragment.LIST_ID_WEEK, -1};
        // This is fine for initial conditions
        final int[] extraStrings = new int[]{R.string.tasks, R.string.date_header_overdue, R
                .string.date_header_today, R.string.next_5_days, R.string.lists};
        // Use this for real data
        final ArrayList<ArrayList<Object>> extraData = new ArrayList<ArrayList<Object>>();
        // Task header
        extraData.add(new ArrayList<Object>());
        extraData.get(0).add(R.string.tasks);
        // Overdue
        extraData.add(new ArrayList<Object>());
        extraData.get(1).add(R.string.date_header_overdue);
        // Today
        extraData.add(new ArrayList<Object>());
        extraData.get(2).add(R.string.date_header_today);
        // Week
        extraData.add(new ArrayList<Object>());
        extraData.get(3).add(R.string.next_5_days);
        // Lists header
        extraData.add(new ArrayList<Object>());
        extraData.get(4).add(R.string.lists);

        final int[] extraTypes = new int[]{1, 0, 0, 0, 1};

        final ExtraTypesCursorAdapter adapter = new ExtraTypesCursorAdapter(this, R.layout
                .simple_light_list_item_2, null, new String[]{TaskList.Columns.TITLE, TaskList
                .Columns.VIEW_COUNT}, new int[]{android.R.id.text1, android.R.id.text2},
                // id -1 for headers, ignore clicks on them
                extraIds, extraStrings, extraTypes, new int[]{R.layout.drawer_header});
        adapter.setExtraData(extraData);

        // Load count of tasks in each one
        Log.d("nononsenseapps drawer", TaskList.CREATE_COUNT_VIEW);

        // Adapter for list titles and ids
        // final SimpleCursorAdapter adapter = new SimpleCursorAdapter(this,
        // R.layout.simple_light_list_item_1, null,
        // new String[] { TaskList.Columns.TITLE },
        // new int[] { android.R.id.text1 }, 0);
        leftDrawer.setAdapter(adapter);
        // Todo update what happens on click here
        // Set click handler
        leftDrawer.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> arg0, View v, int pos, long id) {
                if (id < -1) {
                    // Set preference which type was chosen
                    PreferenceManager.getDefaultSharedPreferences(ActivityMain.this).edit()
                            .putLong(TaskListFragment.LIST_ALL_ID_PREF_KEY, id).commit();
                }
                openList(id);
            }
        });
        leftDrawer.setOnItemLongClickListener(new OnItemLongClickListener() {

            @Override
            public boolean onItemLongClick(AdapterView<?> arg0, View arg1, int pos, long id) {
                // Open dialog to edit list
                if (id > 0) {
                    DialogEditList_ dialog = DialogEditList_.getInstance(id);
                    dialog.show(getSupportFragmentManager(), "fragment_edit_list");
                    return true;
                } else if (id < -1) {
                    // Set as "default"
                    PreferenceManager.getDefaultSharedPreferences(ActivityMain.this).edit()
                            .putLong(getString(R.string.pref_defaultstartlist), id).putLong
                            (TaskListFragment.LIST_ALL_ID_PREF_KEY, id).commit();
                    Toast.makeText(ActivityMain.this, R.string.new_default_set, Toast
                            .LENGTH_SHORT).show();
                    // openList(id);
                    return true;
                } else {
                    return false;
                }
            }
        });
        // Define the callback handler
        final LoaderCallbacks<Cursor> callbacks = new LoaderCallbacks<Cursor>() {

            final String[] COUNTROWS = new String[]{"COUNT(1)"};
            final String NOTCOMPLETED = Task.Columns.COMPLETED + " IS NULL ";

            @Override
            public Loader<Cursor> onCreateLoader(int id, Bundle arg1) {
                // Normal lists
                switch (id) {
                    case TaskListFragment.LIST_ID_OVERDUE:
                        return new CursorLoader(ActivityMain.this, Task.URI, COUNTROWS,
                                NOTCOMPLETED + TaskListFragment.andWhereOverdue(), null, null);
                    case TaskListFragment.LIST_ID_TODAY:
                        return new CursorLoader(ActivityMain.this, Task.URI, COUNTROWS,
                                NOTCOMPLETED + TaskListFragment.andWhereToday(), null, null);
                    case TaskListFragment.LIST_ID_WEEK:
                        return new CursorLoader(ActivityMain.this, Task.URI, COUNTROWS,
                                NOTCOMPLETED + TaskListFragment.andWhereWeek(), null, null);
                    case 0:
                    default:
                        return new CursorLoader(ActivityMain.this, TaskList.URI_WITH_COUNT, new
                                String[]{TaskList.Columns._ID, TaskList.Columns.TITLE, TaskList
                                .Columns.VIEW_COUNT}, null, null, getResources().getString(R
                                .string.const_as_alphabetic, TaskList.Columns.TITLE));
                }
            }

            @Override
            public void onLoadFinished(Loader<Cursor> l, Cursor c) {
                switch (l.getId()) {
                    case TaskListFragment.LIST_ID_OVERDUE:
                        if (c.moveToFirst()) {
                            updateExtra(1, c.getInt(0));
                        }
                        break;
                    case TaskListFragment.LIST_ID_TODAY:
                        if (c.moveToFirst()) {
                            updateExtra(2, c.getInt(0));
                        }
                        break;
                    case TaskListFragment.LIST_ID_WEEK:
                        if (c.moveToFirst()) {
                            updateExtra(3, c.getInt(0));
                        }
                        break;
                    case 0:
                    default:
                        adapter.swapCursor(c);
                }
            }

            @Override
            public void onLoaderReset(Loader<Cursor> l) {
                switch (l.getId()) {
                    case TaskListFragment.LIST_ID_OVERDUE:
                    case TaskListFragment.LIST_ID_TODAY:
                    case TaskListFragment.LIST_ID_WEEK:
                        break;
                    case 0:
                    default:
                        adapter.swapCursor(null);
                }
            }

            private void updateExtra(final int pos, final int count) {
                while (extraData.get(pos).size() < 2) {
                    // To avoid crashes
                    extraData.get(pos).add("0");
                }
                extraData.get(pos).set(1, Integer.toString(count));
                adapter.notifyDataSetChanged();
            }
        };

        // Load actual data
        getSupportLoaderManager().restartLoader(0, null, callbacks);
        // special views
        getSupportLoaderManager().restartLoader(TaskListFragment.LIST_ID_OVERDUE, null, callbacks);
        getSupportLoaderManager().restartLoader(TaskListFragment.LIST_ID_TODAY, null, callbacks);
        getSupportLoaderManager().restartLoader(TaskListFragment.LIST_ID_WEEK, null, callbacks);
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    @Override
    public void openTask(final Uri taskUri, final long listId, final View origin) {
        final Intent intent = new Intent().setAction(Intent.ACTION_EDIT).setClass(this,
                ActivityMain_.class).setData(taskUri).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(TaskDetailFragment.ARG_ITEM_LIST_ID, listId);
        // User clicked a task in the list
        // tablet
        /*if (fragment2 != null) {
            // Set the intent here also so rotations open the same item
            setIntent(intent);
            getSupportFragmentManager().beginTransaction().setCustomAnimations(R.anim
                    .slide_in_top, R.anim.slide_out_bottom).replace(R.id.fragment2,
                    TaskDetailFragment_.getInstance(taskUri)).commitAllowingStateLoss();
            taskHint.setVisibility(View.GONE);
        }
        // phone
        else {

            // if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN
            // && origin != null) {
            // Log.d("nononsenseapps animation", "Animating");
            // //intent.putExtra(ANIMATEEXIT, true);
            // startActivity(
            // intent,
            // ActivityOptions.makeCustomAnimation(this,
            // R.anim.activity_slide_in_left,
            // R.anim.activity_slide_out_left).toBundle());

            // }
            // else {
            startActivity(intent);
            // }
        }*/
    }

    @Override
    public void deleteTasksWithUndo(Snackbar.Callback dismissCallback, View.OnClickListener listener, Task... tasks) {

    }

    public void addTaskInList(final String text, final long listId) {
        if (listId < 1) {
            // Cant add to invalid lists
            return;
        }
        final Intent intent = new Intent().setAction(Intent.ACTION_INSERT).setClass(this,
                ActivityMain_.class).setData(Task.URI).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(TaskDetailFragment.ARG_ITEM_LIST_ID, listId);
        /*if (fragment2 != null) {
            // Set intent to preserve state when rotating
            setIntent(intent);
            // Replace editor fragment
            getSupportFragmentManager().beginTransaction().setCustomAnimations(R.anim
                    .slide_in_top, R.anim.slide_out_bottom).replace(R.id.fragment2,
                    TaskDetailFragment_.getInstance(text, listId)).commitAllowingStateLoss();
            taskHint.setVisibility(View.GONE);
        } else {
            // Open an activity

            // if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            // Log.d("nononsenseapps animation", "Animating");
            // intent.putExtra(ANIMATEEXIT, true);
            // startActivity(
            // intent,
            // ActivityOptions.makeCustomAnimation(this,
            // R.anim.activity_slide_in_left,
            // R.anim.activity_slide_out_left).toBundle());
            // }
            // else {
            startActivity(intent);
            // }
        }*/
    }

    private void simulateBack() {
        if (getSupportFragmentManager().getBackStackEntryCount() <= 1) {
            setIntent(new Intent(this, ActivityMain_.class));
        }

        if (!getSupportFragmentManager().popBackStackImmediate()) {
            finish();
            // Intent i = new Intent(this, ActivityMain_.class);
            // i.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            // startActivity(i);
        }
    }

    /**
     * Only call this when pressing the up-navigation. Makes sure the new
     * activity comes in on top of this one.
     */
    void finishSlideTop() {
        super.finish();
        overridePendingTransition(R.anim.activity_slide_in_right_full, R.anim
                .activity_slide_out_right);
    }

    @Override
    public boolean childItemsVisible() {
        return isDrawerClosed;
    }

    @Override
    public void onSharedPreferenceChanged(final SharedPreferences prefs, final String key) {
        if (key.equals(MainPrefs.KEY_THEME) || key.equals(getString(R.string
                .const_preference_locale_key))) {
            shouldRestart = true;
        } else if (key.startsWith("pref_restart")) {
            shouldRestart = true;
        }
    }

    @Override
    public long getEditorTaskId() {
        return -1;
    }

    @Override
    public long getListOfTask() {
        return 0;
    }

    @Override
    public void closeEditor(Fragment fragment) {
        if (fragment2 != null) {
            getSupportFragmentManager().beginTransaction().setCustomAnimations(R.anim
                    .slide_in_top, R.anim.slide_out_bottom).remove(fragment)
                    .commitAllowingStateLoss();
            taskHint.setAlpha(0f);
            taskHint.setVisibility(View.VISIBLE);
            taskHint.animate().alpha(1f).setStartDelay(500);
        } else {
            // Phone case, simulate back button
            // finish();
            simulateBack();
        }
    }

    @NonNull
    @Override
    public String getInitialTaskText() {
        return null;
    }

    public interface ListOpener {
        void openList(final long id);
    }
}
