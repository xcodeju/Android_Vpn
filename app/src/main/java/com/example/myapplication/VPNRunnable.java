package com.example.myapplication;

import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

// VPNRunnable.java
public class VPNRunnable implements Runnable {
    private static final String TAG = "VPNRunnable";
    private ParcelFileDescriptor vpnInterface;
    private volatile boolean isRunning = true;

    public VPNRunnable(ParcelFileDescriptor vpnInterface) {
        this.vpnInterface = vpnInterface;
    }
    public void stop() {
        isRunning = false;
        // 强制中断I/O操作
        try {
            vpnInterface.close();
        } catch (IOException e) {
            Log.e(TAG, "强制关闭接口", e);
        }
    }

    @Override
    public void run() {
        try (FileInputStream in = new FileInputStream(vpnInterface.getFileDescriptor());
             FileOutputStream out = new FileOutputStream(vpnInterface.getFileDescriptor())) {
            ByteBuffer packet = ByteBuffer.allocate(32767);
            while (isRunning) {
                // 非阻塞读取（优化点）
                int bytesRead = in.read(packet.array());
                if (bytesRead > 0) {
                    processPacket(packet, bytesRead);
                    out.write(packet.array(), 0, bytesRead);
                }
                packet.clear();
            }
        } catch (IOException e) {
            if (isRunning) {
                Log.e(TAG, "I/O异常", e);
            }
        }
    }


    private void processPacket(ByteBuffer packet,int bytesRead) {
        try {
            // 加密数据
            byte[] encryptedData = AESUtil.encrypt(packet.array(), AESUtil.generateAESKey());
            packet.clear();
            packet.put(encryptedData);
        } catch (Exception e) {
            Log.e(TAG, "加密失败", e);
        }
    }
}