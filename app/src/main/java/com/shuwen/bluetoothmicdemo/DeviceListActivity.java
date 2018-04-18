package com.shuwen.bluetoothmicdemo;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import butterknife.BindView;
import butterknife.ButterKnife;

public class DeviceListActivity extends Activity {

    public static final String EXTRA_DEVICE_ADDRESS = "device_address";
    @BindView(R.id.lv_device)
    ListView mDeviceListView;
    private static ArrayAdapter<String> mDevicesArrayAdapter;
    public static final int GET_DEVICE_INFO = 1000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_device_list);
        ButterKnife.bind(this);
        mDevicesArrayAdapter =
                new ArrayAdapter<String>(this, R.layout.device_name);

        mDeviceListView.setAdapter(mDevicesArrayAdapter);
        mDeviceListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

                String info = ((TextView) view).getText().toString();
                String address = info.substring(info.length() - 17);

                // Create the result Intent and include the MAC address
                Intent intent = new Intent();
                intent.putExtra(EXTRA_DEVICE_ADDRESS, address);

                // Set result and finish this Activity
                setResult(Activity.RESULT_OK, intent);
                finish();

            }
        });
        //注册蓝牙广播，接受广播消息（发现蓝牙设备、搜索结束）
        BluetoothReceiver bluetoothReceiver = new BluetoothReceiver();
        registerReceiver(bluetoothReceiver,new IntentFilter(BluetoothDevice.ACTION_FOUND));
        registerReceiver(bluetoothReceiver,new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED));
    }

    public static void start(Activity context){
        context.startActivityForResult(new Intent(context, DeviceListActivity.class),GET_DEVICE_INFO);
    }
    static class BluetoothReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {

            if (intent == null)return;
            //发现蓝牙设备
            if (intent.getAction().equals(BluetoothDevice.ACTION_FOUND)){
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
                    mDevicesArrayAdapter.add(device.getName() + "\n" + device.getAddress());
                }

            }else if (intent.getAction().equals(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)){
                Toast.makeText(context,"蓝牙搜索结束", Toast.LENGTH_LONG).show();
            }
        }
    }
}
