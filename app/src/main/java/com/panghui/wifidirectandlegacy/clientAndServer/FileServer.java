package com.panghui.wifidirectandlegacy.clientAndServer;

import android.os.Handler;

import com.panghui.wifidirectandlegacy.MainActivity;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class FileServer extends Thread{
    Handler handler;
    public FileServer(Handler handler){
        this.handler = handler;
    }

    @Override
    public void run() {
        ServerSocket ss = null; // 服务端
        try {
            ss = new ServerSocket(9998);
            while (true){
                Socket s = ss.accept(); // 客户端
                DataInputStream dataInput  = new DataInputStream(s.getInputStream());
                DataOutputStream dataOutput = new DataOutputStream(s.getOutputStream());
                int size = dataInput.readInt();
                byte[] data = new byte[size];
                int len = 0;
                while (len < size){
                    len += dataInput.read(data,len,size - len);
                }

                dataOutput.writeInt(MainActivity.RECEIVE_IMAGE_DONE);
                handler.obtainMessage(MainActivity.RECEIVE_IMAGE_DONE).sendToTarget();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
