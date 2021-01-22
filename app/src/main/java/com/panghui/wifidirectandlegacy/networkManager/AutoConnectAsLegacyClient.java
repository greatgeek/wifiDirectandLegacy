package com.panghui.wifidirectandlegacy.networkManager;

import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.util.Log;

import com.panghui.wifidirectandlegacy.MainActivity;
import com.panghui.wifidirectandlegacy.WifiAdmin;


import java.util.ArrayList;
import java.util.List;

import static com.panghui.wifidirectandlegacy.MainActivity.SET_TEXTVIEW;

public class AutoConnectAsLegacyClient extends Thread{
    WifiManager wifiManager;
    WifiAdmin wifiAdmin;
    ArrayList<InformationCollection.JaccardIndexItem> networkCredential;
    Handler handler;
    List<ScanResult> deviceScanResult = new ArrayList<>();

    public AutoConnectAsLegacyClient(WifiManager wifiManager,WifiAdmin wifiAdmin,ArrayList<InformationCollection.JaccardIndexItem> networkCredential,Handler handler){
        this.wifiManager = wifiManager;
        this.wifiAdmin = wifiAdmin;
        this.handler = handler;
        this.networkCredential = networkCredential;
    }

    @Override
    public void run() {
        boolean success = wifiManager.startScan();
    }

}
