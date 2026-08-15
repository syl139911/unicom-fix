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
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
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

    private Boolean isZhan = true;//悬浮窗展开状态



    TextView miantv,zongtv,yongtv,shengtv,bentv,tiaotv,sjtv,miant,zongt,yongt,shengt,sjt;
    TextView btnRefresh, btnReset;

    LinearLayout zhe;

    private String gao,kuan,xgao,xkuan;

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

        btnRefresh = displayView.findViewById(R.id.btn_refresh);
        btnReset = displayView.findViewById(R.id.btn_reset);

        btnRefresh.setOnClickListener(v -> {
            update();
            Toast.makeText(getApplicationContext(), "已刷新", Toast.LENGTH_SHORT).show();
        });

        btnReset.setOnClickListener(v -> {
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
            SharedPreferences sp = getSharedPreferences("Cookie", Context.MODE_PRIVATE);
            sp.edit().remove("onem").remove("onet").remove("onem_time").commit();
            update();
            Toast.makeText(getApplicationContext(), "已重置", Toast.LENGTH_SHORT).show();
        });

        // 注册刷新和重置广播
        IntentFilter filter = new IntentFilter();
        filter.addAction("com.junruo.jiankong.ACTION_REFRESH");
        filter.addAction("com.junruo.jiankong.ACTION_RESET");
        registerReceiver(refreshReceiver, filter);

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
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent1, 0);
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
                .addAction(R.mipmap.ic_launcher, "刷新",
                        PendingIntent.getBroadcast(this, 0,
                                new Intent("com.junruo.jiankong.ACTION_REFRESH"), PendingIntent.FLAG_IMMUTABLE))
                .addAction(R.mipmap.ic_launcher, "重置",
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
        windowManager.removeView(displayView);
        handler.removeCallbacksAndMessages(null);
        isStarted = false;
        unregisterReceiver(refreshReceiver);
        stopForeground(true);// 停止前台服务--参数：表示是否移除之前的通知
        // Service被终止的同时也停止定时器继续运行
        Toast.makeText(getApplicationContext(), "已关闭悬浮窗", Toast.LENGTH_SHORT).show();

    }

    //1，首先创建一个Handler对象
    Handler handler=new Handler();
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


            zhe.setOnClickListener(v -> {
                if (isZhan){
                    layoutParams.width = Integer.parseInt(xkuan);//200
                    layoutParams.height = Integer.parseInt(xgao);//150


                    sjt.setText("更：");
                    miantv.setVisibility(8);
                    zongtv.setVisibility(8);
                    yongtv.setVisibility(8);
                    shengtv.setVisibility(8);
                    miant.setVisibility(8);
                    zongt.setVisibility(8);
                    yongt.setVisibility(8);
                    shengt.setVisibility(8);
                    isZhan = false;

                    windowManager.updateViewLayout(displayView, layoutParams);

                }else if (!isZhan){
                    layoutParams.width = Integer.parseInt(kuan);//230
                    layoutParams.height = Integer.parseInt(gao);//320
                    sjt.setText("时间：");
                    miantv.setVisibility(0);
                    zongtv.setVisibility(0);
                    yongtv.setVisibility(0);
                    shengtv.setVisibility(0);
                    miant.setVisibility(0);
                    zongt.setVisibility(0);
                    yongt.setVisibility(0);
                    shengt.setVisibility(0);
                    isZhan = true;

                    windowManager.updateViewLayout(displayView, layoutParams);
                }


            });

            windowManager.addView(displayView, layoutParams);


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
                                // resources（套内流量）
                                JSONArray jsonArray = json.getJSONArray("resources");
                                if (jsonArray != null && jsonArray.size() > 0) {
                                    JSONObject job = jsonArray.getJSONObject(0);
                                    JSONArray details = job.getJSONArray("details");
                                    if (details != null) {
                                        for (int i = 0; i < details.size(); i++) {
                                            try {
                                                JSONObject liuliang = details.getJSONObject(i);
                                                String limited = liuliang.getString("limited");
                                                String addupItemCode = liuliang.getString("addupItemCode");
                                                String use = liuliang.getString("use");

                                                if (use == null) continue;

                                                if ("1".equals(limited) && "40008".equals(addupItemCode)) {
                                                    mianliu = mianliu + Double.parseDouble(use);
                                                } else if ("0".equals(limited)) {
                                                    String total = liuliang.getString("total");
                                                    String remain = liuliang.getString("remain");
                                                    if (total == null || remain == null) continue;

                                                    if ("40008".equals(addupItemCode)) {
                                                        dingz = dingz + Double.parseDouble(total);
                                                        dingy = dingy + Double.parseDouble(use);
                                                        dings = dings + Double.parseDouble(remain);
                                                    } else {
                                                        zong = zong + Double.parseDouble(total);
                                                        yong = yong + Double.parseDouble(use);
                                                        sheng = sheng + Double.parseDouble(remain);
                                                    }
                                                }
                                            } catch (Exception e) {
                                                System.out.println("解析resources[" + i + "]异常: " + e.getMessage());
                                            }
                                        }
                                    }
                                }

                                // MlResources（新版接口新增的免流明细）
                                JSONArray mlArray = json.getJSONArray("MlResources");
                                if (mlArray != null) {
                                    for (int i = 0; i < mlArray.size(); i++) {
                                        try {
                                            JSONObject mlRes = mlArray.getJSONObject(i);
                                            JSONArray mlDetails = mlRes.getJSONArray("details");
                                            if (mlDetails != null) {
                                                for (int j = 0; j < mlDetails.size(); j++) {
                                                    JSONObject ml = mlDetails.getJSONObject(j);
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

                                // unshared（专享流量包，如联通云盘）
                                JSONArray unsharedArray = json.getJSONArray("unshared");
                                if (unsharedArray != null) {
                                    for (int i = 0; i < unsharedArray.size(); i++) {
                                        try {
                                            JSONObject us = unsharedArray.getJSONObject(i);
                                            String usType = us.getString("type");
                                            if ("unsharedFlowList".equals(usType)) {
                                                JSONArray usDetails = us.getJSONArray("details");
                                                if (usDetails != null) {
                                                    for (int j = 0; j < usDetails.size(); j++) {
                                                        try {
                                                            JSONObject ud = usDetails.getJSONObject(j);
                                                            String usTotal = ud.getString("total");
                                                            String usUse = ud.getString("use");
                                                            String usRemain = ud.getString("remain");
                                                            String usLimited = ud.getString("limited");
                                                            if (usTotal == null || usUse == null || usRemain == null) continue;

                                                            if ("0".equals(usLimited)) {
                                                                dingz = dingz + Double.parseDouble(usTotal);
                                                                dingy = dingy + Double.parseDouble(usUse);
                                                                dings = dings + Double.parseDouble(usRemain);
                                                            } else {
                                                                mianliu = mianliu + Double.parseDouble(usUse);
                                                            }
                                                        } catch (Exception e) {
                                                            System.out.println("解析unshared detail异常: " + e.getMessage());
                                                        }
                                                    }
                                                }
                                            }
                                        } catch (Exception e) {
                                            System.out.println("解析unshared[" + i + "]异常: " + e.getMessage());
                                        }
                                    }
                                }
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
                            ben = mianliu - onem;//本次免流
                            if (ben >= 1024.00){//流量大于1024m将使用G来表示
                                ben = ben / 1024.00;
                                bentv.setText(df.format(ben)+"G");
                                dayin = dayin + "免:" + df.format(ben)+"G\t";
                            }else {
                                bentv.setText(df.format(ben)+"M");
                                dayin = dayin + "免:" + df.format(ben)+"M\t";

                            }

                            tiao = yong - onet;//本次消耗
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




    private class FloatingOnTouchListener implements View.OnTouchListener {
        private int x;
        private int y;

        @Override
        public boolean onTouch(View view, MotionEvent event) {
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
