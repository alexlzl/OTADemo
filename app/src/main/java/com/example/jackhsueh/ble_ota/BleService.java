package com.example.jackhsueh.ble_ota;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.UUID;

public class BleService extends Service {
    public final static String GATT_CONNECTED = "com.bluetooth.ble.GATT_CONNECTED";
    public final static String GATT_DISCONNECTED = "com.bluetooth.ble.GATT_DISCONNECTED";
    public final static String GATT_SERVICES_DISCOVERED = "com.bluetooth.ble.GATT_SERVICES_DISCOVERED";

    public final static String ACTION_DATA_CHANGE = "com.bluetooth.ble.ACTION_DATA_CHANGE";
    public final static String ACTION_DATA_READ = "com.bluetooth.ble.ACTION_DATA_READ";
    public final static String ACTION_DATA_WRITE = "com.bluetooth.ble.ACTION_DATA_WRITE";
    public final static String ACTION_DATA_CHANGED = "com.bluetooth.ble.ACTION_DATA_CHANGED";

    public final static String ACTION_RSSI_READ = "com.bluetooth.ble.ACTION_RSSI_READ";

    public final static String ACTION_DATA_MTU_CHANGED = "com.bluetooth.ble.ACTION_DATA_MTU_CHANGED";

    public static final UUID OTA_UPDATE_SERVICE_UUID = UUID.fromString("0000ff00-0000-1000-8000-00805f9b34fb"); // The UUID for service "FF00"
    public static final UUID OTA_UPDATE_CHARACTERISTIC_UUID = UUID.fromString("0000ff01-0000-1000-8000-00805f9b34fb"); // The UUID for service "FF01"

    public static final UUID UART_SERVICE_UUID = UUID.fromString("00000001-0000-1000-8000-00805f9b34fb"); // The UUID for service "0001"
    public static final UUID UART_CHARACTERISTIC_READ_UUID = UUID.fromString("00000002-0000-1000-8000-00805f9b34fb"); // The UUID for service "0002"
    public static final UUID UART_CHARACTERISTIC_NOTIFY_UUID = UUID.fromString("00000003-0000-1000-8000-00805f9b34fb"); // The UUID for service "0003"

    public static final UUID GAP_SERVICE_UUID = UUID.fromString("00001800-0000-1000-8000-00805f9b34fb"); // The UUID for service GAP"1800"
    public static final UUID GAPNAME_CHARACTERISTIC_READ_UUID = UUID.fromString("00002A00-0000-1000-8000-00805f9b34fb"); // The UUID for name "2a00"

    public static final UUID CCCD_DESCRIPTOR = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"); // The cccd Descriptor

    public BluetoothManager mBluetoothManager;
    public BluetoothAdapter mBluetoothAdapter;
    public BluetoothGatt mBluetoothGatt;

    BluetoothGattService GATT_Service_ota_update = null;
    BluetoothGattCharacteristic characteristic_ota_update = null;
    BluetoothGattCharacteristic characteristic_uart_notify = null;
    BluetoothGattService GATT_Service_uart = null;
    BluetoothGattCharacteristic characteristic_uart_write = null;
    BluetoothGattService GAP_Service = null;
    BluetoothGattCharacteristic characteristic_gapname_read = null;

    public boolean BlutoothConnectStatue = false;
    public boolean isDiscoverServices = false;

    private final BluetoothGattCallback bluetoothGattCallback = new BluetoothGattCallback(){

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            if (newState == BluetoothProfile.STATE_CONNECTED){
                Log.i("SYD_OTA", "GattCallback onConnectionStateChange STATE_CONNECTED status: " + status + "newState" + newState);

                mBluetoothGatt.discoverServices();
                BlutoothConnectStatue = true;
                broadcastUpdate(GATT_CONNECTED);
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                BlutoothConnectStatue = false;
                broadcastUpdate(GATT_DISCONNECTED);
                Log.i("SYD_OTA", "GattCallback onConnectionStateChange STATE_DISCONNECTED status: " + status);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            Log.i("SYD_OTA", "GattCallback onServicesDiscovered status: " + status);
            isDiscoverServices = true;
            if ( status == BluetoothGatt.GATT_SUCCESS ) {
                GATT_Service_ota_update = mBluetoothGatt.getService(OTA_UPDATE_SERVICE_UUID);
                if (GATT_Service_ota_update == null){
                    Log.i("SYD_OTA", "GattCallback GATT_Service_ota_update is null");
                    return;
                }
                Log.i("SYD_OTA", "OTA_UPDATE_SERVICE_UUID is "+OTA_UPDATE_SERVICE_UUID.toString());

                characteristic_ota_update = GATT_Service_ota_update.getCharacteristic(OTA_UPDATE_CHARACTERISTIC_UUID);
                if (characteristic_ota_update == null) {
                    Log.i("SYD_OTA", "GattCallback characteristic_ota_update is null");
                    return;
                }
                Log.i("SYD_OTA", "OTA_UPDATE_CHARACTERISTIC_UUID is "+OTA_UPDATE_CHARACTERISTIC_UUID.toString());

                GATT_Service_uart = mBluetoothGatt.getService(UART_SERVICE_UUID);
                if (GATT_Service_uart == null){
                    Log.i("SYD_OTA", "GattCallback GATT_Service_uart is null");
                    return;
                }
                Log.i("SYD_OTA", "UART_SERVICE_UUID is "+UART_SERVICE_UUID.toString());

                characteristic_uart_write = GATT_Service_uart.getCharacteristic(UART_CHARACTERISTIC_READ_UUID);
                if (characteristic_uart_write == null) {
                    Log.i("SYD_OTA", "GattCallback characteristic_uart_write is null");
                    return;
                }
                Log.i("SYD_OTA", "UART_CHARACTERISTIC_READ_UUID is "+UART_CHARACTERISTIC_READ_UUID.toString());

                characteristic_uart_notify = GATT_Service_uart.getCharacteristic(UART_CHARACTERISTIC_NOTIFY_UUID);
                if (characteristic_uart_notify == null) {
                    Log.i("SYD_OTA", "GattCallback characteristic_uart_notify is null");
                    return;
                }
                else
                {
                    BluetoothGattDescriptor descriptor = characteristic_uart_notify.getDescriptor(CCCD_DESCRIPTOR);
                    if (descriptor != null) {
                        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                        mBluetoothGatt.writeDescriptor(descriptor);
                    }
                    mBluetoothGatt.setCharacteristicNotification(characteristic_uart_notify, true);
                    Log.i("SYD_OTA", "setCharacteristicNotification true");
                }
                Log.i("SYD_OTA", "UART_NOTIFY_CHARACTERISTIC_UUID is " +UART_CHARACTERISTIC_NOTIFY_UUID.toString());

                GAP_Service = mBluetoothGatt.getService(GAP_SERVICE_UUID);
                if (GAP_Service == null){
                    Log.i("SYD_OTA", "GattCallback GAP_Service is null");
                    return;
                }
                Log.i("SYD_OTA", "GAP_SERVICE_UUID is "+GAP_SERVICE_UUID.toString());

                characteristic_gapname_read = GAP_Service.getCharacteristic(GAPNAME_CHARACTERISTIC_READ_UUID);
                if (characteristic_gapname_read == null) {
                    Log.i("SYD_OTA", "GattCallback characteristic_gapname_read is null");
                    return;
                }
                Log.i("SYD_OTA", "GAPNAME_CHARACTERISTIC_READ_UUID is "+GAPNAME_CHARACTERISTIC_READ_UUID.toString());
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);
            if((characteristic == characteristic_ota_update) || (characteristic == characteristic_gapname_read)) {
                Log.i("SYD_OTA","read rsp value ----->" +bytes2String(characteristic.getValue()));
                broadcastUpdate(ACTION_DATA_READ,status,characteristic.getValue());
            }
        }

        @Override //notification和indecation
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);

            //Log.i("SYD_OTA","onCharacteristicChanged value ----->" +bytes2ascii(characteristic.getValue(),0,characteristic.getValue().length));
            Log.i("SYD_OTA","onCharacteristicChanged value ----->" + Arrays.toString(characteristic.getValue()));
            if(characteristic == characteristic_uart_notify) {
                broadcastUpdate(ACTION_DATA_CHANGED, 0, characteristic.getValue());
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
            Log.i("SYD_OTA", "GattCallback onCharacteristicWrite status: " + status);
            if((characteristic == characteristic_ota_update) || (characteristic == characteristic_uart_write)) {
                broadcastUpdate(ACTION_DATA_WRITE, status);
            }
        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            super.onReadRemoteRssi(gatt, rssi, status);
            Log.i("SYD_OTA", "disconnect  device."+rssi);
            broadcastUpdate(ACTION_RSSI_READ,rssi);
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            super.onMtuChanged(gatt, mtu, status);
            Log.i("SYD_OTA", "device."+mtu);
            broadcastUpdate(ACTION_DATA_MTU_CHANGED,mtu);
        }
    };

    //发起连接
    public boolean connectDevice(final String Address)
    {
        Log.i("SYD_OTA", "connectDevice.");
        isDiscoverServices = false;
        if (!BlutoothConnectStatue) {

            if (mBluetoothAdapter == null || Address == null) {
                Log.i("SYD_OTA", "BluetoothAdapter not initialized or unspecified address.");
                return false;
            }
            Log.i("SYD_OTA", "getRemoteDevice");
            BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(Address);

            if (device == null) {
                Log.i("SYD_OTA", "device not found. unable to connect.");
                return false;
            }

            //发起GATT服务连接，操作结果将在bluetoothGattCallback回调中响应
            mBluetoothGatt = device.connectGatt(this, false, bluetoothGattCallback);
            Log.i("SYD_OTA", "Tring to link ble device.");
            //BlutoothConnectStatue = true;
        }
        return true;
    }

    public void disconnectDevice(){
        Log.i("SYD_OTA", "disconnect  device.");
        if (mBluetoothGatt != null) {
            BlutoothConnectStatue = false;
            mBluetoothGatt.disconnect();
            mBluetoothGatt.close();
        }
        else
        {
            Log.i("SYD_OTA", "mBluetoothGatt is null");
        }
    }

    public String bytes2String(byte[] data){
        String getString = "";
        for(int i = 0; i < data.length; i++){
            getString += String.format("%02X", data[i]);
        }
        return getString;
    }

    public static String bytes2ascii(byte[] bytes, int offset, int dateLen) {
        if ((bytes == null) || (bytes.length == 0) || (offset < 0) || (dateLen <= 0)) {
            return null;
        }
        if ((offset >= bytes.length) || (bytes.length - offset < dateLen)) {
            return null;
        }

        String asciiStr = null;
        byte[] data = new byte[dateLen];
        System.arraycopy(bytes, offset, data, 0, dateLen);
        try {
            asciiStr = new String(data, "ISO8859-1");
        } catch (UnsupportedEncodingException e) {
        }
        return asciiStr;
    }

    public void getDeviceRssi(){
        if (mBluetoothGatt != null) {
            //BlutoothConnectStatue = false;
            Log.i("SYD_OTA", "disconnect  device.");
            mBluetoothGatt.readRemoteRssi();
        }
    }

    // 发送广播消息
    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

    // 发送广播消息
    private void broadcastUpdate(final String action, int value) {
        final Intent intent = new Intent(action);
        intent.putExtra("value",value);
        sendBroadcast(intent);
    }

    // 发送广播消息
    private void broadcastUpdate(final String action, int value,byte[] data) {
        final Intent intent = new Intent(action);
        intent.putExtra("value",value);
        intent.putExtra("data",data);
        sendBroadcast(intent);
    }

    // 发送广播消息
    private void broadcastUpdate(final String action,final BluetoothGattCharacteristic characteristic) {

        final Intent intent = new Intent(action);
        intent.putExtra("value", characteristic.getValue());
        sendBroadcast(intent);
    }

    //发送数据 20Byte
    public void sendData(byte[] data){
        if (characteristic_ota_update != null){
            characteristic_ota_update.setValue(data);
            characteristic_ota_update.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            mBluetoothGatt.writeCharacteristic(characteristic_ota_update);
        }else{
            Log.i("SYD_OTA", "GattCallback characteristic_ota_update is null");
        }
    }

    //发送数据 20Byte
    public void sendData(byte[] data, int writeType){
        if (characteristic_ota_update != null){
            characteristic_ota_update.setValue(data);
            characteristic_ota_update.setWriteType(writeType);
            mBluetoothGatt.writeCharacteristic(characteristic_ota_update);
        }else{
            Log.i("SYD_OTA", "GattCallback characteristic_ota_update is null");
        }
    }

    //读取数据 20Byte
    public void receiveData(){
        if (characteristic_ota_update != null){
            mBluetoothGatt.readCharacteristic(characteristic_ota_update);
        }else{
            Log.i("SYD_OTA", "GattCallback characteristic_ota_update is null");
        }
    }
    //读取数据 20Byte
    public void receiveData(BluetoothGattCharacteristic characteristic){
        if (characteristic != null){
            mBluetoothGatt.readCharacteristic(characteristic);
        }else{
            Log.i("SYD_OTA", "GattCallback read is null");
        }
    }


    //发送数据 20Byte
    public void setGatt_WriteMtu_sz(int mtu_sz){
            mBluetoothGatt.requestMtu(mtu_sz);
    }




    //发送UART数据 20Byte
    public void sendUartData(byte[] data){
        if (characteristic_uart_write != null){
            characteristic_uart_write.setValue(data);
            mBluetoothGatt.writeCharacteristic(characteristic_uart_write);
        }else{
            Log.i("SYD_OTA", "GattCallback characteristic_uart_write is null");
        }
    }

    public void UartbeginReliableWrite(){
        if (mBluetoothGatt != null){
            mBluetoothGatt.beginReliableWrite();
        }else{
            Log.i("SYD_OTA", "mBluetoothGatt is null");
        }
    }

    public void UartexecuteReliableWrite(){
        if (mBluetoothGatt != null){
            mBluetoothGatt.executeReliableWrite();
        }else{
            Log.i("SYD_OTA", "mBluetoothGatt is null");
        }
    }

    private IBinder iBinder;

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        return iBinder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.v("123","service onCreate");
        if(mBluetoothManager == null){
            mBluetoothManager = (BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE);
            if(mBluetoothManager == null){
                Log.i("SYD_OTA", "mBluetoothManager initialize  false!");
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if(mBluetoothAdapter == null){
            Log.i("SYD_OTA", "obtain a bluetoothAdapter false!");
        }
        iBinder = new LoadcalBinder();
    }

    public class LoadcalBinder extends Binder {
        public BleService getService() {
            return BleService.this;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.v("123","service onStartCommand");
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.v("123","service onDestroy");
        mBluetoothGatt.disconnect();
    }
}