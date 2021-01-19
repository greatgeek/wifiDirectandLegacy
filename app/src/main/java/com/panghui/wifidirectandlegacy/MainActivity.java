package com.panghui.wifidirectandlegacy;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.Channel;
import android.net.wifi.p2p.WifiP2pManager.ConnectionInfoListener;
import android.net.wifi.p2p.WifiP2pManager.GroupInfoListener;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.alibaba.fastjson.JSON;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements ConnectionInfoListener,GroupInfoListener{

    public static final String TAG = "wifiDirectAndLegacy";
    public static final int SET_TEXTVIEW = 1000;
    public static final int FOUND_PEER_DONE = 1001;
    public static final int DISCOVER_SERVICE_DONE = 1002;
    public static final int UPDATE_ROUTING_TABLE = 1003;
    public static final int FORWARD = 1004;
    public static final int UPDATE_MODE = 1005;

    // wifi direct and wifi legacy manager
    private WifiP2pManager wifiP2pManager;
    private boolean isWifiP2pEnabled = false;
    private WifiManager wifiManager;
    private BatteryManager batteryManager;

    // wifiP2p and wifi legacy IntentFilter
    private final IntentFilter wifiP2pIntentFilter = new IntentFilter();
    private final IntentFilter wifiIntentFilter = new IntentFilter();
    private Channel channel;
    private BroadcastReceiver wifiP2pReceiver =null;

    // wifiP2p discoverPeers and wifi legacy scan result
    private List<WifiP2pDevice> peers = new ArrayList<>();
    public List<ScanResult> devicesResult = new ArrayList<>();

    // local android device ID
    private String Android_ID;
    private String[] deviceNamePart = new String[3];

    // UI display
    Button discoverPeersBT;
    Button becomeGOBT;
    Button connectBT;
    Button removeGroupBT;
    Button serverBT;
    Button clientBT;
    Button requestGroupInfoBT;
    Button getIpAddr;
    Button setDeviceName;
    Button scanResult;
    Button connectToLegacy;
    Button getBatteryCapacity;
    EditText ipText;
    Button directSend;

    public TextView log;
    public TextView mode; // relay mode or client mode
    public ScrollView scrollView;

    // network credential
    private HashMap<String,String> networkCredential = new HashMap<>(); // [SSID,passphrase]

    // wifi legacy administrator
    public WifiAdmin wifiAdmin;

    // wifi ip of wifi direct and ip of wifi legacy
    static String ipOfWD = "";
    static String ipOfWL = "";

    // routing table
    static LinkedList<RoutingTableItem> routingTable = new LinkedList<>();

    // Server and Client Thread
    private UDPServerThread serverThread = null;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        discoverPeersBT = findViewById(R.id.discoverPeers);
        becomeGOBT = findViewById(R.id.become_GO);
        connectBT = findViewById(R.id.connect);
        removeGroupBT = findViewById(R.id.removeGroup);
        serverBT = findViewById(R.id.Server);
        clientBT = findViewById(R.id.Client);
        requestGroupInfoBT = findViewById(R.id.requestGroupInfo);
        getIpAddr = findViewById(R.id.getIPaddr);
        setDeviceName = findViewById(R.id.setDeviceName);
        scanResult = findViewById(R.id.scanResult);
        connectToLegacy = findViewById(R.id.connectToLegacy);
        getBatteryCapacity = findViewById(R.id.getBatteryCapacity);

        ipText = findViewById(R.id.ipText);
        directSend = findViewById(R.id.directedSend);
        log = findViewById(R.id.log);
        scrollView = findViewById(R.id.scrollView);
        mode = findViewById(R.id.mode); // 用于设置模式（relay mode or client mode）

        Android_ID = Settings.Secure.getString(getApplicationContext().getContentResolver(),
                Settings.Secure.ANDROID_ID).substring(0,4);
        deviceNamePart[1]=Android_ID;

        wifiManager = (WifiManager)getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        wifiAdmin = new WifiAdmin(getApplicationContext());
        batteryManager = (BatteryManager) getSystemService(BATTERY_SERVICE);

        // wifi p2p intent filter
        wifiP2pIntentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        wifiP2pIntentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        wifiP2pIntentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        wifiP2pIntentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

        // wifi legacy intent filter
        wifiIntentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        wifiIntentFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);

        wifiP2pManager = (WifiP2pManager)getSystemService(Context.WIFI_P2P_SERVICE);
        // 1. 通过WLAN框架注册应用。必须调用此方法，然后再调用任何其他WLAN P2P 方法
        channel = wifiP2pManager.initialize(this,getMainLooper(),null);

        // 2. 发现对等设备
        discoverPeersBT.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                wifiP2pManager.discoverPeers(channel,new WifiP2pManager.ActionListener(){
                    @Override
                    public void onSuccess() {
                        Toast.makeText(MainActivity.this, "Discovery Initiated",
                                Toast.LENGTH_SHORT).show();
                        handler.obtainMessage(SET_TEXTVIEW,"Discovery Initiated").sendToTarget();
                    }

                    @Override
                    public void onFailure(int reasonCode) {
                        Toast.makeText(MainActivity.this, "Discovery Failed : " + reasonCode,
                                Toast.LENGTH_SHORT).show();
                        handler.obtainMessage(SET_TEXTVIEW,"Discovery Failed").sendToTarget();
                    }
                });
            }
        });

        // 连接到指定设备
        connectBT.setOnClickListener(new View.OnClickListener(){
            WifiP2pDevice device;
            WifiP2pConfig config = new WifiP2pConfig(); // A class representing a Wi-Fi P2p configuration for setting up a connection

            @Override
            public void onClick(View v) {
                int i=0;
                if(peers.size() == 0){
                    Log.d(MainActivity.TAG,"No devices found");
                    handler.obtainMessage(SET_TEXTVIEW,"No devices found").sendToTarget();
                    return;
                }else{
                    for(i=0;i<peers.size();i++){
                        // 392：Android_d660
                        // 483：Android_71a3
                        if(peers.get(i).deviceName.contains("GO")){
                            Log.d(MainActivity.TAG,"找到了该设备："+peers.get(i).deviceName);
                            handler.obtainMessage(SET_TEXTVIEW,"找到了该设备："+peers.get(i).deviceName).sendToTarget();
                            break;
                        }
                    }

                    if(i<peers.size()){
                        device=peers.get(i);
                        config.deviceAddress = device.deviceAddress;
//                        config.groupOwnerIntent = 5; // 代表该设备想成为 GO 的意愿强度
                        wifiP2pManager.connect(channel, config, new WifiP2pManager.ActionListener() {
                            @Override
                            public void onSuccess() {
                                Log.d(MainActivity.TAG,"成功连接到："+device.deviceName);
                                handler.obtainMessage(SET_TEXTVIEW,"成功连接到："+device.deviceName).sendToTarget();
                            }

                            @Override
                            public void onFailure(int reason) {
                                Log.d(MainActivity.TAG,"连接失败！");
                                handler.obtainMessage(SET_TEXTVIEW,"连接失败！").sendToTarget();
                            }
                        });
                    }else {
                        // Android_71a3
                        Log.d(MainActivity.TAG,"未发现该设备");
                        handler.obtainMessage(SET_TEXTVIEW,"未发现该设备").sendToTarget();
                        // Android_d660
                    }

                }
            }
        });

        // 成为GO
        becomeGOBT.setOnClickListener(new View.OnClickListener(){

            @Override
            public void onClick(View v) {
                wifiP2pManager.createGroup(channel, new WifiP2pManager.ActionListener() {
                    @Override
                    public void onSuccess() {
                        Log.d(MainActivity.TAG,"成功成为 GO");
                        handler.obtainMessage(SET_TEXTVIEW,"成功成为 GO").sendToTarget();
                        deviceNamePart[0]="GO";
                    }

                    @Override
                    public void onFailure(int reason) {
                        Log.d(MainActivity.TAG,"失败成为 GO");
                        handler.obtainMessage(SET_TEXTVIEW,"失败成为 GO").sendToTarget();
                    }
                });
            }
        });

        // 移除组
        removeGroupBT.setOnClickListener(new View.OnClickListener(){

            @Override
            public void onClick(View v) {
                wifiP2pManager.removeGroup(channel, new WifiP2pManager.ActionListener() {
                    @Override
                    public void onSuccess() {
                        Log.d(MainActivity.TAG,"成功移除当前组");
                        handler.obtainMessage(SET_TEXTVIEW,"成功移除当前组").sendToTarget();
                    }

                    @Override
                    public void onFailure(int reason) {
                        Log.d(MainActivity.TAG,"失败移除当前组");
                        handler.obtainMessage(SET_TEXTVIEW,"失败移除当前组").sendToTarget();
                        deviceNamePart[0]="GC";
                        deviceNamePart[2]="";
                    }
                });
            }
        });

        // 开启服务端线程
        serverBT.setOnClickListener(new View.OnClickListener(){

            @Override
            public void onClick(View v) {
                if(serverThread==null){
                    Log.d(MainActivity.TAG,"开启服务端线程");
                    handler.obtainMessage(SET_TEXTVIEW,"开启服务端线程").sendToTarget();
                    serverThread = new UDPServerThread(Android_ID,handler,ipOfWD,ipOfWL);
                    serverThread.start();
                }
            }
        });

        // 开启客户端线程
        clientBT.setOnClickListener(new View.OnClickListener(){

            @Override
            public void onClick(View v) {
                Log.d(MainActivity.TAG,"点击了CLIENT");
                handler.obtainMessage(SET_TEXTVIEW,"点击了CLIENT").sendToTarget();
                new UDPClientThread(Android_ID,handler).start();
            }
        });

        // 请求组信息
        requestGroupInfoBT.setOnClickListener(new View.OnClickListener(){

            @Override
            public void onClick(View v) {
                wifiP2pManager.requestGroupInfo(channel,MainActivity.this);
            }
        });

        // 获取 IP 地址
        getIpAddr.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ipOfWL = Utils.getIPAddress(true);
                if(ipOfWL!=null && !ipOfWL.equals("")){ // 更新模式
                    handler.obtainMessage(UPDATE_MODE).sendToTarget();
                }
                final String deviceName = getPersistedDeviceName();
                Log.d(MainActivity.TAG,"ip 地址为："+ipOfWL);
                handler.obtainMessage(SET_TEXTVIEW,"ip 地址为："+ipOfWL).sendToTarget();
                Log.d(MainActivity.TAG,"设备名为："+deviceName);
                handler.obtainMessage(SET_TEXTVIEW,"设备名为："+deviceName).sendToTarget();
                Log.d(MainActivity.TAG,"android ID: "+Android_ID);
                handler.obtainMessage(SET_TEXTVIEW,"android ID: "+Android_ID).sendToTarget();
                wifiP2pManager.requestGroupInfo(channel, new WifiP2pManager.GroupInfoListener() {
                    @Override
                    public void onGroupInfoAvailable(WifiP2pGroup group) {
                        if (group==null) return;
                        String groupName = group.getOwner().deviceName;
                        Log.d(MainActivity.TAG,"加入组："+groupName);
                        handler.obtainMessage(SET_TEXTVIEW,"加入组："+groupName).sendToTarget();
                        Log.d(MainActivity.TAG,"网络ID："+group.getNetworkId());
                        handler.obtainMessage(SET_TEXTVIEW,"网络ID："+group.getNetworkId()).sendToTarget();
                        Log.d(MainActivity.TAG,"网络名称："+group.getNetworkName());
                        handler.obtainMessage(SET_TEXTVIEW,"网络名称："+group.getNetworkName()).sendToTarget();
                        Log.d(MainActivity.TAG,"群组密码："+group.getPassphrase());
                        handler.obtainMessage(SET_TEXTVIEW,"群组密码："+group.getPassphrase()).sendToTarget();
                        deviceNamePart[2]=group.getPassphrase();
                    }
                });
            }
        });

        // 设置设备名称
        setDeviceName.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    Method m = wifiP2pManager.getClass().getMethod("setDeviceName",new Class[]{
                            channel.getClass(),String.class,WifiP2pManager.ActionListener.class });

                    String wifiP2pDeviceName = deviceNamePart[0];
                    for(int i=1;i<deviceNamePart.length;i++){
                        if(deviceNamePart[i]==null || deviceNamePart[i].equals("")) break;
                        wifiP2pDeviceName += "_"+deviceNamePart[i];
                    }

                    m.invoke(wifiP2pManager,channel,wifiP2pDeviceName,new WifiP2pManager.ActionListener(){
                        @Override
                        public void onSuccess() {
                            Log.d(MainActivity.TAG,"修改名称成功");
                            handler.obtainMessage(SET_TEXTVIEW,"修改名称成功").sendToTarget();
                        }

                        @Override
                        public void onFailure(int reason) {
                            Log.d(MainActivity.TAG,"修改名称失败");
                            handler.obtainMessage(SET_TEXTVIEW,"修改名称失败").sendToTarget();
                        }
                    });
                } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                    Log.d(MainActivity.TAG,"修改名称失败");
                    handler.obtainMessage(SET_TEXTVIEW,"修改名称失败").sendToTarget();
                    e.printStackTrace();
                }
            }
        });

        // wifi legacy 扫描结果
        scanResult.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean success = wifiManager.startScan();
                if (!success){
                    scanFailure();
                }
            }
        });

        // 连接到 wifi legacy
        connectToLegacy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                wifiAdmin.openWifi();
//                wifiAdmin.addNetwork(wifiAdmin.CreateWifiInfo("DIRECT-W0-Android_71a3", "BMs3EffS", 3));
                loop1:
                for(int i=0;i<devicesResult.size();i++){
                    for(String networkName : networkCredential.keySet()){
                        if(devicesResult.get(i).SSID.contains(networkName)){
                            Log.d(MainActivity.TAG,devicesResult.get(i).SSID+": "+ networkCredential.get(networkName));
                            wifiAdmin.addNetwork(wifiAdmin.CreateWifiInfo(devicesResult.get(i).SSID, networkCredential.get(networkName), 3));
                            break loop1;
                        }
                    }
                }
            }
        });

        // 获取电量
        getBatteryCapacity.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int battery = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
                handler.obtainMessage(SET_TEXTVIEW,"电量为："+battery+"%").sendToTarget();
            }
        });

        // 指定IP地址，定向发送消息
        directSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String ip = ipText.getText().toString();
                try {
                    RoutingItem item = new RoutingItem(Android_ID,ip,"",0,0,5,"hello");
                    sendMessage(JSON.toJSONString(item),ip);
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                }
            }
        });

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

    @Override
    protected void onResume() {
        super.onResume();
        wifiP2pReceiver = new WiFiDirectBroadcastReceiver(wifiP2pManager,channel,this,peers, networkCredential,handler);
        registerReceiver(wifiP2pReceiver,wifiP2pIntentFilter);
        registerReceiver(wifiScanReceiver,wifiIntentFilter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(wifiP2pReceiver);
        unregisterReceiver(wifiScanReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    // wifi legacy 扫描失败
    private void scanFailure(){
        Log.d(MainActivity.TAG,"扫描失败");
        handler.obtainMessage(SET_TEXTVIEW,"扫描失败").sendToTarget();
    }

    // wifi legacy 扫描成功
    private void scanSuccess(){
        devicesResult = wifiManager.getScanResults();
        for (int i=0;i<devicesResult.size();i++){
            Log.d(MainActivity.TAG,"找到"+devicesResult.get(i).SSID);
            handler.obtainMessage(SET_TEXTVIEW,"找到"+devicesResult.get(i).SSID).sendToTarget();
            break;
        }
    }

    // 获取持久化设备名
    public String getPersistedDeviceName() {
        Context mContext = getApplicationContext();
        String deviceName = Settings.Global.getString(mContext.getContentResolver(),
                Settings.Global.DEVICE_NAME);
        if (deviceName == null) {
            // We use the 4 digits of the ANDROID_ID to have a friendly
            // default that has low likelihood of collision with a peer
            String id = Settings.Secure.getString(mContext.getContentResolver(),
                    Settings.Secure.ANDROID_ID);
            return "Android_" + id.substring(0, 4);
        }
        return deviceName;
    }

    /**
     * @param isWifiP2pEnabled the isWifiP2pEnabled to set
     */
    public void setIsWifiP2pEnabled(boolean isWifiP2pEnabled) {
        this.isWifiP2pEnabled = isWifiP2pEnabled;
    }

    // wifi legacy scan receiver
    BroadcastReceiver wifiScanReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if(action.equals(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)){
                boolean success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED,false);
                if (success){
                    scanSuccess();
                }else {
                    scanFailure();
                }
            }else if(action.equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)){
                NetworkInfo info = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
                if(info.getState().equals(NetworkInfo.State.CONNECTED)){
                    ipOfWL = Utils.getIPAddress(true);
                    if(ipOfWL!=null && !ipOfWL.equals("")){ // 更新模式
                        handler.obtainMessage(UPDATE_MODE).sendToTarget();
                    }

                    Log.d(MainActivity.TAG,"wifi legacy 成功连接");
                    handler.obtainMessage(SET_TEXTVIEW,"wifi legacy 成功连接").sendToTarget();

                    int ipAddress = wifiAdmin.getIPAddress();
                    Log.d(MainActivity.TAG,"wifi legacy ip address: "+Utils.int2Ip(ipAddress));
                    handler.obtainMessage(SET_TEXTVIEW,"wifi legacy ip address: "+Utils.int2Ip(ipAddress)).sendToTarget();
                }else if(info.getState().equals(NetworkInfo.State.DISCONNECTED)){
                    ipOfWL = "";
                    handler.obtainMessage(UPDATE_MODE).sendToTarget(); // 更新模式

                    Log.d(MainActivity.TAG,"wifi legacy 断开连接");
                    handler.obtainMessage(SET_TEXTVIEW,"wifi legacy 断开连接").sendToTarget();
                }
            }

        }
    };

    /**
     * 获取 wifi p2p 的连接信息
     * @param info
     */
    @Override
    public void onConnectionInfoAvailable(WifiP2pInfo info) {
        // InetAddress from WifiP2pInfo struct.
        String groupOwnerAddress = info.groupOwnerAddress.getHostAddress();

        // After the group negotiation, we can determine the group owner
        // (server).
        if (info.groupFormed && info.isGroupOwner) {
            // Do whatever tasks are specific to the group owner.
            // One common case is creating a group owner thread and accepting
            // incoming connections.
        } else if (info.groupFormed) {
            // The other device acts as the peer (client). In this case,
            // you'll want to create a peer thread that connects
            // to the group owner.
        }

        ipOfWD = info.groupOwnerAddress.getHostAddress();
        if(ipOfWD!=null && !ipOfWD.equals("")){ // 更新模式
            handler.obtainMessage(UPDATE_MODE).sendToTarget();
        }
        Log.d(MainActivity.TAG,"IP地址："+ipOfWD);
        handler.obtainMessage(SET_TEXTVIEW,"IP地址："+info.groupOwnerAddress.getHostAddress()).sendToTarget();
    }

    @Override
    public void onGroupInfoAvailable(WifiP2pGroup group) {

    }

    public Handler handler = new Handler(){
        @Override
        public void handleMessage(@NonNull Message msg) {
            switch (msg.what){
                case SET_TEXTVIEW:{
                    String str = (String) log.getText();
                    String res = (String) msg.obj;
                    log.setText(str+"\n"+res);
                    scrollView.fullScroll(ScrollView.FOCUS_DOWN);
                    break;
                }
                case FOUND_PEER_DONE:{
                    wifiP2pManager.stopPeerDiscovery(channel,new WifiP2pManager.ActionListener(){

                        @Override
                        public void onSuccess() {
                            Log.d(MainActivity.TAG,"停止发现过程成功");
                        }

                        @Override
                        public void onFailure(int reason) {
                            Log.d(MainActivity.TAG,"停止发现过程失败："+reason);
                        }
                    });

                    scrollView.fullScroll(ScrollView.FOCUS_DOWN);
                    break;
                }
                case DISCOVER_SERVICE_DONE:{
                    wifiP2pManager.clearServiceRequests(channel,new WifiP2pManager.ActionListener(){

                        @Override
                        public void onSuccess() {
                            Log.d(TAG,"clearServiceRequests 成功");
                            handler.obtainMessage(SET_TEXTVIEW,"clearServiceRequests 成功").sendToTarget();
                        }

                        @Override
                        public void onFailure(int reason) {
                            Log.d(TAG,"clearServiceRequests 失败:"+reason);
                            handler.obtainMessage(SET_TEXTVIEW,"clearServiceRequests 失败:"+reason).sendToTarget();
                        }
                    });
                    break;
                }

                case UPDATE_ROUTING_TABLE:{
                    String neighborIP = (String) msg.obj;
                    int i=0;
                    for (RoutingTableItem item : routingTable){
                        if(item.getNeighbor().equals(neighborIP)){
                            break;
                        }
                        i++;
                    }
                    if(i>=routingTable.size()){
                        RoutingTableItem tableItem = new RoutingTableItem("",neighborIP,1);
                        routingTable.add(tableItem);
                        handler.obtainMessage(SET_TEXTVIEW,neighborIP).sendToTarget();
                    }
                    break;
                }

                case FORWARD:{
                    DatagramPacket packet = (DatagramPacket) msg.obj;
                    new Thread(new RoutingService(ipOfWD,ipOfWL,Android_ID,handler,routingTable,packet)).start();
                    break;
                }

                case UPDATE_MODE:{
                    String GO_ip = "192.168.49.1";
                    handler.obtainMessage(SET_TEXTVIEW,"ipOfWD:"+ipOfWD).sendToTarget();
                    handler.obtainMessage(SET_TEXTVIEW,"ipOfWL:"+ipOfWL).sendToTarget();
                    /*
                    * 设备共有四种角色：
                    * GO, relay(GO and Legacy client), legacy client, p2p client
                    * */
                    if(ipOfWD.equals(GO_ip) && ipOfWL.equals(GO_ip)){
                        mode.setText("GO");
                    }else if(!ipOfWD.equals("") && !ipOfWL.equals("")){
                        mode.setText("relay");
                    }else if(ipOfWD.equals("") && !ipOfWL.equals("")){
                        mode.setText("legacy client");
                    }else if(!ipOfWD.equals("") && ipOfWL.equals("")){
                        mode.setText("p2p client");
                    }
                    break;
                }

                default:
                    break;
            }
        }
    };
}