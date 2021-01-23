package com.panghui.wifidirectandlegacy.clientAndServer;

import android.os.Handler;
import android.util.Log;

import com.alibaba.fastjson.JSON;
import com.panghui.wifidirectandlegacy.MainActivity;
import com.panghui.wifidirectandlegacy.routing.RoutingItem;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class UDPClientThread extends Thread {

    public Handler handler;
    public String Android_ID;
    public int sendingPort;
    public String msg;
    public UDPClientThread(String Android_ID,Handler handler,int sendingPort,String msg){
        this.Android_ID = Android_ID;
        this.handler=handler;
        this.sendingPort = sendingPort;
        this.msg = msg;
    }

    @Override
    public void run() {
        try {
            RoutingItem item = new RoutingItem(Android_ID,"192.168.49.255","",0,0,5,msg);
            sendMessage(JSON.toJSONString(item));
            // TODO:1)断开与GO的连接 2）变成GO
//            handler.obtainMessage(MainActivity.DISCONNECT_FROM_GO_DONE).sendToTarget();
//            handler.obtainMessage(MainActivity.BECOME_GO).sendToTarget();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
    }

    private void sendMessage(String str) throws UnknownHostException {
        String TAG = "sendMessage";
        Log.d(TAG,"发送数据");
        handler.obtainMessage(MainActivity.SET_TEXTVIEW,"发送数据").sendToTarget();
        InetAddress ipAddress = InetAddress.getByName("192.168.49.255");
        DatagramSocket ds = null;

        try {
//            int sendingPort = 23000;
            ds = new DatagramSocket();
            ds.setBroadcast(true);
            DatagramPacket dp = new DatagramPacket(str.getBytes(), str.getBytes().length, ipAddress, sendingPort);
            ds.send(dp);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (ds != null) ds.close();
        }
    }
}
