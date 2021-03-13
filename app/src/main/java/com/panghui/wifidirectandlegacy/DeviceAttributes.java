package com.panghui.wifidirectandlegacy;

import com.panghui.wifidirectandlegacy.routing.RoutingTableItem;

import java.util.LinkedList;

/**
 * 该类用于存储设备所需要的属性
 */
public class DeviceAttributes {
    static public String deviceFlag;
    static public String androidID;
    static public String networkCredential;
    static public int jaccardIndex;
    static public boolean isConnectedToGO = false;
    static public boolean isGO = false;
    static public boolean foundP2pDevicesDone = false;

    static public String currentlyConnectedDevice = "";
    static public String targetDeviceName = "";

    // routing table
    static public LinkedList<RoutingTableItem> routingTable = new LinkedList<>();
    static public LinkedList<String> neighborList = new LinkedList<>(); // 邻居列表
}
