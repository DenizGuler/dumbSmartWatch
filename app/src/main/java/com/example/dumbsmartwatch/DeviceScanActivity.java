package com.example.dumbsmartwatch;

import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
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
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.util.ArrayList;

public class DeviceScanActivity extends AppCompatActivity {

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

    private ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice foundDev = result.getDevice();
            textView.append("Found Name: " + foundDev.getName() + ", RSSI: " + result.getRssi() + "\n");
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_scan);

        adapter = BluetoothAdapter.getDefaultAdapter();
        scanner = adapter.getBluetoothLeScanner();

        startScanButton = findViewById(R.id.startScanning);
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
        textView = findViewById(R.id.textView);

        if (adapter != null && !adapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        if (this.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            this.requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_REQUEST_FINE_LOCATION);
        }


    }

    private void scanForWatch() {
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
            textView.setText("Error While Scanning");
        }
    }

    private void stopScan() {
        scanner.stopScan(scanCallback);
        startScanButton.setText("Scan");
        textView.append("~~~Done Scanning~~~\n");
    }
}
