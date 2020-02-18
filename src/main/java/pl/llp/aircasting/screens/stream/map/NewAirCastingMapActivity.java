package pl.llp.aircasting.screens.stream.map;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
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
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.TileOverlay;
import com.google.android.gms.maps.model.TileOverlayOptions;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.maps.android.heatmaps.Gradient;
import com.google.maps.android.heatmaps.HeatmapTileProvider;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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
import pl.llp.aircasting.model.Sensor;
import pl.llp.aircasting.model.internal.Region;
import pl.llp.aircasting.model.internal.MeasurementLevel;
import pl.llp.aircasting.networking.drivers.AveragesDriver;
import pl.llp.aircasting.networking.httpUtils.HttpResult;
import pl.llp.aircasting.screens.common.ApplicationState;
import pl.llp.aircasting.screens.common.ToastHelper;
import pl.llp.aircasting.screens.common.ToggleAircastingManager;
import pl.llp.aircasting.screens.common.ToggleAircastingManagerFactory;
import pl.llp.aircasting.screens.common.helpers.LocationHelper;
import pl.llp.aircasting.screens.common.helpers.NavigationDrawerHelper;
import pl.llp.aircasting.screens.common.helpers.ResourceHelper;
import pl.llp.aircasting.screens.common.helpers.SettingsHelper;
import pl.llp.aircasting.screens.common.sessionState.CurrentSessionManager;
import pl.llp.aircasting.screens.common.sessionState.SessionDataAccessor;
import pl.llp.aircasting.screens.common.sessionState.ViewingSessionsManager;
import pl.llp.aircasting.screens.common.sessionState.VisibleSession;
import pl.llp.aircasting.screens.stream.GaugeHelper;
import pl.llp.aircasting.screens.stream.MeasurementPresenter;
import pl.llp.aircasting.screens.stream.TopBarHelper;
import pl.llp.aircasting.sensor.common.ThresholdsHolder;
import pl.llp.aircasting.sessionSync.SyncBroadcastReceiver;
import pl.llp.aircasting.storage.UnfinishedSessionChecker;
import roboguice.activity.event.OnCreateEvent;
import roboguice.activity.event.OnPauseEvent;
import roboguice.activity.event.OnResumeEvent;
import roboguice.activity.event.OnStopEvent;
import roboguice.application.RoboApplication;
import roboguice.event.EventManager;
import roboguice.inject.ContextScope;
import roboguice.inject.InjectorProvider;


import static com.google.android.gms.common.api.GoogleApiClient.*;
import static pl.llp.aircasting.Intents.startSensors;
import static pl.llp.aircasting.screens.stream.map.LocationConversionHelper.geoPoint;

public class NewAirCastingMapActivity extends FragmentActivity implements
        OnMapReadyCallback,
        ConnectionCallbacks,
        OnConnectionFailedListener,
        LocationListener,
        AppCompatCallback, InjectorProvider, View.OnClickListener, LocationHelper.LocationSettingsListener {


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
    MeasurementPresenter measurementPresenter;
    @Inject
    public LocationHelper locationHelper;

    //导航栏右侧
    private static final int ACTION_TOGGLE = 1;
    private static final int ACTION_CENTER = 2;
    private int mRequestedAction;

    //traceoverlay
    private LatLng currentLatLng;
    /**
     * judge distance, if it exceeds it which means the error of the change
     */
    private static final int DISTANCE_ERROR = 10;
    private SessionDataAccessor mSessionData;
    private VisibleSession mVisibleSession;
    private Sensor mSensor;
    private double nowData;
    @Inject
    ThresholdsHolder thresholds;
    private int very_low, low, mid, high, very_high;
    private int[] colors;
    private Location mLastLocation;

    //heatmapoverlay
    private boolean heatMapVisible = false;
    @Inject
    NewHeatMapOverlay heatMapOverlay;
    public DrawerLayout drawerLayout;
    private TileOverlay mOverlay;
    private HeatmapTileProvider mProvider;
    private LatLngBounds bounds;
    double viewPortHeight;
    double viewPortWidth;
    @Inject
    AveragesDriver averagesDriver;
    private int requestsOutstanding = 0;
    private HeatMapUpdater updater;

    @SuppressLint("ResourceType")
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
//        updater = new HeatMapUpdater();
//        heatMapDetector = detectMapIdle(mapView, HEAT_MAP_UPDATE_TIMEOUT, updater);
//        soundTraceDetector = detectMapIdle(mapView, SOUND_TRACE_UPDATE_TIMEOUT, this);
    }

    @Override
    protected void onPause() {
        super.onPause();

        handler.removeCallbacks(pollServerTask);
        if (viewingSessionsManager.sessionsEmpty() && !currentSessionManager.isSessionRecording()) {
            Intents.stopSensors(this);
        }

        if (registeredReceiver != null) {
            unregisterReceiver(syncBroadcastReceiver);
            registeredReceiver = null;
        }
        eventBus.unregister(this);

        eventManager.fire(new OnPauseEvent());

//        routeRefreshDetector.stop();
//        traceOverlay.stopDrawing(mapView);
//        heatMapDetector.stop();
//        soundTraceDetector.stop();
    }

    @Override
    public void onStop() {
        super.onStop();
        scope.enter(this);
        try {
            eventManager.fire(new OnStopEvent());
        } finally {
            scope.exit(this);
            super.onStop();
        }
        getDelegate().onStop();

        navigationDrawerHelper.removeHeader();

        googleApiClient.disconnect();

    }

    @Override
    public void onPostResume() {
        super.onPostResume();
        getDelegate().invalidateOptionsMenu();
        navigationDrawerHelper.removeHeader();
        navigationDrawerHelper.setDrawerHeader();
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
                    checker.finishIfNeeded(NewAirCastingMapActivity.this);
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
            inflater.inflate(R.menu.toolbar_autoupload_permission, menu);
            inflater.inflate(R.menu.toolbar_start_recording, menu);
        } else if (currentSessionManager.isSessionRecording()) {
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

            heatMapOverlay.remoteOverlay();
            mapView.invalidate();

            menuItem.setIcon(R.drawable.toolbar_crowd_map_icon_inactive);
        } else {
            heatMapVisible = true;
//            mapView.getOverlays().add(0, heatMapOverlay);

            updater = new HeatMapUpdater();
            updater.onMapIdle();
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
        updater.onMapIdle();
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

        initGoogle();
        mMap.setMyLocationEnabled(true);

        // Add a marker in Sydney and move the camera
//        LatLng sydney = new LatLng(-34, 151);
//        locationHelper.updateLocation(sydney);
//        mMap.addMarker(new MarkerOptions().position(sydney).title("Marker in Sydney"));
//        mMap.moveCamera(CameraUpdateFactory.newLatLng(sydney));

        mMap.setOnCameraChangeListener(new GoogleMap.OnCameraChangeListener() {
            @Override
            public void onCameraChange(CameraPosition position) {
                bounds = mMap.getProjection().getVisibleRegion().latLngBounds;
            }
        });
    }

    @Override
    public void onLocationChanged(Location location) {

        double latitude = location.getLatitude();
        double longitude = location.getLongitude();

        //首次定位时设置当前经纬度
        if (currentLatLng == null) {
            currentLatLng = new LatLng(latitude, longitude);
        }
        LatLng lastLatLng = currentLatLng;
        currentLatLng = new LatLng(location.getLatitude(), location.getLongitude());
        //计算当前定位与前一次定位的距离，如果距离异常或是距离为0,则不做任何操作
        double movedDistance = CalculationByDistance(lastLatLng, currentLatLng);
        if (movedDistance > DISTANCE_ERROR || movedDistance <= 0.001) {
            return;
        } else if (currentSessionManager.isSessionRecording()) {

            locationHelper.updateLocation(location);
            addPoint(currentLatLng);
        }
    }

    private double getNowData() {
        mSessionData = mGaugeHelper.getsessionData();
        mVisibleSession = mGaugeHelper.getVisibleSession();
        mSensor = mVisibleSession.getSensor();
        double nowData = mSessionData.getNow(mSensor, mVisibleSession.getVisibleSessionId());

        very_low = thresholds.getValue(mSensor, MeasurementLevel.VERY_LOW);
        low = thresholds.getValue(mSensor, MeasurementLevel.LOW);
        mid = thresholds.getValue(mSensor, MeasurementLevel.MID);
        high = thresholds.getValue(mSensor, MeasurementLevel.HIGH);
        very_high = thresholds.getValue(mSensor, MeasurementLevel.VERY_HIGH);
        return nowData;
    }

    private void addPoint(LatLng latLng) {
        Log.e("add point", "all good");
        List<LatLng> list = new ArrayList<LatLng>();
        list.add(latLng);
        int temp = (int) Math.round(getNowData());

        if (very_low <= temp && temp <= very_high) {
            if (very_low <= temp && temp < low) {
                colors = new int[]{
                        Color.rgb(101, 198, 138) // green
                };
            } else if (low <= temp && temp < mid) {
                colors = new int[]{
                        Color.rgb(254, 230, 101) // yellow
                };
            } else if (mid <= temp && temp < high) {
                colors = new int[]{
                        Color.rgb(254, 176, 101) // orange
                };
            } else if (high <= temp && temp < very_high) {
                colors = new int[]{
                        Color.rgb(254, 100, 101) // orange
                };
            }
        } else {
            return;
        }

        float[] startPoints = {0.2f};

        Gradient gradient = new Gradient(colors, startPoints);

// Create the tile provider.
        mProvider = new HeatmapTileProvider.Builder()
                .data(list)
                .gradient(gradient)
                .build();

// Add the tile overlay to the map.
        mOverlay = mMap.addTileOverlay(new TileOverlayOptions().tileProvider(mProvider));

    }

    public double CalculationByDistance(LatLng StartP, LatLng EndP) {
        int Radius = 6371;// radius of earth in Km
        double lat1 = StartP.latitude;
        double lat2 = EndP.latitude;
        double lon1 = StartP.longitude;
        double lon2 = EndP.longitude;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2)
                * Math.sin(dLon / 2);
        double c = 2 * Math.asin(Math.sqrt(a));
        double valueResult = Radius * c;
        double km = valueResult / 1;
        DecimalFormat newFormat = new DecimalFormat("####");
        int kmInDec = Integer.valueOf(newFormat.format(km));
        double meter = valueResult % 1000;
        int meterInDec = Integer.valueOf(newFormat.format(meter));
        Log.i("Radius Value", "" + valueResult + "   KM  " + kmInDec
                + " Meter   " + meterInDec);

        return Radius * c;
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
                            initGoogle();
                        }
                        mMap.setMyLocationEnabled(true);
                    }
                } else {
                    Toast.makeText(this, "Permission Denied...", Toast.LENGTH_SHORT).show();
                }
        }
    }

    void initGoogle() {
        googleApiClient = new GoogleApiClient.Builder(this)
                .enableAutoManage(this /* FragmentActivity */,
                        this /* OnConnectionFailedListener */)
                .addConnectionCallbacks(this)
                .addApi(LocationServices.API)
                .build();

        googleApiClient.connect();
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.i("LocationFragment", "Connection failed: ConnectionResult.getErrorCode() " + connectionResult.getErrorCode());
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        startLocation();
    }

    private void startLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        LocationRequest request = locationHelper.createLocationRequest();
        LocationServices.FusedLocationApi.requestLocationUpdates(googleApiClient, request, this);
        locationHelper.startLocationUpdates();
        mLastLocation = LocationServices.FusedLocationApi.getLastLocation(googleApiClient);
        if (mLastLocation != null) {
            LatLng currentLocation = new LatLng(mLastLocation.getLatitude(), mLastLocation
                    .getLongitude());
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 14));
        }
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.i("LocationFragment", "Connection suspended");
        googleApiClient.connect();
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

    class HeatMapDownloader extends AsyncTask<Void, Void, HttpResult<Iterable<Region>>> {
        public static final int MAP_BUFFER_SIZE = 3;

        @Override
        protected void onPreExecute() {
            requestsOutstanding += 1;
        }

        protected HttpResult<Iterable<Region>> doInBackground(Void... voids) {

            LatLng northEastLoc = bounds.northeast;
            LatLng southWestLoc = bounds.southwest;

            int size = Math.min(mapView.getWidth(), mapView.getHeight()) / settingsHelper.getHeatMapDensity();
            if (size < 1) size = 1;

            int gridSizeX = MAP_BUFFER_SIZE * mapView.getWidth() / size;
            int gridSizeY = MAP_BUFFER_SIZE * mapView.getHeight() / size;

            return averagesDriver.index(visibleSession.getSensor(), southWestLoc.longitude, northEastLoc.latitude,
                    northEastLoc.longitude, southWestLoc.latitude, gridSizeX, gridSizeY);
        }

        @Override
        protected void onPostExecute(HttpResult<Iterable<Region>> regions) {
            requestsOutstanding -= 1;

            if (regions.getContent() != null) {
                heatMapOverlay.setRegions(regions.getContent());
                heatMapOverlay.draw(mMap);
            }
            mapView.invalidate();
        }
    }

    private class HeatMapUpdater implements MapIdleDetector.MapIdleListener {
        @Override
        public void onMapIdle() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    //noinspection unchecked
                    new HeatMapDownloader().execute();
                }
            });
        }
    }
}

