package com.bryansalisbury.btmeasure.bluno;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import com.bryansalisbury.btmeasure.MeasureActivity;

import java.util.ArrayList;
import java.util.List;

public class Bluno {
    private final static String TAG = Bluno.class.getSimpleName();
    private static final String SerialPortUUID = "0000dfb1-0000-1000-8000-00805f9b34fb";
    private static final String CommandUUID = "0000dfb2-0000-1000-8000-00805f9b34fb";
    private static final String ModelNumberStringUUID = "00002a24-0000-1000-8000-00805f9b34fb";
    private static final String mPassword = "AT+PASSWORD=DFRobot\r\n";

    // Connection State
    private enum connectionStateEnum {isNull, isScanning, isToScan, isConnecting, isConnected, isDisconnecting};

    private static BluetoothGattCharacteristic
            mSCharacteristic,
            mModelNumberCharacteristic,
            mSerialPortCharacteristic,
            mCommandCharacteristic;
    private ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics = new ArrayList<ArrayList<BluetoothGattCharacteristic>>();
    private BluetoothLeService mBluetoothLeService;

    private ArrayList<Byte> mRawMessageBuffer = new ArrayList<>(); // used to temporarily store partially transmitted message

    private Context mainContext;

    // Public Variables -- Can be accessed on the Bluno object
    connectionStateEnum connectionState = connectionStateEnum.isNull;
    boolean connected;
    String mDeviceAddress;
    int baudRate;

    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            System.out.println("mServiceConnection onServiceConnected");
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                ((Activity) mainContext).finish();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            System.out.println("mServiceConnection onServiceDisconnected");
            mBluetoothLeService = null;
        }
    };

    private void getGattServices(List<BluetoothGattService> gattServices) {
        if (gattServices == null) return;
        String uuid = null;
        mModelNumberCharacteristic = null;
        mSerialPortCharacteristic = null;
        mCommandCharacteristic = null;
        mGattCharacteristics = new ArrayList<ArrayList<BluetoothGattCharacteristic>>();

        // Loops through available GATT Services.
        for (BluetoothGattService gattService : gattServices) {
            uuid = gattService.getUuid().toString();
            System.out.println("displayGattServices + uuid=" + uuid);

            List<BluetoothGattCharacteristic> gattCharacteristics =
                    gattService.getCharacteristics();
            ArrayList<BluetoothGattCharacteristic> charas =
                    new ArrayList<BluetoothGattCharacteristic>();

            // Loops through available Characteristics.
            for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                charas.add(gattCharacteristic);
                uuid = gattCharacteristic.getUuid().toString();
                if (uuid.equals(ModelNumberStringUUID)) {
                    mModelNumberCharacteristic = gattCharacteristic;
                    System.out.println("mModelNumberCharacteristic  " + mModelNumberCharacteristic.getUuid().toString());
                } else if (uuid.equals(SerialPortUUID)) {
                    mSerialPortCharacteristic = gattCharacteristic;
                    System.out.println("mSerialPortCharacteristic  " + mSerialPortCharacteristic.getUuid().toString());
//                    updateConnectionState(R.string.comm_establish);
                } else if (uuid.equals(CommandUUID)) {
                    mCommandCharacteristic = gattCharacteristic;
                    System.out.println("mSerialPortCharacteristic  " + mSerialPortCharacteristic.getUuid().toString());
//                    updateConnectionState(R.string.comm_establish);
                }
            }
            mGattCharacteristics.add(charas);
        }

        if (mModelNumberCharacteristic == null || mSerialPortCharacteristic == null || mCommandCharacteristic == null) {
            Toast.makeText(mainContext, "Please select DFRobot devices", Toast.LENGTH_SHORT).show();
           connectionState = connectionStateEnum.isToScan;
        } else {
            mSCharacteristic = mModelNumberCharacteristic;
            mBluetoothLeService.setCharacteristicNotification(mSCharacteristic, true);
            mBluetoothLeService.readCharacteristic(mSCharacteristic);
        }

    }

    private byte[] getRawMessageData(){
        byte[] data = new byte[mRawMessageBuffer.size()];
        for(int i = 0; i < mRawMessageBuffer.size(); i++ ){
            data[i] = mRawMessageBuffer.get(i);
        }
        return data;
    }

    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    //                        or notification operations.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @SuppressLint("DefaultLocale")
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                if (mSCharacteristic == mSerialPortCharacteristic) {
                    if(intent.getStringExtra(BluetoothLeService.EXTRA_DATA) != null) {
                        // TODO Fire Intent to broadcast receiver for data acquisition
                        //onSerialReceived(intent.getStringExtra(BluetoothLeService.EXTRA_DATA));
                        Log.d(TAG, intent.getStringExtra(BluetoothLeService.EXTRA_DATA));
                    }
                    if(intent.getByteArrayExtra(BluetoothLeService.EXTRA_DATA_RAW) != null) {
                        //onSerialReceivedRaw(intent.getByteArrayExtra(BluetoothLeService.EXTRA_DATA_RAW));
                        for(byte b : intent.getByteArrayExtra(BluetoothLeService.EXTRA_DATA_RAW)){
                            if(b == 10){
                                Intent messageIntent = new Intent(MeasureActivity.ACTION_MESSAGE_AVAILABLE);
                                messageIntent.putExtra(MeasureActivity.EXTRA_VALUE, getRawMessageData());
                                mainContext.sendBroadcast(messageIntent);
                                mRawMessageBuffer.clear();
                            }else{
                                mRawMessageBuffer.add(b);
                            }
                        }

                    }
                } else if (mSCharacteristic == mModelNumberCharacteristic) {
                    //if (intent.getStringExtra(BluetoothLeService.EXTRA_DATA).toUpperCase().startsWith("DF BLUNO")) {
                    mBluetoothLeService.setCharacteristicNotification(mSCharacteristic, false); //disable notifications for model number
                    mSCharacteristic = mCommandCharacteristic;

                    // Set Password
                    mSCharacteristic.setValue(mPassword);
                    mBluetoothLeService.writeCharacteristic(mSCharacteristic);

                    // Set Baud Rate
                    String mBaudrateBuffer = "AT+UART=" + baudRate + "\r\n";
                    mSCharacteristic.setValue(mBaudrateBuffer);
                    mBluetoothLeService.writeCharacteristic(mSCharacteristic);

                    // Notifications on for serial port characteristic
                    mSCharacteristic = mSerialPortCharacteristic;
                    mBluetoothLeService.setCharacteristicNotification(mSCharacteristic, true);

                    // Set connected flag
                    connectionState = connectionStateEnum.isConnected;
                }

            } else if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                connected = true;
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                connected = false;
                connectionState = connectionStateEnum.isToScan;
                mBluetoothLeService.close();
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                // Show all the supported services and characteristics on the user interface.
                for (BluetoothGattService gattService : mBluetoothLeService.getSupportedGattServices()) {
                    System.out.println("ACTION_GATT_SERVICES_DISCOVERED  " +
                            gattService.getUuid().toString());
                }
                getGattServices(mBluetoothLeService.getSupportedGattServices());

            /*
                This is where data received event is handled
             */
            }
        }
    };

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }


    // Constructor
    public Bluno(Context context) {
        this.mainContext = context;
        this.baudRate = 115200;

        /*
        if (!mBluetoothAdapter.isEnabled()) {
            if (!mBluetoothAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(
                        BluetoothAdapter.ACTION_REQUEST_ENABLE);
                ((Activity) mainContext).startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
        }*/

        // bind to BT LE service
        Intent gattServiceIntent = new Intent(mainContext, BluetoothLeService.class);
        mainContext.bindService(gattServiceIntent, mServiceConnection, Context.BIND_AUTO_CREATE);
        mainContext.registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
    }

    public void connect(String deviceAddress){
        this.mDeviceAddress = deviceAddress;
        mBluetoothLeService.connect(deviceAddress);
    }

    public void pause(){
        mainContext.unregisterReceiver(mGattUpdateReceiver);
    }

    public void resume(){
        mainContext.registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
    }

    public void close(){
        mBluetoothLeService.close();
        mainContext.unregisterReceiver(mGattUpdateReceiver);
    }

    public void send(String text) {
        if (connectionState == connectionStateEnum.isConnected) {
            mSCharacteristic.setValue(text);
            mBluetoothLeService.writeCharacteristic(mSCharacteristic);
        }
    }

    public boolean setGattPriority(boolean high){
        return mBluetoothLeService.setHighPriority(high);
    }
}
