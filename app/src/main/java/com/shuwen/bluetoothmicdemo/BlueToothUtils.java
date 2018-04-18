package com.shuwen.bluetoothmicdemo;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;

import java.util.Set;

/**
 * Created by ruanchao on 2018/4/16.
 */

public class BlueToothUtils {

    private static volatile BlueToothUtils mBlueToothUtils;
    private BluetoothAdapter mBluetoothAdapter;

    private BlueToothUtils(){
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    private BlueToothUtils getInstance(){
        if (mBlueToothUtils == null) {
            synchronized (BlueToothUtils.class) {
                if (mBlueToothUtils == null) {
                    mBlueToothUtils = new BlueToothUtils();
                }
            }
        }
        return mBlueToothUtils;
    }

    //////////蓝牙api////////////

    /**
     * 获取已配对的设备
     * @return
     */
    public Set<BluetoothDevice> getPairedDevices(){
        Set<BluetoothDevice> bondedDevices = mBluetoothAdapter.getBondedDevices();
        return bondedDevices;
    }

    /**
     * 搜索蓝牙
     * 搜索蓝牙后会接受到发现蓝牙的广播（"android.bluetooth.device.action.FOUND"）
     * 接受广播在静态注册的广播接收器中单独处理（BluetoothReceiver）
     */
    public void doDiscovery(){
        mBluetoothAdapter.startDiscovery();
    }

}
