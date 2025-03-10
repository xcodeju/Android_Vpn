package com.example.myapplication;

import static com.example.myapplication.FileUtils.CONFIG_FILE;

import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;

import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.PersistableBundle;
import android.os.RemoteException;
import android.provider.Settings;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private Button connectButton;

    private boolean isConnected = false;
    // 在 MainActivity.java 中添加
    private ConnectivityManager.NetworkCallback networkCallback;

    private boolean isVerifying = false;
    private boolean isVerifyCode = false;

    private VPNManager vpnManager;
    private NetworkMonitor networkMonitor;


    // 添加成员变量
    private TextView timerTextView;
    private TextView timerTitle;

    private Messenger serviceMessenger;
    private boolean isBound;

    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            serviceMessenger = new Messenger(service);
            isBound = true;


        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceMessenger = null;
            isBound = false;
        }
    };

    private BroadcastReceiver vpnStatusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean isConnected = intent.getBooleanExtra("isConnected", false);
            updateUI(false);
            vpnManager.disconnectVPN();
        }
    };

    private void stopVpn(){
        // 发送消息到服务
        Message msg = Message.obtain(null, 1);
        Bundle bundle = new Bundle();
        bundle.putString("msg", "Hello from client");
        msg.setData(bundle);
        try {
            serviceMessenger.send(msg);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }
    


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 注册广播接收器
        LocalBroadcastManager.getInstance(this)
                .registerReceiver(vpnStatusReceiver, new IntentFilter("VPN_STATUS_UPDATE"));
        setContentView(R.layout.activity_main);

        // 绑定到 VPN 服务
        Intent intent = new Intent(this, MyVpnService.class);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        vpnManager = new VPNManager(this);
        networkMonitor = new NetworkMonitor(this);

        connectButton = findViewById(R.id.connectButton);
        // 设置计时监听
        vpnManager.setTimerListener(millis -> {
            int seconds = (int) (millis / 1000);
            int minutes = seconds / 60;
            int hours = minutes / 60;
            String timeText = String.format(Locale.getDefault(),
                    "%02d:%02d:%02d", hours % 24, minutes % 60, seconds % 60);
            timerTextView.setText(timeText);
        });
        // 设置网络监听
        networkMonitor.startMonitoring(new NetworkMonitor.NetworkListener() {
            @Override
            public void onNetworkAvailable() {
                if (isConnected) {
                    startVPNConnection();
                }
            }

            @Override
            public void onNetworkLost() {
               disconnectVPN();
               connectButton.setText("连接已断开");
            }
        });




        connectButton.setOnClickListener(v -> toggleVPN());

        timerTextView = findViewById(R.id.timerTextView);
        timerTitle = findViewById(R.id.timerTitle);


    }

//    @Override
//    public void onSaveInstanceState(@NonNull Bundle outState, @NonNull PersistableBundle outPersistentState) {
//        super.onSaveInstanceState(outState, outPersistentState);
//        outState.putBoolean(TIMER_STATE, isConnected);
//        outState.putLong(START_TIME_KEY, startTime);
//    }
//
//    @Override
//    protected void onRestoreInstanceState(Bundle savedInstanceState) {
//        super.onRestoreInstanceState(savedInstanceState);
//        if (savedInstanceState.getBoolean(TIMER_STATE, false)) {
//            startTime = savedInstanceState.getLong(START_TIME_KEY);
//            startVPNConnection();
//        }
//    }





    // 在 MainActivity.java 中修改 toggleVPN 方法

    // 修改后的toggleVPN方法
    private void toggleVPN() {
        if (isVerifying) return;

        if (!NetworkUtils.isNetworkAvailable(this)) {
            showNetworkError("网络不可用");
            return;
        }

        new Thread(() -> {
            boolean internetAvailable = NetworkUtils.isInternetAvailable();
            runOnUiThread(() -> {
                if (!isConnected) {
                    // 网络可用时显示授权弹窗
                    if (internetAvailable) {
                        if (!isVerifyCode) {
                            showAuthDialog();
                        } else {
                            startVPNConnection();
                        }
                    } else {
                        showNetworkError("无法访问互联网");
                    }
                } else {
                    disconnectVPN();
                }
            });
        }).start();
    }

    // 添加成员变量
    private AlertDialog dialog;

    // 授权弹窗实现
    private void showAuthDialog() {

        if (dialog != null && dialog.isShowing()) {
            return; // 防止重复显示
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.CenterDialogTheme);

        // 加载自定义布局
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_auth, null);
        EditText authInput = dialogView.findViewById(R.id.authEditText);

        // 配置对话框
        builder.setView(dialogView)
                .setCancelable(false)
                .setPositiveButton("确认", (dialog, which) -> {
                    String code = authInput.getText().toString().trim();
                    isVerifyCode = validateCode(code);
                    if (validateCode(code)) {
                        startVPNConnection();
                    } else {
                        showAuthError("授权码不正确");
                    }
                })
                .setNegativeButton("取消", (dialog, which) -> dialog.dismiss());

        dialog = builder.create();

        // 显示后调整按钮样式
        dialog.setOnShowListener(dialogInterface -> {
            Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            Button negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);

            // 设置按钮文字颜色
            positiveButton.setTextColor(ContextCompat.getColor(this, R.color.colorPrimary));
            negativeButton.setTextColor(ContextCompat.getColor(this, R.color.gray));

            // 设置按钮背景
            positiveButton.setBackgroundResource(R.drawable.dialog_button_bg);
            negativeButton.setBackgroundResource(R.drawable.dialog_button_bg);
        });
        dialog.setOnDismissListener(dialog -> this.dialog = null);


        dialog.show();

        // 调整对话框窗口属性
        Window window = dialog.getWindow();
        if (window != null) {
            window.setGravity(Gravity.CENTER);
            window.setDimAmount(0.3f);

            // 设置入场动画
            window.setWindowAnimations(R.style.DialogAnimation);
        }
    }

    // 修改验证方法
    private boolean validateCode(String inputCode) {
        // 首次使用：保存输入的授权码
        if (!FileUtils.configExists(this)) {
            FileUtils.saveAuthCode(this, inputCode);
            Toast.makeText(this, "初始授权码已保存", Toast.LENGTH_SHORT).show();
            return true;
        }

        // 后续验证：读取配置文件
        try {
            String savedCode = FileUtils.readAuthCode(this);
            return inputCode.equals(savedCode);
        } catch (Exception e) {
            showAuthError("授权码验证失败");
            return false;
        }
    }

    public static void resetAuthCode(Context context) {
        File file = new File(context.getFilesDir(), CONFIG_FILE);
        if (file.exists()) {
            file.delete();
        }
    }


    // 授权错误提示
    private void showAuthError(String message) {
        new AlertDialog.Builder(this)
                .setTitle("验证失败")
                .setMessage(message)
                .setPositiveButton("重试", (d, w) -> showAuthDialog())
                .setNegativeButton("取消", null)
                .show();
    }

    // 在startVPNConnection中添加验证状态
    private void startVPNConnection() {
        vpnManager.startVPN();
        if (!isConnected) {
            isVerifying = true;
            showProgressDialog();
            connectVPN();
            timerTextView.setVisibility(View.VISIBLE);
            timerTitle.setVisibility(View.VISIBLE);
        }
    }


    // 添加加载弹窗
    private void showProgressDialog() {
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("正在验证授权...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        // 模拟服务器验证延迟
        new Handler().postDelayed(() -> {
            progressDialog.dismiss();
            isVerifying = false;
        }, 2000);
    }

    // 修改updateUI方法
    private void updateUI(boolean connected) {
        isConnected = connected;
        //statusTextView.setText(connected ? "状态：已连接" : "状态：未连接");
        if (isConnected) {
            connectButton.setText("断开连接");
            connectButton.setBackgroundResource(R.drawable.button_connected);
        } else {
            connectButton.setText("开始连接");
            connectButton.setBackgroundResource(R.drawable.button_disconnected);
        }
        if (!connected) isVerifying = false;
    }

    private void showNetworkError(String message) {
        Snackbar.make(connectButton, message, Snackbar.LENGTH_LONG)
                .setAction("设置", v -> startActivity(
                        new Intent(Settings.ACTION_WIFI_SETTINGS)))
                .show();
    }

    private void connectVPN() {

        Intent intent = MyVpnService.prepare(this);
        if (intent != null) {
            startActivityForResult(intent, 0);
        } else {
            onActivityResult(0, RESULT_OK, null);
        }
    }

    private void disconnectVPN() {
        updateUI(false);
        vpnManager.disconnectVPN();
        stopVpn();
//        Intent intent = new Intent(this, MyVpnService.class);
//        stopService(intent);
        if (dialog != null && dialog.isShowing()) {
            dialog.dismiss();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            Intent intent = new Intent(this, MyVpnService.class);
            startService(intent);
            updateUI(true);
        }
    }


    @Override
    protected void onResume() {
        super.onResume();
        //registerNetworkCallback();
        if (isConnected) {
            startVPNConnection();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        networkMonitor.stopMonitoring();
        if (isConnected) {
            disconnectVPN();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (dialog != null && dialog.isShowing()) {
            dialog.dismiss();
        }
        if (isBound) {
            unbindService(serviceConnection);
            isBound = false;
        }
    }

}