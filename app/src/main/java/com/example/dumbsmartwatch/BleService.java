package com.example.dumbsmartwatch;

import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.IBinder;

public class BleService extends Service {
    public BleService(BluetoothDevice device) {

    }

    @Override
    public void onCreate() {
        //TODO: set up var etc.
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        //TODO: establish a connection and open info stream
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        //TODO: disconnect from device
        super.onDestroy();
    }
}
