package pl.llp.aircasting.screens.sessionRecord;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.support.v7.app.AppCompatDelegate;
import pl.llp.aircasting.screens.common.sessionState.CurrentSessionManager;

public class RecordWithoutGPSAlert {
    private Activity activity;
    private CurrentSessionManager currentSessionManager;
    private String sessionTitle;
    private String sessionTags;
    private boolean withoutLocation;

    public RecordWithoutGPSAlert(String title,
                                 String tags,
                                 Activity activity,
                                 CurrentSessionManager currentSessionManager,
                                 boolean withoutLocation) {
        this.activity = activity;
        this.currentSessionManager = currentSessionManager;
        this.sessionTitle = title;
        this.sessionTags = tags;
        this.withoutLocation = withoutLocation;
    }

    public void display() {
        currentSessionManager.startMobileSession(sessionTitle, sessionTags, withoutLocation);
        activity.invalidateOptionsMenu();
        activity.finish();
//        DialogInterface.OnClickListener dialogOnClickListener = new DialogInterface.OnClickListener() {
//
//            @Override
//            public void onClick(DialogInterface dialogInterface, int which) {
//                switch (which){
//                    case DialogInterface.BUTTON_POSITIVE:
//                        currentSessionManager.startMobileSession(sessionTitle, sessionTags, withoutLocation);
//                        activity.invalidateOptionsMenu();
//                        activity.finish();
//                        break;
//
//                    case DialogInterface.BUTTON_NEGATIVE:
//                        break;
//                }
//            }
//        };
//
//        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
//        builder.setMessage("Without location data you can't map your session or contribute it to the CrowdMap")
//                .setPositiveButton("Continue", dialogOnClickListener)
//                .setNegativeButton("Cancel", dialogOnClickListener).show();
    }
}
