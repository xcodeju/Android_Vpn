package com.example.myapplication;

import static com.example.myapplication.FileUtils.CONFIG_FILE;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PersistableBundle;
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
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

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
    private boolean isVerifyCode = false;


    // 添加成员变量
    private TextView totalTimeTextView;
    private long totalTimeMillis = 0;
    private static final String TOTAL_TIME_KEY = "total_time";
    private static final String PREFS_NAME = "vpn_stats";

    // 添加成员变量
    private TextView timerTextView;
    private TextView timerTitle;
    private Handler timerHandler = new Handler(Looper.getMainLooper());
    private Runnable timerRunnable;
    private long startTime = 0;

    // 保存计时状态
    private static final String TIMER_STATE = "timer_state";
    private static final String START_TIME_KEY = "start_time";


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

        timerTextView = findViewById(R.id.timerTextView);
        timerTitle = findViewById(R.id.timerTitle);

        initTimer();
        //loadTotalTime();
        //updateTotalTimeDisplay();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState, @NonNull PersistableBundle outPersistentState) {
        super.onSaveInstanceState(outState, outPersistentState);
        outState.putBoolean(TIMER_STATE, isConnected);
        outState.putLong(START_TIME_KEY, startTime);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        if (savedInstanceState.getBoolean(TIMER_STATE, false)) {
            startTime = savedInstanceState.getLong(START_TIME_KEY);
            startVPNConnection();
        }
    }

    // 添加计时器暂停/继续功能
    private boolean isTimerPaused = false;
    private long pauseTime = 0;

    public void pauseTimer() {
        if (isConnected && !isTimerPaused) {
            timerHandler.removeCallbacks(timerRunnable);
            pauseTime = System.currentTimeMillis() - startTime;
            isTimerPaused = true;
        }
    }

    public void resumeTimer() {
        if (isConnected && isTimerPaused) {
            startTime += System.currentTimeMillis() - pauseTime;
            timerHandler.postDelayed(timerRunnable, 0);
            isTimerPaused = false;
        }
    }

    // 添加计时完成回调
    private OnTimerCompleteListener timerCompleteListener;

    public interface OnTimerCompleteListener {
        void onTimerComplete(long totalTime);
    }

    public void setOnTimerCompleteListener(OnTimerCompleteListener listener) {
        this.timerCompleteListener = listener;
    }

    private void initTimer() {
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                long millis = System.currentTimeMillis() - startTime;
                int seconds = (int) (millis / 1000);
                int minutes = seconds / 60;
                int hours = minutes / 60;

                timerTextView.setText(String.format(Locale.getDefault(),
                        "%02d:%02d:%02d", hours % 24, minutes % 60, seconds % 60));

                timerHandler.postDelayed(this, 1000);
            }
        };
    }

    // 加载累计时间
    private void loadTotalTime() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        totalTimeMillis = prefs.getLong(TOTAL_TIME_KEY, 0);
    }

    // 保存累计时间
    private void saveTotalTime() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit().putLong(TOTAL_TIME_KEY, totalTimeMillis).apply();
    }

    // 更新累计时间显示
    private void updateTotalTimeDisplay() {
        long hours = TimeUnit.MILLISECONDS.toHours(totalTimeMillis);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(totalTimeMillis) % 60;
        String timeText = String.format("累计连接时间：%d小时%d分钟", hours, minutes);
        totalTimeTextView.setText(timeText);
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
                if(!isConnected) {
                    // 网络可用时显示授权弹窗
                    if (internetAvailable) {
                        if(!isVerifyCode) {
                            showAuthDialog();
                        }else{
                            reStartVPNConnection();
                        }
                    } else {
                        showNetworkError("无法访问互联网");
                    }
                }else{
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
            startTime = System.currentTimeMillis();
            timerTextView.setVisibility(View.VISIBLE);
            timerTitle.setVisibility(View.VISIBLE);
            timerHandler.postDelayed(timerRunnable, 0);

        } else {
            disconnectVPN();
        }
    }

    private void reStartVPNConnection() {

        if (!isConnected) {
            isVerifying = true;
            showProgressDialog();
            connectVPN();
            startTime = System.currentTimeMillis() - pauseTime;
            timerTextView.setVisibility(View.VISIBLE);
            timerTitle.setVisibility(View.VISIBLE);
            timerHandler.postDelayed(timerRunnable, 0);

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
        if (dialog != null && dialog.isShowing()) {
            dialog.dismiss();
        }
        pauseTimer();

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
        if (isConnected) {
            timerHandler.postDelayed(timerRunnable, 0);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterNetworkCallback();
        if (isConnected) {
            timerHandler.removeCallbacks(timerRunnable);
        }
        if (dialog != null && dialog.isShowing()) {
            dialog.dismiss();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (dialog != null && dialog.isShowing()) {
            dialog.dismiss();
        }
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