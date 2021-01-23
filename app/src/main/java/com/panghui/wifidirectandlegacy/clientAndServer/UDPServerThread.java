package com.panghui.wifidirectandlegacy.clientAndServer;

import android.os.Handler;
import android.util.Log;

import com.alibaba.fastjson.JSON;
import com.panghui.wifidirectandlegacy.MainActivity;
import com.panghui.wifidirectandlegacy.Utils;
import com.panghui.wifidirectandlegacy.routing.RoutingItem;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

public class UDPServerThread extends Thread{
    Handler handler;
    String Andriod_ID;
    int receivePort;
    String ip;

    public UDPServerThread(String Android_ID,Handler handler,int receivePort){
        this.Andriod_ID = Android_ID;
        this.handler = handler;
        this.receivePort = receivePort;
        this.ip = Utils.getIPAddress(true);
        handler.obtainMessage(MainActivity.SET_TEXTVIEW,"设备IP为:"+ip).sendToTarget();
    }
    @Override
    public void run() {
        listen();
    }

    private void listen(){
        String TAG = "Listen";
        while (true){
            Log.d(TAG,"UDP Listen begin");
            handler.obtainMessage(MainActivity.SET_TEXTVIEW,"UDP Listen begin").sendToTarget();
            DatagramSocket rds = null;
            try{
//                int receivePort = 23000;
                byte[] inBuf = new byte[1024];
                rds = new DatagramSocket(receivePort);
                DatagramPacket inPacket = new DatagramPacket(inBuf,inBuf.length);
                rds.receive(inPacket);
                rds.setSoTimeout(1000);

                InetAddress address = inPacket.getAddress(); //获取发送方的IP地址
                String clientIP = address.toString();
                clientIP = clientIP.substring(1);
                if(clientIP.equals(ip)){ // 若收到来自本设备的数据包，则重新开始循环
                    continue;
                }

                RoutingItem item = new RoutingItem(Andriod_ID,clientIP,"",0,0,5,"ack");

                String str = JSON.toJSONString(item);
                sendMessage(str,clientIP); // 接收到消息后，回复一个 ack
                handler.obtainMessage(MainActivity.ROUND_TRIP_TIME_RECEIVE).sendToTarget();

//                handler.obtainMessage(MainActivity.FORWARD,inPacket).sendToTarget(); // 转发
                handler.obtainMessage(MainActivity.SET_TEXTVIEW,"clientIP:"+clientIP).sendToTarget();
                handler.obtainMessage(MainActivity.SET_TEXTVIEW,"设备IP为:"+ip).sendToTarget();

                String rdata = new String(inPacket.getData()).trim();

                Log.d(TAG,rdata);

                handler.obtainMessage(MainActivity.SET_TEXTVIEW,rdata+" "+"from "+ clientIP).sendToTarget();

                // 方案一：每发送完一条消息就要断开连接进入GO； 方案二：每发送完成一条消息可以继续发送，等待用户已经确定发送完成消息后，再手动进入GO状态
//                if(clientIP.equals("192.168.49.1")){
//                    // 收到来自GO 的数据包后，即可以断开连接
//                    handler.obtainMessage(MainActivity.DISCONNECT_FROM_GO_DONE).sendToTarget();
//                }

            }catch (SocketTimeoutException e){
                Log.e(TAG,"listen timeout");
                handler.obtainMessage(MainActivity.SET_TEXTVIEW,"listen timeout").sendToTarget();
            }catch (Exception e){
                e.printStackTrace();
            }finally {
                if(rds != null && !rds.isClosed()){
                    rds.close();
                }
            }
        }
    }

    /**
     * 发送消息
     * @param str 消息内容
     * @param ip 指定 IP 地址
     * @throws UnknownHostException
     */
    private void sendMessage(String str,String ip) throws UnknownHostException {
        String TAG = "sendMessage";
        Log.d(TAG,"发送数据");
        handler.obtainMessage(MainActivity.SET_TEXTVIEW,"发送数据").sendToTarget();
        InetAddress broadcastAddress = InetAddress.getByName(ip);
        DatagramSocket ds = null;

        try {
            int sendPort = 23000;
            ds = new DatagramSocket();
            ds.setBroadcast(true);
            DatagramPacket dp = new DatagramPacket(str.getBytes(), str.getBytes().length, broadcastAddress, sendPort);
            ds.send(dp);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (ds != null) ds.close();
        }
    }
}
