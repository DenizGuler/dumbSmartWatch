package com.example.dumbsmartwatch;

import android.Manifest;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.KeyEvent;

import androidx.core.app.NotificationCompat;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.UUID;

import static android.bluetooth.BluetoothGatt.GATT_SUCCESS;
import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_INDICATE;
import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_NOTIFY;

public class BleService extends Service {

    private final static String OWM_KEY = "05d76d752ee05ec911af166f05651c97";
    private final static String OWM_EPT = "http://api.openweathermap.org/data/2.5/forecast?";

    private final static String SERVICE_UUID = "6e400001-b5a3-f393-e0a9-e50e24dcca9e";
    private final static String CHARA_UUID_RX = "6e400002-b5a3-f393-e0a9-e50e24dcca9e";
    private final static String CHARA_UUID_TX = "6e400003-b5a3-f393-e0a9-e50e24dcca9e";

    private final static String TAG = "BleService";
    private static final int MAX_TRIES = 5;
    private int startId = 0;
    private final String CHANNEL_ID = "290M";
    final int SERVICE_ID = 290;

    public static boolean CONNECTED = false;

    BluetoothDevice device;
    BluetoothAdapter adapter;
    public static BluetoothGatt gatt;

    private Queue<Runnable> commandQueue;
    private boolean commandQueueBusy;
    private int nrTries;
    private boolean isRetrying;
    private HashSet<UUID> notifyingCharacteristics = new HashSet<>();

    Runnable discoverServicesRunnable;
    Handler bleHandler = new Handler();

    Notification notification;

    AudioManager audioManager;
    private final static byte PAUSE_PLAY = 1;
    private final static byte NEXT_TRACK = 2;
    private final static byte PREV_TRACK = 0;

    private double lat;
    private double lon;

    public BleService() {
        super();
    }

    BluetoothGattCallback bluetoothGattCallback = new BluetoothGattCallback() {

        @Override
        public void onConnectionStateChange(final BluetoothGatt gatt, int status, int newState) {
            if (status == GATT_SUCCESS) {
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
//                                setNotify()
                                discoverServicesRunnable = null;
                            }
                        };
                        bleHandler.postDelayed(discoverServicesRunnable, delay);
                        CONNECTED = true;
                    } else if (bondState == BluetoothDevice.BOND_BONDING) {
                        // Bonding process in progress, let it complete
                        Log.i(TAG, "waiting for bonding to complete");
                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    gatt.close();
                    CONNECTED = false;
                    stopSelf();
                } else {
                    // connecting or disconnecting, ignore for now
                    Log.i(TAG, "device is either connecting or disconnecting; waiting");
                }
            } else {
                // error occurred
                gatt.close();
                CONNECTED = false;
                stopSelf();
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

            List<BluetoothGattService> services = gatt.getServices();
            ArrayList<UUID> serviceUuids = new ArrayList<>();

            for (BluetoothGattService s : services) {
                serviceUuids.add(s.getUuid());
            }

            Log.i(TAG, "onConnectionStateChange: Discovered Services:" + serviceUuids);
            BluetoothGattService service;
            BluetoothGattCharacteristic characteristic;
            if ((service = gatt.getService(UUID.fromString(SERVICE_UUID))) != null) {

                List<BluetoothGattCharacteristic> chars = service.getCharacteristics();
                ArrayList<UUID> charUuids = new ArrayList<>();
                for (BluetoothGattCharacteristic c : chars) {
                    charUuids.add(c.getUuid());
                }
                Log.i(TAG, "onServicesDiscovered: Discovered Characteristics: " + charUuids);
                if ((characteristic = service.getCharacteristic(UUID.fromString(CHARA_UUID_TX))) != null) {
                    setNotify(characteristic, true);
                }
                if ((characteristic = service.getCharacteristic(UUID.fromString(CHARA_UUID_RX))) != null) {
                    String weatherJSON = getOWMJSON(lat, lon);
//                    Log.d(TAG, "onServicesDiscovered: " + weatherJSON);
                    writeCharacteristic(characteristic, weatherJSON);
                }
            } else {
                Log.e(TAG, "onServicesDiscovered: ERROR SERVICE NOT FOUND");
            }
            super.onServicesDiscovered(gatt, status);
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status != GATT_SUCCESS) {
                Log.e(TAG, String.format(Locale.ENGLISH, "ERROR: Read failed for characteristic: %s, status %d", characteristic.getUuid(), status));
                completedCommand();
                return;
            }
            completedCommand();
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, final BluetoothGattDescriptor descriptor, final int status) {
            final BluetoothGattCharacteristic parentCharacteristics = descriptor.getCharacteristic();
            if (status != GATT_SUCCESS) {
                Log.e(TAG, "onDescriptorWrite: error");
            }
            if (descriptor.getUuid().equals(UUID.fromString(CCC_DESCRIPTOR_UUID))) {
                if (status == GATT_SUCCESS) {
                    byte[] value = descriptor.getValue();
                    if (value != null) {
                        if (value[0] != 0) {
                            // notify is turned on
                            notifyingCharacteristics.add(parentCharacteristics.getUuid());
                        }
                    } else {
                        // notify was turned off
                        notifyingCharacteristics.remove(parentCharacteristics.getUuid());
                    }
                }
                // this was a setNotify op
            } else {
                // this was a normal descriptor write

            }
            completedCommand();
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            byte value = characteristic.getValue()[0];
//            String value = Base64.getEncoder().encodeToString(characteristic.getValue());
            Log.i(TAG, "read value: " + value);
            KeyEvent downEvent;
            KeyEvent upEvent;
            switch (value) {
                case PAUSE_PLAY:
                    downEvent = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
                    audioManager.dispatchMediaKeyEvent(downEvent);

                    upEvent = new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
                    audioManager.dispatchMediaKeyEvent(upEvent);
                    break;
                case NEXT_TRACK:
                    downEvent = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT);
                    audioManager.dispatchMediaKeyEvent(downEvent);
                    break;
                case PREV_TRACK:
                    downEvent = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS);
                    audioManager.dispatchMediaKeyEvent(downEvent);
                    break;
                default:
                    break;
            }
        }
    };

    @Override
    public void onCreate() {
        //TODO: set up var etc.
        super.onCreate();
        Intent notificationIntent = new Intent(this, BleService.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
        commandQueue = new LinkedList<>();

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

        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        Location loc = null;
        if (locationManager != null) {
            loc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        }
        if (loc == null) {
            Log.e(TAG, "location is null");
            return;
        }
        lat = loc.getLatitude();
        lon = loc.getLongitude();
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
        if (CONNECTED) {
            Log.i(TAG, "onDestroy: disconnecting gatt");
            gatt.disconnect();
            gatt.close();
            gatt = null;
            CONNECTED = false;
        }
        stopForeground(true);
        super.onDestroy();
    }

    public boolean readCharacteristic(final BluetoothGattCharacteristic characteristic) {
        if (gatt == null) {
            Log.e(TAG, "ERROR: Gatt is 'null', ignoring read request");
            return false;
        }

        // Check if characteristic is valid
        if (characteristic == null) {
            Log.e(TAG, "ERROR: Characteristic is 'null', ignoring read request");
            return false;
        }

        // Check if this characteristic actually has READ property
        if ((characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_READ) == 0) {
            Log.e(TAG, "ERROR: Characteristic cannot be read");
            return false;
        }

        // Enqueue the read command now that all checks have been passed
        boolean result = commandQueue.add(new Runnable() {
            @Override
            public void run() {
                if (!gatt.readCharacteristic(characteristic)) {
                    Log.e(TAG, String.format("ERROR: readCharacteristic failed for characteristic: %s", characteristic.getUuid()));
                    completedCommand();
                } else {
                    Log.d(TAG, String.format("reading characteristic <%s>", characteristic.getUuid()));
                    nrTries++;
                }
            }
        });

        if (result) {
            nextCommand();
        } else {
            Log.e(TAG, "ERROR: Could not enqueue read characteristic command");
        }
        return result;
    }

    public boolean writeCharacteristic(final BluetoothGattCharacteristic characteristic, String data) {
        if (adapter == null || gatt == null) {
            Log.w(TAG, "writeCharacteristic: adapter not init");
            return false;
        }

        Log.i(TAG, "writeCharacteristic: characteristic: " + characteristic.toString());
        try {
            Log.i(TAG, "writeCharacteristic: data: " + URLEncoder.encode(data, "utf-8"));
            characteristic.setValue(URLEncoder.encode(data, "utf-8"));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        Log.d(TAG, "writeCharacteristic: " + characteristic.getUuid());

        boolean result = commandQueue.add(new Runnable() {
            @Override
            public void run() {
                if (!gatt.writeCharacteristic(characteristic)) {
                    Log.e(TAG, "Write characteristic failed");
                    completedCommand();
                } else {
                    Log.d(TAG, "Writing characteristic to device");
                    nrTries++;
                }
            }
        });

        if (result) {
            nextCommand();
        } else {
            Log.e(TAG, "writeCharacteristic: ERROR: could not enqueue write characteristic command");
        }
        return result;
    }

    private void nextCommand() {
        // make sure the queue is not busy
        if (commandQueueBusy) {
            return;
        }

        if (gatt == null) {
            Log.e(TAG, "nextCommand: ERROR: Gatt is null; clearing command queue");
            commandQueue.clear();
            commandQueueBusy = false;
            return;
        }

        if (commandQueue.size() > 0) {
            final Runnable bleCommand = commandQueue.peek();
            commandQueueBusy = true;
            nrTries = 0;

            bleHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        bleCommand.run();
                    } catch (Exception ex) {
                        Log.e(TAG, "run: ", ex);
                    }
                }
            });
        }
    }

    private void completedCommand() {
        commandQueueBusy = false;
        isRetrying = false;
        commandQueue.poll();
        nextCommand();
    }

    private void retryCommand() {
        commandQueueBusy = false;
        Runnable currCommand = commandQueue.peek();
        if (currCommand != null) {
            if (nrTries >= MAX_TRIES) {
                Log.v(TAG, "Maximum number of tries reached");
                commandQueue.poll();
            } else {
                isRetrying = true;
            }
        }
        nextCommand();
    }

    private final String CCC_DESCRIPTOR_UUID = "00002902-0000-1000-8000-00805f9b34fb";

    public boolean setNotify(BluetoothGattCharacteristic characteristic, final boolean enable) {
        // Check if characteristic is valid
        if (characteristic == null) {
            Log.e(TAG, "ERROR: Characteristic is 'null', ignoring setNotify request");
            return false;
        }

        // Get the CCC Descriptor for the characteristic
        final BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID.fromString(CCC_DESCRIPTOR_UUID));
        if (descriptor == null) {
            Log.e(TAG, String.format("ERROR: Could not get CCC descriptor for characteristic %s", characteristic.getUuid()));
            return false;
        }

        // Check if characteristic has NOTIFY or INDICATE properties and set the correct byte value to be written
        byte[] value;
        int properties = characteristic.getProperties();
        if ((properties & PROPERTY_NOTIFY) > 0) {
            value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE;
        } else if ((properties & PROPERTY_INDICATE) > 0) {
            value = BluetoothGattDescriptor.ENABLE_INDICATION_VALUE;
        } else {
            Log.e(TAG, String.format("ERROR: Characteristic %s does not have notify or indicate property", characteristic.getUuid()));
            return false;
        }
        final byte[] finalValue = enable ? value : BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE;

        // Queue Runnable to turn on/off the notification now that all checks have been passed
        boolean result = commandQueue.add(new Runnable() {
            @Override
            public void run() {
                // First set notification for Gatt object
                if (!gatt.setCharacteristicNotification(descriptor.getCharacteristic(), enable)) {
                    Log.e(TAG, String.format("ERROR: setCharacteristicNotification failed for descriptor: %s", descriptor.getUuid()));
                }
                // Then write to descriptor
                descriptor.setValue(finalValue);
                boolean result = gatt.writeDescriptor(descriptor);
                if (!result) {
                    Log.e(TAG, String.format("ERROR: writeDescriptor failed for descriptor: %s", descriptor.getUuid()));
                    completedCommand();
                } else {
                    nrTries++;
                }
            }
        });

        if (result) {
            nextCommand();
        } else {
            Log.e(TAG, "ERROR: Could not enqueue write command");
        }
        return result;
    }

    public String getOWMJSON (double lat, double lon) {
        HttpURLConnection con = null;
        InputStream stream = null;
        try {
            con = (HttpURLConnection) (new URL(OWM_EPT + "lat=" + lat + "&lon=" + lon + "&APPID=" + OWM_KEY)).openConnection();
            con.setRequestMethod("GET");
            con.setDoInput(true);
            con.setDoOutput(true);
            con.connect();

            StringBuilder buff = new StringBuilder();
            stream = con.getInputStream();
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(stream));
            String line;
            while((line = bufferedReader.readLine()) != null) {
                buff.append(line).append("rn");
            }
            stream.close();
            con.disconnect();
            return buff.toString();
        } catch (Throwable t) {
            Log.e(TAG, "getOWMJSON: ", t);
        } finally {
            try {
                stream.close();
            } catch (Throwable t) { }
            try {
                con.disconnect();
            } catch (Throwable t) { }
        }
        return null;
    }
}
