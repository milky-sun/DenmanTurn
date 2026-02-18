package com.denman.turn;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

/**
 * 极简旋转控制服务 (通用兼容版)
 * 适配范围: Android 7.0 - Android 14
 * * 功能：
 * 1. 创建一个 0x0 像素的不可见窗口来强制系统屏幕方向。
 * 2. 在通知栏提供操作按钮，避免创建额外的 UI 界面。
 * 3. 极低的内存占用设计。
 */
public class RotationService extends Service {

    // 定义操作指令常量
    private static final String ACTION_PORTRAIT = "com.denman.turn.ACTION_0";
    private static final String ACTION_LANDSCAPE = "com.denman.turn.ACTION_90";
    private static final String ACTION_REVERSE_PORTRAIT = "com.denman.turn.ACTION_180";
    private static final String ACTION_REVERSE_LANDSCAPE = "com.denman.turn.ACTION_270";
    private static final String ACTION_EXIT = "com.denman.turn.ACTION_EXIT";

    private static final int NOTIFICATION_ID = 9527;
    private static final String CHANNEL_ID = "rotation_control_channel";

    // 系统窗口管理器引用
    private WindowManager windowManager;
    // 我们的"幽灵"视图
    private View overlayView;
    // 当前视图的布局参数
    private WindowManager.LayoutParams layoutParams;

    @Override
    public IBinder onBind(Intent intent) {
        // 不需要绑定，这是一个独立运行的前台服务
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        
        // 获取 WindowManager 服务
        windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        
        // 初始化布局参数 (仅初始化一次)
        initializeLayoutParams();

        // 立即启动前台通知以防止被杀
        startForegroundServiceNotification();
    }

    /**
     * 初始化不可见窗口的参数
     * 关键点：尺寸为0，不可触摸，不可获取焦点
     */
    private void initializeLayoutParams() {
        int overlayType;
        // Android 8.0 (API 26) 及以上必须使用 TYPE_APPLICATION_OVERLAY
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            overlayType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            overlayType = WindowManager.LayoutParams.TYPE_PHONE;
        }

        layoutParams = new WindowManager.LayoutParams(
            0, 0, // 宽度和高度设为 0，极致省内存
            overlayType,
            // FLAG_NOT_FOCUSABLE: 不抢键盘焦点
            // FLAG_NOT_TOUCHABLE: 点击穿透
            // FLAG_LAYOUT_NO_LIMITS: 允许在屏幕之外
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        );
        
        // 默认居中（虽然看不见）
        layoutParams.gravity = Gravity.CENTER;
        
        // 默认启动时设为正常竖屏，或者根据需求修改
        layoutParams.screenOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            handleAction(intent.getAction());
        }

        // 确保视图已添加
        addOverlayViewIfNotExists();

        // START_STICKY: 如果服务被杀，尝试重启
        return START_STICKY;
    }

    /**
     * 处理通知栏点击事件
     */
    private void handleAction(String action) {
        int targetOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;

        switch (action) {
            case ACTION_PORTRAIT: // 0度
                targetOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
                break;
            case ACTION_LANDSCAPE: // 90度
                targetOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
                break;
            case ACTION_REVERSE_PORTRAIT: // 180度 (倒置)
                targetOrientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
                break;
            case ACTION_REVERSE_LANDSCAPE: // 270度
                targetOrientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
                break;
            case ACTION_EXIT: // 退出程序
                stopSelf();
                return; // 直接返回，不再执行更新逻辑
        }

        if (targetOrientation != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
            updateOverlayOrientation(targetOrientation);
            // 更新通知栏状态（可选，为了让用户知道当前选了什么，这里为了精简暂不刷新通知文案）
        }
    }

    /**
     * 将幽灵视图添加到 WindowManager
     */
    private void addOverlayViewIfNotExists() {
        if (overlayView == null) {
            overlayView = new View(this);
            try {
                windowManager.addView(overlayView, layoutParams);
            } catch (Exception e) {
                // 通常是权限被拒绝，或者 View 已经添加
                e.printStackTrace();
            }
        }
    }

    /**
     * 更新屏幕方向
     */
    private void updateOverlayOrientation(int orientation) {
        if (overlayView != null && layoutParams.screenOrientation != orientation) {
            layoutParams.screenOrientation = orientation;
            try {
                windowManager.updateViewLayout(overlayView, layoutParams);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 构建并启动前台通知
     * 包含 5 个操作按钮
     */
    private void startForegroundServiceNotification() {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        // 1. 创建通知渠道 (Android 8.0+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Rotation Control",
                NotificationManager.IMPORTANCE_LOW // 低优先级，静音
            );
            channel.setShowBadge(false);
            channel.enableVibration(false);
            // 公开可见性，确保锁屏能看到
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            notificationManager.createNotificationChannel(channel);
        }

        // 2. 构建通知 Action 按钮
        // 注意：使用 android.R.drawable 系统图标以减小 APK 体积
        Notification.Action action0 = createNotificationAction("0°", ACTION_PORTRAIT);
        Notification.Action action90 = createNotificationAction("90°", ACTION_LANDSCAPE);
        Notification.Action action180 = createNotificationAction("180°", ACTION_REVERSE_PORTRAIT);
        Notification.Action action270 = createNotificationAction("270°", ACTION_REVERSE_LANDSCAPE);
        Notification.Action actionExit = createNotificationAction("退出", ACTION_EXIT);

        // 3. 构建通知主体
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        Notification notification = builder
            .setContentTitle("屏幕旋转控制")
            .setContentText("点击下方按钮切换方向")
            // 使用系统自带的旋转图标
            .setSmallIcon(android.R.drawable.ic_menu_rotate)
            .setOngoing(true) // 禁止滑动删除
            .setAutoCancel(false)
            // 按顺序添加按钮
            .addAction(action0)
            .addAction(action180)
            .addAction(actionExit)
            .addAction(action90)
            .addAction(action270)
            // 适配锁屏显示
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .build();

        // 4. 启动前台服务
        // Android 14 需要指定 foregroundServiceType
        // if (Build.VERSION.SDK_INT >= 34) { // Android 14
        //      startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        // } else {
             startForeground(NOTIFICATION_ID, notification);
        // }
    }

    /**
     * 辅助方法：创建通知栏按钮
     */
    private Notification.Action createNotificationAction(String title, String actionString) {
        Intent intent = new Intent(this, RotationService.class);
        intent.setAction(actionString);
        
        // 创建 PendingIntent
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        // Android 6.0+ 推荐使用 IMMUTABLE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }

        PendingIntent pendingIntent = PendingIntent.getService(this, actionString.hashCode(), intent, flags);

        // 使用系统透明图标作为按钮图标 (因为我们主要看文字)
        int iconResId = android.R.drawable.ic_menu_rotate;
        if (ACTION_EXIT.equals(actionString)) {
            iconResId = android.R.drawable.ic_menu_close_clear_cancel;
        }

        return new Notification.Action.Builder(iconResId, title, pendingIntent).build();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // 清理视图，恢复屏幕控制权
        if (overlayView != null && windowManager != null) {
            try {
                windowManager.removeView(overlayView);
            } catch (Exception e) {
                // 忽略异常
            }
        }
        // 移除前台通知
        stopForeground(true);
    }
}
