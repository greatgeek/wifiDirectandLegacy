package com.panghui.wifidirectandlegacy;

import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pManager.PeerListListener;
import android.os.Handler;
import android.util.Log;

import java.util.HashMap;
import java.util.List;

public class MyPeerListListener implements PeerListListener {
    private List<WifiP2pDevice> peers;
    private Handler handler;
    private HashMap<String,String> networkCredential;
        // 将 Device 列表传递到 MainActivity
    public MyPeerListListener(List<WifiP2pDevice> peers, HashMap<String,String> networkCredential, Handler handler){
        this.peers=peers;
        this.handler=handler;
        this.networkCredential = networkCredential;
    }
    @Override
    public void onPeersAvailable(WifiP2pDeviceList peersList) {
        peers.clear();
        peers.addAll(peersList.getDeviceList());

//        handler.obtainMessage(MainActivity.FOUND_PEER_DONE).sendToTarget();// 表示已找到设备

        if(peers.size() == 0){
            Log.d(MainActivity.TAG,"No devices found");
//            handler.obtainMessage(MainActivity.SET_TEXTVIEW,"No devices found").sendToTarget();
            return;
        }else{
            for(int i=0;i<peers.size();i++){
                String[] deviceNamePart=peers.get(i).deviceName.split("_");
                if(deviceNamePart.length==3){
                    networkCredential.put(deviceNamePart[1],deviceNamePart[2]);
                }

                Log.d(MainActivity.TAG,peers.get(i).deviceName);
                handler.obtainMessage(MainActivity.SET_TEXTVIEW,peers.get(i).deviceName).sendToTarget();
            }
            Log.d(MainActivity.TAG, networkCredential.toString());
            handler.obtainMessage(MainActivity.SET_TEXTVIEW, networkCredential.toString()).sendToTarget();
        }
    }
}
