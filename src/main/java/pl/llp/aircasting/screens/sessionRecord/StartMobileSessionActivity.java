package pl.llp.aircasting.screens.sessionRecord;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.content.FileProvider;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.csvreader.CsvWriter;
import com.google.common.base.Strings;
import com.google.common.io.Closer;
import com.google.inject.Inject;

import java.io.BufferedInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URI;
import java.net.URL;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import pl.llp.aircasting.Intents;
import pl.llp.aircasting.R;
import pl.llp.aircasting.model.Measurement;
import pl.llp.aircasting.model.MeasurementStream;
import pl.llp.aircasting.model.Session;
import pl.llp.aircasting.screens.common.ToggleAircastingManager;
import pl.llp.aircasting.screens.common.base.SimpleProgressTask;
import pl.llp.aircasting.screens.common.helpers.LocationHelper;
import pl.llp.aircasting.screens.common.ToastHelper;
import pl.llp.aircasting.screens.common.helpers.NoOp;
import pl.llp.aircasting.screens.common.sessionState.CurrentSessionManager;
import pl.llp.aircasting.screens.common.base.DialogActivity;

import pl.llp.aircasting.screens.dashboard.DashboardChartManager;
import pl.llp.aircasting.screens.sessions.CSVHelper;
import pl.llp.aircasting.screens.sessions.OpenSessionTask;
//import pl.llp.aircasting.screens.sessions.SessionWriter;
import pl.llp.aircasting.screens.sessions.SessionsActivity;
import pl.llp.aircasting.screens.sessions.shareSession.ShareSessionActivity;
import pl.llp.aircasting.storage.repository.SessionRepository;
import pl.llp.aircasting.util.Constants;
import pl.llp.aircasting.util.Logger;
import roboguice.inject.InjectResource;
import roboguice.inject.InjectView;

import static java.lang.String.valueOf;

/**
 * Created by radek on 04/09/17.
 */
public class StartMobileSessionActivity extends DialogActivity implements View.OnClickListener {
    @InjectView(R.id.start_session)
    Button startButton;
    @InjectView(R.id.start_session_and_share)
    Button startAndShareButton;

    @InjectView(R.id.session_title)
    EditText sessionTitle;
    @InjectView(R.id.session_tags)
    EditText sessionTags;

    @Inject
    Application context;
    @Inject
    CurrentSessionManager currentSessionManager;
    @Inject
    LocationHelper locationHelper;


    @InjectResource(R.string.share_title)
    String shareTitle;
    @InjectResource(R.string.share_file)
    String shareChooserTitle;
    @InjectResource(R.string.session_file_template)
    String shareText;

    private SessionRepository sessionRepository;
    private CSVHelper csvHelper;
    private Session session;
    private long sessionId;
    private ToggleAircastingManager toggleAircastingManager;

    final Closer closer = Closer.create();
    private final String ZIP_EXTENSION = ".zip";
    private final String CSV_EXTENSION = ".csv";
    public static final String SESSION_FALLBACK_FILE = "session_data";
    private static final int MINIMUM_SESSION_NAME_LENGTH = 2;

    private Socket client;
    private FileInputStream fileInputStream;
    private BufferedInputStream bufferedInputStream;
    private OutputStream outputStream;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.start_mobile_session);
        initDialogToolbar("Session Details");

        startButton.setOnClickListener(this);

        if (settingsHelper.isContributingToCrowdMap()) {
            startAndShareButton.setVisibility(View.GONE);
        } else {
            startAndShareButton.setOnClickListener(this);
        }

    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.start_session:
                startMobileSession();

                Handler handler = new Handler();
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        session = currentSessionManager.getCurrentSession();
//                        dashboardChartManager.stop();
                        locationHelper.stopLocationUpdates();
                        stopMobileAirCasting(session);
                        Log.e("stopAirCasting", "all good");

                        Intent intent = new Intent(StartMobileSessionActivity.this, SessionsActivity.class);
                        //设置传递键值对
                        intent.putExtra("finish", "ok");
                        startActivity(intent);

                    }
                }, 4000);

                new Handler().postDelayed(new Runnable() {
                    //                Runnable networkTask = new Runnable() {
                    @Override
                    public void run() {
                        Log.e("loadSession", "all good");
                        try {
//                        Uri uri = prepareCSV(StartMobileSessionActivity.this, session);
                            File file = prepareCSV(StartMobileSessionActivity.this, session);
//                        prepareAndShare();
//                            new ServiceInBackGround().execute();
                            uploadFile(file);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }, 4100);

                break;
            case R.id.start_session_and_share:
                settingsHelper.setContributeToCrowdmap(true);
                startMobileSession();
                break;
        }
    }

    private void uploadFile(final File file) {

        new Thread() {
            @Override
            public void run() {
               try {
                   client = new Socket("10.12.224.194", 8888);
                   Log.e("uploadFile","connect successful");
                   byte[] mybytearray = new byte[(int) file.length()]; //create a byte array to file

                   fileInputStream = new FileInputStream(file);
                   bufferedInputStream = new BufferedInputStream(fileInputStream);

                   bufferedInputStream.read(mybytearray, 0, mybytearray.length); //read the file

                   outputStream = client.getOutputStream();

                   outputStream.write(mybytearray, 0, mybytearray.length); //write file to the output stream byte by byte
                   outputStream.flush();
                   bufferedInputStream.close();
                   outputStream.close();
                   client.close();
                   Log.e("shareSession", "all good");

               } catch (Exception e) {
                   e.printStackTrace();
               }
            }
        }.start();
    }
//    private class ServiceInBackGround extends AsyncTask<Void, Void, File> {
//
//        @Override
//        protected File doInBackground(Void... voids) {
//            try {
//                Log.e("background", "all good");
//                return prepareCSV(context, session);
//
//            } catch (IOException e) {
//                Logger.e("Error while creating session CSV", e);
//                return null;
//            }
//        }
//
//        @Override
//        protected void onPostExecute(File file) {
//            super.onPostExecute(file);
//            try {
//                client = new Socket("localhost", 4444);
//                byte[] mybytearray = new byte[(int) file.length()]; //create a byte array to file
//
//                fileInputStream = new FileInputStream(file);
//                bufferedInputStream = new BufferedInputStream(fileInputStream);
//
//                bufferedInputStream.read(mybytearray, 0, mybytearray.length); //read the file
//
//                outputStream = client.getOutputStream();
//
//                outputStream.write(mybytearray, 0, mybytearray.length); //write file to the output stream byte by byte
//                outputStream.flush();
//                bufferedInputStream.close();
//                outputStream.close();
//                client.close();
//                Log.e("shareSession", "all good");
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//
//        }
//    }

//    private void prepareAndShare() {
//        final Activity context = StartMobileSessionActivity.this;
//        new AsyncTask<Void, Void, File>() {
//            @Override
//            protected File doInBackground(Void... voids) {
//                try {
//                    Log.e("background", "all good");
//                    return prepareCSV(context, session);
//                } catch (IOException e) {
//                    Logger.e("Error while creating session CSV", e);
//                    return null;
//                }
//            }
//
//            @Override
//            protected void onPostExecute(File file) {
//                super.onPostExecute(file);
//
////                if (file == null) {
////                    ToastHelper.show(context, R.string.unknown_error, Toast.LENGTH_SHORT);
////                } else {
////                    Intents.shareCSV(ShareSessionActivity.this, uri, shareChooserTitle, shareTitle, shareText);
//
//
//                    try {
//                        client = new Socket("localhost", 4444);
//                        byte[] mybytearray = new byte[(int) file.length()]; //create a byte array to file
//
//                        fileInputStream = new FileInputStream(file);
//                        bufferedInputStream = new BufferedInputStream(fileInputStream);
//
//                        bufferedInputStream.read(mybytearray, 0, mybytearray.length); //read the file
//
//                        outputStream = client.getOutputStream();
//
//                        outputStream.write(mybytearray, 0, mybytearray.length); //write file to the output stream byte by byte
//                        outputStream.flush();
//                        bufferedInputStream.close();
//                        outputStream.close();
//                        client.close();
//                        Log.e("shareSession", "all good");
//                    } catch (IOException e) {
//                        e.printStackTrace();
//                    }
////                }
//
//                finish();
//            }
//        }.execute();
//    }


//    private void prepareAndShare() {
//        //noinspection unchecked
//        final Activity context = StartMobileSessionActivity.this;
//        new SimpleProgressTask<Void, Void, File>(this) {
//            @Override
//            protected File doInBackground(Void... voids) {
//                try {
//                    return prepareCSV(context, session);
//                } catch (IOException e) {
//                    Logger.e("Error while creating session CSV", e);
//                    return null;
//                }
//            }
//
//            @Override
//            protected void onPostExecute(File file) {
//                super.onPostExecute(file);
//
//                if (file == null) {
//                    ToastHelper.show(context, R.string.unknown_error, Toast.LENGTH_SHORT);
//                } else {
////                    Intents.shareCSV(StartMobileSessionActivity.this, uri, shareChooserTitle, shareTitle, shareText);
//                    try {
//                        client = new Socket("localhost", 4444);
//                        byte[] mybytearray = new byte[(int) file.length()]; //create a byte array to file
//
//                        fileInputStream = new FileInputStream(file);
//                        bufferedInputStream = new BufferedInputStream(fileInputStream);
//
//                        bufferedInputStream.read(mybytearray, 0, mybytearray.length); //read the file
//
//                        outputStream = client.getOutputStream();
//
//                        outputStream.write(mybytearray, 0, mybytearray.length); //write file to the output stream byte by byte
//                        outputStream.flush();
//                        bufferedInputStream.close();
//                        outputStream.close();
//                        client.close();
//                    } catch (IOException e) {
//                        e.printStackTrace();
//                    }
//
//                    Log.e("shareSession", "all good");
////                            uploadFile(getFPUriToPath(StartMobileSessionActivity.this, uri), "http://47.254.79.126");
////                            Intents.shareCSV(StartMobileSessionActivity.this, uri, shareChooserTitle, shareTitle, shareText);
//                }
////                catch (IOException e) {
////                    Logger.e("Error while creating session CSV", e);
////                }
//            }
//
////            finish();
////        }
//        };
//
////    execute();
//
//    }

//    private static String getFPUriToPath(Context context, Uri uri) {
//        try {
//            List<PackageInfo> packs = context.getPackageManager().getInstalledPackages(PackageManager.GET_PROVIDERS);
//            if (packs != null) {
//                String fileProviderClassName = FileProvider.class.getName();
//                for (PackageInfo pack : packs) {
//                    ProviderInfo[] providers = pack.providers;
//                    if (providers != null) {
//                        for (ProviderInfo provider : providers) {
//                            if (uri.getAuthority().equals(provider.authority)) {
//                                if (provider.name.equalsIgnoreCase(fileProviderClassName)) {
//                                    Class<FileProvider> fileProviderClass = FileProvider.class;
//                                    try {
//                                        Method getPathStrategy = fileProviderClass.getDeclaredMethod("getPathStrategy", Context.class, String.class);
//                                        getPathStrategy.setAccessible(true);
//                                        Object invoke = getPathStrategy.invoke(null, context, uri.getAuthority());
//                                        if (invoke != null) {
//                                            String PathStrategyStringClass = FileProvider.class.getName() + "$PathStrategy";
//                                            Class<?> PathStrategy = Class.forName(PathStrategyStringClass);
//                                            Method getFileForUri = PathStrategy.getDeclaredMethod("getFileForUri", Uri.class);
//                                            getFileForUri.setAccessible(true);
//                                            Object invoke1 = getFileForUri.invoke(invoke, uri);
//                                            if (invoke1 instanceof File) {
//                                                String filePath = ((File) invoke1).getAbsolutePath();
//                                                return filePath;
//                                            }
//                                        }
//                                    } catch (NoSuchMethodException e) {
//                                        e.printStackTrace();
//                                    } catch (InvocationTargetException e) {
//                                        e.printStackTrace();
//                                    } catch (IllegalAccessException e) {
//                                        e.printStackTrace();
//                                    } catch (ClassNotFoundException e) {
//                                        e.printStackTrace();
//                                    }
//                                    break;
//                                }
//                                break;
//                            }
//                        }
//                    }
//                }
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//        return null;
//    }


    public File prepareCSV(Context context, Session session) throws IOException {
        try {
            File storage = context.getFilesDir();
            File dir = new File(storage, "aircasting_sessions");
            dir.mkdirs();
            File file = new File(dir, fileName(session.getTitle()) + ZIP_EXTENSION);
            OutputStream outputStream = new FileOutputStream(file);
            closer.register(outputStream);
            ZipOutputStream zippedOutputStream = new ZipOutputStream(outputStream);
            zippedOutputStream.putNextEntry(new ZipEntry(fileName(session.getTitle()) + CSV_EXTENSION));

            Writer writer = new OutputStreamWriter(zippedOutputStream);

            CsvWriter csvWriter = new CsvWriter(writer, ',');
            write(session).toWriter(csvWriter);

            csvWriter.flush();
            csvWriter.close();

//            Uri uri = Uri.fromFile(file);
            Uri uri = FileProvider.getUriForFile(context, "pl.llp.aircasting.fileprovider", file);
            if (Constants.isDevMode()) {
                Logger.i("File path [" + uri + "]");
            }
            return file;
        } finally {
            closer.close();
        }
    }

    String fileName(String title) {
        StringBuilder result = new StringBuilder();
        if (!Strings.isNullOrEmpty(title)) {
            try {
                Matcher matcher = Pattern.compile("([_\\-a-zA-Z0-9])*").matcher(title.toLowerCase());
                while (matcher.find()) {
                    result.append(matcher.group());
                }
            } catch (IllegalStateException ignore) {

            }
        }

        return result.length() > MINIMUM_SESSION_NAME_LENGTH ? result.toString() : SESSION_FALLBACK_FILE;
    }

    private SessionWriter write(Session session) {
        return new SessionWriter(session);
    }


    private void stopMobileAirCasting(Session session) {
        Long sessionId = session.getId();
        if (session.isEmpty()) {
            ToastHelper.show(context, R.string.no_data, Toast.LENGTH_SHORT);
            currentSessionManager.discardSession(sessionId);
        } else {
            currentSessionManager.stopSession();

            if (session.isLocationless()) {
                currentSessionManager.finishSession(sessionId, false);
//                Log.e("stop without location", "crowdmap"); //this way
            } else if (settingsHelper.isContributingToCrowdMap()) {
                currentSessionManager.finishSession(sessionId, true);
            }
        }
    }

    private void startMobileSession() {
        String title = sessionTitle.getText().toString();
        String tags = sessionTags.getText().toString();

        if (settingsHelper.areMapsDisabled()) {
            currentSessionManager.startMobileSession(title, tags, true);
        } else {
            if (locationHelper.getLastLocation() == null) {
                RecordWithoutGPSAlert recordAlert = new RecordWithoutGPSAlert(title, tags, this, currentSessionManager, true);
                recordAlert.display();
                return;
            } else {
                currentSessionManager.startMobileSession(title, tags, false);
                showWarnings();
            }
        }

        finish();
    }

    private void showWarnings() {
        if (settingsHelper.hasNoCredentials()) {
            ToastHelper.show(context, R.string.account_reminder, Toast.LENGTH_LONG);
        }

        if (locationHelper.getLastLocation() == null) {
            ToastHelper.show(context, R.string.no_gps_fix_warning, Toast.LENGTH_LONG);
        }
    }
}

class SessionWriter {
    final SimpleDateFormat TIMESTAMP_FORMAT = new SimpleDateFormat(CSVHelper.TIMESTAMP_FORMAT);

    Session session;

    SessionWriter(Session session) {
        this.session = session;
    }

    void toWriter(CsvWriter writer) throws IOException {
        Iterable<MeasurementStream> streams = session.getActiveMeasurementStreams();
        for (MeasurementStream stream : streams) {
            writeSensorHeader(writer);
            writeSensor(stream, writer);

            writeMeasurementHeader(writer);

            for (Measurement measurement : stream.getMeasurements()) {
                writeMeasurement(writer, measurement);
            }
        }
    }

    private void writeMeasurementHeader(CsvWriter writer) throws IOException {
        writer.write("Timestamp");
        writer.write("geo:lat");
        writer.write("geo:long");
        writer.write("Value");
        writer.endRecord();
    }

    private void writeMeasurement(CsvWriter writer, Measurement measurement) throws IOException {
        writer.write(TIMESTAMP_FORMAT.format(measurement.getTime()));
        writer.write(valueOf(measurement.getLatitude()));
        writer.write(valueOf(measurement.getLongitude()));
        writer.write(valueOf(measurement.getValue()));
        writer.endRecord();
    }

    private void writeSensor(MeasurementStream stream, CsvWriter writer) throws IOException {
        writer.write(stream.getSensorName());
        writer.write(stream.getPackageName());
        writer.write(stream.getMeasurementType());
        writer.write(valueOf(stream.getUnit()));
        writer.endRecord();
    }

    private void writeSensorHeader(CsvWriter writer) throws IOException {
        writer.write("sensor:model");
        writer.write("sensor:package");
        writer.write("sensor:capability");
        writer.write("sensor:units");
        writer.endRecord();
    }
}

