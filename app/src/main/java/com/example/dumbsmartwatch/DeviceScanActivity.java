package com.example.dumbsmartwatch;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.ActionBar;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;

public class DeviceScanActivity extends AppCompatActivity {

    public static String MAC_ADDRESS;
    private static final String TAG = "DeviceScan";
    HashSet<String> foundMacAddresses;

    Button startScanButton;
    Button stopScanButton;
    Button backButton;

    TextView textView;

    BluetoothDevice device;
    BluetoothAdapter adapter;
    BluetoothLeScanner scanner;

    Handler handler = new Handler();
    private final int SCAN_PERIOD = 5000;
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int PERMISSION_REQUEST_FINE_LOCATION = 1;
    private static final int PERMISSION_REQUEST_FOREGROUND_SERVICE = 1;
    private static boolean isScanning = false;

    private ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            textView.setText("Scanning for watch...");
            final BluetoothDevice foundDev = result.getDevice();
            if(foundMacAddresses.add(foundDev.getAddress())) {
                createDevBtn("" + foundDev.getName() + ", MAC ADDRESS: " + foundDev.getAddress(), foundDev);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_scan);

        adapter = BluetoothAdapter.getDefaultAdapter();
        scanner = adapter.getBluetoothLeScanner();

        startScanButton = findViewById(R.id.start_scanning);
        startScanButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                textView.setText("");
                scanForWatch();
            }
        });

        backButton = findViewById(R.id.back);
        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        textView = findViewById(R.id.text_view);

        if (adapter != null && !adapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        if (this.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            this.requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_REQUEST_FINE_LOCATION);
        }

        checkForegroundPerm();
    }

    private void scanForWatch() {
        // stop scanning if currently scanning
        if (isScanning) {
            stopScan();
        }
        isScanning = true;
        // clean up last scan results
        foundMacAddresses = new HashSet<>();
        LinearLayout layout = findViewById(R.id.button_container);
        layout.removeAllViews();

        // set up scan
        ScanFilter.Builder builderSF = new ScanFilter.Builder();
        ScanSettings.Builder builderSS = new ScanSettings.Builder();
        builderSF.setDeviceName("DumbWatch");
        builderSS.setScanMode(ScanSettings.SCAN_MODE_BALANCED)
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
                .setNumOfMatches(ScanSettings.MATCH_NUM_FEW_ADVERTISEMENT)
                .setReportDelay(0);
        final ArrayList<ScanFilter> filters = new ArrayList<>();
        final ScanSettings scanSettings = builderSS.build();
        filters.add(builderSF.build());


        if (scanner != null) {
            AsyncTask.execute(new Runnable() {
                @Override
                public void run() {
                    scanner.startScan(filters, scanSettings, scanCallback);
                }
            });
            startScanButton.setText("Scanning");
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    stopScan();
                }
            }, SCAN_PERIOD);
        } else {
            Log.e(TAG, "scanForWatch: Error While Scanning");
            textView.setText("Error While Scanning");
        }
    }

    private void stopScan() {
        scanner.stopScan(scanCallback);
        startScanButton.setText("Scan");
        textView.setText("Done Scanning");
        isScanning = false;
    }

    private void createDevBtn(String name, final BluetoothDevice foundDev) {
        Button devBtn = new Button(this);
        LinearLayout layout = findViewById(R.id.button_container);
        devBtn.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        devBtn.setText(name);
        devBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startConnectFS(foundDev);
            }
        });
        layout.addView(devBtn);
    }

    private void startConnectFS(BluetoothDevice watch) {
        MAC_ADDRESS = watch.getAddress();
        Intent intent = new Intent(this, BleService.class);
        startForegroundService(intent);
    }

    @TargetApi(28)
    private void checkForegroundPerm() {
        if (this.checkSelfPermission(Manifest.permission.FOREGROUND_SERVICE) != PackageManager.PERMISSION_GRANTED) {
            this.requestPermissions(new String[]{Manifest.permission.FOREGROUND_SERVICE}, PERMISSION_REQUEST_FOREGROUND_SERVICE);
        }
    }
}
