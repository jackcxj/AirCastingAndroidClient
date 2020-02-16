package pl.llp.aircasting.screens.stream.map;

import android.graphics.Color;
import android.graphics.Paint;
import android.util.Log;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.TileOverlay;
import com.google.android.gms.maps.model.TileOverlayOptions;
import com.google.inject.Inject;
import com.google.maps.android.heatmaps.Gradient;
import com.google.maps.android.heatmaps.HeatmapTileProvider;

import java.util.ArrayList;
import java.util.List;

import pl.llp.aircasting.model.Sensor;
import pl.llp.aircasting.model.internal.MeasurementLevel;
import pl.llp.aircasting.model.internal.Region;
import pl.llp.aircasting.screens.common.helpers.ResourceHelper;
import pl.llp.aircasting.screens.common.sessionState.VisibleSession;
import pl.llp.aircasting.sensor.common.ThresholdsHolder;

public class NewHeatMapOverlay {

    private static final int ALPHA = 100;
    private TileOverlay mOverlay;
    private HeatmapTileProvider mProvider_green, mProvider_yellow, mProvider_orange, mProvider_red;
    private int very_low, low, mid, high, very_high;


    @Inject
    SoundHelper soundHelper;
    @Inject
    ResourceHelper resourceHelper;
    @Inject
    Paint paint;
    @Inject
    VisibleSession visibleSession;
    @Inject
    ThresholdsHolder thresholds;

    private Iterable<Region> regions;
//    private int[] color_green, color_yellow, color_orange, color_red;

    public void draw(GoogleMap mMap) {
        Sensor sensor = visibleSession.getSensor();
        very_low = thresholds.getValue(sensor, MeasurementLevel.VERY_LOW);
        low = thresholds.getValue(sensor, MeasurementLevel.LOW);
        mid = thresholds.getValue(sensor, MeasurementLevel.MID);
        high = thresholds.getValue(sensor, MeasurementLevel.HIGH);
        very_high = thresholds.getValue(sensor, MeasurementLevel.VERY_HIGH);
        Log.e("very_low", " " + very_low);
        Log.e("low", " " + low);
        Log.e("mid", " " + mid);
        Log.e("high", " " + high);
        Log.e("very_high", " " + very_high);

        List<LatLng> list_green = new ArrayList<LatLng>();
        List<LatLng> list_yellow = new ArrayList<LatLng>();
        List<LatLng> list_orange = new ArrayList<LatLng>();
        List<LatLng> list_red = new ArrayList<LatLng>();

        if (regions == null) return;

        for (Region region : regions) {
            double value = region.getValue();
            if (soundHelper.shouldDisplay(sensor, value)) {

                LatLng southWest = new LatLng(region.getSouth(), region.getWest());
                LatLng northEast = new LatLng(region.getNorth(), region.getEast());

//                list_green.add(southWest);
//                list_green.add(northEast);
                if (very_low <= value && value <= very_high) {
                    if (value < low) {
                        Log.e("add green", "lalalal");
                        list_green.add(southWest);
                        list_green.add(northEast);
                    } else if (value < mid) {
                        list_yellow.add(southWest);
                        list_yellow.add(northEast);

                    } else if (value < high) {
                        list_orange.add(southWest);
                        list_orange.add(northEast);
                    } else {
                        list_red.add(southWest);
                        list_red.add(northEast);
                    }
                }

            }
        }
        int[] color_green = new int[]{Color.rgb(255,255,255), Color.rgb(101, 198, 138)}; // green
        int[] color_yellow = new int[]{Color.rgb(255,255,255), Color.rgb(254, 230, 101)}; // yellow
        int[] color_orange = new int[]{Color.rgb(255,255,255), Color.rgb(254, 176, 101)}; // orange
        int[] color_red = new int[]{Color.rgb(255,255,255), Color.rgb(254, 100, 101)}; // red

        float[] startPoints = {0.2f, 0.5f};
        float transparency = 0.6f;

        Gradient gradient_green = new Gradient(color_green, startPoints);
        Gradient gradient_yellow = new Gradient(color_yellow, startPoints);
        Gradient gradient_orange = new Gradient(color_orange, startPoints);
        Gradient gradient_red = new Gradient(color_red, startPoints);

// Create the tile provider.
        if (!list_red.isEmpty()) {
            mProvider_red = new HeatmapTileProvider.Builder()
                    .data(list_red)
                    .gradient(gradient_red).radius(35)
                    .build();
            mOverlay = mMap.addTileOverlay(new TileOverlayOptions().tileProvider(mProvider_red));
        }
        if (!list_orange.isEmpty()) {
            mProvider_orange = new HeatmapTileProvider.Builder()
                    .data(list_orange)
                    .gradient(gradient_orange).radius(35)
                    .build();
            mOverlay = mMap.addTileOverlay(new TileOverlayOptions().tileProvider(mProvider_orange));
        }
        if (!list_yellow.isEmpty()) {
            mProvider_yellow = new HeatmapTileProvider.Builder()
                    .data(list_yellow)
                    .gradient(gradient_yellow).radius(35)
                    .build();
            mOverlay = mMap.addTileOverlay(new TileOverlayOptions().tileProvider(mProvider_yellow));
        }
        if (!list_green.isEmpty()) {
            mProvider_green = new HeatmapTileProvider.Builder()
                    .data(list_green)
                    .gradient(gradient_green).radius(35)
                    .build();
            mOverlay = mMap.addTileOverlay(new TileOverlayOptions().tileProvider(mProvider_green));
        }
        mOverlay.setTransparency(transparency);
    }

    public void setRegions(Iterable<Region> regions) {
        this.regions = regions;
    }
}
