package com.example.myapplication;

import android.content.Intent;
import android.net.VpnService;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// MyVpnService.java
public class MyVpnService extends VpnService {
    private static final String TAG = "MyVpnService";


    private static final String PROXY_HOST = "127.0.0.1";
    private static final int PROXY_PORT = 8080;
    private ParcelFileDescriptor vpnInterface;
    private VPNRunnable vpnRunnable;
    private Thread vpnThread;


    private class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 1: // 处理来自客户端的消息
                    stopVPN();
                    Log.d(TAG, "收到客户端消息: " + msg.getData().getString("msg"));
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    }

    private Messenger serviceMessenger = new Messenger(new IncomingHandler());

    @Override
    public IBinder onBind(Intent intent) {
        return serviceMessenger.getBinder();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        // 启动 VPN 连接
        startVPN();
        return START_STICKY;
    }

    private void startVPN() {
        // 配置 VPN 接口
        Builder builder = new Builder();
        builder.setSession("MyVPN")
                .addAddress("10.0.0.2", 24) // 虚拟 IP 地址
                .addDnsServer("8.8.8.8")    // DNS 服务器
                .addRoute("0.0.0.0", 0)     // 路由所有流量
                .setMtu(1500);               // 最大传输单元

        // 建立 VPN 接口
        vpnInterface = builder.establish();

        // 启动 VPN 线程处理数据包
        vpnRunnable = new VPNRunnable(vpnInterface);
        vpnThread = new Thread(vpnRunnable);
        vpnThread.start();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopVPN();
    }

    private ExecutorService executor = Executors.newSingleThreadExecutor(); // 线程池

    private void stopVPN() {
        executor.execute(() -> {
            // 在后台线程执行耗时操作
            if (vpnRunnable != null) {
                vpnRunnable.stop();
            }
            if (vpnThread != null) {
                vpnThread.interrupt();
                try {
                    vpnThread.join(500); // 最多等待500ms
                } catch (InterruptedException e) {
                    Log.e(TAG, "线程终止超时", e);
                }
            }
            if (vpnInterface != null) {
                try {
                    vpnInterface.close(); // 关闭VPN接口（耗时操作）
                } catch (IOException e) {
                    Log.e(TAG, "关闭接口失败", e);
                }
            }
            // 通知主线程更新UI
            sendStatusToUI(false);
        });
    }

    private void sendStatusToUI(boolean isConnected) {
        Intent intent = new Intent("VPN_STATUS_UPDATE");
        intent.putExtra("isConnected", isConnected);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }
}
