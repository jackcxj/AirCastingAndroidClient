package pl.llp.aircasting.screens.sessionRecord;

import android.app.Application;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
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
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import pl.llp.aircasting.R;
import pl.llp.aircasting.model.Measurement;
import pl.llp.aircasting.model.MeasurementStream;
import pl.llp.aircasting.model.Session;
import pl.llp.aircasting.screens.common.ToggleAircastingManager;
import pl.llp.aircasting.screens.common.helpers.LocationHelper;
import pl.llp.aircasting.screens.common.ToastHelper;
import pl.llp.aircasting.screens.common.sessionState.CurrentSessionManager;
import pl.llp.aircasting.screens.common.base.DialogActivity;

import pl.llp.aircasting.screens.sessions.CSVHelper;
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
                startSession();
                break;

            case R.id.start_session_and_share:
                settingsHelper.setContributeToCrowdmap(true);
                startMobileSession();
                break;
        }
    }

    private void startSession() {

        startMobileSession();

        session = currentSessionManager.getCurrentSession();
//                        dashboardChartManager.stop();
//                    stopMobileAirCasting(session);
        Log.e("stopAirCasting", "all good");

        new Thread() {
            @Override
            public void run() {
                while (currentSessionManager.isSessionRecording()) {
                    Log.e("loadSession", "all good");
                    try {
                        Thread.sleep(4000);
//                        Uri uri = prepareCSV(StartMobileSessionActivity.this, session);
                        File file = prepareCSV(StartMobileSessionActivity.this, session);
//                        prepareAndShare();
//                            new ServiceInBackGround().execute();
                        uploadFile(file);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }.start();
    }

    private void uploadFile(final File file) {

        new Thread() {
            @Override
            public void run() {
                try {
                    client = new Socket("10.12.224.194", 8888);
                    Log.e("uploadFile", "connect successful");
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

