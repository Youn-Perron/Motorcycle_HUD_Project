package com.lavie.motorcycle_HUD_Instructables;

import android.widget.Toast;
import android.bluetooth.BluetoothAdapter;

import com.harrysoft.androidbluetoothserial.BluetoothManager;
import com.harrysoft.androidbluetoothserial.SimpleBluetoothDeviceInterface;
import com.harrysoft.androidbluetoothserial.BluetoothSerialDevice;

import io.reactivex.schedulers.Schedulers;
import io.reactivex.android.schedulers.AndroidSchedulers;


public class BluetoothManagement extends MainActivity {

    private String macAddress = "!!! ENTER YOUR B MAC ADDRESS HERE !!!";
    private SimpleBluetoothDeviceInterface deviceInterface;
    private BluetoothManager bluetoothManager;
    public Boolean isBTSet = false;
    private Boolean connectionAttemptedOrMade = false;


    boolean setupBT_noErrors() {
        if (isBTSet == false) {
            isBTSet = true;
            bluetoothManager = BluetoothManager.getInstance();

            if (bluetoothManager == null) {         // Bluetooth unavailable on this device ?
                Toast.makeText(MainActivity.context, "Bluetooth not available on this device.", Toast.LENGTH_LONG).show();
                return false;
            } else if (!isBluetoothEnabled()) {     // Bluetooth is off ?
                Toast.makeText(MainActivity.context, "Bluetooth is off.", Toast.LENGTH_LONG).show();
                return false;
            } else return true;     // Bluetooth available and turned on : everything is ok
        } else {
            Toast.makeText(MainActivity.context, "Bluetooth being/is already set up.", Toast.LENGTH_LONG).show();
            return false;
        }
    }

    boolean connectBT_noErrors() {
        if (!connectionAttemptedOrMade) {
            Toast.makeText(MainActivity.context, "Setting up BT ...", Toast.LENGTH_LONG).show();
            bluetoothManager.openSerialDevice(macAddress)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(this::onConnected, this::onError);
            connectionAttemptedOrMade = true;
            return true;
        } else return false;
    }

    void closeBT() {
        if (connectionAttemptedOrMade && deviceInterface != null) {
            Toast.makeText(MainActivity.context, "Closing all BT devices ...", Toast.LENGTH_LONG).show();
            bluetoothManager.close();
            deviceInterface = null;
            connectionAttemptedOrMade = false;
            isBTSet = false;
        }
    }

    void sendMsg(String msg) {
        deviceInterface.sendMessage(msg);
    }

    public boolean isBluetoothEnabled()
    {
        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        return mBluetoothAdapter.isEnabled();
    }

    private void onConnected(BluetoothSerialDevice connectedDevice) {
        Toast.makeText(MainActivity.context, "BLE target connected !", Toast.LENGTH_LONG).show();
        // retain an instance to your device:
        deviceInterface = connectedDevice.toSimpleDeviceInterface();
        // Listen to bluetooth events
        deviceInterface.setListeners(this::onMessageReceived, this::onMessageSent, this::onError);
    }

    private void onMessageSent(String message) {
        // We sent a message! Handle it here.
        Toast.makeText(MainActivity.context, "Sent a message! Message was: " + message, Toast.LENGTH_LONG).show();
    }

    private void onMessageReceived(String message) {
        // We received a message! Handle it here.
        Toast.makeText(MainActivity.context, "Received a message! Message was: " + message, Toast.LENGTH_LONG).show();
    }

    private void onError(Throwable error) {
        //Toast.makeText(this, "ERROR !", Toast.LENGTH_LONG).show();  Errors should be handled
    }
}
