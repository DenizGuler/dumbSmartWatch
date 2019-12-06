package com.example.dumbsmartwatch;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.nfc.Tag;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    Button startDeviceScanButton;
    Button settings;
    Button disconnect;
    LinearLayout menu;

    BluetoothDevice device;
    BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
    BluetoothLeScanner scanner = adapter.getBluetoothLeScanner();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        createNotificationChannel();

        menu = findViewById(R.id.menu_layout);

        startDeviceScanButton = findViewById(R.id.SetUp);
        startDeviceScanButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openDeviceScan();
            }
        });

        disconnect = findViewById(R.id.disconnect_button);
        final Intent bleIntent = new Intent(this, BleService.class);
        final Handler visHandler = new Handler();
        disconnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopService(bleIntent);
                visHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        updateVisibility(BleService.CONNECTED);
                    }
                }, 500);
            }
        });

        settings = findViewById(R.id.settings);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateVisibility(BleService.CONNECTED);
    }

    public void updateVisibility(boolean vis) {
        if (vis) {
            menu.setVisibility(View.VISIBLE);
            startDeviceScanButton.setVisibility(View.INVISIBLE);
        } else {
            menu.setVisibility(View.INVISIBLE);
            startDeviceScanButton.setVisibility(View.VISIBLE);
        }
    }

    private void createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Dumb Smart Watch";
            String description = "channel for dumb smart watch";
            int importance = NotificationManager.IMPORTANCE_LOW;
            NotificationChannel channel = new NotificationChannel("290M", name, importance);
            channel.setDescription(description);
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private void openDeviceScan() {
        startActivity(new Intent(this, DeviceScanActivity.class));
    }
}
