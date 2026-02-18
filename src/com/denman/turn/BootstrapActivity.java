package com.denman.turn;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

/**
 * 引导 Activity
 * * 职责：
 * 1. 检查是否拥有悬浮窗权限 (SYSTEM_ALERT_WINDOW)。
 * 2. 如果没有，跳转系统设置页面申请。
 * 3. 如果有，启动 Service 并立即结束自己。
 * * 这是一个 "透明" Activity，用户几乎感觉不到它的存在。
 */
public class BootstrapActivity extends Activity {

    private static final int REQUEST_CODE_OVERLAY_PERMISSION = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 检查权限并执行逻辑
        checkPermissionAndStart();
    }

    private void checkPermissionAndStart() {
        // Android 6.0 (M) 以上需要动态申请悬浮窗权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                requestOverlayPermission();
            } else {
                startRotationService();
            }
        } else {
            // Android 6.0 以下通常默认允许，或在 Manifest 中声明即可
            startRotationService();
        }
    }

    private void requestOverlayPermission() {
        Toast.makeText(this, "需要悬浮窗权限来控制屏幕旋转", Toast.LENGTH_LONG).show();
        try {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, REQUEST_CODE_OVERLAY_PERMISSION);
        } catch (Exception e) {
            // 防止部分 ROM 找不到此 Intent
            e.printStackTrace();
            Toast.makeText(this, "无法打开设置页面，请手动授权", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_OVERLAY_PERMISSION) {
            // 再次检查权限
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Settings.canDrawOverlays(this)) {
                    startRotationService();
                } else {
                    Toast.makeText(this, "权限被拒绝，无法运行", Toast.LENGTH_SHORT).show();
                    finish();
                }
            }
        }
    }

    /**
     * 启动核心服务并关闭当前界面
     */
    private void startRotationService() {
        Intent serviceIntent = new Intent(this, RotationService.class);
        
        // 默认启动为 180 度 (用户痛点)
        serviceIntent.setAction("com.denman.turn.ACTION_180");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }

        Toast.makeText(this, "旋转服务已启动 (查看通知栏)", Toast.LENGTH_SHORT).show();
        
        // 任务完成，立即自杀，释放 Activity 占用的内存
        finish();
    }
}
