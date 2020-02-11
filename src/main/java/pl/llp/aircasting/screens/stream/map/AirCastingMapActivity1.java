package pl.llp.aircasting.screens.stream.map;

import pl.llp.aircasting.Intents;
import pl.llp.aircasting.R;
import pl.llp.aircasting.event.measurements.MobileMeasurementEvent;
import pl.llp.aircasting.event.sensor.AudioReaderErrorEvent;
import pl.llp.aircasting.event.sensor.FixedSensorEvent;
import pl.llp.aircasting.event.sensor.SensorConnectedEvent;
import pl.llp.aircasting.event.sensor.SensorEvent;
import pl.llp.aircasting.event.sensor.ThresholdSetEvent;
import pl.llp.aircasting.event.session.VisibleSessionUpdatedEvent;
import pl.llp.aircasting.event.ui.VisibleStreamUpdatedEvent;
import pl.llp.aircasting.model.Measurement;
import pl.llp.aircasting.screens.common.ApplicationState;
import pl.llp.aircasting.screens.common.ToastHelper;
import pl.llp.aircasting.screens.common.ToggleAircastingManager;
import pl.llp.aircasting.screens.common.ToggleAircastingManagerFactory;
import pl.llp.aircasting.screens.common.base.SimpleProgressTask;
import pl.llp.aircasting.screens.common.helpers.LocationHelper;
import pl.llp.aircasting.screens.common.helpers.NavigationDrawerHelper;
import pl.llp.aircasting.screens.common.helpers.ResourceHelper;
import pl.llp.aircasting.screens.common.helpers.SelectSensorHelper;
import pl.llp.aircasting.screens.common.helpers.SettingsHelper;
import pl.llp.aircasting.screens.common.sessionState.CurrentSessionManager;
import pl.llp.aircasting.screens.common.sessionState.SessionDataAccessor;
import pl.llp.aircasting.screens.common.sessionState.ViewingSessionsManager;
import pl.llp.aircasting.screens.common.sessionState.VisibleSession;
import pl.llp.aircasting.screens.stream.GaugeHelper;
import pl.llp.aircasting.screens.stream.MeasurementPresenter;
import pl.llp.aircasting.screens.stream.TopBarHelper;
import pl.llp.aircasting.screens.stream.base.AirCastingBaseActivity;
import pl.llp.aircasting.sessionSync.SyncBroadcastReceiver;
import pl.llp.aircasting.storage.UnfinishedSessionChecker;
import roboguice.activity.event.OnCreateEvent;
import roboguice.activity.event.OnResumeEvent;
import roboguice.activity.event.OnStartEvent;
import roboguice.application.RoboApplication;
import roboguice.event.EventManager;
import roboguice.inject.ContextScope;
import roboguice.inject.InjectView;
import roboguice.inject.InjectorProvider;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AppCompatCallback;
import android.support.v7.app.AppCompatDelegate;
import android.support.v7.view.ActionMode;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.location.LocationListener;
import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapController;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.inject.Inject;
import com.google.inject.Injector;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static pl.llp.aircasting.Intents.startSensors;
import static pl.llp.aircasting.screens.stream.map.LocationConversionHelper.boundingBox;
import static pl.llp.aircasting.screens.stream.map.LocationConversionHelper.geoPoint;
import static pl.llp.aircasting.screens.stream.map.MapIdleDetector.detectMapIdle;


public class AirCastingMapActivity1 extends FragmentActivity implements
        OnMapReadyCallback,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LocationListener, AppCompatCallback, InjectorProvider, View.OnClickListener, LocationHelper.LocationSettingsListener {
    // 添加导航栏和顶部
    public AppCompatDelegate delegate;
    public Toolbar toolbar;
    @Inject
    NavigationDrawerHelper navigationDrawerHelper;
    protected EventManager eventManager;
    protected ContextScope scope;

    // 添加地图组件
    private GoogleMap mMap;
    private GoogleApiClient googleApiClient;
    private LocationRequest locationRequest;
    private Location lastLocation;
    private Marker currentUserLocationMarker;
    private static final int Request_User_Location_Code = 99;
    private View mapView;

    // 添加数据
    private ToggleAircastingManager toggleAircastingManager;
    private boolean initialized = false;
    @Inject
    ToggleAircastingManagerFactory aircastingHelperFactory;
    @Inject
    SyncBroadcastReceiver syncBroadcastReceiver;
    SyncBroadcastReceiver registeredReceiver;
    @Inject
    EventBus eventBus;
    @Inject
    public CurrentSessionManager currentSessionManager;
    @Inject
    ApplicationState state;
    private long lastChecked = 0;
    public static final long DELTA = TimeUnit.SECONDS.toMillis(15);
    @Inject
    UnfinishedSessionChecker checker;
    @Inject
    ViewingSessionsManager viewingSessionsManager;
    @Inject
    public Context context;
    protected View mGauges;
    protected GaugeHelper mGaugeHelper;
    @Inject
    public ResourceHelper resourceHelper;
    @Inject
    public VisibleSession visibleSession;
    @Inject
    SessionDataAccessor sessionData;
    private Handler handler = new Handler();

    private Thread pollServerTask = new Thread(new Runnable() {
        @Override
        public void run() {
            Intents.triggerStreamingSessionsSync(context);

            handler.postDelayed(pollServerTask, 60000);
        }
    });
    final AtomicBoolean noUpdateInProgress = new AtomicBoolean(true);
    @Inject
    public SettingsHelper settingsHelper;
    @Inject
    TopBarHelper topBarHelper;
    private View topBar;
    @Inject
    ConnectivityManager connectivityManager;
    @Inject
    public MeasurementPresenter measurementPresenter;
    @Inject
    public LocationHelper locationHelper;

    //导航栏右侧
    private static final int ACTION_TOGGLE = 1;
    private static final int ACTION_CENTER = 2;
    private int mRequestedAction;

    //heatmap
    private boolean heatMapVisible = false;
    @Inject HeatMapOverlay heatMapOverlay;
    public DrawerLayout drawerLayout;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        drawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);

        final Injector injector = getInjector();
        eventManager = injector.getInstance(EventManager.class);
        scope = injector.getInstance(ContextScope.class);
        scope.enter(this);
        injector.injectMembers(this);
        eventManager.fire(new OnCreateEvent(savedInstanceState));

        getDelegate().onCreate(savedInstanceState);

        setContentView(R.layout.new_map);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            checkUserLocationPermission();
        }

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map_id);
        mapFragment.getMapAsync(this);
        mapView = mapFragment.getView();
        if (mapView != null &&
                mapView.findViewById(1) != null) {
            // Get the button view
            View locationButton = ((View) mapView.findViewById(1).getParent()).findViewById(2);
            // and next place it, on bottom right (as Google Maps app)
            RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams)
                    locationButton.getLayoutParams();
            // position on right bottom
            layoutParams.addRule(RelativeLayout.ALIGN_PARENT_TOP, 0);
            layoutParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE);
            layoutParams.setMargins(0, 0, 30, 30);
        }
        initToolbar("Map");
        initNavigationDrawer();
    }

    public void initToolbar(String title) {
        toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.setNavigationIcon(R.drawable.navigation_empty_icon);
        toolbar.setContentInsetStartWithNavigation(0);
        getDelegate().setSupportActionBar(toolbar);
        getDelegate().setTitle(title);
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

    public void initNavigationDrawer() {
        navigationDrawerHelper.initNavigationDrawer(toolbar, this);
    }

    @Override
    public Injector getInjector() {
        return ((RoboApplication) getApplication()).getInjector();
    }

    @Override
    protected void onResume() {
        // RoboMapActivityWithProgress
        scope.enter(this);
        super.onResume();
        eventManager.fire(new OnResumeEvent());
        // AirCastingBaseActivity
        initialize_Base();

        registerReceiver(syncBroadcastReceiver, SyncBroadcastReceiver.INTENT_FILTER);
        registeredReceiver = syncBroadcastReceiver;

        eventBus.register(this);
        checkForUnfinishedSessions();

        if (viewingSessionsManager.anySessionPresent() || currentSessionManager.anySensorConnected()) {
            startSensors(context);
        }
        // AirCastingActivity
        initialize_AirCasting();

        startUpdatingFixedSessions();

        updateGauges();
        updateKeepScreenOn();
        topBarHelper.updateTopBar(visibleSession.getSensor(), topBar);
        Intents.startIOIO(context);
        Intents.startDatabaseWriterService(context);

        // AirCastingMapActivity
//        initialize();
//        refreshNotes();
//        spinnerAnimation.start();
//        initializeMap();
//        measurementPresenter.registerListener(this);
//        initializeRouteOverlay();
//        traceOverlay.startDrawing();
//        traceOverlay.refresh(mapView);

        checkConnection();

//        updater = new AirCastingMapActivity.HeatMapUpdater();
//        heatMapDetector = detectMapIdle(mapView, HEAT_MAP_UPDATE_TIMEOUT, updater);
//        soundTraceDetector = detectMapIdle(mapView, SOUND_TRACE_UPDATE_TIMEOUT, this);
    }

    private void initialize_Base() {
        toggleAircastingManager = aircastingHelperFactory.getAircastingHelper(this, getDelegate());

//        if (!initialized) {
//            initialized = true;
//        }
    }

    private void checkForUnfinishedSessions() {
        if (shouldCheckForUnfinishedSessions()) {
            new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... voids) {
                    checker.finishIfNeeded(AirCastingMapActivity1.this);
                    lastChecked = System.currentTimeMillis();
                    return null;
                }
            }.execute();
        }
    }

    private boolean shouldCheckForUnfinishedSessions() {
        if (currentSessionManager.isSessionRecording()) {
            return false;
        }

        if (state.saving().isSaving()) {
            return false;
        }

        long timeout = System.currentTimeMillis() - lastChecked;
        return timeout > DELTA;
    }

    private void initialize_AirCasting() {
        if (!initialized) {
            mGauges = findViewById(R.id.gauge_container);

            topBar = findViewById(R.id.top_bar);
            if (mGaugeHelper == null) {
                mGaugeHelper = new GaugeHelper(this, mGauges, resourceHelper, visibleSession, sessionData);
            }

//            zoomOut.setOnClickListener(this);
//            zoomIn.setOnClickListener(this);
//            topBar.setOnClickListener(this);
            topBar.setOnClickListener(this);
//            mGauges.setOnClickListener(this);

            initialized = true;
        }
    }

    private void startUpdatingFixedSessions() {
        if (viewingSessionsManager.isAnySessionFixed()) {
            handler.post(pollServerTask);
        }
    }

    protected void updateGauges() {
        if (noUpdateInProgress.get()) {
            noUpdateInProgress.set(false);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mGaugeHelper.updateGaugesFromSensor();
                    noUpdateInProgress.set(true);
                }
            });
        }
    }

    private void updateKeepScreenOn() {
        if (settingsHelper.isKeepScreenOn()) {
            getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    private void checkConnection() {
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        if (activeNetworkInfo == null || !activeNetworkInfo.isConnectedOrConnecting()) {
            ToastHelper.show(this, R.string.no_internet, Toast.LENGTH_SHORT);
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);

        MenuInflater inflater = getDelegate().getMenuInflater();

        if (currentSessionManager.isSessionIdle()) {
            inflater.inflate(R.menu.toolbar_start_recording, menu);
        } else if (currentSessionManager.isSessionRecording()){
            inflater.inflate(R.menu.toolbar_stop_recording, menu);
            inflater.inflate(R.menu.toolbar_make_note, menu);
        } else {
            return true;
        }

        inflater.inflate(R.menu.toolbar_crowd_map_toggle, menu);

        if (heatMapVisible) {
            menu.getItem(menu.size() - 1).setIcon(R.drawable.toolbar_crowd_map_icon_active);
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        super.onOptionsItemSelected(menuItem);

        switch (menuItem.getItemId()) {
            case R.id.toggle_aircasting:
                mRequestedAction = ACTION_TOGGLE;

                if (!settingsHelper.areMapsDisabled()) {
                    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        locationHelper.checkLocationSettings(this);
                    }
                } else {
                    toggleSessionRecording();
                }

                break;
            case R.id.make_note:
                Intents.makeANote(this);
                break;
            case R.id.toggle_heat_map_button:
                toggleHeatMapVisibility(menuItem);
                break;
        }
        return true;
    }

    private void toggleHeatMapVisibility(MenuItem menuItem) {
        if (heatMapVisible) {
            heatMapVisible = false;
//             mapView.getOverlays().remove(heatMapOverlay);
            mapView.invalidate();
            menuItem.setIcon(R.drawable.toolbar_crowd_map_icon_inactive);
        } else {
            heatMapVisible = true;
//            mapView.getOverlays().add(0, heatMapOverlay);
            mapView.invalidate();
            menuItem.setIcon(R.drawable.toolbar_crowd_map_icon_active);
        }
    }


    @Override
    public void onLocationSettingsSatisfied() {
        if (mRequestedAction == ACTION_TOGGLE) {
            toggleSessionRecording();
        } else if (mRequestedAction == ACTION_CENTER) {
//            centerMap();
        }
    }

    private void toggleSessionRecording() {
        toggleAirCasting();

        measurementPresenter.reset();
//        traceOverlay.refresh(mapView);
//        routeOverlay.clear();
//        routeOverlay.invalidate();
        mapView.invalidate();
    }

    public synchronized void toggleAirCasting() {
        toggleAircastingManager.toggleAirCasting();
        getDelegate().invalidateOptionsMenu();

        measurementPresenter.reset();
    }

//    private void toggleHeatMapVisibility(MenuItem menuItem) {
//        if (heatMapVisible) {
//            heatMapVisible = false;
//            mapView.getOverlays().remove(heatMapOverlay);
//            mapView.invalidate();
//            menuItem.setIcon(R.drawable.toolbar_crowd_map_icon_inactive);
//        } else {
//            heatMapVisible = true;
//            mapView.getOverlays().add(0, heatMapOverlay);
//            mapView.invalidate();
//            menuItem.setIcon(R.drawable.toolbar_crowd_map_icon_active);
//        }
//    }

//    @Override
//    public void onViewUpdated() {
//    }
//
//    @Override
//    public void onAveragedMeasurement(Measurement measurement) {
//        if (currentSessionManager.isSessionRecording()) {
//            if (!settingsHelper.isAveraging()) {
//                traceOverlay.update(measurement);
//            } else if (lastMeasurement != null) {
//                traceOverlay.update(lastMeasurement);
//            }
//        }
//
//        if (settingsHelper.isAveraging()) {
//            lastMeasurement = measurement;
//        }
//
//        runOnUiThread(new Runnable() {
//            @Override
//            public void run() {
//                mapView.invalidate();
//            }
//        });
//    }


    @Subscribe
    public void onEvent(MobileMeasurementEvent event) {
        updateGauges();
    }

    @Subscribe
    public void onEvent(VisibleSessionUpdatedEvent event) {
        updateGauges();
    }

    @Subscribe
    public void onEvent(SensorEvent event) {
        updateGauges();
    }

    @Subscribe
    public void onEvent(FixedSensorEvent event) {
        updateGauges();
    }

    @Subscribe
    public void onEvent(ThresholdSetEvent event) {
        updateGauges();
    }

    @Subscribe
    public void onEvent(SensorConnectedEvent event) {
        invalidateOptionsMenu();
    }

    @Subscribe
    public void onEvent(AudioReaderErrorEvent event) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ToastHelper.show(context, R.string.mic_error, Toast.LENGTH_LONG);
            }
        });
    }

    @Subscribe
    public void onEvent(VisibleStreamUpdatedEvent event) {
        topBarHelper.updateTopBar(event.getSensor(), topBar);
        updateGauges();
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        buildGoogleApiClient();
        mMap.setMyLocationEnabled(true);
        // Add a marker in Sydney and move the camera
//        LatLng sydney = new LatLng(-34, 151);
//        mMap.addMarker(new MarkerOptions().position(sydney).title("Marker in Sydney"));
//        mMap.moveCamera(CameraUpdateFactory.newLatLng(sydney));
    }


    public boolean checkUserLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, Request_User_Location_Code);
            } else {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, Request_User_Location_Code);
            }
            return false;
        } else {
            return false;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case Request_User_Location_Code:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        if (googleApiClient == null) {
                            buildGoogleApiClient();
                        }
                        mMap.setMyLocationEnabled(true);
                    }
                } else {
                    Toast.makeText(this, "Permission Denied...", Toast.LENGTH_SHORT).show();
                }
        }
    }

    protected synchronized void buildGoogleApiClient() {

        googleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();

        googleApiClient.connect();
    }

    @Override

    public void onLocationChanged(Location location) {

        lastLocation = location;

        if (currentUserLocationMarker != null) {
            currentUserLocationMarker.remove();
        }

        LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());

        MarkerOptions markerOptions = new MarkerOptions();
        markerOptions.position(latLng);
        markerOptions.title("User Current Location");
        markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN));

        currentUserLocationMarker = mMap.addMarker(markerOptions);

        mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng));
        mMap.animateCamera(CameraUpdateFactory.zoomBy(12));

        if (googleApiClient != null) {
            LocationServices.FusedLocationApi.removeLocationUpdates(googleApiClient, (com.google.android.gms.location.LocationListener) this);
        }
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {

        locationRequest = new LocationRequest();
        locationRequest.setInterval(1100);
        locationRequest.setFastestInterval(1100);
        locationRequest.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

            LocationServices.FusedLocationApi.requestLocationUpdates(googleApiClient, locationRequest, (com.google.android.gms.location.LocationListener) this);
        }
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.top_bar:
                Intents.thresholdsEditor(this, visibleSession.getSensor());
                break;
//            case R.id.gauge_container:
//                showDialog(SelectSensorHelper.DIALOG_ID);
//                break;
            default:
                break;
        }
    }
}

