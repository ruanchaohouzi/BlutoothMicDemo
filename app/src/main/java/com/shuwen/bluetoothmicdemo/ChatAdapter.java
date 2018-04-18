package com.shuwen.bluetoothmicdemo;

import android.content.Context;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by ruanchao on 2018/4/12.
 */

public class ChatAdapter extends BaseAdapter {

    private List<String> mChatList = new ArrayList<>();
    private Context mContext;

    public ChatAdapter(Context context){
        mContext = context;
        mChatList.add("测试数据");
    }

    public void addChatList(String msg){
        if (!TextUtils.isEmpty(msg)){
            mChatList.add(msg);
            notifyDataSetChanged();
        }
    }

    @Override
    public int getCount() {
        return mChatList.size();
    }

    @Override
    public Object getItem(int position) {
        return null;
    }

    @Override
    public long getItemId(int position) {
        return 0;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        TextView textView = new TextView(mContext);
        textView.setText(mChatList.get(position));
        return textView;
    }
}
