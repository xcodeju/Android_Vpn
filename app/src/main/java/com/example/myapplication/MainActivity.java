package com.example.myapplication;

import static com.example.myapplication.FileUtils.CONFIG_FILE;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
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
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.snackbar.Snackbar;

import java.io.File;

public class MainActivity extends AppCompatActivity {
    private Button connectButton;
    private TextView statusTextView;
    //private Spinner serverSpinner;
    private boolean isConnected = false;
    // 在 MainActivity.java 中添加
    private ConnectivityManager.NetworkCallback networkCallback;

    // 添加成员变量
    private static final String VALID_CODE = "123456"; // 示例有效验证码
    private boolean isVerifying = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        connectButton = findViewById(R.id.connectButton);
        statusTextView = findViewById(R.id.statusTextView);
        //serverSpinner = findViewById(R.id.serverSpinner);

//        // 服务器列表
//        String[] servers = {"US Server", "UK Server", "JP Server"};
//        ArrayAdapter<String> adapter = new ArrayAdapter<>(
//                this, android.R.layout.simple_spinner_item, servers);
//        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        //serverSpinner.setAdapter(adapter);

        connectButton.setOnClickListener(v -> toggleVPN());
    }

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
                if (internetAvailable) {
                    showAuthDialog(); // 网络可用时显示授权弹窗
                } else {
                    showNetworkError("无法访问互联网");
                }
            });
        }).start();
    }

    // 授权弹窗实现
    private void showAuthDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.CenterDialogTheme);

        // 加载自定义布局
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_auth, null);
        EditText authInput = dialogView.findViewById(R.id.authEditText);

        // 配置对话框
        builder.setView(dialogView)
                .setCancelable(false)
                .setPositiveButton("确认", (dialog, which) -> {
                    String code = authInput.getText().toString().trim();
                    validateCode(code);
                    if (validateCode(code)) {
                        startVPNConnection();
                    } else {
                        showAuthError("授权码不正确");
                    }
                })
                .setNegativeButton("取消", (dialog, which) -> dialog.dismiss());

        AlertDialog dialog = builder.create();

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
            Toast.makeText(this,"初始授权码已保存",Toast.LENGTH_SHORT).show();
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
        if (!isConnected) {
            isVerifying = true;
            showProgressDialog();
            connectVPN();
        } else {
            disconnectVPN();
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
        connectButton.setText(connected ? "断开连接" : "连接VPN");
        if (!connected) isVerifying = false;
    }

    private void showNetworkError(String message) {
        Snackbar.make(connectButton, message, Snackbar.LENGTH_LONG)
                .setAction("设置", v -> startActivity(
                        new Intent(Settings.ACTION_WIFI_SETTINGS)))
                .show();
    }
    private void connectVPN() {
        Intent intent = VpnService.prepare(this);
        if (intent != null) {
            startActivityForResult(intent, 0);
        } else {
            onActivityResult(0, RESULT_OK, null);
        }
    }

    private void disconnectVPN() {
        Intent intent = new Intent(this, BasicVPNService.class);
        stopService(intent);
        updateUI(false);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            Intent intent = new Intent(this, BasicVPNService.class);
            startService(intent);
            updateUI(true);
        }
    }



    @Override
    protected void onResume() {
        super.onResume();
        registerNetworkCallback();
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterNetworkCallback();
    }

    private void registerNetworkCallback() {
        ConnectivityManager cm = (ConnectivityManager)
                getSystemService(Context.CONNECTIVITY_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            networkCallback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onLost(Network network) {
                    runOnUiThread(() -> {
                        if (isConnected) {
                            disconnectVPN();
                            showNetworkError("网络连接已断开");
                        }
                    });
                }
            };
            cm.registerDefaultNetworkCallback(networkCallback);
        }
    }

    private void unregisterNetworkCallback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && networkCallback != null) {
            ConnectivityManager cm = (ConnectivityManager)
                    getSystemService(Context.CONNECTIVITY_SERVICE);
            cm.unregisterNetworkCallback(networkCallback);
        }
    }
}