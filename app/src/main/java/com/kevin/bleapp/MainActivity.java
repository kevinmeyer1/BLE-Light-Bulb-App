package com.kevin.bleapp;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.admin.SystemUpdateInfo;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.UUID;

import static android.bluetooth.BluetoothAdapter.STATE_CONNECTED;
import static android.bluetooth.BluetoothProfile.STATE_DISCONNECTED;

public class MainActivity extends AppCompatActivity {

    public BluetoothAdapter bluetoothAdapter;
    public BluetoothLeScanner bluetoothLeScanner;
    public BluetoothManager bluetoothManager;
    public BluetoothGatt bluetoothGatt;

    public BluetoothGattService gattService;
    public BluetoothGattCharacteristic bulbChar;
    public BluetoothGattCharacteristic tempChar;
    public BluetoothGattCharacteristic beepChar;

    public boolean charsAssigned;
    public int beepStatus;

    public final String BLEuuid = "dfd650e7-bf8e-388d-ae77-af4911d4ff74";
    public final String bulbUUID = "fb959362-f26e-43a9-927c-7e17d8fb2d8d";
    public final String tempUUID = "0ced9345-b31f-457d-a6a2-b3db9b03e39a";
    public final String beepUUID = "ec958823-f26e-43a9-927c-7e17d8f32a90";

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;

    private Button btnOn;
    private Button btnOff;
    private Button btnBeep;
    private TextView lblTemp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "BLE not supported", Toast.LENGTH_SHORT).show();
            finish();
        }

        requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 0);

        charsAssigned = false;
        beepStatus = 0;

        bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();

        bluetoothAdapter.startLeScan(new UUID[]{UUID.fromString(BLEuuid)}, leScanCallback);

        lblTemp = findViewById(R.id.lblTemp);

        btnOn = findViewById(R.id.btnOn);
        btnOn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bulbOn();
            }
        });

        btnOff = findViewById(R.id.btnOff);
        btnOff.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bulbOff();
            }
        });

        btnBeep = findViewById(R.id.btnBeep);
        btnBeep.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                beep();
            }
        });
    }

    private BluetoothAdapter.LeScanCallback leScanCallback =
            new BluetoothAdapter.LeScanCallback() {
                @Override
                public void onLeScan(final BluetoothDevice device, int rssi,
                                     byte[] scanRecord) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            bluetoothGatt = device.connectGatt(getBaseContext(), false, gattCallback);
                        }
                    });
                }
            };

    private final BluetoothGattCallback gattCallback =
            new BluetoothGattCallback() {
                @Override
                public void onConnectionStateChange(BluetoothGatt gatt, int status,
                                                    int newState) {
                    switch (newState) {
                        case STATE_DISCONNECTED:
                            System.out.println("---DISCONNECTED---");
                            break;
                        case STATE_CONNECTING:
                            System.out.println("---CONNECTING---");
                            break;
                        case STATE_CONNECTED:
                            System.out.println("---CONNECTED---");
                            bluetoothGatt.discoverServices();
                            break;
                    }
                }

                @Override
                public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                    for (BluetoothGattService service : gatt.getServices()) {
                        if (service.getUuid().toString().equals(BLEuuid)) {
                            gattService = service;
                        }
                    }

                    if (!charsAssigned) {
                        assignCharacteristics();
                    }

                }

                @Override
                // Result of a characteristic read operation
                public void onCharacteristicRead(BluetoothGatt gatt,
                                                 BluetoothGattCharacteristic characteristic,
                                                 int status) {
                    // ------------ NOT USED --------------
                }

                @Override
                // Characteristic notification
                public void onCharacteristicChanged(BluetoothGatt gatt,
                                                    BluetoothGattCharacteristic characteristic) {
                    System.out.println(characteristic.getUuid().toString());
                    if (characteristic.getUuid().toString().equals(tempUUID)) {
                        String temp = new String(characteristic.getValue());
                        lblTemp.setText(temp + " F");
                    }
                }
            };

    public void assignCharacteristics() {
        for (BluetoothGattCharacteristic ch : gattService.getCharacteristics()) {
            switch (ch.getUuid().toString()) {
                case bulbUUID:
                    bulbChar = ch;
                    break;
                case tempUUID:
                    tempChar = ch;
                    bluetoothGatt.setCharacteristicNotification(tempChar, true);
                    break;
                case beepUUID:
                    beepChar = ch;
                    break;
            }
        }

        charsAssigned = true;
    }

    public void bulbOn() {
        byte bytes[] = {new Integer(1).byteValue()};
        bulbChar.setValue(bytes);
        bluetoothGatt.writeCharacteristic(bulbChar);
    }

    public void bulbOff() {
        byte bytes[] = {new Integer(0).byteValue()};
        bulbChar.setValue(bytes);
        bluetoothGatt.writeCharacteristic(bulbChar);
    }

    public void beep() {
        if (beepStatus == 0) {
            byte bytes[] = {new Integer(1).byteValue()};
            beepChar.setValue(bytes);
            bluetoothGatt.writeCharacteristic(beepChar);
            beepStatus = 1;
        } else {
            byte bytes[] = {new Integer(0).byteValue()};
            beepChar.setValue(bytes);
            bluetoothGatt.writeCharacteristic(beepChar);
            beepStatus = 0;
        }
    }
}
