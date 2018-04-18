package com.shuwen.bluetoothmicdemo;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.shuwen.bluetoothmicdemo.restapi.asrdemo.AsrMain;
import com.shuwen.bluetoothmicdemo.restapi.common.DemoException;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Set;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

import static com.shuwen.bluetoothmicdemo.VoiceRecorder.AudioFormat.OPU;

public class MainActivity extends AppCompatActivity implements View.OnClickListener, VoiceRecorder.Callback {

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothService mBluetoothService;
    private String mConnectedDeviceName = null;
    private PermissionsManager mPermissionsManager;
    private boolean isNeedCheck = true;
    public static final int PERMISSON_REQUESTCODE = 0;
    private VoiceRecorder mRecorder;
    @BindView(R.id.tv_result)
    TextView mResult;
    @BindView(R.id.et_msg)
    EditText mMsg;
    private Handler mHandler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what){
                case Constants.MESSAGE_DEVICE_NAME:
                    // save the connected device's name
                    mConnectedDeviceName = msg.getData().getString(Constants.DEVICE_NAME);
                    showToast("已经连接到设备：" + mConnectedDeviceName);
                    break;
                case Constants.MESSAGE_READ:
                    byte[] readBuf = (byte[]) msg.obj;
                    // construct a string from the valid bytes in the buffer
                    String readMessage = new String(readBuf, 0, msg.arg1);
                    mResult.setText(mResult.getText().toString() + "\n" + readMessage);
                    break;
                case Constants.ASR_RESULT:
                    showToast(msg.obj.toString());
                    try {
                        JSONObject data = new JSONObject(msg.obj.toString());
                        String result = data.optString("result");
                        mResult.setText(mResult.getText().toString() + "\n" + "语音识别结果："+ result);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }

                    break;
                    default:break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        mPermissionsManager = new PermissionsManager(this);
        initData();
        initBlueTooth();
    }

    private void initData() {
        mRecorder = new VoiceRecorder.Builder(this)
                .sampleRate(16000)
                .channels(1)
                .bitrate(16)
                .format(OPU)
                .frameTime(500)
                .bufferSize(1280)
                .build();
    }

    private void initBlueTooth() {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null){
            Toast.makeText(this,"蓝牙不可用",Toast.LENGTH_LONG).show();
            return;
        }
        if (!mBluetoothAdapter.isEnabled()){
            if(!mBluetoothAdapter.enable()){
                Toast.makeText(this,"蓝牙打开失败",Toast.LENGTH_LONG).show();
            }
        }
        mBluetoothService = new BluetoothService(this,mHandler);
        if (mBluetoothService.getState() == mBluetoothService.STATE_NONE){
            mBluetoothService.start();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && getApplicationInfo().targetSdkVersion >= Build.VERSION_CODES.M) {
            if (isNeedCheck){
                mPermissionsManager.checkPermissions(MAP_NEED_PERMISSIONS);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mBluetoothService != null) {
            mBluetoothService.stop();
        }
        mHandler.removeCallbacks(null);
    }

    @OnClick({R.id.btn_search_bluetooth, R.id.btn_send_msg,R.id.btn_connect_bluetooth,
            R.id.btn_start_record, R.id.btn_stop_record, R.id.btn_speak, R.id.btn_asr})
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.btn_search_bluetooth:
                if (mBluetoothAdapter!= null) {
                    DeviceListActivity.start(MainActivity.this);
                    mBluetoothAdapter.startDiscovery();
                }
                break;
            case R.id.btn_send_msg:
                sendMsg();
                break;
            case R.id.btn_connect_bluetooth:
                connectBlueTooth();
                break;
            case R.id.btn_start_record:
                showToast("开始录音中。。。");
                mRecorder.start();
                break;
            case R.id.btn_stop_record:
                mRecorder.stop();
                showToast("结束录音。。。");
                break;
            case R.id.btn_speak:
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        play();
                    }
                }).start();
                break;
            case R.id.btn_asr:
                asrStart();
                break;
                default:
                    break;
        }
    }

    private void connectBlueTooth() {
        Set<BluetoothDevice> bondedDevices = mBluetoothAdapter.getBondedDevices();
        if (bondedDevices.size() >0){
            BluetoothDevice device = bondedDevices.iterator().next();
            mBluetoothService.connect(device, true);
        }else {
            Toast.makeText(this, "请首先连接蓝牙", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);

        if (requestCode == DeviceListActivity.GET_DEVICE_INFO && intent != null){
            String stringExtra = intent.getStringExtra(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
            if (!TextUtils.isEmpty(stringExtra)) {
                Toast.makeText(this, stringExtra, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void sendMsg() {
        String msg = mMsg.getText().toString();
        sendMessage(msg);
        mResult.setText(mResult.getText().toString() + "\n" + "我：" + msg);
    }

    private void showToast(String msg){
        if (MainActivity.this != null) {
            Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
        }
    }

    private void sendMessage(String message) {
        // Check that we're actually connected before trying anything
        if (mBluetoothService.getState() != BluetoothService.STATE_CONNECTED) {
            showToast("连接失败");
            return;
        }

        // Check that there's actually something to send
        if (message.length() > 0) {
            // Get the message bytes and tell the BluetoothChatService to write
            byte[] send = message.getBytes();
            mBluetoothService.write(send);
        }
    }

    /**
     * Sends a message.
     *
     * @param send A string of text to send.
     */
    private void sendVoice(byte[] send) {
        // Check that we're actually connected before trying anything
        if (mBluetoothService.getState() != BluetoothService.STATE_CONNECTED) {
            showToast("连接失败");
            return;
        }

        // Check that there's actually something to send
        if (send.length > 0) {
            // Get the message bytes and tell the BluetoothChatService to write
            mBluetoothService.write(send);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSON_REQUESTCODE) {
            if (!mPermissionsManager.verifyPermissions(grantResults)) {
                //mPermissionsManager.showMissingPermissionDialog();
                isNeedCheck = false;
            }
        }
    }

    public final static String[] MAP_NEED_PERMISSIONS = {
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.INTERNET,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE
    };

    @Override
    public boolean doRecordReady() {
        return true;
    }

    @Override
    public void onRecordStart() {

    }

    @Override
    public void onRecordStop() {

    }

    @Override
    public void onRecord(short[] voice) {
        sendVoice(DataChangeUtils.changeShortsToBytes(voice));
    }

    @Override
    public void onRecordFailed(int errorCode) {

    }

    //播放音频（PCM）
    public void play() {
        int sampleRateInHz = 16000;
        DataInputStream dis = null;
        try {
            //从音频文件中读取声音
            dis = new DataInputStream(new BufferedInputStream(new FileInputStream(Constants.pcmFilePath)));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            showToast("文件不存在");
            return;
        }
        //最小缓存区
        int bufferSizeInBytes = AudioTrack.getMinBufferSize(sampleRateInHz, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        //创建AudioTrack对象   依次传入 :流类型、采样率（与采集的要一致）、音频通道（采集是IN 播放时OUT）、量化位数、最小缓冲区、模式
        AudioTrack player = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRateInHz, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSizeInBytes, AudioTrack.MODE_STREAM);

        byte[] data = new byte[bufferSizeInBytes];
        player.play();//开始播放
        while (true) {
            int i = 0;
            try {
                while (dis.available() > 0 && i < data.length) {
                    data[i] = dis.readByte();//录音时write Byte 那么读取时就该为readByte要相互对应
                    i++;
                }
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            player.write(data, 0, data.length);

            if (i != bufferSizeInBytes) //表示读取完了
            {
                player.stop();//停止播放
                player.release();//释放资源
                break;
            }
        }
    }
    private void asrStart() {
        new Thread(
                new Runnable() {
                    @Override
                    public void run() {
                        AsrMain asrMain = new AsrMain();
                        String result = null;
                        try {
                            result = asrMain.run();
                            mHandler.obtainMessage(Constants.ASR_RESULT,result).sendToTarget();
                        } catch (IOException e) {
                            e.printStackTrace();
                        } catch (DemoException e) {
                            e.printStackTrace();
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                }
        ).start();
    }

}
