package com.panghui.wifidirectandlegacy.clientAndServer;

import android.os.Handler;

import com.panghui.wifidirectandlegacy.MainActivity;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.Socket;

public class FileClient extends Thread {
    Handler handler;
    String path;
    final int NUM = 10;
    int count;
    long start,stop; // 用于统计图片发送所用时间

    public FileClient(Handler handler,String path){
        this.handler = handler;
        this.path = path;
        this.count = 0;
    }

    @Override
    public void run() {
        start=System.currentTimeMillis();
        for(int i=0;i<NUM;i++){
            sendImage(path);
        }

        if(count==NUM){
            stop = System.currentTimeMillis();
            handler.obtainMessage(MainActivity.RECEIVE_IMAGE_DONE).sendToTarget();
            handler.obtainMessage(MainActivity.SET_TEXTVIEW,"用时："+(stop-start)+"ms").sendToTarget();
        }
    }

    private void sendImage(String path)  {
        Socket s= null;
        try {
            FileInputStream fis = new FileInputStream(path); // 获取文件输入流
            s = new Socket("192.168.49.1", 9998); // 连接到服务器
            DataOutputStream dos = new DataOutputStream(s.getOutputStream()); // 获取 socket 的输入流
            DataInputStream dis = new DataInputStream(s.getInputStream());
            int size = fis.available(); // 文件输入流的可用字节数
            byte[] data = new byte[size]; // 数据发送缓冲区
            fis.read(data); // 将数据复制到缓冲区
            dos.writeInt(size);
            dos.write(data);
            dos.flush();
            int result = dis.readInt();
            if(result == MainActivity.RECEIVE_IMAGE_DONE){
                count++;
                handler.obtainMessage(MainActivity.SET_TEXTVIEW,"对方接收完成").sendToTarget();
            }
        }catch (IOException e){
            e.printStackTrace();
        }finally {
            try {
                s.close();
            }catch (IOException e){
                e.printStackTrace();
            }
        }
    }
}
