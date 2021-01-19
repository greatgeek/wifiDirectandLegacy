package com.panghui.wifidirectandlegacy;

import android.os.Handler;
import android.util.Log;

import com.alibaba.fastjson.JSON;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

public class UDPServerThread extends Thread{
    Handler handler;
    String ipOfWD;
    String ipOfWL;
    String Andriod_ID;

    public UDPServerThread(String Android_ID,Handler handler,String ipOfWD,String ipOfWL){
        this.Andriod_ID = Android_ID;
        this.handler = handler;
        this.ipOfWD = ipOfWD;
        this.ipOfWL = ipOfWL;
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
                int receivePort = 23000;
                byte[] inBuf = new byte[1024];
                rds = new DatagramSocket(receivePort);
                DatagramPacket inPacket = new DatagramPacket(inBuf,inBuf.length);
                rds.receive(inPacket);
                rds.setSoTimeout(1000);

                InetAddress address = inPacket.getAddress(); //获取发送方的IP地址
                String clientIP = address.toString();
                clientIP = clientIP.substring(1);

                RoutingItem item = new RoutingItem(Andriod_ID,clientIP,"",0,0,5,"ack");
                sendMessage(JSON.toJSONString(item),clientIP);

                 handler.obtainMessage(MainActivity.FORWARD,inPacket).sendToTarget(); // 转发
                 handler.obtainMessage(MainActivity.SET_TEXTVIEW,"clientIP:"+clientIP).sendToTarget();
                 handler.obtainMessage(MainActivity.SET_TEXTVIEW,"ipOfWD:"+ipOfWD).sendToTarget();
                 handler.obtainMessage(MainActivity.SET_TEXTVIEW,"ipOfWL:"+ipOfWL).sendToTarget();

                String rdata = new String(inPacket.getData()).trim();

                Log.d(TAG,rdata);


                handler.obtainMessage(MainActivity.SET_TEXTVIEW,rdata+" "+"from "+ clientIP).sendToTarget();
                handler.obtainMessage(MainActivity.UPDATE_ROUTING_TABLE, clientIP).sendToTarget(); // 更新路由表

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
