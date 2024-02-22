package com.genuine.demo.idtagsrecorder;

import static com.hbisoft.hbrecorder.Constants.MAX_FILE_SIZE_REACHED_ERROR;
import static com.hbisoft.hbrecorder.Constants.SETTINGS_ERROR;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaScannerConnection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.Address;
import android.location.Geocoder;

import com.google.common.util.concurrent.ListenableFuture;
import com.hbisoft.hbrecorder.HBRecorder;
import com.hbisoft.hbrecorder.HBRecorderListener;

// -- To add ID TAG reader functionality --
// import <id-tag-reader-provider.api-module>;  // e.g. com.zebra.rfid.api3.TagData

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.sql.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

public class MainActivity extends AppCompatActivity implements HBRecorderListener, LocationListener {
    // -- For ID TAG reader functionality
    // -- `MainActivity` would implement handler interface, say, `IdTagHandler`

    public TextView statusTextViewIdTags = null;
    private TextView textIdTags;
    private static final int BLUETOOTH_PERMISSION_REQUEST_CODE = 100;

    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    PreviewView previewView;

    private TextClock clockTC;

    protected String provider;
    protected LocationManager locationManager;
    protected LocationListener locationListener;
    protected TextView textLocation;
    protected TextView textLatLon;
    protected String latitude,longitude;
    protected Geocoder geocoder;
    protected List<Address> addresses;

    private HBRecorder hbRecorder;

    private static final int SCREEN_RECORD_REQUEST_CODE = 777;
    private static final int PERMISSION_REQ_ID_RECORD_AUDIO = 22;
    private static final int PERMISSION_REQ_POST_NOTIFICATIONS = 33;
    private static final int PERMISSION_REQ_ID_WRITE_EXTERNAL_STORAGE = PERMISSION_REQ_ID_RECORD_AUDIO + 1;
    private boolean hasPermissions = false;

    private Button startbtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        clockTC = findViewById(R.id.textClock);
        // clockTC.setFormat12Hour("yyyy-MM-dd hh:mm:ss a z");

        textLocation = (TextView) findViewById(R.id.textLoc);
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
        }
        geocoder = new Geocoder(this, Locale.getDefault());

        // -- HBRecorder
        initViews();
        setOnClickListeners();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // Init HBRecorder
            hbRecorder = new HBRecorder(this, this);

            if (hbRecorder.isBusyRecording()) {
                startbtn.setText(R.string.stop_recording);
            }
        }

        // UI
        statusTextViewIdTags = findViewById(R.id.textStatus);
        textIdTags = findViewById(R.id.textViewdata);

        // -- For ID TAG reader functionality
        // -- Initialize `idTagHandler`

        Button simulate = findViewById(R.id.button_simulate);
        simulate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
               String[] strArray = new String[] {"","","","","","","",""};
                int rand1 = (int) ((Math.random() * 8) + 1);
                for (int i=0; i < rand1; i++) {
                    int rand2 = (int) ((Math.random() * Math.pow(16,5)));
                    strArray[i] = String.format("%1$07x", rand2);
                }
                textIdTags.setText(String.join("\n", strArray));

            }
        });

        previewView = findViewById(R.id.previewView);
        cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                startCameraX(cameraProvider);
            } catch (ExecutionException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        }, getExecutor());
    }

    // -- HBRecorder
    private void createFolder() {
        File f1 = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "HBRecorder");
        if (!f1.exists()) {
            if (f1.mkdirs()) {
                Log.i("Folder ", "created");
            }
        }
    }

    private void initViews() {
        startbtn = findViewById(R.id.button_start);
    }

    private void setOnClickListeners() {
        startbtn.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS, PERMISSION_REQ_POST_NOTIFICATIONS) && checkSelfPermission(Manifest.permission.RECORD_AUDIO, PERMISSION_REQ_ID_RECORD_AUDIO)) {
                        hasPermissions = true;
                    }
                }
                else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    if (checkSelfPermission(Manifest.permission.RECORD_AUDIO, PERMISSION_REQ_ID_RECORD_AUDIO)) {
                        hasPermissions = true;
                    }
                } else {
                    if (checkSelfPermission(Manifest.permission.RECORD_AUDIO, PERMISSION_REQ_ID_RECORD_AUDIO) && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, PERMISSION_REQ_ID_WRITE_EXTERNAL_STORAGE)) {
                        hasPermissions = true;
                    }
                }
                if (hasPermissions) {
                    if (hbRecorder.isBusyRecording()) {
                        hbRecorder.stopScreenRecording();
                        startbtn.setText(R.string.start_recording);
                    }
                    //else start recording
                    else {
                        startRecordingScreen();
                    }
                }
            } else {
                showLongToast("This library requires API 21>");
            }
        });
    }

    @Override
    public void HBRecorderOnStart() {
        Log.e("HBRecorder", "HBRecorderOnStart called");
    }

    @Override
    public void HBRecorderOnComplete() {
        startbtn.setText(R.string.start_recording);
        showLongToast("Saved Successfully");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (hbRecorder.wasUriSet()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ) {
                    updateGalleryUri();
                } else {
                    refreshGalleryFile();
                }
            }else{
                refreshGalleryFile();
            }
        }

    }

    @Override
    public void HBRecorderOnError(int errorCode, String reason) {
        if (errorCode == SETTINGS_ERROR) {
            showLongToast(getString(R.string.settings_not_supported_message));
        } else if ( errorCode == MAX_FILE_SIZE_REACHED_ERROR) {
            showLongToast(getString(R.string.max_file_size_reached_message));
        } else {
            showLongToast(getString(R.string.general_recording_error_message));
            Log.e("HBRecorderOnError", reason);
        }

        startbtn.setText(R.string.start_recording);

    }

    @Override
    public void HBRecorderOnPause() {
    }

    @Override
    public void HBRecorderOnResume() {
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void refreshGalleryFile() {
        MediaScannerConnection.scanFile(this,
                new String[]{hbRecorder.getFilePath()}, null,
                new MediaScannerConnection.OnScanCompletedListener() {
                    public void onScanCompleted(String path, Uri uri) {
                        Log.i("ExternalStorage", "Scanned " + path + ":");
                        Log.i("ExternalStorage", "-> uri=" + uri);
                    }
                });
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private void updateGalleryUri(){
        contentValues.clear();
        contentValues.put(MediaStore.Video.Media.IS_PENDING, 0);
        getContentResolver().update(mUri, contentValues, null, null);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void startRecordingScreen() {
        quickSettings();
        MediaProjectionManager mediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        Intent permissionIntent = mediaProjectionManager != null ? mediaProjectionManager.createScreenCaptureIntent() : null;
        startActivityForResult(permissionIntent, SCREEN_RECORD_REQUEST_CODE);
        startbtn.setText(R.string.stop_recording);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void quickSettings() {
        hbRecorder.setAudioBitrate(128000);
        hbRecorder.setAudioSamplingRate(44100);
        hbRecorder.recordHDVideo(true);
        hbRecorder.isAudioEnabled(true);
        hbRecorder.setNotificationSmallIcon(R.drawable.icon);
        hbRecorder.setNotificationTitle(getString(R.string.stop_recording_notification_title));
        hbRecorder.setNotificationDescription(getString(R.string.stop_recording_notification_message));
    }

    private boolean checkSelfPermission(String permission, int requestCode) {
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{permission}, requestCode);
            return false;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if(requestCode == BLUETOOTH_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // -- For ID TAG reader functionality
                // -- Call `idTagHandler`'s relevant method
            }
            else {
                Toast.makeText(this, "Bluetooth Permissions not granted", Toast.LENGTH_SHORT).show();
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        // HBRecorder
        switch (requestCode) {
            case PERMISSION_REQ_POST_NOTIFICATIONS:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    checkSelfPermission(Manifest.permission.RECORD_AUDIO, PERMISSION_REQ_ID_RECORD_AUDIO);
                } else {
                    hasPermissions = false;
                    showLongToast("No permission for " + Manifest.permission.POST_NOTIFICATIONS);
                }
                break;
            case PERMISSION_REQ_ID_RECORD_AUDIO:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, PERMISSION_REQ_ID_WRITE_EXTERNAL_STORAGE);
                } else {
                    hasPermissions = false;
                    showLongToast("No permission for " + Manifest.permission.RECORD_AUDIO);
                }
                break;
            case PERMISSION_REQ_ID_WRITE_EXTERNAL_STORAGE:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    hasPermissions = true;
                    startRecordingScreen();
                } else {
                    if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                        hasPermissions = true;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            startRecordingScreen();
                        }
                    } else {
                        hasPermissions = false;
                        showLongToast("No permission for " + Manifest.permission.WRITE_EXTERNAL_STORAGE);
                    }
                }
                break;
            default:
                break;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (requestCode == SCREEN_RECORD_REQUEST_CODE) {
                if (resultCode == RESULT_OK) {
                    setOutputPath();
                    hbRecorder.startScreenRecording(data, resultCode);

                }
            }
        }
    }

    ContentResolver resolver;
    ContentValues contentValues;
    Uri mUri;
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void setOutputPath() {
        String filename = generateFileName();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            resolver = getContentResolver();
            contentValues = new ContentValues();
            contentValues.put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/" + "HBRecorder");
            contentValues.put(MediaStore.Video.Media.TITLE, filename);
            contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
            contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4");
            mUri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues);
            hbRecorder.setFileName(filename);
            hbRecorder.setOutputUri(mUri);
        }else{
            createFolder();
            hbRecorder.setOutputPath(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES) +"/HBRecorder");
        }
    }

    private String generateFileName() {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.getDefault());
        Date curDate = new Date(System.currentTimeMillis());
        return formatter.format(curDate).replace(" ", "");
    }

    private void showLongToast(final String msg) {
        Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show();
    }

    private Executor getExecutor() {
        return ContextCompat.getMainExecutor(this);
    }

    @SuppressLint("RestrictedApi")
    private void startCameraX(ProcessCameraProvider cameraProvider) {

        cameraProvider.unbindAll();

        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        Preview preview = new Preview.Builder().build();

        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        cameraProvider.bindToLifecycle(this, cameraSelector, preview);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    // -- Location/Geocoder
    public String addressOf(double latitude, double longitude) {
        try {
            addresses = geocoder.getFromLocation(latitude, longitude, 1);
            String address = addresses.get(0).getAddressLine(0);
            return address;
        }
        catch (IOException e) {
            e.printStackTrace();
            return e.getMessage();
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        double lat = location.getLatitude();
        double lon = location.getLongitude();
        // lat = 37.337670;    // for testing
        // lon = -121.891650;  // for testing
        textLocation = (TextView) findViewById(R.id.textLoc);
        textLocation.setText(addressOf(lat, lon));
        textLatLon = (TextView) findViewById(R.id.textLatLon);
        textLatLon.setText("( " + String.format("%.4f", lat) + " , " + String.format("%.4f", lon) + " )");
    }

    @Override
    public void onProviderDisabled(String provider) {
        Log.d("Latitude","disable");
    }

    @Override
    public void onProviderEnabled(String provider) {
        Log.d("Latitude","enable");
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        Log.d("Latitude","status");
    }

    // -- For ID TAG reader functionality
    // -- Implement `idTagHandler`'s lifecycle methods onPause(), onResume() and
    // --   event-based methods like onTriggerPressed(), onTagsDetected(), etc

}
