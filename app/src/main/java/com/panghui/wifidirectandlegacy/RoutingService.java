package com.panghui.wifidirectandlegacy;

import android.os.Handler;
import android.util.Log;

import com.alibaba.fastjson.JSON;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.LinkedList;

public class RoutingService implements Runnable{
    Handler handler;
    LinkedList<RoutingTableItem> routingTable; // 引用本地路由表
    DatagramPacket packet;
    String Android_ID; // 本机地址 ID
    String ipOfWD; // ip of wifi direct
    String ipOfWL; // ip of wifi legacy

    public RoutingService( String ipOfWD,String ipOfWL,String Android_ID, Handler handler,LinkedList<RoutingTableItem> routingTable , DatagramPacket packet){
        this.handler = handler;
        this.ipOfWD = ipOfWD;
        this.ipOfWL = ipOfWL;
        this.Android_ID = Android_ID;
        this.routingTable = routingTable;
        this.packet = packet;
    }

    public RoutingItem parsePacket(DatagramPacket packet){
        String jsonDate = new String(packet.getData()).trim();
        RoutingItem routingItem = JSON.parseObject(jsonDate, RoutingItem.class);
        return routingItem;
    }

    private void sendMessage(String str,String ipAddress) throws UnknownHostException {

        InetAddress nextHop = InetAddress.getByName(ipAddress);
        DatagramSocket ds = null;

        try{
            int sendPort = 23000;
            ds = new DatagramSocket();
            DatagramPacket dp = new DatagramPacket(str.getBytes(),str.getBytes().length,nextHop,sendPort);
            ds.send(dp);
        }catch (Exception e){
            e.printStackTrace();
        }finally {
            if(ds != null) ds.close();
        }
    }

    @Override
    public void run() {
        RoutingItem packetItem = parsePacket(packet); // 获取UDP包中的对象实例

        if(packetItem.getSender().equals(Android_ID)){
            Log.d(MainActivity.TAG,"发送设备为本机，不予以转发");
            handler.obtainMessage(MainActivity.SET_TEXTVIEW,"发送设备为本机，不予以转发").sendToTarget();
            return;
        }else if(packetItem.getDestination().equals(ipOfWL)){ // 目的地址为本机，则不予以转发
            Log.d(MainActivity.TAG,"接收设备为本机，不予以转发");
            handler.obtainMessage(MainActivity.SET_TEXTVIEW,"接收设备为本机，不予以转发").sendToTarget();
            return;
        }else if(packetItem.getDestination().equals(ipOfWD)){
            Log.d(MainActivity.TAG,"接收设备为GO，不予以转发");
            handler.obtainMessage(MainActivity.SET_TEXTVIEW,"接收设备为GO，不予以转发").sendToTarget();
            return;
        }

        int ttl = packetItem.getTTL();
        if(ttl>0){
            packetItem.setTTL(ttl-1);
        }else { // 若已过期，则不进行转发
            return;
        }

        String destination="";
        if(packetItem.getDestination().equals("192.168.49.255")){ // 若目的地址为广播地址
            destination = "192.168.49.255";
        }else {
            for (RoutingTableItem tableItem : routingTable){
                if(tableItem.getNeighbor().equals(packetItem.getDestination())){ // 在当前路由表中找到有该邻居的条目
                    destination = tableItem.getNeighbor();
                    break;
                }
            }
            if(destination.equals("")){
                destination = "192.168.49.255";
            }
        }

        try {
            sendMessage(JSON.toJSONString(packetItem),destination);
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
    }
}
