/**
 * Author:  ANONYMOUS
 * Purpose: Evaluate a Cribbage hand containing four cards + a Starting card.
 * Project: COMP90041 Semester 2 2018 Project
 *
 * This class contains a main method which evaluates a Cribbage Hand.
 * This is achieved by entering a hand into the command line in the form of
 * two-character Strings, which are then translated into 'Card' objects.
 *
 * evaluateHand() takes an array of Cards and calculates the total number of
 * points gained given the rules of Cribbage.
 *
 * This class expects there to be 4 cards in a hand, plus an additional 5th
 * Starting Card, or 'S/C' as it is referred to in the comments.
 */
package pl.llp.aircasting.screens.stream.map;

import android.graphics.Color;

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
import pl.llp.aircasting.screens.common.sessionState.VisibleSession;
import pl.llp.aircasting.sensor.common.ThresholdsHolder;

public class NewHeatMapOverlay {

    private TileOverlay mOverlay_green, mOverlay_yellow, mOverlay_orange, mOverlay_red;
    private HeatmapTileProvider mProvider_green, mProvider_yellow, mProvider_orange, mProvider_red;
    private int very_low, low, mid, high, very_high;
    private boolean is_green, is_yellow, is_orange, is_red = false;
    private final float[] startPoints = {0.2f, 0.5f, 1.0f};
    private final float transparency = 0.5f;
    private final double opacity = 0.7;
    private final double opacity_red = 0.8;
    private final int[] color_green = new int[]{Color.rgb(152,251,152),
            Color.rgb(144,238,144), Color.rgb(101, 198, 138)}; // green
    private final int[] color_yellow = new int[]{Color.rgb( 255,255,240),
            Color.rgb(255,255,224), Color.rgb(254, 230, 101)}; // yellow
    private final int[] color_orange = new int[]{Color.rgb(255,218,185),
            Color.rgb(244,164,96), Color.rgb(254, 176, 101)}; // orange
    private final int[] color_red = new int[]{Color.rgb(255,160,122),
            Color.rgb(255,99,71), Color.rgb(254, 100, 101)}; // red

    @Inject
    SoundHelper soundHelper;
    @Inject
    VisibleSession visibleSession;
    @Inject
    ThresholdsHolder thresholds;

    private Iterable<Region> regions;

    public void draw(GoogleMap mMap) {
        Sensor sensor = visibleSession.getSensor();
        very_low = thresholds.getValue(sensor, MeasurementLevel.VERY_LOW);
        low = thresholds.getValue(sensor, MeasurementLevel.LOW);
        mid = thresholds.getValue(sensor, MeasurementLevel.MID);
        high = thresholds.getValue(sensor, MeasurementLevel.HIGH);
        very_high = thresholds.getValue(sensor, MeasurementLevel.VERY_HIGH);

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

                if (very_low <= value && value <= very_high) {
                    if (value < low) {
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

        Gradient gradient_green = new Gradient(color_green, startPoints);
        Gradient gradient_yellow = new Gradient(color_yellow, startPoints);
        Gradient gradient_orange = new Gradient(color_orange, startPoints);
        Gradient gradient_red = new Gradient(color_red, startPoints);

        // Create the tile provider.
        if (!list_orange.isEmpty()) {
            mProvider_orange = new HeatmapTileProvider.Builder()
                    .data(list_orange)
                    .gradient(gradient_orange).radius(35)
                    .opacity(opacity)
                    .build();
            mOverlay_orange = mMap.addTileOverlay(new TileOverlayOptions().tileProvider(mProvider_orange));
            mOverlay_orange.setTransparency(transparency);
            is_orange = true;
        }
        if (!list_red.isEmpty()) {
            mProvider_red = new HeatmapTileProvider.Builder()
                    .data(list_red)
                    .gradient(gradient_red).radius(35)
                    .opacity(opacity_red)
                    .build();
            mOverlay_red = mMap.addTileOverlay(new TileOverlayOptions().tileProvider(mProvider_red));
            mOverlay_red.setTransparency(transparency);
            is_red = true;
        }
        if (!list_yellow.isEmpty()) {
            mProvider_yellow = new HeatmapTileProvider.Builder()
                    .data(list_yellow)
                    .gradient(gradient_yellow).radius(35)
                    .opacity(opacity)
                    .build();
            mOverlay_yellow = mMap.addTileOverlay(new TileOverlayOptions().tileProvider(mProvider_yellow));
            mOverlay_yellow.setTransparency(transparency);
            is_yellow = true;
        }
        if (!list_green.isEmpty()) {
            mProvider_green = new HeatmapTileProvider.Builder()
                    .data(list_green)
                    .gradient(gradient_green).radius(35)
                    .build();
            mOverlay_green = mMap.addTileOverlay(new TileOverlayOptions().tileProvider(mProvider_green));
            mOverlay_green.setTransparency(transparency);
            is_green = true;
        }
    }

    public void setRegions(Iterable<Region> regions) {
        this.regions = regions;
    }

    public void remoteOverlay(){
        if(is_green){
            mOverlay_green.remove();
            mOverlay_green.clearTileCache();
        }
        if(is_yellow){
            mOverlay_yellow.remove();
            mOverlay_yellow.clearTileCache();
        }
        if(is_orange){
            mOverlay_orange.remove();
            mOverlay_orange.clearTileCache();
        }
        if(is_red){
            mOverlay_red.remove();
            mOverlay_red.clearTileCache();
        }
        is_green = false;
        is_yellow = false;
        is_orange = false;
        is_red = false;
    }
}
