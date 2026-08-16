package com.junruo.jiankong.server;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.IBinder;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.junruo.jiankong.MainActivity;
import com.junruo.jiankong.R;
import com.lzy.okhttputils.OkHttpUtils;
import com.lzy.okhttputils.callback.StringCallback;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import okhttp3.Call;
import okhttp3.Response;


public class FloatingImageDisplayService extends Service {
    public static boolean isStarted = false;

    private WindowManager windowManager;
    private WindowManager.LayoutParams layoutParams;


    private View displayView;

    private String dayin = "";//打印流量包
    private Double mianliu=0.00;//总免流
    private Double zong=0.00;//套餐总量
    private Double yong=0.00;//套餐已用
    private Double sheng =0.00;//剩余流量
    private Double ben = 0.00;//本次免流
    private Double tiao = 0.00;//本次消耗

    private Double dingz = 0.00;//定向总量
    private Double dingy = 0.00;//定向已用
    private Double dings = 0.00;//定向剩余


    private String orone = "yes";//是否首次获取

    private Double onem = 0.00;//初始化本次免流
    private Double onet = 0.00;//初始化本次消耗

    private Long time ;//默认3分钟刷新一次180000l

    private String cookie = "";//储存cookie信息




    TextView miantv,zongtv,yongtv,shengtv,bentv,tiaotv,sjtv,miant,zongt,yongt,shengt,sjt;

    private GestureDetector gestureDetector;
    TextView btnRefresh, btnReset;
    private boolean isFolded = false;

    LinearLayout zhe;

    private String gao,kuan,xgao,xkuan;

    // 网络恢复自动刷新
    private BroadcastReceiver networkReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (isNetworkAvailable()) {
                System.out.println("网络恢复，自动刷新");
                update();
            }
        }
    };

    // 显示项目设置更新
    private BroadcastReceiver displayReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            applyDisplaySettings();
        }
    };

    private void applyDisplaySettings() {
        if (miant == null || bentv == null) return; // 视图未初始化时跳过
        SharedPreferences sp = getSharedPreferences("Cookie", Context.MODE_PRIVATE);
        boolean showMian = sp.getBoolean("show_mian", true);
        boolean showZong = sp.getBoolean("show_zong", true);
        boolean showYong = sp.getBoolean("show_yong", true);
        boolean showSheng = sp.getBoolean("show_sheng", true);
        boolean showBen = sp.getBoolean("show_ben", true);
        boolean showTiao = sp.getBoolean("show_tiao", true);

        // 显示/隐藏
        miant.setVisibility(showMian ? View.VISIBLE : View.GONE);
        miantv.setVisibility(showMian ? View.VISIBLE : View.GONE);
        zongt.setVisibility(showZong ? View.VISIBLE : View.GONE);
        zongtv.setVisibility(showZong ? View.VISIBLE : View.GONE);
        yongt.setVisibility(showYong ? View.VISIBLE : View.GONE);
        yongtv.setVisibility(showYong ? View.VISIBLE : View.GONE);
        shengt.setVisibility(showSheng ? View.VISIBLE : View.GONE);
        shengtv.setVisibility(showSheng ? View.VISIBLE : View.GONE);
        bentv.setVisibility(showBen ? View.VISIBLE : View.GONE);
        tiaotv.setVisibility(showTiao ? View.VISIBLE : View.GONE);
        LinearLayout benRow = (LinearLayout) bentv.getParent();
        LinearLayout tiaoRow = (LinearLayout) tiaotv.getParent();
        ((TextView)benRow.getChildAt(0)).setVisibility(showBen ? View.VISIBLE : View.GONE);
        ((TextView)tiaoRow.getChildAt(0)).setVisibility(showTiao ? View.VISIBLE : View.GONE);

        // 颜色
        int labelColor = sp.getInt("color_label", Color.parseColor("#E6E6E6"));
        int valueColor = sp.getInt("color_value", Color.WHITE);
        int btnColor = sp.getInt("color_btn", Color.parseColor("#4FC3F7"));

        miant.setTextColor(labelColor);
        zongt.setTextColor(labelColor);
        yongt.setTextColor(labelColor);
        shengt.setTextColor(labelColor);
        sjt.setTextColor(labelColor);
        ((TextView)benRow.getChildAt(0)).setTextColor(labelColor);
        ((TextView)tiaoRow.getChildAt(0)).setTextColor(labelColor);

        miantv.setTextColor(valueColor);
        zongtv.setTextColor(valueColor);
        yongtv.setTextColor(valueColor);
        shengtv.setTextColor(valueColor);
        sjtv.setTextColor(valueColor);
        bentv.setTextColor(valueColor);
        tiaotv.setTextColor(valueColor);



    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        NetworkCapabilities nc = cm.getNetworkCapabilities(cm.getActiveNetwork());
        return nc != null && (nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                || nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                || nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
    }

    // 通知栏按钮的广播接收器
    private BroadcastReceiver refreshReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("com.junruo.jiankong.ACTION_RESET".equals(intent.getAction())) {
                // 重置：清零所有数据，重新开始记录
                mianliu = 0.00;
                zong = 0.00;
                yong = 0.00;
                sheng = 0.00;
                dingz = 0.00;
                dingy = 0.00;
                dings = 0.00;
                ben = 0.00;
                tiao = 0.00;
                onem = 0.00;
                onet = 0.00;
                orone = "yes";
                // 清除SharedPreferences里的起始值
                SharedPreferences sp = getSharedPreferences("Cookie", Context.MODE_PRIVATE);
                sp.edit().remove("onem").remove("onet").remove("onem_time").commit();
                update();
            } else {
                // 刷新
                update();
            }
        }
    };

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public void onCreate() {
        super.onCreate();

        // 创建SharedPreferences对象用于获取Cookie信息,并将其私有化
        SharedPreferences share = getSharedPreferences("Cookie",
                Context.MODE_PRIVATE);
        // 获取编辑器来存储数据到sharedpreferences中
        cookie = share.getString("Cookie","");
        gao = share.getString("gao","320");
        kuan = share.getString("kuan","230");
        xgao = share.getString("xgao","152");
        xkuan = share.getString("xkuan","202");

        time = Long.valueOf(share.getString("time","180"))*1000;
        System.out.println("==========>高"+gao+"==========>宽"+kuan+"==========>小高"+xgao+"==========>小宽"+xkuan);

        isStarted = true;
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        layoutParams = new WindowManager.LayoutParams();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            layoutParams.type = WindowManager.LayoutParams.TYPE_PHONE;
        }
        layoutParams.format = PixelFormat.RGBA_8888;
        layoutParams.gravity = Gravity.LEFT | Gravity.TOP;
        layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        layoutParams.width = Integer.parseInt(kuan);//230
        layoutParams.height = Integer.parseInt(gao);//320
        layoutParams.x = 300;
        layoutParams.y = 300;


        LayoutInflater layoutInflater = LayoutInflater.from(this);
        displayView = layoutInflater.inflate(R.layout.xfc, null);
        displayView.setOnTouchListener(new FloatingOnTouchListener());

        miant = displayView.findViewById(R.id.miant);
        zongt = displayView.findViewById(R.id.zongt);
        yongt = displayView.findViewById(R.id.yongt);
        shengt = displayView.findViewById(R.id.shengt);

        miantv = displayView.findViewById(R.id.mian);
        zongtv = displayView.findViewById(R.id.zong);
        yongtv = displayView.findViewById(R.id.yong);
        shengtv = displayView.findViewById(R.id.sheng);
        bentv = displayView.findViewById(R.id.ben);
        tiaotv = displayView.findViewById(R.id.tiao);
        sjtv = displayView.findViewById(R.id.sj);
        sjt = displayView.findViewById(R.id.sjt);

        zhe = displayView.findViewById(R.id.zhe);

        // 刷新/重置按钮
        btnRefresh = displayView.findViewById(R.id.btn_refresh);
        btnReset = displayView.findViewById(R.id.btn_reset);
        if (btnRefresh != null) {
            btnRefresh.setOnClickListener(v -> {
                update();
                Toast.makeText(getApplicationContext(), "已刷新", Toast.LENGTH_SHORT).show();
            });
        }
        if (btnReset != null) {
            btnReset.setOnClickListener(v -> {
                mianliu = 0.00; zong = 0.00; yong = 0.00; sheng = 0.00;
                dingz = 0.00; dingy = 0.00; dings = 0.00;
                ben = 0.00; tiao = 0.00; onem = 0.00; onet = 0.00;
                orone = "yes";
                SharedPreferences sp = getSharedPreferences("Cookie", Context.MODE_PRIVATE);
                sp.edit().remove("onem").remove("onet").remove("onem_time").commit();
                update();
                Toast.makeText(getApplicationContext(), "已重置", Toast.LENGTH_SHORT).show();
            });
        }

        // 双击折叠/展开
        gestureDetector = new GestureDetector(this, new SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (zhe != null) {
                    isFolded = !isFolded;
                    // zhe 的第一个子 view 是内层容器
                    // 0=时间 1=总免 2=通用 3=已用 4=剩余 5=免 6=跳
                    // 折叠时只显示 时间、免、跳
                    LinearLayout inner = (LinearLayout) ((LinearLayout)zhe).getChildAt(0);
                    for (int i = 0; i < inner.getChildCount(); i++) {
                        if (i == 0 || i == 5 || i == 6) {
                            inner.getChildAt(i).setVisibility(View.VISIBLE); // 时间、免、跳始终显示
                        } else {
                            inner.getChildAt(i).setVisibility(isFolded ? View.GONE : View.VISIBLE);
                        }
                    }
                    // 折叠时时间标签改"更"，展开时恢复"时间"
                    sjt.setText(isFolded ? "更 " : "时间 ");
                    // 折叠时隐藏刷新/重置按钮
                    if (btnRefresh != null) btnRefresh.setVisibility(isFolded ? View.GONE : View.VISIBLE);
                    if (btnReset != null) btnReset.setVisibility(isFolded ? View.GONE : View.VISIBLE);
                    layoutParams.height = isFolded ? Integer.parseInt(xgao) : Integer.parseInt(gao);
                    layoutParams.width = isFolded ? Integer.parseInt(xkuan) : Integer.parseInt(kuan);
                    windowManager.updateViewLayout(displayView, layoutParams);
                }
                return true;
            }
        });



        // 注册刷新和重置广播
        IntentFilter filter = new IntentFilter();
        filter.addAction("com.junruo.jiankong.ACTION_REFRESH");
        filter.addAction("com.junruo.jiankong.ACTION_RESET");
        registerReceiver(refreshReceiver, filter, Context.RECEIVER_NOT_EXPORTED);

        // 注册网络恢复广播
        IntentFilter netFilter = new IntentFilter();
        netFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(networkReceiver, netFilter, Context.RECEIVER_NOT_EXPORTED);

        // 注册显示设置更新广播
        IntentFilter displayFilter = new IntentFilter("com.junruo.jiankong.ACTION_UPDATE_DISPLAY");
        registerReceiver(displayReceiver, displayFilter, Context.RECEIVER_NOT_EXPORTED);

        // 应用已保存的显示设置
        applyDisplaySettings();

    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void startForeground() {
        startForeground(1, getMyActivityNotification(""));
    }



    private Notification getMyActivityNotification(String text){

        String ID = "com.junruo.jiankong";	//这里的id里面输入自己的项目的包的路径
        String NAME = "前台服务通知栏";
        Intent intent1 = new Intent(FloatingImageDisplayService.this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent1, PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder notification; //创建服务对象
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(ID, NAME, manager.IMPORTANCE_MIN);//静默通知
            channel.enableLights(true);
            channel.setShowBadge(true);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            manager.createNotificationChannel(channel);
            notification = new NotificationCompat.Builder(FloatingImageDisplayService.this).setChannelId(ID);
        } else {
            notification = new NotificationCompat.Builder(FloatingImageDisplayService.this);
        }
        notification.setContentTitle("联通流量监控")
                .setContentText(text)//设置内容
                .setWhen(System.currentTimeMillis())
                .setSmallIcon(R.mipmap.ic_launcher)
                .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher))
                .setContentIntent(pendingIntent)
                // 添加"刷新"和"重置"按钮
                .addAction(R.drawable.ic_refresh, "刷新",
                        PendingIntent.getBroadcast(this, 0,
                                new Intent("com.junruo.jiankong.ACTION_REFRESH"), PendingIntent.FLAG_IMMUTABLE))
                .addAction(R.drawable.ic_reset, "重置",
                        PendingIntent.getBroadcast(this, 1,
                                new Intent("com.junruo.jiankong.ACTION_RESET"), PendingIntent.FLAG_IMMUTABLE))
                .build();


        Notification notification1 = notification.build();
        startForeground(1,notification1);// 开始前台服务


        return notification1;
    }


    private void updateNotification(String text) {
        String text1 = text;

        Notification notification = getMyActivityNotification(text1);

        NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.notify(1, notification);
    }




    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        startForeground();

        showFloatingWindow();
        return super.onStartCommand(intent, flags, startId);
    }
    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            windowManager.removeView(displayView);
        } catch (Exception e) {
            // view 可能未添加
        }
        handler.removeCallbacksAndMessages(null);
        isStarted = false;
        unregisterReceiver(refreshReceiver);
        unregisterReceiver(networkReceiver);
        unregisterReceiver(displayReceiver);
        stopForeground(true);// 停止前台服务--参数：表示是否移除之前的通知
        // Service被终止的同时也停止定时器继续运行
        Toast.makeText(getApplicationContext(), "已关闭悬浮窗", Toast.LENGTH_SHORT).show();

    }

    //1，首先创建一个Handler对象
    Handler handler=new Handler(Looper.getMainLooper());
    @SuppressLint("WrongConstant")
    @RequiresApi(api = Build.VERSION_CODES.M)
    private void showFloatingWindow() {
        if (Settings.canDrawOverlays(this)) {

            update();
            //2，然后创建一个Runnable对像
            Runnable runnable=new Runnable(){
                @Override
                public void run() {
                    update();
                    windowManager.updateViewLayout(displayView, layoutParams);
                    // TODO Auto-generated method stub
                    //要做的事情，这里再次调用此Runnable对象，以实现每两秒实现一次的定时器操作
                    handler.postDelayed(this, time);
                }
            };
            //3，使用PostDelayed方法，调用此Runnable对象
            handler.postDelayed(runnable, time);
            //4，关闭此定时器，可以这样操作
            //  handler.removeCallbacks(runnable);
            //移除所有的消息
            //handler.removeCallbacksAndMessages(null);




            try {
                windowManager.addView(displayView, layoutParams);
            } catch (Exception e) {
                // view 已添加，更新即可
                windowManager.updateViewLayout(displayView, layoutParams);
            }


        }
    }

    private void update(){
        mianliu=0.00;//总免流
        zong=0.00;//套餐总量
        yong=0.00;//套餐已用
        sheng =0.00;//剩余流量
        dingz=0.00;//定向总量
        dingy=0.00;//定向已用
        dings=0.00;//定向剩余

        DecimalFormat df = new DecimalFormat("0.000");
        Date day=new Date();
        SimpleDateFormat sj = new SimpleDateFormat("HH:mm:ss");
        sjtv.setText(sj.format(day));

        try {

            OkHttpUtils.post("https://m.client.10010.com/servicequerybusiness/operationservice/queryOcsPackageFlowLeftContentRevisedInJune")
                    .headers("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
                    .headers("Cookie", cookie)
                    .execute(new StringCallback() {
                        @Override
                        public void onSuccess(String s, Call call, Response response) {
                            // 1. null检查放最前面
                            if (s == null || s.isEmpty() || s.equals("999999")){
                                Toast.makeText(FloatingImageDisplayService.this,"解析cookie失败，请重新获取。",Toast.LENGTH_LONG).show();
                                return;
                            }
                            // 2. HTML检测
                            if (!s.trim().startsWith("{")) {
                                return;
                            }
                            JSONObject json = JSONObject.parseObject(s);
                            if (json == null) return;
                            System.out.println("=========================>成功");
                            //binding.packageName.setText(json.get("packageName").toString());

                            // 3. 解析全部流量数据
                            mianliu = 0.00;
                            yong = 0.00;
                            zong = 0.00;
                            sheng = 0.00;
                            dingz = 0.00;
                            dingy = 0.00;
                            dings = 0.00;

                            try {
                                // 总免从定向包 use 累加（summary.freeFlow 已弃用）

                                // resources（套内流量，可能多张卡）
                                JSONArray jsonArray = json.getJSONArray("resources");
                                if (jsonArray != null) {
                                    for (int j = 0; j < jsonArray.size(); j++) {
                                    JSONObject job = jsonArray.getJSONObject(j);
                                    String cardType = job.getString("type");
                                    // 只处理流量卡，跳过语音卡、短信卡
                                    if (cardType != null && !cardType.equals("flow")) continue;
                                    String cardName = job.getString("packageName");
                                    System.out.println("卡" + j + ": " + cardName + " type=" + cardType);
                                    JSONArray details = job.getJSONArray("details");
                                    if (details != null) {
                                        for (int i = 0; i < details.size(); i++) {
                                            try {
                                                JSONObject liuliang = details.getJSONObject(i);
                                                String limited = liuliang.getString("limited");
                                                String addupItemCode = liuliang.getString("addupItemCode");
                                                String use = liuliang.getString("use");

                                                if (use == null) continue;

                                                String total = liuliang.getString("total");
                                                String remain = liuliang.getString("remain");

                                                if ("40008".equals(addupItemCode)) {
                                                    // 定向包（包括钉钉免流、联通云盘等）
                                                    double totalVal = safeDouble(total);
                                                    dingz = dingz + totalVal;
                                                    dingy = dingy + Double.parseDouble(use);
                                                    dings = totalVal == 0 ? dings : dings + safeDouble(remain);
                                                    mianliu = mianliu + Double.parseDouble(use); // 定向已用计入总免
                                                } else if ("0".equals(limited)) {
                                                    // 通用包（只算流量，排除语音和短信）
                                                    String flowType = liuliang.getString("flowType");
                                                    if (flowType == null || (!flowType.equals("1") && !flowType.equals("2"))) continue;
                                                    if (total == null || remain == null) continue;
                                                    zong = zong + Double.parseDouble(total);
                                                    yong = yong + Double.parseDouble(use);
                                                    sheng = sheng + Double.parseDouble(remain);
                                                }
                                            } catch (Exception e) {
                                                System.out.println("解析resources[" + i + "]异常: " + e.getMessage());
                                            }
                                        }
                                    }
                                    } // end for j (cards)
                                }

                                // MlResources（新版接口新增的免流明细）
                                // 只在 summary.freeFlow 无值时累加，避免重复计算
                                JSONArray mlArray = json.getJSONArray("MlResources");
                                if (mlArray != null && mianliu == 0.00) {
                                    for (int i = 0; i < mlArray.size(); i++) {
                                        try {
                                            JSONObject mlRes = mlArray.getJSONObject(i);
                                            JSONArray mlDetails = mlRes.getJSONArray("details");
                                            if (mlDetails != null) {
                                                for (int k = 0; k < mlDetails.size(); k++) {
                                                    JSONObject ml = mlDetails.getJSONObject(k);
                                                    String mlUse = ml.getString("use");
                                                    if (mlUse != null && !mlUse.equals("0.00")) {
                                                        mianliu = mianliu + Double.parseDouble(mlUse);
                                                    }
                                                }
                                            }
                                        } catch (Exception e) {
                                            System.out.println("解析MlResources[" + i + "]异常: " + e.getMessage());
                                        }
                                    }
                                }

                                // unshared 跳过（联通云盘已不免流）
                            } catch (Exception e) {
                                System.out.println("解析流量数据异常: " + e.getMessage());
                            }

                            if (orone.equals("yes")){
                                onet = yong;
                                onem = mianliu;
                                orone="no";
                                // 保存起始值到SharedPreferences，供主界面同步
                                SharedPreferences share = getSharedPreferences("Cookie",
                                        Context.MODE_PRIVATE);
                                SharedPreferences.Editor editor = share.edit();
                                editor.putString("Cookie",cookie);
                                editor.putFloat("onem", onem.floatValue());
                                editor.putFloat("onet", onet.floatValue());
                                editor.putLong("onem_time", System.currentTimeMillis());
                                editor.commit();
                            }else {
                            }
                            String dayin = "";//打印到通知栏
                            ben = Math.max(0, mianliu - onem);//本次免流（防止负数）
                            if (ben >= 1024.00){//流量大于1024m将使用G来表示
                                ben = ben / 1024.00;
                                bentv.setText(df.format(ben)+"G");
                                dayin = dayin + "免:" + df.format(ben)+"G\t";
                            }else {
                                bentv.setText(df.format(ben)+"M");
                                dayin = dayin + "免:" + df.format(ben)+"M\t";

                            }

                            tiao = Math.max(0, yong - onet);//本次消耗（防止负数）
                            if (tiao >= 1024.00){//流量大于1024m将使用G来表示
                                tiao = tiao / 1024.00;
                                tiaotv.setText(df.format(tiao)+"G");
                                dayin = dayin + "跳:" + df.format(tiao)+"G";
                            }else {
                                tiaotv.setText(df.format(tiao)+"M");
                                dayin = dayin + "跳:" + df.format(tiao)+"M";
                            }


                            if (mianliu >= 1024.00){//流量大于1024m将使用G来表示
                                mianliu = mianliu / 1024.00;

                                miantv.setText(df.format(mianliu)+"G");
                            }else {
                                miantv.setText(df.format(mianliu)+"M");
                            }


                            if (zong >= 1024.00){//流量大于1024m将使用G来表示
                                zong = zong / 1024.00;

                                zongtv.setText(df.format(zong)+"G");
                            }else {
                                zongtv.setText(df.format(zong)+"M");
                            }


                            if (yong >= 1024.00){//流量大于1024m将使用G来表示
                                yong = yong / 1024.00;

                                yongtv.setText(df.format(yong)+"G");
                            }else {
                                yongtv.setText(df.format(yong)+"M");
                            }


                            if (sheng >= 1024.00){//流量大于1024m将使用G来表示
                                sheng = sheng / 1024.00;

                                shengtv.setText(df.format(sheng)+"G");
                            }else {
                                shengtv.setText(df.format(sheng)+"M");
                            }

                            updateNotification(dayin+"\t更："+sj.format(day));

                        }
                    });

        }catch (Exception e){
            e.printStackTrace();
        }


    }




    // 安全取double，null 返回0
    private static double safeDouble(String s) {
        if (s == null) return 0.0;
        try { return Double.parseDouble(s); } catch (Exception e) { return 0.0; }
    }

    private class FloatingOnTouchListener implements View.OnTouchListener {
        private int x;
        private int y;

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            // 先让 GestureDetector 处理双击
            if (gestureDetector != null && gestureDetector.onTouchEvent(event)) {
                return true;
            }
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    x = (int) event.getRawX();
                    y = (int) event.getRawY();
                    break;
                case MotionEvent.ACTION_MOVE:
                    int nowX = (int) event.getRawX();
                    int nowY = (int) event.getRawY();
                    int movedX = nowX - x;
                    int movedY = nowY - y;
                    x = nowX;
                    y = nowY;
                    layoutParams.x = layoutParams.x + movedX;
                    layoutParams.y = layoutParams.y + movedY;
                    windowManager.updateViewLayout(view, layoutParams);
                    break;
                default:
                    break;
            }
            return false;
        }
    }
}
