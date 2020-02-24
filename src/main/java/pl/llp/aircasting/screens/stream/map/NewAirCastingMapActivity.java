package pl.llp.aircasting.screens.stream.map;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AppCompatCallback;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Animation;
import android.widget.ImageView;
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
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.TileOverlay;
import com.google.android.gms.maps.model.TileOverlayOptions;
import com.google.common.eventbus.Subscribe;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.maps.android.heatmaps.Gradient;
import com.google.maps.android.heatmaps.HeatmapTileProvider;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

import pl.llp.aircasting.Intents;
import pl.llp.aircasting.R;
import pl.llp.aircasting.event.sensor.LocationEvent;
import pl.llp.aircasting.event.session.NoteCreatedEvent;
import pl.llp.aircasting.event.session.VisibleSessionUpdatedEvent;
import pl.llp.aircasting.event.ui.VisibleStreamUpdatedEvent;
import pl.llp.aircasting.model.Measurement;
import pl.llp.aircasting.model.Sensor;
import pl.llp.aircasting.model.internal.Region;
import pl.llp.aircasting.model.internal.MeasurementLevel;
import pl.llp.aircasting.networking.drivers.AveragesDriver;
import pl.llp.aircasting.networking.httpUtils.HttpResult;
import pl.llp.aircasting.screens.common.ToastHelper;
import pl.llp.aircasting.screens.common.helpers.LocationHelper;
import pl.llp.aircasting.screens.common.helpers.SelectSensorHelper;
import pl.llp.aircasting.screens.common.sessionState.SessionDataAccessor;
import pl.llp.aircasting.screens.common.sessionState.VisibleSession;
import pl.llp.aircasting.screens.dashboard.DashboardActivity;
import pl.llp.aircasting.screens.stream.MeasurementPresenter;
import pl.llp.aircasting.screens.stream.base.NewAirCastingActivity;
import pl.llp.aircasting.sensor.common.ThresholdsHolder;
import roboguice.application.RoboApplication;
import roboguice.inject.InjectResource;
import roboguice.inject.InjectView;
import roboguice.inject.InjectorProvider;

import static com.google.android.gms.common.api.GoogleApiClient.*;
import static pl.llp.aircasting.screens.common.helpers.LocationHelper.REQUEST_CHECK_SETTINGS;

/**
 * Purpose: This class is used to achieve all the map function related to MapView,
 *          which is replaced with Fragment. It can be used to show the original
 *          google map and all the components such as toolbar, gauges and topbar.
 *          All the related functions like start session, traceOverlay and
 *          heat map Overlay are fulfilled here.
 */

public class NewAirCastingMapActivity extends NewAirCastingActivity implements
        OnMapReadyCallback,
        ConnectionCallbacks,
        OnConnectionFailedListener,
        LocationListener,
        AppCompatCallback, InjectorProvider, View.OnClickListener, LocationHelper.LocationSettingsListener, MeasurementPresenter.Listener {

    @Inject public ConnectivityManager connectivityManager;
    @Inject SelectSensorHelper selectSensorHelper;
    @Inject ThresholdsHolder thresholds;
    @Inject NewHeatMapOverlay heatMapOverlay;
    @Inject AveragesDriver averagesDriver;
    @InjectView(R.id.spinner)
    ImageView spinner;
    @InjectResource(R.anim.spinner)
    Animation spinnerAnimation;

    // Navigation drawer and above part
    public Toolbar toolbar;
    public DrawerLayout drawerLayout;
    private boolean zoomToSession = true;
    private boolean soundTraceComplete = true;

    // Google map component
    private View mapView;
    private GoogleMap mMap;
    private GoogleApiClient googleApiClient;
    private static final int Request_User_Location_Code = 99;

    //Navigation right
    private static final int ACTION_TOGGLE = 1;
    private static final int ACTION_CENTER = 2;
    private int mRequestedAction;

    //TraceOverlay relevant
    private static final int DISTANCE_ERROR = 10;
    private LatLng currentLatLng;
    private SessionDataAccessor mSessionData;
    private VisibleSession mVisibleSession;
    private Sensor mSensor;
    private int very_low, low, mid, high, very_high;
    private int[] colors;
    private Location mLastLocation;
    private TileOverlay mOverlay;
    private HeatmapTileProvider mProvider;

    //HeatMapOverlay relevant
    private static final String HEAT_MAP_VISIBLE = "heat_map_visible";
    private boolean heatMapVisible = false;
    private LatLngBounds bounds;
    private int requestsOutstanding = 0;
    private HeatMapUpdater updater;

    @SuppressLint("ResourceType")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        drawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
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

    @Override
    public void onPostResume() {
        super.onPostResume();

        getDelegate().invalidateOptionsMenu();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putBoolean(HEAT_MAP_VISIBLE, heatMapVisible);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);

        zoomToSession = false;
        heatMapVisible = savedInstanceState.getBoolean(HEAT_MAP_VISIBLE);
    }


    private void toggleHeatMapVisibility(MenuItem menuItem) {
        if (heatMapVisible) {
            heatMapVisible = false;
            mMap.clear();
            mapView.invalidate();
            menuItem.setIcon(R.drawable.toolbar_crowd_map_icon_inactive);
        } else {
            heatMapVisible = true;
            updater = new HeatMapUpdater();
            updater.onMapIdle();
            mapView.invalidate();
            menuItem.setIcon(R.drawable.toolbar_crowd_map_icon_active);

        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);

        MenuInflater inflater = getDelegate().getMenuInflater();
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
            case R.id.auto_upload:
                if (DashboardActivity.auto_upload == false) {
                    Toast.makeText(this, "It is allow to upload the data automatically", Toast.LENGTH_SHORT).show();
                    DashboardActivity.auto_upload = true;
                } else {
                    Toast.makeText(this, "It is not allow to upload the data automatically", Toast.LENGTH_SHORT).show();
                    DashboardActivity.auto_upload = false;
                }
                break;
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

    @Override
    protected void onResume() {
        super.onResume();
        refreshNotes();
        spinnerAnimation.start();
        checkConnection();
        initializeMap();
        measurementPresenter.registerListener(this);
        //Todo: there is a problem after resume traceOverlay which needs to fix
//        initializeRouteOverlay();
        updater = new HeatMapUpdater();
    }

    @Override
    public Injector getInjector() {
        return ((RoboApplication) getApplication()).getInjector();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    public void onStop() {
        super.onStop();
        measurementPresenter.unregisterListener(this);
        googleApiClient.disconnect();
    }

    private void initializeMap() {
        if (settingsHelper.isFirstLaunch()) {
            Location location = locationHelper.getLastLocation();
            if (location != null) {
                CameraUpdateFactory.newLatLng(new LatLng(location.getLatitude(), location.getLongitude()));
            }
            settingsHelper.setFirstLaunch(false);
        }
    }

    private void initializeRouteOverlay() {
        if (shouldShowRoute()) {
            Sensor sensor = visibleSession.getSensor();
            List<Measurement> measurements = visibleSession.getMeasurements(sensor);

            Log.i("measurements", " "+measurements);
            for (Measurement measurement : measurements) {
                LatLng latlng = new LatLng(measurement.getLatitude(), measurement.getLongitude());
                drawPoint(latlng);
            }
        }
    }

    private boolean shouldShowRoute() {
        return settingsHelper.isShowRoute() &&
                (visibleSession.isVisibleSessionRecording() || visibleSession.isVisibleSessionViewed());
    }

    @Override
    public void onLocationSettingsSatisfied() {
        if (mRequestedAction == ACTION_TOGGLE) {
            toggleSessionRecording();
        } else if (mRequestedAction == ACTION_CENTER) {
            centerMap();
        }
    }

    private void checkConnection() {
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        if (activeNetworkInfo == null || !activeNetworkInfo.isConnectedOrConnecting()) {
            ToastHelper.show(this, R.string.no_internet, Toast.LENGTH_SHORT);
        }
    }

    private void toggleSessionRecording() {
        if (currentSessionManager.isSessionRecording()) {
            toggleAircastingManager.stopAirCasting();
            mMap.clear();
        } else {
            toggleAircastingManager.startMobileAirCasting();
        }
        getDelegate().invalidateOptionsMenu();
        measurementPresenter.reset();
        mapView.invalidate();
    }

    //Todo: Need to implement AirCastingMapView.Listener by extend Fragment (Mapview changed)
//    @Override
//    public void onMapIdle() {
//        runOnUiThread(new Runnable() {
//            @Override
//            public void run() {
//                refreshSoundTrace();
//            }
//        });
//    }

    protected void startSpinner() {
        if (spinner.getVisibility() != View.VISIBLE) {
            spinner.setVisibility(View.VISIBLE);
            spinner.setAnimation(spinnerAnimation);
        }
    }

    protected void stopSpinner() {
        spinner.setVisibility(View.INVISIBLE);
        spinner.setAnimation(null);
    }

    private void refresh() {
        boolean complete = (requestsOutstanding == 0) && soundTraceComplete;
        if (complete) {
            stopSpinner();
        } else {
            startSpinner();
        }
        if (!complete) mapView.invalidate();
    }

    private void refreshSoundTrace() {
        soundTraceComplete = false;
        refresh();

        soundTraceComplete = true;
        mapView.invalidate();
        refresh();
    }
    //Todo: until here

    @Subscribe
    public void onEvent(VisibleStreamUpdatedEvent event) {
        super.onEvent(event);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mapView.invalidate();
            }
        });
//        updater.onMapIdle();
//        onMapIdle();
    }

    @Subscribe
    public void onEvent(VisibleSessionUpdatedEvent event) {
        super.onEvent(event);
        refreshNotes();
        mapView.invalidate();
    }

    @Subscribe
    public void onEvent(NoteCreatedEvent event) {
        refreshNotes();
    }

    @Subscribe
    public void onEvent(MotionEvent event) {
        mapView.dispatchTouchEvent(event);
    }

    @Subscribe
    public void onEvent(LocationEvent event) {
//        updateRoute();
        mapView.invalidate();
    }

    protected void centerMap() {
        if (locationHelper.getLastLocation() != null) {
            Location location = locationHelper.getLastLocation();
            CameraUpdateFactory.newLatLng(new LatLng(location.getLatitude(), location.getLongitude()));
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_CHECK_SETTINGS:
                if (resultCode != RESULT_OK) {
                    ToastHelper.show(this, R.string.enable_location, Toast.LENGTH_LONG);
                } else {
                    locationHelper.startLocationUpdates();
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                    }
                    onLocationSettingsSatisfied();
                }
                break;
            default:
                super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    protected void refreshNotes() {
    }

    @Override
    public void onViewUpdated() {
    }

    @Override
    public void onAveragedMeasurement(Measurement measurement) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mapView.invalidate();
            }
        });
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
        mMap.setOnCameraMoveListener(new GoogleMap.OnCameraMoveListener() {

            @Override
            public void onCameraMove() {
                bounds = mMap.getProjection().getVisibleRegion().latLngBounds;
            }
        });
    }

    @Override
    public void onLocationChanged(Location location) {

        double latitude = location.getLatitude();
        double longitude = location.getLongitude();

        if (currentLatLng == null) {
            currentLatLng = new LatLng(latitude, longitude);
        }
        LatLng lastLatLng = currentLatLng;
        currentLatLng = new LatLng(location.getLatitude(), location.getLongitude());
        double movedDistance = CalculationByDistance(lastLatLng, currentLatLng);
        if (movedDistance > DISTANCE_ERROR || movedDistance <= 0.0008) {
            return;
        } else if (currentSessionManager.isSessionRecording()) {
            locationHelper.updateLocation(location);
            drawPoint(currentLatLng);
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

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        startLocation();
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.i("LocationFragment", "Connection suspended");
        googleApiClient.connect();
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.i("LocationFragment", "Connection failed: ConnectionResult.getErrorCode() " + connectionResult.getErrorCode());
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

    /**
     * This method is used to calculate the distance between two position with latitude
     * and longitude. It is used in the traceOverlay to avoid a smalle or large joggle.
     */
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

    /**
     * This method is used to obtain the current showing value in the gauges
     */
    public double getNowData() {
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

    /**
     * This method is used to draw the point when starting a new session which is
     * main method to fulfill the traceOverlay function.
     */
    private void drawPoint(LatLng latLng) {
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
        mapView.invalidate();
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
    protected Dialog onCreateDialog(int id) {
        switch (id) {
            case SelectSensorHelper.DIALOG_ID:
                return selectSensorHelper.chooseSensor(this);
            default:
                return super.onCreateDialog(id);
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

