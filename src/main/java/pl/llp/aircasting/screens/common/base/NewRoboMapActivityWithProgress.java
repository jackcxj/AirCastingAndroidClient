package pl.llp.aircasting.screens.common.base;

import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v7.app.AppCompatCallback;
import android.support.v7.app.AppCompatDelegate;
import android.support.v7.view.ActionMode;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.ViewGroup;

import com.google.inject.Inject;
import com.google.inject.Injector;

import pl.llp.aircasting.R;
import pl.llp.aircasting.screens.common.helpers.NavigationDrawerHelper;
import pl.llp.aircasting.screens.common.helpers.SettingsHelper;
import pl.llp.aircasting.screens.userAccount.ProfileActivity;
import pl.llp.aircasting.screens.userAccount.SignOutActivity;
import roboguice.activity.event.OnActivityResultEvent;
import roboguice.activity.event.OnConfigurationChangedEvent;
import roboguice.activity.event.OnContentChangedEvent;
import roboguice.activity.event.OnContentViewAvailableEvent;
import roboguice.activity.event.OnCreateEvent;
import roboguice.activity.event.OnDestroyEvent;
import roboguice.activity.event.OnNewIntentEvent;
import roboguice.activity.event.OnPauseEvent;
import roboguice.activity.event.OnRestartEvent;
import roboguice.activity.event.OnResumeEvent;
import roboguice.activity.event.OnStartEvent;
import roboguice.activity.event.OnStopEvent;
import roboguice.application.RoboApplication;
import roboguice.event.EventManager;
import roboguice.inject.ContextScope;
import roboguice.inject.InjectorProvider;

public abstract class NewRoboMapActivityWithProgress extends FragmentActivity implements ActivityWithProgress, AppCompatCallback, View.OnClickListener, InjectorProvider {

    @Inject
    NavigationDrawerHelper navigationDrawerHelper;
    @Inject
    SettingsHelper settingsHelper;

    public AppCompatDelegate delegate;
    public Toolbar toolbar;
    private int progressStyle;
    private ProgressDialog dialog;
    private SimpleProgressTask task;

    protected EventManager eventManager;
    protected ContextScope scope;

    @Override
    public ProgressDialog showProgressDialog(int progressStyle, SimpleProgressTask task) {
        this.progressStyle = progressStyle;
        this.task = task;

        showDialog(SPINNER_DIALOG);
        return dialog;
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        this.dialog = SimpleProgressTask.prepareDialog(this, progressStyle);

        return dialog;
    }

    protected void onCreate(Bundle savedInstanceState) {

        final Injector injector = getInjector();
        eventManager = injector.getInstance(EventManager.class);
        scope = injector.getInstance(ContextScope.class);
        scope.enter(this);
        injector.injectMembers(this);
        super.onCreate(savedInstanceState);
        eventManager.fire(new OnCreateEvent(savedInstanceState));

        getDelegate().onCreate(savedInstanceState);

        Object instance = getLastNonConfigurationInstance();
        if (instance != null) {
            ((SimpleProgressTask) instance).setActivity(this);
        }
    }

    @Override
    public void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        getDelegate().onPostCreate(savedInstanceState);
    }

    public void initToolbar(String title) {
        toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.setNavigationIcon(R.drawable.navigation_empty_icon);
        toolbar.setContentInsetStartWithNavigation(0);
        getDelegate().setSupportActionBar(toolbar);
        getDelegate().setTitle(title);
    }

    public void initNavigationDrawer() {
        navigationDrawerHelper.initNavigationDrawer(toolbar, this);
    }

    @Override
    public void hideProgressDialog() {
        try {
            dismissDialog(SPINNER_DIALOG);
            removeDialog(SPINNER_DIALOG);
        } catch (IllegalArgumentException e) {
            // Ignore - there was no dialog after all
        }
    }

    @Override
    public void onPostResume() {
        super.onPostResume();
        getDelegate().onPostResume();

        navigationDrawerHelper.removeHeader();
        navigationDrawerHelper.setDrawerHeader();
    }

    public AppCompatDelegate getDelegate() {
        if (delegate == null) {
            delegate = AppCompatDelegate.create(this, this);
        }
        return delegate;
    }

    @Override
    public void onSupportActionModeStarted(ActionMode mode) {
    }

    @Override
    public void onSupportActionModeFinished(ActionMode mode) {
    }

    @Override
    public ActionMode onWindowStartingSupportActionMode(ActionMode.Callback callback) {
        return null;
    }

    public void onProfileClick(View view) {
        signInOrOut();
    }

    private void signInOrOut() {
        if (settingsHelper.hasCredentials()) {
            startActivity(new Intent(this, SignOutActivity.class));
        } else {
            startActivity(new Intent(this, ProfileActivity.class));
        }
    }

    @Override
    public void setContentView(int layoutResID) {
        super.setContentView(layoutResID);
        scope.injectViews();
        eventManager.fire(new OnContentViewAvailableEvent());
    }

    @Override
    public void setContentView(View view, ViewGroup.LayoutParams params) {
        super.setContentView(view, params);
        scope.injectViews();
        eventManager.fire(new OnContentViewAvailableEvent());
    }

    @Override
    public void setContentView(View view) {
        super.setContentView(view);
        scope.injectViews();
        eventManager.fire(new OnContentViewAvailableEvent());
    }

//    @Override
//    public Object onRetainNonConfigurationInstance() {
//        return task;
//    }

    @Override
    protected void onRestart() {
        scope.enter(this);
        super.onRestart();
        eventManager.fire(new OnRestartEvent());
    }

    @Override
    public void onStart() {
        scope.enter(this);
        super.onStart();
        eventManager.fire(new OnStartEvent());
        getDelegate().onStart();
        navigationDrawerHelper.setDrawerHeader();
    }

    @Override
    protected void onResume() {
        scope.enter(this);
        super.onResume();
        eventManager.fire(new OnResumeEvent());
    }

    @Override
    protected void onPause() {
        super.onPause();
        eventManager.fire(new OnPauseEvent());
    }

    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        scope.enter(this);
        eventManager.fire(new OnNewIntentEvent());
    }

    @Override
    public void onStop() {
        scope.enter(this);
        try {
            eventManager.fire(new OnStopEvent());
        } finally {
            scope.exit(this);
            super.onStop();
        }
        getDelegate().onStop();

        navigationDrawerHelper.removeHeader();
    }

    @Override
    public void onDestroy() {
        scope.enter(this);
        try {
            eventManager.fire(new OnDestroyEvent());
        } finally {
            eventManager.clear(this);
            scope.exit(this);
            scope.dispose(this);
            super.onDestroy();
        }
        getDelegate().onDestroy();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        final Configuration currentConfig = getResources().getConfiguration();
        super.onConfigurationChanged(newConfig);
        eventManager.fire(new OnConfigurationChangedEvent(currentConfig, newConfig));
    }

    public void onContentChanged() {
        super.onContentChanged();
        eventManager.fire(new OnContentChangedEvent());
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        scope.enter(this);
        try {
            eventManager.fire(new OnActivityResultEvent(requestCode, resultCode, data));
        } finally {
            scope.exit(this);
        }
    }

    @Override
    public Injector getInjector() {
        return ((RoboApplication) getApplication()).getInjector();
    }

}
