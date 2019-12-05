package com.example.dumbsmartwatch;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothProfile;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.util.Locale;

public class BleService extends Service {

    private final static String TAG = "BleService";
    private int startId = 0;
    private final String CHANNEL_ID = "290M";
    final int SERVICE_ID = 290;

    BluetoothDevice device;
    BluetoothAdapter adapter;
    BluetoothGatt gatt;

    Runnable discoverServicesRunnable;
    Handler bleHandler = new Handler();

    Notification notification;


    public BleService() {
        super();
    }

    BluetoothGattCallback bluetoothGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(final BluetoothGatt gatt, int status, int newState) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    int bondState = device.getBondState();
                    // Take action depending on the bond state
                    if (bondState == BluetoothDevice.BOND_NONE || bondState == BluetoothDevice.BOND_BONDED) {

                        // Connected to device, now proceed to discover it's services but delay a bit if needed
                        int delayWhenBonded = 0;
                        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N) {
                            delayWhenBonded = 1000;
                        }
                        final int delay = bondState == BluetoothDevice.BOND_BONDED ? delayWhenBonded : 0;
                        discoverServicesRunnable = new Runnable() {
                            @Override
                            public void run() {
                                Log.d(TAG, String.format(Locale.ENGLISH, "discovering services of '%s' with delay of %d ms", device.getName(), delay));
                                boolean result = gatt.discoverServices();
                                if (!result) {
                                    Log.e(TAG, "discoverServices failed to start");
                                }
                                discoverServicesRunnable = null;
                            }
                        };
                        bleHandler.postDelayed(discoverServicesRunnable, delay);
                    } else if (bondState == BluetoothDevice.BOND_BONDING) {
                        // Bonding process in progress, let it complete
                        Log.i(TAG, "waiting for bonding to complete");
                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    gatt.close();
                } else {
                    // connecting or disconnecting, ignore
                }
            } else {
                // error occurred
                gatt.close();
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            // Check if the service discovery succeeded. If not disconnect
            if (status == BluetoothGatt.GATT_FAILURE) {
                Log.e(TAG, "Service discovery failed");
                gatt.disconnect();
                return;
            }
            super.onServicesDiscovered(gatt, status);
        }
    };

    @Override
    public void onCreate() {
        //TODO: set up var etc.
        super.onCreate();
        Intent notificationIntent = new Intent(this, BleService.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_name)
                .setContentTitle("Dumb Watch")
                .setContentText("Connected to watch")
                .setContentIntent(pendingIntent);
        notification = builder.build();
        adapter = BluetoothAdapter.getDefaultAdapter();
        Log.d(TAG, "onCreate: Device MAC ADDRESS: " + DeviceScanActivity.MAC_ADDRESS);
        device = adapter.getRemoteDevice(DeviceScanActivity.MAC_ADDRESS);

        gatt = device.connectGatt(this, false, bluetoothGattCallback, BluetoothDevice.TRANSPORT_LE);


    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || this.startId != 0) {
            // service restarted
            Log.d(TAG, "onStartCommand: already running");
        } else {
            this.startId = startId;
            startForeground(SERVICE_ID, notification);
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        //TODO: disconnect from device
        startId = 0;
        gatt.disconnect();
        super.onDestroy();
    }
}
