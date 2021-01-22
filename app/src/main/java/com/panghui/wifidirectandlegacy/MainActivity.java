package com.panghui.wifidirectandlegacy;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
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
import com.panghui.wifidirectandlegacy.clientAndServer.UDPClientThread;
import com.panghui.wifidirectandlegacy.clientAndServer.UDPServerThread;
import com.panghui.wifidirectandlegacy.networkManager.InformationCollection;
import com.panghui.wifidirectandlegacy.routing.RoutingItem;
import com.panghui.wifidirectandlegacy.routing.RoutingTableItem;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements ConnectionInfoListener, GroupInfoListener {

    public static final String TAG = "wifiDirectAndLegacy";
    public static final int SET_TEXTVIEW = 1000;
    public static final int FOUND_P2P_DEVICES_DONE = 1001;
    public static final int FOUND_LEGACY_DEVICES_DONE = 1002;
    public static final int DISCOVER_SERVICE_DONE = 1003;
    public static final int ROUND_TRIP_TIME_SEND = 1007;
    public static final int ROUND_TRIP_TIME_RECEIVE = 1008;
    public static final int SEND_A_MESSAGE_DONE = 1009;
    public static final int REMOVE_GROUP = 10010;
    public static final int CONNECT_TO_GO_DONE = 10011;
    public static final int DISCONNECT_FROM_GO_DONE = 10012;
    public static final int BECOME_GO = 10013;

    // wifi direct and wifi legacy manager
    private WifiP2pManager wifiP2pManager;
    private boolean isWifiP2pEnabled = false;
    private WifiManager wifiManager;
    private BatteryManager batteryManager;

    // wifiP2p and wifi legacy IntentFilter
    private final IntentFilter wifiP2pIntentFilter = new IntentFilter();
    private final IntentFilter wifiIntentFilter = new IntentFilter();
    private Channel channel;
    private BroadcastReceiver wifiP2pReceiver = null;

    // wifiP2p discoverPeers and wifi legacy scan result
    private List<WifiP2pDevice> peers = new ArrayList<>(); // p2p 接口扫描结果
    public List<ScanResult> devicesResult = new ArrayList<>(); // 传统 wifi 接口扫描结果
    private ArrayList<InformationCollection.JaccardIndexItem> jaccardIndexArray = new ArrayList<>(); // 从 p2p 接口扫描结果
    private int networkId; // 自动连接上的网络ID。断开时需要忘记密码，防止自动连接
    public WifiConfiguration wifiConfiguration;

    // local android device ID
    private String Android_ID;

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
    Button getBatteryCapacity;
    EditText ipText, messageET;
    Button directSend, sendMessageBT;

    public TextView log;
    public TextView mode; // relay mode or client mode
    public ScrollView scrollView;

    // network credential
    private HashMap<String, String> networkCredential = new HashMap<>(); // [SSID,passphrase]

    // wifi legacy administrator
    public WifiAdmin wifiAdmin;

    // wifi ip of wifi direct and ip of wifi legacy
    static String ip = "";

    // routing table
    static LinkedList<RoutingTableItem> routingTable = new LinkedList<>();

    // Server and Client Thread
    private UDPServerThread serverThread = null;

    // Calculate round trip time
    long roundTripTime = 0;

    // global sending and receiving port
    int globalReceivePort = 23000;
    int globalSendPort = 23000;

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
        getBatteryCapacity = findViewById(R.id.getBatteryCapacity);
        Button AutoConn = findViewById(R.id.AutoConn);


        ipText = findViewById(R.id.ipText);
        directSend = findViewById(R.id.directedSend);
        messageET = findViewById(R.id.messageET);
        sendMessageBT = findViewById(R.id.sendMessageBT);

        log = findViewById(R.id.log);
        scrollView = findViewById(R.id.scrollView);
        mode = findViewById(R.id.mode); // 用于设置模式（relay mode or client mode）

        Button disconnectWifi = findViewById(R.id.disconnectWifi);

        Android_ID = Settings.Secure.getString(getApplicationContext().getContentResolver(),
                Settings.Secure.ANDROID_ID).substring(0, 4);
        DeviceAttributes.deviceID = Android_ID;

        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
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

        wifiP2pManager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        // 1. 通过WLAN框架注册应用。必须调用此方法，然后再调用任何其他WLAN P2P 方法
        channel = wifiP2pManager.initialize(this, getMainLooper(), null);

        wifiP2pReceiver = new WiFiDirectBroadcastReceiver(wifiP2pManager, channel, this, peers, handler);

        // 2. 发现对等设备
        discoverPeersBT.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                wifiP2pManager.discoverPeers(channel, new WifiP2pManager.ActionListener() {
                    @Override
                    public void onSuccess() {
                        Toast.makeText(MainActivity.this, "Discovery Initiated",
                                Toast.LENGTH_SHORT).show();
                        handler.obtainMessage(SET_TEXTVIEW, "Discovery Initiated").sendToTarget();
                    }

                    @Override
                    public void onFailure(int reasonCode) {
                        Toast.makeText(MainActivity.this, "Discovery Failed : " + reasonCode,
                                Toast.LENGTH_SHORT).show();
                        handler.obtainMessage(SET_TEXTVIEW, "Discovery Failed").sendToTarget();
                    }
                });
            }
        });

        // 自动扫描发送消息
        sendMessageBT.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handler.obtainMessage(SET_TEXTVIEW,"DeviceAttributes.isGO"+DeviceAttributes.isGO).sendToTarget();

                // 先进行组移除再启动扫描P2P设备过程
                handler.obtainMessage(REMOVE_GROUP).sendToTarget();
                // 启动扫描P2P设备过程
                new InformationCollection(wifiP2pManager,channel,handler).start();
            }
        });


        // 成为GO
        becomeGOBT.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new BecomeGroupOwner().start();
            }
        });

        // 移除组
        removeGroupBT.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new RemoveGroupOwner().start();
            }
        });

        // 开启服务端线程
        serverBT.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
//                if(serverThread==null){
//                    Log.d(MainActivity.TAG,"开启服务端线程");
//                    handler.obtainMessage(SET_TEXTVIEW,"开启服务端线程").sendToTarget();
//                    serverThread = new UDPServerThread(Android_ID,handler,ipOfWD,ipOfWL);
//                    serverThread.start();
//                }
                new UDPServerThread(Android_ID, handler, globalReceivePort).start();
            }
        });

        // 开启客户端线程
        clientBT.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Log.d(MainActivity.TAG, "点击了CLIENT");
                handler.obtainMessage(SET_TEXTVIEW, "点击了CLIENT").sendToTarget();
                new UDPClientThread(Android_ID, handler, globalSendPort).start();
                handler.obtainMessage(ROUND_TRIP_TIME_SEND).sendToTarget();
            }
        });

        // 请求组信息
        requestGroupInfoBT.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                wifiP2pManager.requestGroupInfo(channel, MainActivity.this);
            }
        });

        // 获取 IP 地址
        getIpAddr.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ip = Utils.getIPAddress(true);

                final String deviceName = getPersistedDeviceName();
                Log.d(MainActivity.TAG, "ip 地址为：" + ip);
                handler.obtainMessage(SET_TEXTVIEW, "ip 地址为：" + ip).sendToTarget();
                Log.d(MainActivity.TAG, "设备名为：" + deviceName);
                handler.obtainMessage(SET_TEXTVIEW, "设备名为：" + deviceName).sendToTarget();
                Log.d(MainActivity.TAG, "android ID: " + Android_ID);
                handler.obtainMessage(SET_TEXTVIEW, "android ID: " + Android_ID).sendToTarget();
                wifiP2pManager.requestGroupInfo(channel, new WifiP2pManager.GroupInfoListener() {
                    @Override
                    public void onGroupInfoAvailable(WifiP2pGroup group) {
                        if (group == null) return;
                        String groupName = group.getOwner().deviceName;
                        Log.d(MainActivity.TAG, "加入组：" + groupName);
                        handler.obtainMessage(SET_TEXTVIEW, "加入组：" + groupName).sendToTarget();
                        Log.d(MainActivity.TAG, "网络ID：" + group.getNetworkId());
                        handler.obtainMessage(SET_TEXTVIEW, "网络ID：" + group.getNetworkId()).sendToTarget();
                        Log.d(MainActivity.TAG, "网络名称：" + group.getNetworkName());
                        handler.obtainMessage(SET_TEXTVIEW, "网络名称：" + group.getNetworkName()).sendToTarget();
                        Log.d(MainActivity.TAG, "群组密码：" + group.getPassphrase());
                        handler.obtainMessage(SET_TEXTVIEW, "群组密码：" + group.getPassphrase()).sendToTarget();
                        DeviceAttributes.networkCredential = group.getPassphrase();
                    }
                });
            }
        });

        // 设置设备名称
        setDeviceName.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    Method m = wifiP2pManager.getClass().getMethod("setDeviceName", new Class[]{
                            channel.getClass(), String.class, WifiP2pManager.ActionListener.class});

                    String wifiP2pDeviceName = DeviceAttributes.deviceFlag;
                    wifiP2pDeviceName += "_" + DeviceAttributes.deviceID;
                    wifiP2pDeviceName += "_" + DeviceAttributes.networkCredential;
                    int battery = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY); // 获取电量
                    int jaccardIndex = battery;
                    DeviceAttributes.jaccardIndex = jaccardIndex;
                    wifiP2pDeviceName += "_" + DeviceAttributes.jaccardIndex;

                    m.invoke(wifiP2pManager, channel, wifiP2pDeviceName, new WifiP2pManager.ActionListener() {
                        @Override
                        public void onSuccess() {
                            Log.d(MainActivity.TAG, "修改名称成功");
                            handler.obtainMessage(SET_TEXTVIEW, "修改名称成功").sendToTarget();
                        }

                        @Override
                        public void onFailure(int reason) {
                            Log.d(MainActivity.TAG, "修改名称失败");
                            handler.obtainMessage(SET_TEXTVIEW, "修改名称失败").sendToTarget();
                        }
                    });
                } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                    Log.d(MainActivity.TAG, "修改名称失败");
                    handler.obtainMessage(SET_TEXTVIEW, "修改名称失败").sendToTarget();
                    e.printStackTrace();
                }
            }
        });

        // wifi legacy 扫描结果
        scanResult.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean success = wifiManager.startScan();
                if (!success) {
                    scanFailure();
                }
            }
        });


        // 获取电量
        getBatteryCapacity.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int battery = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
                handler.obtainMessage(SET_TEXTVIEW, "电量为：" + battery + "%").sendToTarget();
            }
        });

        // 启动自动连接
        AutoConn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new InformationCollection(wifiP2pManager, channel, handler).start();
            }
        });

        // 指定IP地址，定向发送消息
        directSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String ip = ipText.getText().toString();
                try {
                    RoutingItem item = new RoutingItem(Android_ID, ip, "", 0, 0, 5, "hello");
                    sendUDPMessage(JSON.toJSONString(item), ip, globalSendPort);
                    handler.obtainMessage(ROUND_TRIP_TIME_SEND).sendToTarget();
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                }
            }
        });


        // 断开 Wi-Fi 连接
        disconnectWifi.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                disconnectFromGO();
            }
        });

        // 用户需要手动授予权限,这个权限是 wifi legacy 扫描热点时需要的权限
        String[] PERMS_INITIAL = {Manifest.permission.ACCESS_FINE_LOCATION};
        ActivityCompat.requestPermissions(this, PERMS_INITIAL, 127);

        // 应用启动即开启监听线程
        new UDPServerThread(Android_ID, handler, globalReceivePort).start();

        // 应用启动即进入GO状态
        new BecomeGroupOwner().start();
    }

    /**
     * 发送消息
     * @param str 消息内容
     * @param ip 指定 IP 地址
     * @throws UnknownHostException
     */
    private void sendUDPMessage(String str, String ip, int sendPort) throws UnknownHostException {
        String TAG = "sendMessage";
        Log.d(TAG, "发送数据");
        handler.obtainMessage(MainActivity.SET_TEXTVIEW, "发送数据").sendToTarget();
        InetAddress ipAddress = InetAddress.getByName(ip);
        DatagramSocket ds = null;

        try {
            ds = new DatagramSocket();
            ds.setBroadcast(true);
            DatagramPacket dp = new DatagramPacket(str.getBytes(), str.getBytes().length, ipAddress, sendPort);
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
        wifiP2pReceiver = new WiFiDirectBroadcastReceiver(wifiP2pManager, channel, this, peers, handler);
        registerReceiver(wifiP2pReceiver, wifiP2pIntentFilter);
        registerReceiver(wifiReceiver, wifiIntentFilter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(wifiP2pReceiver);
        unregisterReceiver(wifiReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    // wifi legacy 扫描失败
    private void scanFailure() {
        Log.d(MainActivity.TAG, "扫描失败");
        handler.obtainMessage(SET_TEXTVIEW, "扫描失败").sendToTarget();
    }

    // wifi legacy 扫描成功
    private void scanSuccess() {
        devicesResult = wifiManager.getScanResults();
        if (devicesResult.size() > 0) {
            handler.obtainMessage(FOUND_LEGACY_DEVICES_DONE).sendToTarget();
        }
        for (int i = 0; i < devicesResult.size(); i++) {
            if(devicesResult.get(i).SSID.contains("GO")){
                Log.d(MainActivity.TAG, "找到" + devicesResult.get(i).SSID);
                handler.obtainMessage(SET_TEXTVIEW, "找到" + devicesResult.get(i).SSID).sendToTarget();
            }
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
    BroadcastReceiver wifiReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            handler.obtainMessage(MainActivity.SET_TEXTVIEW,"DeviceAttributes.isConnectedToGO: "+DeviceAttributes.isConnectedToGO).sendToTarget();
            handler.obtainMessage(MainActivity.SET_TEXTVIEW,"DeviceAttributes.isGO: "+DeviceAttributes.isGO).sendToTarget();

            if (DeviceAttributes.isConnectedToGO || DeviceAttributes.isGO) {
                return;
            }

            String action = intent.getAction();
            if (action.equals(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)) {
                boolean success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false);
                if (success) {
                    scanSuccess();
                } else {
                    scanFailure();
                }
            } else if (action.equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {
                NetworkInfo info = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
                if (info.getState().equals(NetworkInfo.State.CONNECTED)) { // 以 wifi Legacy 连接到GO时，会收到该广播
                    DeviceAttributes.isConnectedToGO = true;

                    ip = Utils.getIPAddress(true);

                    Log.d(MainActivity.TAG, "wifi legacy 成功连接");
                    handler.obtainMessage(SET_TEXTVIEW, "wifi legacy 成功连接").sendToTarget();
                    handler.obtainMessage(CONNECT_TO_GO_DONE).sendToTarget();

                    Log.d(MainActivity.TAG, "wifi legacy ip address: " + Utils.getIPAddress(true));
                    handler.obtainMessage(SET_TEXTVIEW, "wifi legacy ip address: " + Utils.getIPAddress(true)).sendToTarget();
                } else if (info.getState().equals(NetworkInfo.State.DISCONNECTED)) {
                    DeviceAttributes.isConnectedToGO = false;

                    Log.d(MainActivity.TAG, "wifi legacy 断开连接");
                    handler.obtainMessage(SET_TEXTVIEW, "wifi legacy 断开连接").sendToTarget();

//                    handler.obtainMessage(BECOME_GO).sendToTarget();
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
            DeviceAttributes.isGO = true;
            DeviceAttributes.deviceFlag = "GO"; // 设置 deviceFlag 为 GO

            // 获取组信息并修改设备名
            new GetGroupInformationAndModifyDeviceName().start();
        } else if (info.groupFormed) {
            // The other device acts as the peer (client). In this case,
            // you'll want to create a peer thread that connects
            // to the group owner.
            DeviceAttributes.isGO = false;
        }
    }

    @Override
    public void onGroupInfoAvailable(WifiP2pGroup group) {
        if (group == null) return;
        String groupName = group.getOwner().deviceName;
        Log.d(MainActivity.TAG, "加入组：" + groupName);
        handler.obtainMessage(SET_TEXTVIEW, "加入组：" + groupName).sendToTarget();
        Log.d(MainActivity.TAG, "网络ID：" + group.getNetworkId());
        handler.obtainMessage(SET_TEXTVIEW, "网络ID：" + group.getNetworkId()).sendToTarget();
        Log.d(MainActivity.TAG, "网络名称：" + group.getNetworkName());
        handler.obtainMessage(SET_TEXTVIEW, "网络名称：" + group.getNetworkName()).sendToTarget();
        Log.d(MainActivity.TAG, "群组密码：" + group.getPassphrase());
        handler.obtainMessage(SET_TEXTVIEW, "群组密码：" + group.getPassphrase()).sendToTarget();
        DeviceAttributes.networkCredential = group.getPassphrase();
    }

    /**
     * 从GO处断开
     */
    public void disconnectFromGO() {
        DeviceAttributes.isConnectedToGO = false; // 将标记位设置为 false，方便 wifiReceiver 接收广播信号
        wifiManager.disconnect();
        // 忘记网络的密码
        List<WifiConfiguration> list = wifiManager.getConfiguredNetworks();
        for( WifiConfiguration i : list ) {
            wifiManager.removeNetwork(i.networkId);
            //wifiManager.saveConfiguration();
        }
    }


    class BecomeGroupOwner extends Thread {
        @Override
        public void run() {

            wifiP2pManager.createGroup(channel, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    DeviceAttributes.isGO = true;
                    Log.d(TAG, "成功成为GO");
                    handler.obtainMessage(SET_TEXTVIEW, "成功成为GO").sendToTarget();
                }

                @Override
                public void onFailure(int reason) {
                    Log.d(TAG, "失败成为GO");
                    handler.obtainMessage(SET_TEXTVIEW, "失败成为GO").sendToTarget();
                }
            });
        }
    }

    class GetGroupInformationAndModifyDeviceName extends Thread{
        @Override
        public void run() {
            wifiP2pManager.requestGroupInfo(channel, new WifiP2pManager.GroupInfoListener() { //获取组网密码
                @Override
                public void onGroupInfoAvailable(WifiP2pGroup group) {
                    if (group == null) return;
                    String groupName = group.getOwner().deviceName;
                    Log.d(MainActivity.TAG, "加入组：" + groupName);
                    handler.obtainMessage(SET_TEXTVIEW, "加入组：" + groupName).sendToTarget();
                    Log.d(MainActivity.TAG, "网络ID：" + group.getNetworkId());
                    handler.obtainMessage(SET_TEXTVIEW, "网络ID：" + group.getNetworkId()).sendToTarget();
                    Log.d(MainActivity.TAG, "网络名称：" + group.getNetworkName());
                    handler.obtainMessage(SET_TEXTVIEW, "网络名称：" + group.getNetworkName()).sendToTarget();
                    Log.d(MainActivity.TAG, "群组密码：" + group.getPassphrase());
                    handler.obtainMessage(SET_TEXTVIEW, "群组密码：" + group.getPassphrase()).sendToTarget();
                    DeviceAttributes.networkCredential = group.getPassphrase();

                    // 修改设备名
                    try {
                        Method m = wifiP2pManager.getClass().getMethod("setDeviceName", new Class[]{
                                channel.getClass(), String.class, WifiP2pManager.ActionListener.class});

                        String wifiP2pDeviceName = DeviceAttributes.deviceFlag;
                        wifiP2pDeviceName += "_" + DeviceAttributes.deviceID;
                        wifiP2pDeviceName += "_" + DeviceAttributes.networkCredential;
                        int battery = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
                        int jaccardIndex = battery;
                        DeviceAttributes.jaccardIndex = jaccardIndex;
                        wifiP2pDeviceName += "_" + DeviceAttributes.jaccardIndex;

                        m.invoke(wifiP2pManager, channel, wifiP2pDeviceName, new WifiP2pManager.ActionListener() {
                            @Override
                            public void onSuccess() {
                                Log.d(MainActivity.TAG, "修改名称成功");
                                handler.obtainMessage(SET_TEXTVIEW, "修改名称成功").sendToTarget();
                            }

                            @Override
                            public void onFailure(int reason) {
                                Log.d(MainActivity.TAG, "修改名称失败");
                                handler.obtainMessage(SET_TEXTVIEW, "修改名称失败").sendToTarget();
                            }
                        });
                    } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                        Log.d(MainActivity.TAG, "修改名称失败");
                        handler.obtainMessage(SET_TEXTVIEW, "修改名称失败").sendToTarget();
                        e.printStackTrace();
                    }
                }
            });
        }
    }

    class RemoveGroupOwner extends Thread{
        @Override
        public void run() {
            wifiP2pManager.removeGroup(channel, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    DeviceAttributes.isGO = false;
                    Log.d(TAG,"成功移除组");
                    handler.obtainMessage(SET_TEXTVIEW,"成功移除组").sendToTarget();
                    // 组移除成功后，启动P2P设备扫描过程
                    new InformationCollection(wifiP2pManager,channel,handler).start();
                }

                @Override
                public void onFailure(int reason) {
                    Log.d(TAG,"失败移除组");
                    handler.obtainMessage(SET_TEXTVIEW,"失败移除组"+reason).sendToTarget();
                }
            });
        }
    }

    class StopPeerDiscovery extends Thread{
        @Override
        public void run() {
            // 找到peers 设备后，停止发现过程
            DeviceAttributes.foundP2pDevicesDone = true;
            wifiP2pManager.stopPeerDiscovery(channel,new WifiP2pManager.ActionListener(){
                @Override
                public void onSuccess() {
                    Log.d(MainActivity.TAG,"停止发现过程成功");
                    handler.obtainMessage(SET_TEXTVIEW,"停止发现过程成功").sendToTarget();
                }

                @Override
                public void onFailure(int reason) {
                    Log.d(MainActivity.TAG,"停止发现过程失败："+reason);
                    handler.obtainMessage(SET_TEXTVIEW,"停止发现过程失败").sendToTarget();
                }
            });
        }
    }

    class ClearServiceRequests extends Thread{
        @Override
        public void run() {
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
        }
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
                case FOUND_P2P_DEVICES_DONE:{
                    handler.obtainMessage(SET_TEXTVIEW,"FOUND_P2P_DEVICES_DONE").sendToTarget();

                    new StopPeerDiscovery().start();
                    // 获取到并进行解析了 peers 设备列表
                    if(peers!=null){
                        jaccardIndexArray = InformationCollection.parseDeivceName(peers);
                        for (int i=0;i<jaccardIndexArray.size();i++){
                            handler.obtainMessage(SET_TEXTVIEW,jaccardIndexArray.get(i).getDeviceID()).sendToTarget();
                        }
                    }

                    // 进行 wifi legacy 扫描
                    wifiManager.startScan();
                    break;
                }

                case FOUND_LEGACY_DEVICES_DONE:{
                    handler.obtainMessage(SET_TEXTVIEW,"FOUND_LEGACY_DEVICES_DONE").sendToTarget();
                    // 进行自动连接
                    if(jaccardIndexArray.size()>0){
                        String networkSSID = jaccardIndexArray.get(0).getDeviceID();
                        String credential = jaccardIndexArray.get(0).getCredential();
                        handler.obtainMessage(SET_TEXTVIEW,networkSSID+":"+credential).sendToTarget();
                        for(int i=0;i<devicesResult.size();i++){
                            if(devicesResult.get(i).SSID.contains(networkSSID)){
                                wifiConfiguration = wifiAdmin.CreateWifiInfo(devicesResult.get(i).SSID,credential,3);
                                networkId = wifiConfiguration.networkId;
                                wifiAdmin.addNetwork(wifiConfiguration);
                                break;
                            }
                        }
                    }

                    // 发现LEGACY 设备完成后，暂时不需要接收器工作
//                    DeviceAttributes.isConnectedToGO = true;
                    break;
                }

                case REMOVE_GROUP:{
                    new RemoveGroupOwner().start();
                    break;
                }

                case DISCOVER_SERVICE_DONE:{
                    new ClearServiceRequests().start();
                    break;
                }

                case SEND_A_MESSAGE_DONE:{
                    new UDPClientThread(Android_ID,handler,globalSendPort).start();
                    break;
                }

                case CONNECT_TO_GO_DONE:{
                    new UDPClientThread(Android_ID,handler,globalSendPort).start();
                    break;
                }

                case DISCONNECT_FROM_GO_DONE:{
                    disconnectFromGO();
                    break;
                }

                case BECOME_GO:{
                    new BecomeGroupOwner().start();
                    break;
                }

                default:
                    break;
            }
        }
    };
}