package com.example.jackhsueh.ble_ota;

import android.app.Activity;
import android.app.DownloadManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Vibrator;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Array;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Timer;

import android.widget.Spinner;

public class OTA_Active extends Activity {

    public static final String TAG = "SYD OTA";

    private BleService bleService = null;

    String m_dir = Environment.getExternalStorageDirectory().getPath();

    private TextView Status_textView;
    private TextView MAC_textView;
    private TextView NAME_textView;
    private TextView StatusPercentage_textView;
    private TextView elapsedtimer_textView;
    private TextView Update_textView;
    ListView listview;
    private List<String> FileNameList = new ArrayList<String>();  //结果 List
    private List<String> FilePathList = new ArrayList<String>();  //结果 List

    private ProgressBar mprogressBarOta;

    private String DeviceMac = null;
    private String DeviceName = null;

    private String OTA_FilePath = null;
    byte[] ReadData = null;
    private int SendPacketID = 0;
    private int SendPacketAllNum = 0;
    private int SendSectionID = 0;
    private int LastPacketByte = 0;
    int CRC = 0;
    byte Xor = 0;
    int SECTION_CRC = 0;

    final byte CMD_FW_WRITE_START = 0x14;

    final byte CMD_FW_ERASE = 0x16;
    final byte CMD_FW_WRITE = 0x17;
    final byte CMD_FW_UPGRADEV20 = 0x15;
    final byte CMD24K_FW_ERASE = 0x20;
    final byte CMD24K_FW_WRITE = 0x21;
    final byte CMD4K_FW_ERASE = 0x11;
    final byte CMD4K_FW_WRITE = 0x12;
    final byte CMD4K_FW_UPGRADE = 0x13;
    final byte CMD24K_FW_WRITE_START = 0x23;
    final byte CMD24K_FW_UPGRADE_V30 = 0x24;

    final byte CMD_EXFLASH_ERASE = 0x25;
    final byte CMD_EXFLASH_WRITE_START = 0x28;
    final byte CMD_EXFLASH_UPGRADE_V30 = 0x29;

    final byte ERR_COMMAND_FAILED = 0x01;
    final byte EVT_COMMAND_COMPLETE = (0x0E);

    final int MAX_TRANS_COUNT = 15;
    final int MAX_TRANS_COUNT_V30 = 20;
    final int MAX_TRANS_SECTIONALL_COUNT = 5120;
    final int MAX_TRANS_SECTIONALL_PACKET_COUNT = MAX_TRANS_SECTIONALL_COUNT / 20;
    private int MAX_TRANS_SECTIONALL_SIZE = 0;

    private CheckBox CheckBox_24k;
    private boolean CheckBox_24k_ischeck = false;
    private CheckBox CheckBox_4k;
    private boolean CheckBox_4k_ischeck = false;
    private CheckBox checkBox_exFlash;
    private boolean checkBox_exFlash_ischeck = false;

    private int actionType = 0;
    final byte ACTIONTYPE_CMD_FW_WRITE_START = 0x01;
    final byte ACTIONTYPE_CMD_FW_WRITE_END = 0x02;
    final byte ACTIONTYPE_CMD_FW_ERASE = 0x03;
    final byte ACTIONTYPE_CMD_FW_WRITE = 0x04;
    final byte ACTIONTYPE_CMD_FW_UPGRADE = 0x05;
    final byte ACTIONTYPE_CMD24K_FW_ERASE = 0x06;
    final byte ACTIONTYPE_CMD24K_FW_WRITE = 0x07;
    final byte ACTIONTYPE_CMD24K_FW_UPGRADE = 0x08;
    final byte ACTIONTYPE_CMD_FW_FINISH = 0x09;

    final byte ACTIONTYPE_CMD_FW24k_FINISH = 0x0a;
    final byte ACTIONTYPE_CMD4K_FW_ERASE = 0x0B;
    final byte ACTIONTYPE_CMD4K_FW_WRITE = 0x0C;
    final byte ACTIONTYPE_CMD4K_FW_UPGRADE = 0x0D;
    final byte ACTIONTYPE_CMD_FW4k_FINISH = 0x0E;
    final byte ACTIONTYPE_CMD24K_FW_WRITE_START = 0x0F;
    final byte ACTIONTYPE_CMD24K_FW_UPGRADE_V30 = 0x10;
    final byte ACTIONTYPE_CMD24K_FW_WRITE_END = 0x11;

    final byte ACTIONTYPE_CMD_EXFLASH_ERASE = 0x12;
    final byte ACTIONTYPE_CMD_EXFLASH_WRITE_START = 0x13;
    final byte ACTIONTYPE_CMD_EXFLASH_UPGRADE_V30 = 0x14;
    final byte ACTIONTYPE_CMD_EXFLASH_WRITE_END = 0x15;
    final byte ACTIONTYPE_CMD_EXFLASH_FINISH = 0x16;

    final byte ACTIONTYPE_CMD_FW_FINISH_CONFIRM = 0x17;

    private long oat_start_time = 0;
    private boolean oat_start_result = false;

    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BleService.GATT_CONNECTED.equals(action)) {
                ShowStatus("设备已连接");
                oat_start_result = false;
            } else if (BleService.GATT_DISCONNECTED.equals(action)) {
                if (oat_start_result == true) {
                    ShowStatus("OTA失败 请重新OTA!  设备已断开");
                    oat_start_result = false;
                } else {
                    ShowStatus("设备已断开");
                }
                OTA_Process_init();
            } else if (BleService.ACTION_DATA_WRITE.equals(action)) {
                int status = intent.getIntExtra("value", 0);
                byte[] data = intent.getByteArrayExtra("data");
                OTA_Process_doing(status,data);
            }else if (BleService.ACTION_DATA_READ.equals(action)) {
                int status = intent.getIntExtra("value", 0);
                byte[] data = intent.getByteArrayExtra("data");
                OTA_Process_doing(status,data);
            }

        }
    };

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BleService.GATT_CONNECTED);
        intentFilter.addAction(BleService.GATT_DISCONNECTED);
        intentFilter.addAction(BleService.GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BleService.ACTION_DATA_CHANGE);
        intentFilter.addAction(BleService.ACTION_DATA_READ);
        intentFilter.addAction(BleService.ACTION_DATA_WRITE);
        intentFilter.addAction(BleService.ACTION_RSSI_READ);
        return intentFilter;
    }

    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.i("SYD_OTA", "DisplayCtrl service onServiceConnected");
            bleService = ((BleService.LoadcalBinder) service).getService();

            if (!bleService.BlutoothConnectStatue) {
                if (DeviceMac != null) {
                    bleService.connectDevice(DeviceMac);
                    ShowStatus("设备连接中");
                } else {
                    ShowStatus("设备未选中");
                }
            } else {
                ShowStatus("设备已连接");
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.i("SYD_OTA", "service onServiceDisconnected");
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        Intent intent = getIntent();
        DeviceMac = intent.getStringExtra("DEVICE_MAC");
        DeviceName = intent.getStringExtra("DEVICE_NAME");
        Status_textView = (TextView) findViewById(R.id.Status_textView);
        StatusPercentage_textView = (TextView) findViewById(R.id.StatusPercentage_textView);
        elapsedtimer_textView = (TextView) findViewById(R.id.elapsedtimer_textView);
        Update_textView = (TextView) findViewById(R.id.Update_textView);
        listview = (ListView) findViewById(R.id.f_name);
        MAC_textView = (TextView) findViewById(R.id.MAC_textView);
        NAME_textView = (TextView) findViewById(R.id.NAME_textView);
        MAC_textView.setText("MAC:" + DeviceMac);
        NAME_textView.setText(DeviceName);

        intent = new Intent(this, BleService.class);
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        boolean a = bindService(intent, serviceConnection, BIND_AUTO_CREATE);
        Log.i("SYD_OTA", "onCreate bindService end:" + a);

        CheckBox_24k = (CheckBox) findViewById(R.id.checkBox_24k);
        CheckBox_24k.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked)
                    CheckBox_24k_ischeck = true;
                else
                    CheckBox_24k_ischeck = false;
            }
        });

        CheckBox_4k = (CheckBox) findViewById(R.id.checkBox_4k);
        CheckBox_4k.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked)
                    CheckBox_4k_ischeck = true;
                else
                    CheckBox_4k_ischeck = false;
            }
        });

        checkBox_exFlash = (CheckBox) findViewById(R.id.checkBox_exflash);
        checkBox_exFlash.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked)
                    checkBox_exFlash_ischeck = true;
                else
                    checkBox_exFlash_ischeck = false;
            }
        });

        mprogressBarOta = (ProgressBar) findViewById(R.id.progressBarOta);

        refreshFiles();

        listview.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                Toast.makeText(OTA_Active.this, "选择:" + FileNameList.get(i) + "文件,开始OTA，耐心等待!!!", Toast.LENGTH_SHORT).show();

                OTA_FilePath =  FilePathList.get(i);

                File file = new File(OTA_FilePath);
                ShowUpdatefile("已选文件:" + FileNameList.get(i) + " " + Long.toString(file.length() / 1000) + "." + Long.toString(file.length() % 1000) + "Kbyte/S");
                if (file.length() < 1024) {
                    Toast.makeText(OTA_Active.this, "文件太小，请重新选择！", Toast.LENGTH_SHORT).show();
                    ShowUpdatefile("文件错误！" + FileNameList.get(i) + " " + Long.toString(file.length() / 1000) + "." + Long.toString(file.length() % 1000) + "Kbyte");
                    return;
                }
                oat_start_time = System.currentTimeMillis();
                if (CheckBox_24k_ischeck == true) {
                    ProcessOTA24K_Start();
                } else if (CheckBox_4k_ischeck == true) {
                    ProcessOTA4K_Start();
                } else if (checkBox_exFlash_ischeck == true) {
                    ProcessOTA_Exflash_Start();
                } else {
                    OTA_Process_Start();
                }
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.i("123", "OTA Activity onDestroy");
        bleService.disconnectDevice();
        unbindService(serviceConnection);
        unregisterReceiver(mGattUpdateReceiver);
        finish();
    }

    void OTA_Process_init() {
        SendPacketID = 0;
        SendPacketAllNum = 0;
        OTA_FilePath = null;
        CRC = 0;
        ReadData = null;
        actionType = 0;
        SendSectionID = 0;
        SECTION_CRC = 0;
        MAX_TRANS_SECTIONALL_SIZE = 0;
    }

    void OTA_Process_doing(int status, final byte[] data) {
        if (actionType == ACTIONTYPE_CMD_FW_ERASE) {
            Toast.makeText(this, "请稍等，擦除空间中！", Toast.LENGTH_LONG).show();
            new Handler().postDelayed(new Runnable() {
                public void run() {
                    //execute the task
                    OTA_Write_Flash_Start_V30(data);
                }
            }, 5000);
        } else if (actionType == ACTIONTYPE_CMD_FW_WRITE) {
            OTA_Write_All_Flash(status);

        } else if (actionType == ACTIONTYPE_CMD_FW_WRITE_START) {
            OTA_Write_Section_All_Flash(status);

        } else if (actionType == ACTIONTYPE_CMD_FW_WRITE_END) {

            OTA_Write_Flash_Continue_V30(data);

        } else if (actionType == ACTIONTYPE_CMD_FW_UPGRADE) {
            OTA_Upgrade_Flash_V30(ReadData.length, CRC);
        } else if (actionType == ACTIONTYPE_CMD24K_FW_ERASE) {
            Toast.makeText(this, "请稍等，擦除数据区空间中！", Toast.LENGTH_LONG).show();
            new Handler().postDelayed(new Runnable() {
                public void run() {
                    //execute the task
                    Write24KFlash_Start_V30(data);
                }
            }, 5000);
        } else if (actionType == ACTIONTYPE_CMD24K_FW_WRITE) {
            Write24KFlash_All(status);

        } else if (actionType == ACTIONTYPE_CMD4K_FW_ERASE) {
            Write4KFlash_Start();

        } else if (actionType == ACTIONTYPE_CMD4K_FW_WRITE) {
            Write4KFlash_All(status);

        } else if (actionType == ACTIONTYPE_CMD4K_FW_UPGRADE) {
            OTA4K_Upgrade_Flash(Xor, CRC);

        } else if (actionType == ACTIONTYPE_CMD_FW_FINISH) {
            actionType =ACTIONTYPE_CMD_FW_FINISH_CONFIRM;
            bleService.receiveData();
        } else if (actionType == ACTIONTYPE_CMD_FW_FINISH_CONFIRM) {
            if (((data[0] & 0xFF) == EVT_COMMAND_COMPLETE) & ((data[3] & 0xFF) == ERR_COMMAND_FAILED)) {
                ShowStatus("OTA失败, 请重新OTA，请退出该界面重新选择设备");
                oat_start_result = true;
            } else {
                ShowStatus("OTA完成, 复位设备中，请退出该界面重新选择设备");
                ShowVibrator((byte) 0);
            }

            EnableListview(listview, true);
            OTA_Process_init();
            bleService.disconnectDevice();
        } else if (actionType == ACTIONTYPE_CMD_FW24k_FINISH) {
            ShowStatus("24k OTA完成, 请复位设备");
            EnableListview(listview, true);
            OTA_Process_init();
            ShowVibrator((byte) 0);
        } else if (actionType == ACTIONTYPE_CMD_FW4k_FINISH) {
            ShowStatus("4k OTA完成, 请复位设备");
            EnableListview(listview, true);
            OTA_Process_init();
            ShowVibrator((byte) 0);
        } else if (actionType == ACTIONTYPE_CMD24K_FW_WRITE_START) {
            OTA_Write_24K_Section_All_Flash(status);
        } else if (actionType == ACTIONTYPE_CMD24K_FW_WRITE_END) {
            Write24KFlash_Continue_V30(data);
        } else if (actionType == ACTIONTYPE_CMD24K_FW_UPGRADE_V30) {
            OTA24K_Upgrade_Flash_V30(ReadData.length, CRC);
        } else if (actionType == ACTIONTYPE_CMD_EXFLASH_ERASE) {
            Toast.makeText(this, "请稍等，擦除数据区空间中！", Toast.LENGTH_LONG).show();
            new Handler().postDelayed(new Runnable() {
                public void run() {
                    //execute the task
                    Write_ExFlash_Start_V30(data);
                }
            }, 25000);
        } else if (actionType == ACTIONTYPE_CMD_EXFLASH_WRITE_START) {
            OTA_Write_Exflash_Section_All_Flash(status);
        } else if (actionType == ACTIONTYPE_CMD_EXFLASH_WRITE_END) {
            Write_ExFlash_Continue_V30(data);
        } else if (actionType == ACTIONTYPE_CMD_EXFLASH_UPGRADE_V30) {
            OTA_Upgrade_ExFlash_V30(ReadData.length, CRC);
        } else if (actionType == ACTIONTYPE_CMD_EXFLASH_FINISH) {
            ShowStatus("Extern Flash OTA完成, 请复位设备");
            EnableListview(listview, true);
            OTA_Process_init();
            ShowVibrator((byte) 0);
        }

        long oat_now_time = System.currentTimeMillis();
        Showelapsedtimer(oat_now_time - oat_start_time);
    }

    void OTA_Process_Start() {
        ReadData = ReadOTAFileBinary(OTA_FilePath);
        int i = 0;
        EnableListview(listview, false);
        for (i = 0; i < ReadData.length; i++) {
            int CC = ReadData[i];
            CC &= 0x000000FF;
            CRC += CC;
            CRC = CRC & 0x0000FFFF;
        }
        Log.i("SYD_OTA", "OTA_Process_Start CRC ==>" + CRC);

        OTA_Erase_Flash();
    }

    void ProcessOTA24K_Start() {
        ReadData = ReadOTAFileBinary(OTA_FilePath);
        int i = 0;

        EnableListview(listview, false);

        for (i = 0; i < ReadData.length; i++) {
            int CC = ReadData[i];
            CC &= 0x000000FF;
            CRC += CC;
            CRC = CRC & 0x0000FFFF;
        }
        Log.i("SYD_OTA", "24K CRC ==>" + CRC);
        OTA24K_Erase_Flash();
    }


    void ProcessOTA_Exflash_Start() {
        ReadData = ReadOTAFileBinary(OTA_FilePath);
        int i = 0;

        EnableListview(listview, false);

        for (i = 0; i < ReadData.length; i++) {
            int CC = ReadData[i];
            CC &= 0x000000FF;
            CRC += CC;
            CRC = CRC & 0x0000FFFF;
        }
        Log.i("SYD_OTA", "Ex flash CRC ==>" + CRC);
        OTA_Erase_ExFlash();
    }

    void ProcessOTA4K_Start() {
        ReadData = ReadOTAFileBinary(OTA_FilePath);
        int i;

        EnableListview(listview, false);

        for (i = 0; i < ReadData.length; i++) {
            int CC = ReadData[i];
            CC &= 0x000000FF;
            CRC += CC;
        }

        Xor = 0;
        for (i = 0; i < ReadData.length; i++) {
            Xor ^= ReadData[i];
        }

        Log.i("SYD_OTA", "24K CRC ==>" + CRC);
        OTA4K_Erase_Flash();
    }

    public void Write24KFlash_Start_V30(byte[] data) {
        Log.i("SYD_OTA", "24K_OTA_Write_Flashdata_Start_V30");

        SendPacketAllNum = ReadData.length / MAX_TRANS_COUNT_V30;
        if (ReadData.length % MAX_TRANS_COUNT_V30 != 0)
            SendPacketAllNum += 1;


        Write24KFlash_Continue_V30(data);

        // 创建一个数值格式化对象
        NumberFormat numberFormat = NumberFormat.getInstance();
        // 设置精确到小数点后2位
        numberFormat.setMaximumFractionDigits(2);

        String result = numberFormat.format((float) SendPacketID / (float) SendPacketAllNum * 100) + "%";
        ShowStatus("请勿中断，OTA进行中 ...");
        ShowStatusPercentage(result);
        ShowProgressBar((int) ((float) SendPacketID / (float) SendPacketAllNum * 100));
    }

    public void Write24KFlash_Continue_V30(byte[] data) {
        actionType = ACTIONTYPE_CMD24K_FW_WRITE_START;
        if ((SendSectionID != 0) && (data != null)) {
            int check = ((data[7] & 0xff) << 8) | (data[6] & 0xff);

            //error check and resend
            if ((check & 0x0000ffff) != (SECTION_CRC & 0x0000ffff)) {
                Log.i("SYD_OTA", "SECTION resend:" + SendSectionID + " check device:" + check + " check app:" + SECTION_CRC);
                SendSectionID -= 1;
                SendPacketID = MAX_TRANS_SECTIONALL_PACKET_COUNT * SendSectionID;
            }
        }
        Log.i("SYD_OTA", "SendPacketAllNum=" + SendPacketAllNum + " SendPacketID=" + SendPacketID + "  24K ReadData.leng=" + ReadData.length+ "  SectionTotalPacket=" + MAX_TRANS_SECTIONALL_PACKET_COUNT);
        if ((SendPacketAllNum - SendPacketID) > MAX_TRANS_SECTIONALL_PACKET_COUNT)
            MAX_TRANS_SECTIONALL_SIZE = MAX_TRANS_SECTIONALL_COUNT;
        else
            MAX_TRANS_SECTIONALL_SIZE = ReadData.length % MAX_TRANS_SECTIONALL_COUNT;

        SECTION_CRC = 0;

        for (int i = 0; i < MAX_TRANS_SECTIONALL_SIZE; i++) {
            int CC = ReadData[SendSectionID * MAX_TRANS_SECTIONALL_COUNT + i];
            CC &= 0x000000FF;
            SECTION_CRC += CC;
        }

        Log.i("SYD_OTA", "24K  SECTION_CRC ==>" + SECTION_CRC + " SendSectionID ==>" + SendSectionID+ " Now_Section_Size ==>" + MAX_TRANS_SECTIONALL_SIZE + " Section_Total_Size ==>" + MAX_TRANS_SECTIONALL_COUNT);
        Write24KFlash_section_start(SECTION_CRC, MAX_TRANS_SECTIONALL_SIZE, SendSectionID);
        SendSectionID += 1;
    }

    public void Write_ExFlash_Start_V30(byte[] data) {
        Log.i("SYD_OTA", "Write_ExFlash_Start_V30");

        SendPacketAllNum = ReadData.length / MAX_TRANS_COUNT_V30;
        if (ReadData.length % MAX_TRANS_COUNT_V30 != 0)
            SendPacketAllNum += 1;


        Write_ExFlash_Continue_V30(data);

        // 创建一个数值格式化对象
        NumberFormat numberFormat = NumberFormat.getInstance();
        // 设置精确到小数点后2位
        numberFormat.setMaximumFractionDigits(2);

        String result = numberFormat.format((float) SendPacketID / (float) SendPacketAllNum * 100) + "%";
        ShowStatus("请勿中断，OTA进行中 ...");
        ShowStatusPercentage(result);
        ShowProgressBar((int) ((float) SendPacketID / (float) SendPacketAllNum * 100));
    }

    public void Write_ExFlash_Continue_V30(byte[] data) {
        Log.i("SYD_OTA", "OTA_Write_ExFlash_Continue");
        actionType = ACTIONTYPE_CMD_EXFLASH_WRITE_START;

        Log.i("SYD_OTA", "SendSectionID ==>" + SendSectionID);

        if ((SendSectionID != 0) && (data != null)) {
            int check = ((data[7] & 0xff) << 8) | (data[6] & 0xff);

            //error check and resend
            if ((check & 0x0000ffff) != (SECTION_CRC & 0x0000ffff)) {
                Log.i("SYD_OTA", "SECTION resend:" + SendSectionID + " check device:" + check + " check app:" + SECTION_CRC);
                SendSectionID -= 1;
                SendPacketID = MAX_TRANS_SECTIONALL_PACKET_COUNT * SendSectionID;
            }
        }
        Log.i("SYD_OTA", "SendPacketAllNum=" + SendPacketAllNum + " SendPacketID=" + SendPacketID + "  EX Flash ReadData.leng=" + ReadData.length+ "  SectionTotalPacket=" + MAX_TRANS_SECTIONALL_PACKET_COUNT);
        if ((SendPacketAllNum - SendPacketID) > MAX_TRANS_SECTIONALL_PACKET_COUNT)
            MAX_TRANS_SECTIONALL_SIZE = MAX_TRANS_SECTIONALL_COUNT;
        else
            MAX_TRANS_SECTIONALL_SIZE = ReadData.length % MAX_TRANS_SECTIONALL_COUNT;

        SECTION_CRC = 0;

        for (int i = 0; i < MAX_TRANS_SECTIONALL_SIZE; i++) {
            int CC = ReadData[SendSectionID * MAX_TRANS_SECTIONALL_COUNT + i];
            CC &= 0x000000FF;
            SECTION_CRC += CC;
        }

        Log.i("SYD_OTA", "EX SECTION_CRC ==>" + SECTION_CRC + " SendSectionID ==>" + SendSectionID+ " Now_Section_Size ==>" + MAX_TRANS_SECTIONALL_SIZE + " Section_Total_Size ==>" + MAX_TRANS_SECTIONALL_COUNT);
        Write_ExFlash_section_start(SECTION_CRC, MAX_TRANS_SECTIONALL_SIZE, SendSectionID);
        SendSectionID += 1;
    }


    public void Write4KFlash_Start() {
        byte[] dataPacket = new byte[MAX_TRANS_COUNT];
        actionType = ACTIONTYPE_CMD4K_FW_WRITE;

        SendPacketAllNum = ReadData.length / MAX_TRANS_COUNT;
        if (ReadData.length % MAX_TRANS_COUNT != 0)
            SendPacketAllNum += 1;

        LastPacketByte = ReadData.length % MAX_TRANS_COUNT;
        System.arraycopy(ReadData, 0, dataPacket, 0, MAX_TRANS_COUNT);

        OTA4K_Write_Flash(dataPacket, SendPacketID);
        SendPacketID += 1;

        // 创建一个数值格式化对象
        NumberFormat numberFormat = NumberFormat.getInstance();
        // 设置精确到小数点后2位
        numberFormat.setMaximumFractionDigits(2);
        String result = numberFormat.format((float) SendPacketID / (float) SendPacketAllNum * 100) + "%";
        ShowStatus("请勿中断，24K OTA进行中 ...");
        ShowProgressBar((int) ((float) SendPacketID / (float) SendPacketAllNum * 100));
        ShowStatusPercentage(result);
    }

    void Write24KFlash_All(int status) {
        int srcPos = SendPacketID * MAX_TRANS_COUNT;

        final byte[] dataPacket = new byte[MAX_TRANS_COUNT];
        if (status == 0) {
            if (SendPacketID == SendPacketAllNum) {
                actionType = 0;
            } else {
                if (SendPacketID == (SendPacketAllNum - 1)) {
                    System.arraycopy(ReadData, srcPos, dataPacket, 0, (ReadData.length - srcPos));//last a packet
                    actionType = ACTIONTYPE_CMD24K_FW_UPGRADE;//发送完最后一包了
                } else {
                    System.arraycopy(ReadData, srcPos, dataPacket, 0, MAX_TRANS_COUNT);//other packet except first and last packet
                }
                OTA24K_Write_Flash(dataPacket, SendPacketID);
                SendPacketID += 1;
            }
            // 创建一个数值格式化对象
            NumberFormat numberFormat = NumberFormat.getInstance();
            // 设置精确到小数点后2位
            numberFormat.setMaximumFractionDigits(2);
            String result = numberFormat.format((float) SendPacketID / (float) SendPacketAllNum * 100) + "%";
            ShowStatus("请勿中断，24K OTA进行中 ...");
            ShowProgressBar((int) ((float) SendPacketID / (float) SendPacketAllNum * 100));
            ShowStatusPercentage(result);
        } else {
            ShowStatus("24K OTA更新失败,请重试");
        }
    }

    void Write4KFlash_All(int status) {
        int srcPos = SendPacketID * MAX_TRANS_COUNT;

        final byte[] dataPacket;
        if (status == 0) {
            if (SendPacketID == SendPacketAllNum) {
                actionType = 0;
            } else {
                if (SendPacketID == (SendPacketAllNum - 1)) {
                    dataPacket = new byte[LastPacketByte];
                    System.arraycopy(ReadData, srcPos, dataPacket, 0, (ReadData.length - srcPos));//last a packet
                    actionType = ACTIONTYPE_CMD4K_FW_UPGRADE;//发送完最后一包了
                } else {
                    dataPacket = new byte[MAX_TRANS_COUNT];
                    System.arraycopy(ReadData, srcPos, dataPacket, 0, MAX_TRANS_COUNT);//other packet except first and last packet
                }
                OTA4K_Write_Flash(dataPacket, SendPacketID);
                SendPacketID += 1;
            }
            // 创建一个数值格式化对象
            NumberFormat numberFormat = NumberFormat.getInstance();
            // 设置精确到小数点后2位
            numberFormat.setMaximumFractionDigits(2);
            String result = numberFormat.format((float) SendPacketID / (float) SendPacketAllNum * 100) + "%";
            ShowStatus("请勿中断，4K OTA进行中 ...");
            ShowProgressBar((int) ((float) SendPacketID / (float) SendPacketAllNum * 100));
            ShowStatusPercentage(result);
        } else {
            ShowStatus("4K OTA更新失败,请重试");
        }
    }

    public void OTA24K_Erase_Flash() {
        actionType = ACTIONTYPE_CMD24K_FW_ERASE;

        byte[] WriteData = new byte[2];
        WriteData[0] = CMD24K_FW_ERASE;
        WriteData[1] = 0x00;
        Log.i(TAG, "Process 24kOTA Start");
        bleService.sendData(WriteData);
    }

    public void OTA4K_Erase_Flash() {
        actionType = ACTIONTYPE_CMD4K_FW_ERASE;

        byte[] WriteData = new byte[2];
        WriteData[0] = CMD4K_FW_ERASE;
        WriteData[1] = 0x00;
        Log.i(TAG, "Process 4kOTA Start");
        bleService.sendData(WriteData);
    }

    public void OTA_Erase_ExFlash() {
        actionType = ACTIONTYPE_CMD_EXFLASH_ERASE;

        byte[] WriteData = new byte[2];
        WriteData[0] = CMD_EXFLASH_ERASE;
        WriteData[1] = 0x00;
        Log.i(TAG, "Process OTA_Erase_ExFlash Send ERASE");
        bleService.sendData(WriteData);
    }

    public void OTA24K_Write_Flash(byte[] ProgramData, int Address) {
        byte[] WriteData = new byte[20];
        Address = Address * MAX_TRANS_COUNT;

        WriteData[0] = CMD24K_FW_WRITE;
        WriteData[1] = 0x13;
        WriteData[2] = (byte) (Address & 0x000000FF);
        WriteData[3] = (byte) ((Address & 0x0000FF00) >> 8);
        WriteData[4] = (byte) ProgramData.length;

        int i = 0;
        for (i = 0; i < ProgramData.length; i++) {
            WriteData[i + 5] = ProgramData[i];
        }
        bleService.sendData(WriteData);//写入第一包
    }

    public void OTA4K_Write_Flash(byte[] ProgramData, int Address) {
        byte[] WriteData = new byte[20];
        Address = Address * MAX_TRANS_COUNT;

        WriteData[0] = CMD4K_FW_WRITE;
        WriteData[1] = 0x13;
        WriteData[2] = (byte) (Address & 0x000000FF);
        WriteData[3] = (byte) ((Address & 0x0000FF00) >> 8);
        WriteData[4] = (byte) ProgramData.length;

        int i = 0;
        for (i = 0; i < ProgramData.length; i++) {
            WriteData[i + 5] = ProgramData[i];
        }
        bleService.sendData(WriteData);//写入第一包
    }
    public void OTA24K_Upgrade_Flash_V30(int Size, int CRC) {
        Log.i("SYD_OTA", "OTA_Upgrade_Flash CRC_24K:" + CRC + "Size" + Size);
        byte[] WriteData = new byte[8];
        WriteData[0] = CMD24K_FW_UPGRADE_V30;
        WriteData[1] = 0x06;
        WriteData[2] = (byte) (Size & 0x000000FF);
        WriteData[3] = (byte) ((Size & 0x0000FF00) >> 8);
        WriteData[4] = (byte) ((Size & 0x00FF0000) >> 16);
        WriteData[5] = (byte) ((Size & 0xFF000000) >> 24);
        WriteData[6] = (byte) (CRC & 0x000000FF);
        WriteData[7] = (byte) ((CRC & 0x0000FF00) >> 8);

        actionType = ACTIONTYPE_CMD_FW24k_FINISH;
        bleService.sendData(WriteData);
    }

    public void OTA4K_Upgrade_Flash(byte xor, int CRC) {
        Log.i("SYD_OTA", "OTA_Upgrade_Flash CRC_4K:" + CRC + "Size" + xor);
        byte[] WriteData = new byte[7];
        WriteData[0] = CMD4K_FW_UPGRADE;
        WriteData[1] = 0x04;
        WriteData[2] = xor;
        WriteData[3] = (byte) (CRC & 0x000000FF);
        WriteData[4] = (byte) ((CRC & 0x0000FF00) >> 8);
        WriteData[5] = (byte) ((CRC & 0x00FF0000) >> 16);
        WriteData[6] = (byte) ((CRC & 0xFF000000) >> 24);

        actionType = ACTIONTYPE_CMD_FW4k_FINISH;
        bleService.sendData(WriteData);
    }

    public void OTA_Upgrade_ExFlash_V30(int Size, int CRC) {
        Log.i("SYD_OTA", "OTA_Upgrade_Flash CRC_Exflash:" + CRC + "Size" + Size);
        byte[] WriteData = new byte[8];
        WriteData[0] = CMD_EXFLASH_UPGRADE_V30;
        WriteData[1] = 0x06;
        WriteData[2] = (byte) (Size & 0x000000FF);
        WriteData[3] = (byte) ((Size & 0x0000FF00) >> 8);
        WriteData[4] = (byte) ((Size & 0x00FF0000) >> 16);
        WriteData[5] = (byte) ((Size & 0xFF000000) >> 24);
        WriteData[6] = (byte) (CRC & 0x000000FF);
        WriteData[7] = (byte) ((CRC & 0x0000FF00) >> 8);

        actionType = ACTIONTYPE_CMD_EXFLASH_FINISH;
        bleService.sendData(WriteData);
    }

    public void OTA_Erase_Flash() {
        actionType = ACTIONTYPE_CMD_FW_ERASE;

        byte[] WriteData = new byte[2];
        WriteData[0] = CMD_FW_ERASE;
        WriteData[1] = 0x00;
        Log.i("SYD_OTA", "OTA_Erase_Flash Start");
        bleService.sendData(WriteData);
    }
    public void OTA_Write_Flash_Start_V30(byte[] data) {
        Log.i("SYD_OTA", "OTA_Write_Flash_Start_V30");

        SendPacketAllNum = ReadData.length / MAX_TRANS_COUNT_V30;
        if (ReadData.length % MAX_TRANS_COUNT_V30 != 0)
            SendPacketAllNum += 1;


        OTA_Write_Flash_Continue_V30(data);

        // 创建一个数值格式化对象
        NumberFormat numberFormat = NumberFormat.getInstance();
        // 设置精确到小数点后2位
        numberFormat.setMaximumFractionDigits(2);

        String result = numberFormat.format((float) SendPacketID / (float) SendPacketAllNum * 100) + "%";
        ShowStatus("请勿中断，OTA进行中 ...");
        ShowStatusPercentage(result);
        ShowProgressBar((int) ((float) SendPacketID / (float) SendPacketAllNum * 100));
    }

    public void OTA_Write_Flash_Continue_V30(byte[] data) {
        actionType = ACTIONTYPE_CMD_FW_WRITE_START;
        if ((SendSectionID != 0) && (data != null)) {
            int check = ((data[7] & 0xff) << 8) | (data[6] & 0xff);

            //error check and resend
            if ((check & 0x0000ffff) != (SECTION_CRC & 0x0000ffff)) {
                Log.i("SYD_OTA", "SECTION resend:" + SendSectionID + " check device:" + check + " check app:" + SECTION_CRC);
                SendSectionID -= 1;
                SendPacketID = MAX_TRANS_SECTIONALL_PACKET_COUNT * SendSectionID;
            }
        }
        Log.i("SYD_OTA", "SendPacketAllNum=" + SendPacketAllNum + " SendPacketID=" + SendPacketID + "  ReadData.leng=" + ReadData.length+ "  SectionTotalPacket=" + MAX_TRANS_SECTIONALL_PACKET_COUNT);
        if ((SendPacketAllNum - SendPacketID) > MAX_TRANS_SECTIONALL_PACKET_COUNT) {
            MAX_TRANS_SECTIONALL_SIZE = MAX_TRANS_SECTIONALL_COUNT;
        } else {
            MAX_TRANS_SECTIONALL_SIZE = ReadData.length % MAX_TRANS_SECTIONALL_COUNT;
        }
        SECTION_CRC = 0;
        for (int i = 0; i < MAX_TRANS_SECTIONALL_SIZE; i++) {
            int CC = ReadData[SendSectionID * MAX_TRANS_SECTIONALL_COUNT + i];
            CC &= 0x000000FF;
            SECTION_CRC += CC;
        }

        Log.i("SYD_OTA", "SECTION_CRC ==>" + SECTION_CRC + " SendSectionID ==>" + SendSectionID+ " Now_Section_Size ==>" + MAX_TRANS_SECTIONALL_SIZE + " Section_Total_Size ==>" + MAX_TRANS_SECTIONALL_COUNT);
        OTA_Write_Flash_section_start(SECTION_CRC, MAX_TRANS_SECTIONALL_SIZE, SendSectionID);
        SendSectionID += 1;
    }

    public void OTA_Write_All_Flash(int status) {
        int srcPos = SendPacketID * MAX_TRANS_COUNT;

        final byte[] dataPacket = new byte[MAX_TRANS_COUNT];
        if (status == 0) {
            if (SendPacketID == SendPacketAllNum) {
                actionType = 0;
            } else {
                if (SendPacketID == (SendPacketAllNum - 1)) {
                    System.arraycopy(ReadData, srcPos, dataPacket, 0, (ReadData.length - srcPos));//last a packet
                    actionType = ACTIONTYPE_CMD_FW_UPGRADE;//发送完最后一包了
                } else {
                    System.arraycopy(ReadData, srcPos, dataPacket, 0, MAX_TRANS_COUNT);//other packet except first and last packet
                }
                OTA_Write_Flash(dataPacket, SendPacketID);
                SendPacketID += 1;
            }
            // 创建一个数值格式化对象
            NumberFormat numberFormat = NumberFormat.getInstance();
            // 设置精确到小数点后2位
            numberFormat.setMaximumFractionDigits(2);
            String result = numberFormat.format((float) SendPacketID / (float) SendPacketAllNum * 100) + "%";
            ShowStatus("请勿中断，OTA进行中 ...");
            ShowStatusPercentage(result);
            ShowProgressBar((int) ((float) SendPacketID / (float) SendPacketAllNum * 100));
        } else {
            ShowStatus("OTA更新失败,请重试");
        }
    }

    public void OTA_Write_Section_All_Flash(int status) {
        int srcPos = SendPacketID * MAX_TRANS_COUNT_V30;
        final byte[] dataPacket = new byte[MAX_TRANS_COUNT_V30];
        if (status == 0) {
            if (SendPacketID == SendPacketAllNum) {
                actionType = 0;
            } else {
                if (SendPacketID == (SendPacketAllNum - 1)) {
                    System.arraycopy(ReadData, srcPos, dataPacket, 0, (ReadData.length - srcPos));//last a packet
                    actionType = ACTIONTYPE_CMD_FW_UPGRADE;//发送完最后一包了
                } else {
                    System.arraycopy(ReadData, srcPos, dataPacket, 0, MAX_TRANS_COUNT_V30);//other packet except first and last packet
                }
                OTA_Write_Secton_Flash(dataPacket, SendPacketID);
                SendPacketID += 1;
            }
            Log.i("SYD_OTA", "SendPacketAllNum ==>" + SendPacketAllNum+"srcPos ==>" + srcPos+"SendPacketID=" + SendPacketID + " SectionTotalPacket=" + MAX_TRANS_SECTIONALL_PACKET_COUNT + " SendPacketID%SectionTotalPacket ==>" + SendPacketID % MAX_TRANS_SECTIONALL_PACKET_COUNT);

            if (actionType != ACTIONTYPE_CMD_FW_UPGRADE) {
                if (SendPacketID % MAX_TRANS_SECTIONALL_PACKET_COUNT == 0) {
                    actionType = ACTIONTYPE_CMD_FW_WRITE_END;
                    Log.i("SYD_OTA", "Section:" + Integer.toString(SendPacketID / MAX_TRANS_SECTIONALL_PACKET_COUNT));
                }
            }
            // 创建一个数值格式化对象
            NumberFormat numberFormat = NumberFormat.getInstance();
            // 设置精确到小数点后2位
            numberFormat.setMaximumFractionDigits(2);
            String result = numberFormat.format((float) SendPacketID / (float) SendPacketAllNum * 100) + "%";
            ShowStatus("请勿中断，OTA进行中 ...");
            ShowStatusPercentage(result);
            ShowProgressBar((int) ((float) SendPacketID / (float) SendPacketAllNum * 100));
        } else {
            ShowStatus("OTA更新失败,请重试");
        }
    }

    public void OTA_Write_24K_Section_All_Flash(int status) {

        int srcPos = SendPacketID * MAX_TRANS_COUNT_V30;
        final byte[] dataPacket = new byte[MAX_TRANS_COUNT_V30];
        if (status == 0) {
            if (SendPacketID == SendPacketAllNum) {
                actionType = 0;
            } else {
                if (SendPacketID == (SendPacketAllNum - 1)) {
                    System.arraycopy(ReadData, srcPos, dataPacket, 0, (ReadData.length - srcPos));//last a packet
                    actionType = ACTIONTYPE_CMD24K_FW_UPGRADE_V30;//发送完最后一包了
                } else {
                    System.arraycopy(ReadData, srcPos, dataPacket, 0, MAX_TRANS_COUNT_V30);//other packet except first and last packet
                }
                OTA_Write_Secton_Flash(dataPacket, SendPacketID);
                SendPacketID += 1;
            }
            Log.i("SYD_OTA", "24k SendPacketAllNum ==>" + SendPacketAllNum+"srcPos ==>" + srcPos+"SendPacketID=" + SendPacketID + " SectionTotalPacket=" + MAX_TRANS_SECTIONALL_PACKET_COUNT + " SendPacketID%SectionTotalPacket ==>" + SendPacketID % MAX_TRANS_SECTIONALL_PACKET_COUNT);

            if (actionType != ACTIONTYPE_CMD24K_FW_UPGRADE_V30) {
                if (SendPacketID % MAX_TRANS_SECTIONALL_PACKET_COUNT == 0) {
                    actionType = ACTIONTYPE_CMD24K_FW_WRITE_END;
                    Log.i("SYD_OTA", "Section:" + Integer.toString(SendPacketID / MAX_TRANS_SECTIONALL_PACKET_COUNT));
                }
            }
            // 创建一个数值格式化对象
            NumberFormat numberFormat = NumberFormat.getInstance();
            // 设置精确到小数点后2位
            numberFormat.setMaximumFractionDigits(2);
            String result = numberFormat.format((float) SendPacketID / (float) SendPacketAllNum * 100) + "%";
            ShowStatus("请勿中断，OTA进行中 ...");
            ShowStatusPercentage(result);
            ShowProgressBar((int) ((float) SendPacketID / (float) SendPacketAllNum * 100));
        } else {
            ShowStatus("OTA更新失败,请重试");
        }
    }


    public void OTA_Write_Exflash_Section_All_Flash(int status) {

        int srcPos = SendPacketID * MAX_TRANS_COUNT_V30;
        final byte[] dataPacket = new byte[MAX_TRANS_COUNT_V30];
        if (status == 0) {
            if (SendPacketID == SendPacketAllNum) {
                actionType = 0;
            } else {
                if (SendPacketID == (SendPacketAllNum - 1)) {
                    System.arraycopy(ReadData, srcPos, dataPacket, 0, (ReadData.length - srcPos));//last a packet
                    actionType = ACTIONTYPE_CMD_EXFLASH_UPGRADE_V30;//发送完最后一包了
                } else {
                    System.arraycopy(ReadData, srcPos, dataPacket, 0, MAX_TRANS_COUNT_V30);//other packet except first and last packet
                }
                OTA_Write_Secton_Flash(dataPacket, SendPacketID);
                SendPacketID += 1;
            }
            Log.i("SYD_OTA", "Exflash SendPacketAllNum ==>" + SendPacketAllNum+"srcPos ==>" + srcPos+"SendPacketID=" + SendPacketID + " SectionTotalPacket=" + MAX_TRANS_SECTIONALL_PACKET_COUNT + " SendPacketID%SectionTotalPacket ==>" + SendPacketID % MAX_TRANS_SECTIONALL_PACKET_COUNT);

            if (actionType != ACTIONTYPE_CMD_EXFLASH_UPGRADE_V30) {
                if (SendPacketID % MAX_TRANS_SECTIONALL_PACKET_COUNT == 0) {
                    actionType = ACTIONTYPE_CMD_EXFLASH_WRITE_END;
                    Log.i("SYD_OTA", "Section:" + Integer.toString(SendPacketID / MAX_TRANS_SECTIONALL_PACKET_COUNT));
                }
            }
            // 创建一个数值格式化对象
            NumberFormat numberFormat = NumberFormat.getInstance();
            // 设置精确到小数点后2位
            numberFormat.setMaximumFractionDigits(2);
            String result = numberFormat.format((float) SendPacketID / (float) SendPacketAllNum * 100) + "%";
            ShowStatus("请勿中断，OTA进行中 ...");
            ShowStatusPercentage(result);
            ShowProgressBar((int) ((float) SendPacketID / (float) SendPacketAllNum * 100));
        } else {
            ShowStatus("OTA更新失败,请重试");
        }
    }


    public void OTA_Write_Flash(byte[] ProgramData, int Address) {
        byte[] WriteData = new byte[20];
        Address = Address * MAX_TRANS_COUNT;

        WriteData[0] = CMD_FW_WRITE;
        WriteData[1] = 0x13;
        WriteData[2] = (byte) (Address & 0x000000FF);
        WriteData[3] = (byte) ((Address & 0x0000FF00) >> 8);
        WriteData[4] = (byte) ProgramData.length;

        int i = 0;
        for (i = 0; i < ProgramData.length; i++) {
            WriteData[i + 5] = ProgramData[i];
        }
        Log.i("SYD_OTA", "OTA_Write_Flash");
        bleService.sendData(WriteData);//写入第一包
    }

    public void OTA_Write_Secton_Flash(byte[] ProgramData, int Address) {
        bleService.sendData(ProgramData, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);//写入第一包
    }

    public void Write24KFlash_section_start(int check, int size, int Address) {
        byte[] WriteData = new byte[10];
        Address = Address * MAX_TRANS_SECTIONALL_COUNT;

        WriteData[0] = CMD24K_FW_WRITE_START;
        WriteData[1] = 0x13;
        WriteData[2] = (byte) (Address & 0x000000FF);
        WriteData[3] = (byte) ((Address & 0x0000FF00) >> 8);
        WriteData[4] = (byte) ((Address & 0x00FF0000) >> 16);
        WriteData[5] = (byte) ((Address & 0xFF000000) >> 24);
        WriteData[6] = (byte) (size & 0x000000FF);
        WriteData[7] = (byte) ((size & 0x0000FF00) >> 8);
        WriteData[8] = (byte) (check & 0x000000FF);
        WriteData[9] = (byte) ((check & 0x0000FF00) >> 8);

        bleService.sendData(WriteData);//写入第一包
    }

    public void Write_ExFlash_section_start(int check, int size, int Address) {
        byte[] WriteData = new byte[10];
        Address = Address * MAX_TRANS_SECTIONALL_COUNT;

        WriteData[0] = CMD_EXFLASH_WRITE_START;
        WriteData[1] = 0x13;
        WriteData[2] = (byte) (Address & 0x000000FF);
        WriteData[3] = (byte) ((Address & 0x0000FF00) >> 8);
        WriteData[4] = (byte) ((Address & 0x00FF0000) >> 16);
        WriteData[5] = (byte) ((Address & 0xFF000000) >> 24);
        WriteData[6] = (byte) (size & 0x000000FF);
        WriteData[7] = (byte) ((size & 0x0000FF00) >> 8);
        WriteData[8] = (byte) (check & 0x000000FF);
        WriteData[9] = (byte) ((check & 0x0000FF00) >> 8);

        bleService.sendData(WriteData);//写入第一包
    }

    public void OTA_Write_Flash_section_start(int check, int size, int Address) {
        byte[] WriteData = new byte[10];
        Address = Address * MAX_TRANS_SECTIONALL_COUNT;

        WriteData[0] = CMD_FW_WRITE_START;
        WriteData[1] = 0x13;
        WriteData[2] = (byte) (Address & 0x000000FF);
        WriteData[3] = (byte) ((Address & 0x0000FF00) >> 8);
        WriteData[4] = (byte) ((Address & 0x00FF0000) >> 16);
        WriteData[5] = (byte) ((Address & 0xFF000000) >> 24);
        WriteData[6] = (byte) (size & 0x000000FF);
        WriteData[7] = (byte) ((size & 0x0000FF00) >> 8);
        WriteData[8] = (byte) (check & 0x000000FF);
        WriteData[9] = (byte) ((check & 0x0000FF00) >> 8);

        bleService.sendData(WriteData);//写入第一包
    }

    public void OTA_Upgrade_Flash_V30(int Size, int CRC) {
        Log.i("SYD_OTA", "OTA_Upgrade_Flash CRC_V30:" + CRC + "Size" + Size);
        byte[] WriteData = new byte[8];
        WriteData[0] = CMD_FW_UPGRADEV20;
        WriteData[1] = 0x04;
        WriteData[2] = (byte) (Size & 0x000000FF);
        WriteData[3] = (byte) ((Size & 0x0000FF00) >> 8);
        WriteData[4] = (byte) ((Size & 0x00FF0000) >> 16);
        WriteData[5] = (byte) ((Size & 0xFF000000) >> 24);
        WriteData[6] = (byte) (CRC & 0x000000FF);
        WriteData[7] = (byte) ((CRC & 0x0000FF00) >> 8);
        actionType = ACTIONTYPE_CMD_FW_FINISH;
        bleService.sendData(WriteData);
    }

    byte[] ReadOTAFileBinary(String filepath) {
        File file = new File(filepath);
        try {
            FileInputStream fis = new FileInputStream(file);
            int length = fis.available();
            byte[] BinaryData = new byte[length];

            fis.read(BinaryData);

            fis.close();
            return BinaryData;
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        byte[] BinaryData = new byte[]{0x01, 0x01};
        return BinaryData;
    }

    public void refreshFiles() {
        FileNameList.clear();
        FilePathList.clear();
        GetFiles(m_dir, ".bin");
        ArrayAdapter<String> array = new ArrayAdapter(OTA_Active.this, android.R.layout.simple_list_item_1, FileNameList);
        listview.setAdapter(array);
    }

    public void GetFiles(String Path, String Extension)  //搜索目录，扩展名
    {
        File file = new File(Path);
        //判断文件夹是否存在,如果不存在就退出
        if (!file.exists()) {
            return;
        }
        File[] files = new File(Path).listFiles();//获取文件列表，文件夹和文件

        Log.i("SYD_OTA", "files L:" + files.length + "Path:" + Path);

        for (int i = 0; i < files.length; i++) {
            File f = files[i];
            if (f.isFile()) {
                String f_name = f.getName();

                if (f_name.indexOf(Extension) != -1) {
                    //Log.i("SYD_OTA","files name:"+f_name);
                    FileNameList.add(f_name);
                    FilePathList.add(f.getAbsolutePath());
                    //Log.i("SYD_OTA", "name:" + f.getName() + "      "+"path:" + f.getPath());
                }
            }
        }
    }

    void ShowVibrator(final Byte status) {
        Vibrator vibrator = (Vibrator) this.getSystemService(this.VIBRATOR_SERVICE);
        long[] patter = {1000, 1000, 2000, 100};
        vibrator.vibrate(patter, -1);   //0 循环 1不循环
    }

    void ShowStatus(final String status) {
        runOnUiThread(new Runnable()   // 這個執行緒是為了 UI 畫面顯示
        {
            @Override
            public void run() {
                Status_textView.setText(status);
            }
        });
    }

    void ShowUpdatefile(final String status) {
        runOnUiThread(new Runnable()   // 這個執行緒是為了 UI 畫面顯示
        {
            @Override
            public void run() {
                Update_textView.setText(status);
            }
        });
    }

    void ShowStatusPercentage(final String status) {
        runOnUiThread(new Runnable()   // 這個執行緒是為了 UI 畫面顯示
        {
            @Override
            public void run() {
                StatusPercentage_textView.setText(status);
            }
        });
    }

    void Showelapsedtimer(final long second) {
        runOnUiThread(new Runnable()   // 這個執行緒是為了 UI 畫面顯示
        {
            @Override
            public void run() {
                elapsedtimer_textView.setText(Long.toString(second / 1000) + "." + Long.toString(second % 1000) + "S");
            }
        });
    }

    void ShowProgressBar(int mProgressStatus) {
        mprogressBarOta.setProgress(mProgressStatus);
    }

    void EnableButton(final Button bt, final Boolean IsEnable) {
        runOnUiThread(new Runnable()   // 這個執行緒是為了 UI 畫面顯示
        {
            @Override
            public void run() {
                bt.setEnabled(IsEnable);
            }
        });
    }

    void EnableListview(final ListView lv, final Boolean IsEnable) {
        runOnUiThread(new Runnable()   // 這個執行緒是為了 UI 畫面顯示
        {
            @Override
            public void run() {
                lv.setEnabled(IsEnable);
            }
        });
    }
}
