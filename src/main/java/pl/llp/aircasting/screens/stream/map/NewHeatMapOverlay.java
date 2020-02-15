package pl.llp.aircasting.screens.stream.map;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.util.Log;
import android.view.View;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.TileOverlay;
import com.google.android.gms.maps.model.TileOverlayOptions;
import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapView;
import com.google.android.maps.Overlay;
import com.google.android.maps.Projection;
import com.google.inject.Inject;
import com.google.maps.android.heatmaps.Gradient;
import com.google.maps.android.heatmaps.HeatmapTileProvider;

import java.util.ArrayList;
import java.util.List;

import pl.llp.aircasting.model.Sensor;
import pl.llp.aircasting.model.internal.Region;
import pl.llp.aircasting.screens.common.helpers.ResourceHelper;
import pl.llp.aircasting.screens.common.sessionState.VisibleSession;

import static pl.llp.aircasting.screens.stream.map.LocationConversionHelper.geoPoint;

public class NewHeatMapOverlay {

    private static final int ALPHA = 100;
    private TileOverlay mOverlay;
    private HeatmapTileProvider mProvider;

    @Inject
    SoundHelper soundHelper;
    @Inject
    ResourceHelper resourceHelper;
    @Inject
    Paint paint;
    @Inject
    VisibleSession visibleSession;

    private Iterable<Region> regions;

    public void draw(View view, GoogleMap mMap) {
        if (regions == null) return;

//        Projection projection = view.getProjection();

        Sensor sensor = visibleSession.getSensor();
        for (Region region : regions) {
            double value = region.getValue();

            if (soundHelper.shouldDisplay(sensor, value)) {
                int color = resourceHelper.getColorAbsolute(sensor, value);

                paint.setColor(color);
                paint.setAlpha(ALPHA);

                LatLng southWest = new LatLng(region.getSouth(), region.getWest());
                LatLng northEast = new LatLng(region.getNorth(), region.getEast());
                addPoint(southWest, mMap);
                addPoint(northEast, mMap);
//                Point bottomLeft = projection.toPixels(southWest, null);
//                Point topRight = projection.toPixels(northEast, null);

//                canvas.drawRect(bottomLeft.x, topRight.y, topRight.x, bottomLeft.y, paint);
            }
        }
    }

    private void addPoint(LatLng latLng, GoogleMap mMap) {
        Log.e("add heatmap", "all good");
        Log.e("heatmap latlag", ": "+ latLng);
        List<LatLng> list = new ArrayList<LatLng>();
        list.add(latLng);
        int[] colors = {
                Color.rgb(101, 198, 138)// green
        };

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

    public void setRegions(Iterable<Region> regions) {
        this.regions = regions;
    }
}
