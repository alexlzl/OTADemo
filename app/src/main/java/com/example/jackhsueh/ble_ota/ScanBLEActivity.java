package com.example.jackhsueh.ble_ota;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.Toast;

import com.example.jackhsueh.ble_ota.permission.PermissionRequest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ScanBLEActivity extends Activity {

    private static final int REQUEST_ENABLE_BT = 1;

    private boolean mScanning;

    private ArrayList<String> deviceMac_list;
    private ArrayList<String> deviceName_list;

    List<Map<String, String>> DevicelistData = new ArrayList<Map<String, String>>();

    // 控件声明
    ListView blelistView = null;
    private BluetoothAdapter mBluetoothAdapter = null;
    private SimpleAdapter myBleDeviceAdapter = null;

    private Button About_button;

    // 声明一个集合，在后面的代码中用来存储用户拒绝授权的权限
    private List<String> deniedPermissionList = new ArrayList<>();
    //动态申请权限
    private String[] requestPermissionArray = new String[]{
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    /**
     * 初始化权限
     */
    private void initPermissions() {
        //Android 6.0以上动态申请权限
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
            final PermissionRequest permissionRequest = new PermissionRequest();
            permissionRequest.requestRuntimePermission(ScanBLEActivity.this, requestPermissionArray, new PermissionListener() {
                @Override
                public void onGranted() {
                    Log.d("123","所有权限已被授予");
                }

                //用户勾选“不再提醒”拒绝权限后，关闭程序再打开程序只进入该方法！
                @Override
                public void onDenied(List<String> deniedPermissions) {
                    deniedPermissionList = deniedPermissions;
                    for (String deniedPermission : deniedPermissionList) {
                        Log.e("123","被拒绝权限：" + deniedPermission);
                    }
                }
            });
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan_ble);
        blelistView = (ListView)findViewById(R.id.device_list);
        initPermissions();
        deviceMac_list = new ArrayList<>();
        deviceName_list = new ArrayList<>();

        myBleDeviceAdapter = new SimpleAdapter(ScanBLEActivity.this,
                DevicelistData,
                R.layout.simple_list_item_3,
                new String[]{"name", "mac", "rssi"},
                new int[]{R.id.text1, R.id.text2, R.id.text3});
        blelistView.setAdapter(myBleDeviceAdapter);

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "BLE is not supported", Toast.LENGTH_SHORT).show();
            finish();
        }

        //不单只是添加权限 还要主动获取位置权限
        if (Build.VERSION.SDK_INT >= 23)
        {
            if ((ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED ) ||
                 (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) ||
                    (ContextCompat.checkSelfPermission(this, Manifest.permission.MOUNT_UNMOUNT_FILESYSTEMS) != PackageManager.PERMISSION_GRANTED))
            {
                ActivityCompat.requestPermissions(ScanBLEActivity.this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.WRITE_EXTERNAL_STORAGE,Manifest.permission.MOUNT_UNMOUNT_FILESYSTEMS }, 10);
            }
        }

        // Initializes a Bluetooth adapter.
        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();
        // Checks if Bluetooth is supported on the device.
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "手机不支持蓝牙BLE，请更换手机！", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        About_button = (Button) findViewById(R.id.about_button);

        About_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getApplicationContext(),ABOUT_Active.class);
                startActivity(intent);
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        if (!mScanning) {
            menu.findItem(R.id.menu_stop).setVisible(false);
            menu.findItem(R.id.menu_scan).setVisible(true);
            menu.findItem(R.id.menu_refresh).setActionView(null);
        } else {
            menu.findItem(R.id.menu_stop).setVisible(true);
            menu.findItem(R.id.menu_scan).setVisible(false);
            menu.findItem(R.id.menu_refresh).setActionView(R.layout.actionbar_indeterminate_progress);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_scan:
                deviceMac_list.clear();
                deviceName_list.clear();
                DevicelistData.clear();
                myBleDeviceAdapter.notifyDataSetChanged();
                scanLeDevice(true);
                break;
            case R.id.menu_stop:
                scanLeDevice(false);
                break;
        }
        return true;
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.i("123", "MainActivity onStart");
        // 请求打开蓝牙
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        blelistView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

                scanLeDevice(false);

                Intent intent = new Intent(getApplicationContext(), OTA_Active.class);
                intent.putExtra("DEVICE_NAME",deviceName_list.get(position));
                intent.putExtra("DEVICE_MAC", deviceMac_list.get(position));
                startActivity(intent);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i("123", "MainActivity onResume");
        if(mBluetoothAdapter != null && mBluetoothAdapter.isEnabled())
        {
            scanLeDevice(true);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.i("123", "MainActivity onPause");
        scanLeDevice(false);
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.i("123", "MainActivity onStop");
        scanLeDevice(false);
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        Log.i("123", "MainActivity onRestart");
    }

    @Override
    protected void onDestroy() {
        Log.i("123", "MainActivity onDestroy");
        super.onDestroy();
        scanLeDevice(false);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // TODO Auto-generated method stub
        super.onActivityResult(requestCode, resultCode, data);
        // 未打开蓝牙
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            Toast.makeText(getApplicationContext(), "请先打开蓝牙!", Toast.LENGTH_SHORT).show();
            Log.i("123", "未打开蓝牙");
        } else {
            Log.i("123", "成功打开蓝牙");
            scanLeDevice(true);
        }
    }

    // 扫描函数
    private void scanLeDevice(final boolean enable) {
        Log.i("123", "scanLeDevice_func");
        if (enable) {
            mScanning = true;
            Log.i("123", "startLeScan_1");
            mBluetoothAdapter.startLeScan(mLeScanCallback);
        } else {

            Log.i("123", "stopLeScan_1");
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
            mScanning = false;
        }
        invalidateOptionsMenu();
    }

    // 扫描回调
    private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(final BluetoothDevice device, final int rssi, byte[] scanRecord) {
            //if(rssi > -70) {
            final String device_mac = device.getAddress();//.replace(":", "");
            final String device_name = device.getName();

            Log.i("123", "device_mac："+device_mac);

            for (int i = 0; i < deviceMac_list.size(); i++) {
                if (0 == device_mac.compareTo(deviceMac_list.get(i))) {
                    return;
                }
            }
            if(device_name !=null) {
                deviceMac_list.add(device_mac);
                deviceName_list.add(device_name);
                Map<String, String> listem = new HashMap<String, String>();
                listem.put("name", device_name);
                listem.put("mac", device_mac);
                listem.put("rssi",String.valueOf(rssi));
                DevicelistData.add(listem);


            runOnUiThread(new Runnable() {
                @Override
                public void run(){
                    myBleDeviceAdapter.notifyDataSetChanged();
                }
            });
            }
        }
    };

    public void updateDialog(String string) {
        Dialog alertDialog = new AlertDialog.Builder(ScanBLEActivity.this)
                // 设置对话框图标
                // 设置标题
                .setMessage(string)
                .setPositiveButton("确认",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                            }
                        })
                .create();
        alertDialog.show();
    }
    public  void saveUserInfo(Context context,int otaversions,String key){
        /**
         * SharedPreferences将用户的数据存储到该包下的shared_prefs/config.xml文件中，
         * 并且设置该文件的读取方式为私有，即只有该软件自身可以访问该文件
         */
        SharedPreferences sPreferences=context.getSharedPreferences("svae_config", context.MODE_PRIVATE);
        SharedPreferences.Editor editor=sPreferences.edit();
        //当然sharepreference会对一些特殊的字符进行转义，使得读取的时候更加准确
        editor.putInt(key, otaversions);
        //这里我们输入一些特殊的字符来实验效果
//          editor.putString("specialtext", "hajsdh><?//");
//          editor.putBoolean("or", true);
//          editor.putInt("int", 47);
        //切记最后要使用commit方法将数据写入文件
        editor.commit();
    }

}
