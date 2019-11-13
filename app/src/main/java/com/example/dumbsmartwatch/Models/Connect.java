package com.example.dumbsmartwatch.Models;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.bluetooth.BluetoothAdapter;

public class Connect {

    private final static int REQUEST_ENABLE_BT = 1;

    public static void toWatch() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();

//        if (adapter == null) {
//            throw new java.lang.Error("device does not support bluetooth");
//        }

//        if (!adapter.isEnabled()) {
//            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
//            // startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT); have to start a new activity...
//            // looking into what that means
//        }
    }
}
