package com.junruo.jiankong;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.widget.CompoundButton;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Icon;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.junruo.jiankong.databinding.ActivityMainBinding;
import com.junruo.jiankong.server.FloatingImageDisplayService;
import com.lzy.okhttputils.OkHttpUtils;
import com.lzy.okhttputils.callback.StringCallback;

import java.io.File;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

import okhttp3.Call;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;

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

    private String time ;

    private String cookie = "";//储存cookie信息

    // 网络恢复自动刷新
    private BroadcastReceiver networkReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(android.content.Context context, Intent intent) {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(android.content.Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                NetworkCapabilities nc = cm.getNetworkCapabilities(cm.getActiveNetwork());
                if (nc != null && (nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                        || nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                        || nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))) {
                    if (cookie != null && !cookie.isEmpty()) {
                        update();
                    }
                }
            }
        }
    };

    private String versionName = "";
    private int versioncode;
    private String oldVersion ;

    private String NewVersion,versionmsg,getcookie,gonggao,notice;

    private String gao,kuan,xgao,xkuan;

    //定义读写权限
    private static final int REQUEST_EXTERNAL_STORAGE = 1;

    private static String[] PERMISSIONS_STORAGE = {
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE" };

    //动态菜单
    private ShortcutManager shortcutManager;

    public static Context context;

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        CrashHandler.getInstance().init(getApplicationContext());
        setContentView(R.layout.activity_main);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        View view = binding.getRoot();
        setContentView(view);

        //获取当前版本
        oldVersion = getAppVersionName(this);

        //初始化
        initView();

        //获取读写外部存储权限
        verifyStoragePermissions(this);

        // Android 13+ 请求通知权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 1001);
            }
        }

        // 注册网络恢复广播
        IntentFilter netFilter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(networkReceiver, netFilter, Context.RECEIVER_NOT_EXPORTED);

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(networkReceiver);
        } catch (Exception ignored) {}
    }

    //申请读写权限
    public void verifyStoragePermissions(Activity activity) {
        try {
            //检测是否有写的权限
            int permission = ActivityCompat.checkSelfPermission(activity,
                    "android.permission.WRITE_EXTERNAL_STORAGE");
            if (permission != PackageManager.PERMISSION_GRANTED) {
                // 没有写的权限，去申请写的权限，会弹出对话框
                ActivityCompat.requestPermissions(activity, PERMISSIONS_STORAGE,REQUEST_EXTERNAL_STORAGE);
            }else {
                File file = new File(getExternalFilesDir(null).toString()+"/crash/");
                //判断文件夹是否存在,如果不存在则创建文件夹
                if (!file.exists()) {
                    boolean mkdir = file.mkdir();

                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //获取当前版本
    public String getAppVersionName(Context context) {
        try {
            // ---get the package info---
            PackageManager pm = context.getPackageManager();
            PackageInfo pi = pm.getPackageInfo(context.getPackageName(), 0);
            versionName = pi.versionName;
            versioncode = pi.versionCode;
            if (versionName == null || versionName.length() <= 0) {
                return "";
            }
        } catch (Exception e) {
            Log.e("VersionInfo", "Exception", e);
        }
        return versionName;
    }


    //初始化
    private void initView() {

        Intent intent = new Intent(this, toumingActivity.class);
        intent.setAction(Intent.ACTION_VIEW);
        //动态菜单
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            shortcutManager = getSystemService(ShortcutManager.class);
            ShortcutInfo shortcut = new ShortcutInfo.Builder(this, "id1")
                    .setShortLabel("监控")
                    .setLongLabel("监控")
                    .setIcon(Icon.createWithResource(this, R.mipmap.ic_launcher))
                    .setIntent(intent)
                    .build();
/* ShortcutInfo shortcut2 = new ShortcutInfo.Builder(this, "id2")
                .setShortLabel("Web site")
                .setLongLabel("第二个")
                .setIcon(Icon.createWithResource(this, R.drawable.link))
                .setIntent(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://www.csdn.com/")))
                .build();*/


            shortcutManager.setDynamicShortcuts(Arrays.asList(shortcut));
        }

        //检查更新
        upupup();

        // 创建SharedPreferences对象用于获取Cookie信息,并将其私有化
        SharedPreferences share = getSharedPreferences("Cookie",
                Context.MODE_PRIVATE);
        // 获取编辑器来存储数据到sha redpreferences中
        cookie = share.getString("Cookie","");
        time = share.getString("time","");
        gao = share.getString("gao","320");
        xgao = share.getString("xgao","150");
        xkuan = share.getString("xkuan","200");
        kuan = share.getString("kuan","230");


        if (time.equals("")){

        }else {
            binding.shuaxin.setText(time);
        }

        binding.cookie.setText(cookie);

        binding.kuan.setText(kuan);
        binding.gao.setText(gao);
        binding.xkuan.setText(xkuan);
        binding.xgao.setText(xgao);

        // 加载悬浮窗显示项目设置（默认全显示）
        binding.cbMian.setChecked(share.getBoolean("show_mian", true));
        binding.cbZong.setChecked(share.getBoolean("show_zong", true));
        binding.cbYong.setChecked(share.getBoolean("show_yong", true));
        binding.cbSheng.setChecked(share.getBoolean("show_sheng", true));
        binding.cbBen.setChecked(share.getBoolean("show_ben", true));
        binding.cbTiao.setChecked(share.getBoolean("show_tiao", true));

        // 有cookie时自动加载数据
        if (cookie != null && !cookie.isEmpty()) {
            update();
        }

        OnClick();
        setupDisplayCheckboxes();

    }

    private void setupDisplayCheckboxes() {
        CompoundButton.OnCheckedChangeListener listener = (buttonView, isChecked) -> {
            SharedPreferences sp = getSharedPreferences("Cookie", Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = sp.edit();
            editor.putBoolean("show_mian", binding.cbMian.isChecked());
            editor.putBoolean("show_zong", binding.cbZong.isChecked());
            editor.putBoolean("show_yong", binding.cbYong.isChecked());
            editor.putBoolean("show_sheng", binding.cbSheng.isChecked());
            editor.putBoolean("show_ben", binding.cbBen.isChecked());
            editor.putBoolean("show_tiao", binding.cbTiao.isChecked());
            editor.commit();
            // 通知悬浮窗服务刷新显示
            sendBroadcast(new Intent("com.junruo.jiankong.ACTION_UPDATE_DISPLAY"));
        };
        binding.cbMian.setOnCheckedChangeListener(listener);
        binding.cbZong.setOnCheckedChangeListener(listener);
        binding.cbYong.setOnCheckedChangeListener(listener);
        binding.cbSheng.setOnCheckedChangeListener(listener);
        binding.cbBen.setOnCheckedChangeListener(listener);
        binding.cbTiao.setOnCheckedChangeListener(listener);

        setupColorPickers();
    }

    // 预设颜色列表
    private static final int[] PRESET_COLORS = {
            Color.parseColor("#FFFFFF"), // 白
            Color.parseColor("#E6E6E6"), // 浅灰
            Color.parseColor("#4FC3F7"), // 天蓝
            Color.parseColor("#81C784"), // 浅绿
            Color.parseColor("#FFD54F"), // 金黄
            Color.parseColor("#FF8A65"), // 橘红
            Color.parseColor("#E57373"), // 红
            Color.parseColor("#BA68C8"), // 紫
            Color.parseColor("#FF69B4"), // 粉
            Color.parseColor("#00E5FF"), // 青
    };

    private void setupColorPickers() {
        SharedPreferences sp = getSharedPreferences("Cookie", Context.MODE_PRIVATE);

        // 加载保存的颜色
        int labelColor = sp.getInt("color_label", Color.parseColor("#E6E6E6"));
        int valueColor = sp.getInt("color_value", Color.WHITE);
        int btnColor = sp.getInt("color_btn", Color.parseColor("#4FC3F7"));

        binding.colorLabel.setBackgroundColor(labelColor);
        binding.colorValue.setBackgroundColor(valueColor);
        binding.colorBtn.setBackgroundColor(btnColor);

        binding.colorLabel.setOnClickListener(v -> showColorPicker("color_label", binding.colorLabel));
        binding.colorValue.setOnClickListener(v -> showColorPicker("color_value", binding.colorValue));
        binding.colorBtn.setOnClickListener(v -> showColorPicker("color_btn", binding.colorBtn));
    }

    private void showColorPicker(String prefKey, View target) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("选择颜色");

        // 创建颜色网格
        android.widget.GridView gridView = new android.widget.GridView(this);
        gridView.setNumColumns(5);
        gridView.setAdapter(new android.widget.BaseAdapter() {
            @Override
            public int getCount() { return PRESET_COLORS.length; }
            @Override
            public Object getItem(int position) { return PRESET_COLORS[position]; }
            @Override
            public long getItemId(int position) { return position; }
            @Override
            public View getView(int position, View convertView, android.view.ViewGroup parent) {
                View colorView = new View(MainActivity.this);
                colorView.setLayoutParams(new android.widget.AbsListView.LayoutParams(80, 80));
                GradientDrawable bg = new GradientDrawable();
                bg.setColor(PRESET_COLORS[position]);
                bg.setCornerRadius(12);
                bg.setStroke(2, Color.parseColor("#333333"));
                colorView.setBackground(bg);
                return colorView;
            }
        });
        gridView.setPadding(16, 16, 16, 16);

        builder.setView(gridView);
        AlertDialog dialog = builder.create();

        gridView.setOnItemClickListener((parent, view, position, id) -> {
            int color = PRESET_COLORS[position];
            target.setBackgroundColor(color);
            SharedPreferences sp = getSharedPreferences("Cookie", Context.MODE_PRIVATE);
            sp.edit().putInt(prefKey, color).commit();
            sendBroadcast(new Intent("com.junruo.jiankong.ACTION_UPDATE_DISPLAY"));
            dialog.dismiss();
        });

        dialog.show();
    }

    private void OnClick() {

        binding.stop.setOnClickListener(v -> {//点击开始
            if (binding.cookie.getText().toString().equals("")){
                toast("请填写cookie后再登录！");
            }else {
                cookie = binding.cookie.getText().toString();
                //开始获取数据
                update();
            }

        });


        binding.join.setOnClickListener(v -> {
            //加群代码

            String key = "QE-4bRJCa2w2Tu4lnWNuRBx4NqlIL8Op";
            Intent intent = new Intent();
            intent.setData(Uri.parse("mqqopensdkapi://bizAgent/qm/qr?url=http%3A%2F%2Fqm.qq.com%2Fcgi-bin%2Fqm%2Fqr%3Ffrom%3Dapp%26p%3Dandroid%26jump_from%3Dwebapi%26k%3D" + key));
            // 此Flag可根据具体产品需要自定义，如设置，则在加群界面按返回，返回手Q主界面，不设置，按返回会返回到呼起产品界面    //intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                startActivity(intent);
                toast("正在加群.....");
                return ;
            } catch (Exception e) {
                // 未安装手Q或安装的版本不支持
                toast("未安装手Q或安装的版本不支持.");
                return ;
            }

        });


        binding.get.setOnClickListener(v -> {
            Intent intent2 = new Intent(this,GetCookieActivity.class);
            AlertDialog alertDialog2 = new AlertDialog.Builder(MainActivity.this)
                    .setTitle("警告")
                    .setMessage("本功能为第三方支持，本软件不会保存此功能获取的所有返回信息，并在退出第三方获取cookie时，清除所有页面缓存信息，有更好的工具可以找我进行添加，介意者请自行通过抓包的方式获取cookie！")
                    .setIcon(R.mipmap.ic_launcher)
                    .setPositiveButton("我已知晓", new DialogInterface.OnClickListener() {//添加"Yes"按钮
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            startActivity(intent2);
                        }
                    })

                    .setNegativeButton("不同意", new DialogInterface.OnClickListener() {//添加取消
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                        }
                    })
                    .create();
            alertDialog2.show();

        });


        binding.clear.setOnClickListener(v -> {
            binding.cookie.setText("");
            toast("已清除");
        });

        binding.paste.setOnClickListener(v -> {
            String clipboardContent = getClipboardContent(this);
            binding.cookie.setText(clipboardContent);
            toast("已粘贴");

        });

        binding.usehelp.setOnClickListener(v -> {

            AlertDialog alertDialog2 = new AlertDialog.Builder(MainActivity.this)
                    .setTitle("使用帮助")
                    .setMessage(gonggao)
                    .setIcon(R.mipmap.ic_launcher)
                    .setPositiveButton("确定", new DialogInterface.OnClickListener() {//添加"Yes"按钮
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {

                        }
                    })

                    .create();
            alertDialog2.show();

        });

        binding.noticecv.setOnClickListener(v -> {
            AlertDialog alertDialog2 = new AlertDialog.Builder(MainActivity.this)
                    .setTitle("公告")
                    .setMessage(notice)
                    .setIcon(R.mipmap.ic_launcher)
                    .setPositiveButton("确定", new DialogInterface.OnClickListener() {//添加"Yes"按钮
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {

                        }
                    })

                    .create();
            alertDialog2.show();
        });

        binding.zhichi.setOnClickListener(v -> {
            Intent intent =new Intent(this, payActivity.class);

            AlertDialog alertDialog2 = new AlertDialog.Builder(MainActivity.this)
                    .setTitle("赞助")
                    .setMessage("每一次赞助都承载的希望，如果您觉得本软件还不错的话，您可以随意赞助。当然，也不会因为没有人赞助而添加会员机制。\n因为本软件永久免费。")
                    .setIcon(R.mipmap.ic_launcher)
                    .setPositiveButton("赞助", new DialogInterface.OnClickListener() {//添加"Yes"按钮
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            startActivity(intent);
                            toast("感谢您对本软件的支持！");
                        }
                    })
                    .setNegativeButton("算了", new DialogInterface.OnClickListener() {//添加"no"按钮
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            toast("嘤嘤嘤");
                        }
                    })

                    .create();
            alertDialog2.show();

        });

    }

    //获取剪贴板内容
    public static String getClipboardContent(Context context) {
        ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            ClipData data = cm.getPrimaryClip();
            if (data != null && data.getItemCount() > 0) {
                ClipData.Item item = data.getItemAt(0);
                if (item != null) {
                    CharSequence sequence = item.coerceToText(context);
                    if (sequence != null) {
                        return sequence.toString();
                    }
                }
            }
        }
        return null;
    }


    //版本更新检查 - 移除失效的第三方接口
    private void upupup() {
        gonggao = "本软件永久免费，请加群获取最新版本。QQ群：914950367";
        notice = "";
        // 获取Cookie的地址改为联通官方登录页
        getcookie = "https://m.client.10010.com/";
        SharedPreferences share = getSharedPreferences("gonggao", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = share.edit();
        editor.putString("gonggao", gonggao);
        editor.putString("getcookie", getcookie);
        editor.commit();
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public void startFloatingButtonService(View view) {
        if (binding.xfc.getText().toString().equals("显示悬浮窗")){

            if (binding.cookie.getText().toString().equals("")){
                toast("请填写cookie后再开启悬浮窗！");
            }else {
                if (FloatingImageDisplayService.isStarted) {
                    binding.xfc.setText("关闭悬浮窗");
                    return;
                }
                if (!Settings.canDrawOverlays(this)) {
                    Toast.makeText(this, "当前无权限，请授权", Toast.LENGTH_SHORT);
                    startActivityForResult(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())), 1);
                } else {
                    if (binding.shuaxin.getText().toString().equals("")){
                        toast("请输入刷新时间后开启悬浮窗。");
                    }else {
                        // 创建SharedPreferences对象用于存储Cookie信息,并将其私有化
                        SharedPreferences share = getSharedPreferences("Cookie",
                                Context.MODE_PRIVATE);
                        // 获取编辑器来存储数据到sharedpreferences中
                        SharedPreferences.Editor editor = share.edit();
                        editor.putString("time",binding.shuaxin.getText().toString());
                        editor.putString("Cookie",binding.cookie.getText().toString());
                        editor.putString("gao",binding.gao.getText().toString());
                        editor.putString("kuan",binding.kuan.getText().toString());
                        editor.putString("xgao",binding.xgao.getText().toString());
                        editor.putString("xkuan",binding.xkuan.getText().toString());
                        // 保存悬浮窗显示项目设置
                        editor.putBoolean("show_mian", binding.cbMian.isChecked());
                        editor.putBoolean("show_zong", binding.cbZong.isChecked());
                        editor.putBoolean("show_yong", binding.cbYong.isChecked());
                        editor.putBoolean("show_sheng", binding.cbSheng.isChecked());
                        editor.putBoolean("show_ben", binding.cbBen.isChecked());
                        editor.putBoolean("show_tiao", binding.cbTiao.isChecked());
                        editor.commit();
                        binding.xfc.setText("关闭悬浮窗");
                        // 通知悬浮窗服务刷新显示设置
                        sendBroadcast(new Intent("com.junruo.jiankong.ACTION_UPDATE_DISPLAY"));

                        Intent intent = new Intent(MainActivity.this, FloatingImageDisplayService.class);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            startForegroundService(intent);
                        } else {
                            startService(intent);
                        }


                    }
                }
            }

        }  else {

            Intent intent = new Intent(MainActivity.this, FloatingImageDisplayService.class);
            stopService(intent);

            binding.xfc.setText("显示悬浮窗");

        }

    }



    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1) {
            if (!Settings.canDrawOverlays(this)) {
            } else {
                Toast.makeText(this, "授权成功", Toast.LENGTH_SHORT).show();
                //startService(new Intent(MainActivity.this, FloatingImageDisplayService.class));
            }
        }
    }



    //更新数据
    private void update(){

        //初始化数据防止累加
        mianliu=0.00;//总免流
        zong=0.00;//套餐总量
        yong=0.00;//套餐已用
        sheng =0.00;//剩余流量
        dingz = 0.00;//定向总量
        dingy = 0.00;//定向已用
        dings = 0.00;//定向剩余

        dayin = "";
        binding.dayin.setText("");
        //格式化double值
        DecimalFormat df = new DecimalFormat("0.000");
        Date day=new Date();
        SimpleDateFormat sj = new SimpleDateFormat("HH:mm:ss");
        binding.sj.setText(sj.format(day));

        try {
            OkHttpUtils.post("https://m.client.10010.com/servicequerybusiness/operationservice/queryOcsPackageFlowLeftContentRevisedInJune")
                    .headers("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
                    .headers("Cookie", cookie)
                    .execute(new StringCallback() {
                        @Override
                        public void onError(Call call, Response response, Exception e) {
                            super.onError(call, response, e);
                            Toast.makeText(MainActivity.this,e.getMessage(),Toast.LENGTH_LONG).show();
                        }

                        @SuppressLint("LongLogTag")
                        @Override
                        public void onSuccess(String s, Call call, Response response) {
                            System.out.println(s);
                            // 1. null检查放最前面
                            if (s == null || s.isEmpty() || s.equals("999999")){
                                toast("失败，请重新获取cookie");
                                return;
                            }
                            // 2. HTML检测
                            if (!s.trim().startsWith("{")) {
                                toast("服务器异常，请稍后再试");
                                return;
                            }
                            toast("正在解析");
                            JSONObject json = JSONObject.parseObject(s);
                            if (json == null) {
                                toast("返回数据为空");
                                return;
                            }

                            Object pkgName = json.get("packageName");
                            if (pkgName != null) {
                                binding.packageName.setText(pkgName.toString());
                            } else {
                                binding.packageName.setText("未知套餐");
                            }

                            // 3. 解析全部流量数据
                            mianliu = 0.00;
                            yong = 0.00;
                            zong = 0.00;
                            sheng = 0.00;
                            dingz = 0.00;
                            dingy = 0.00;
                            dings = 0.00;

                            try {
                                // 先尝试从 summary 取总免（旧接口兼容）
                                JSONObject summary = json.getJSONObject("summary");
                                if (summary != null) {
                                    String freeFlow = summary.getString("freeFlow");
                                    if (freeFlow != null && !freeFlow.isEmpty() && !freeFlow.equals("0") && !freeFlow.equals("0.00")) {
                                        mianliu = Double.parseDouble(freeFlow);
                                    }
                                }

                                // resources（套内流量，可能多张卡）
                                JSONArray jsonArray = json.getJSONArray("resources");
                                if (jsonArray != null) {
                                    for (int j = 0; j < jsonArray.size(); j++) {
                                    JSONObject job = jsonArray.getJSONObject(j);
                                    String cardName = job.getString("packageName");
                                    System.out.println("卡" + j + ": " + cardName);
                                    JSONArray details = job.getJSONArray("details");
                                    if (details != null) {
                                        for (int i = 0; i < details.size(); i++) {
                                            try {
                                                JSONObject liuliang = details.getJSONObject(i);
                                                String limited = liuliang.getString("limited");
                                                String addupItemCode = liuliang.getString("addupItemCode");
                                                String use = liuliang.getString("use");
                                                String feePolicyName = liuliang.getString("feePolicyName");

                                                if (use == null) continue;

                                                String total = liuliang.getString("total");
                                                String remain = liuliang.getString("remain");

                                                if ("40008".equals(addupItemCode)) {
                                                    // 定向包（包括钉钉免流、联通云盘等）
                                                    double totalVal = safeDouble(total);
                                                    dingz = dingz + totalVal;
                                                    dingy = dingy + Double.parseDouble(use);
                                                    String remainDisplay = totalVal == 0 ? "不限" : safeStr(remain) + "M";
                                                    dings = totalVal == 0 ? dings : dings + safeDouble(remain);
                                                    if (mianliu == 0.00) mianliu = mianliu + Double.parseDouble(use); // summary无值时，定向已用计入总免
                                                    dayin = dayin + "\n定向包：" + safeStr(feePolicyName) + " 总量：" + (totalVal == 0 ? "不限" : safeStr(total) + "M") + "，已用：" + use + "M，剩余：" + remainDisplay + "\n";
                                                } else if ("0".equals(limited)) {
                                                    // 通用包
                                                    if (total == null || remain == null) continue;
                                                    zong = zong + Double.parseDouble(total);
                                                    yong = yong + Double.parseDouble(use);
                                                    sheng = sheng + Double.parseDouble(remain);
                                                    dayin = dayin + "\n通用包：" + safeStr(feePolicyName) + " 总量：" + total + "M，已用：" + use + "M，剩余：" + remain + "M\n";
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
                                                        dayin = dayin + "\n其他免流：" + safeStr(ml.getString("feePolicyName")) + " 已用：" + mlUse + "M\n";
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
                                toast("数据解析异常，请重试");
                            }

                            if (orone.equals("yes")){
                                // 先检查悬浮窗是否已经保存了起始值
                                SharedPreferences share = getSharedPreferences("Cookie", Context.MODE_PRIVATE);
                                long savedTime = share.getLong("onem_time", 0);
                                // 10分钟内的起始值有效，直接用
                                if (savedTime > 0 && System.currentTimeMillis() - savedTime < 600000) {
                                    onet = (double) share.getFloat("onet", 0f);
                                    onem = (double) share.getFloat("onem", 0f);
                                } else {
                                    onet = yong;
                                    onem = mianliu;
                                }
                                orone="no";
                                SharedPreferences.Editor editor = share.edit();
                                editor.putString("Cookie",cookie);
                                editor.putFloat("onem", onem.floatValue());
                                editor.putFloat("onet", onet.floatValue());
                                editor.putLong("onem_time", System.currentTimeMillis());
                                editor.commit();
                            }else {
                                //toast("不是第一次");
                            }

                            ben = mianliu - onem;//本次免流
                            if (ben >= 1024.00){//流量大于1024m将使用G来表示
                                ben = ben / 1024.00;

                                binding.ben.setText(df.format(ben)+"G");
                            }else {
                                binding.ben.setText(df.format(ben)+"M");
                            }

                            tiao = yong - onet;//本次消耗
                            if (tiao >= 1024.00){//流量大于1024m将使用G来表示
                                tiao = tiao / 1024.00;

                                binding.tiao.setText(df.format(tiao)+"G");
                            }else {
                                binding.tiao.setText(df.format(tiao)+"M");
                            }


                            if (mianliu >= 1024.00){//流量大于1024m将使用G来表示
                                mianliu = mianliu / 1024.00;

                                binding.mian.setText(df.format(mianliu)+"G");
                            }else {
                                binding.mian.setText(df.format(mianliu)+"M");
                            }

                            //套餐
                            if (zong >= 1024.00){//流量大于1024m将使用G来表示
                                zong = zong / 1024.00;

                                binding.zong.setText(df.format(zong)+"G");
                            }else {
                                binding.zong.setText(df.format(zong)+"M");
                            }


                            if (yong >= 1024.00){//流量大于1024m将使用G来表示
                                yong = yong / 1024.00;

                                binding.yong.setText(df.format(yong)+"G");
                            }else {
                                binding.yong.setText(df.format(yong)+"M");
                            }


                            if (sheng >= 1024.00){//流量大于1024m将使用G来表示
                                sheng = sheng / 1024.00;

                                binding.sheng.setText(df.format(sheng)+"G");
                            }else {
                                binding.sheng.setText(df.format(sheng)+"M");
                            }

                            //定向
                            if (dingz >= 1024.00){//流量大于1024m将使用G来表示
                                dingz = dingz / 1024.00;

                                binding.dingz.setText(df.format(dingz)+"G");
                            }else {
                                binding.dingz.setText(df.format(dingz)+"M");
                            }


                            if (dingy >= 1024.00){//流量大于1024m将使用G来表示
                                dingy = dingy / 1024.00;

                                binding.dingy.setText(df.format(dingy)+"G");
                            }else {
                                binding.dingy.setText(df.format(dingy)+"M");
                            }


                            if (dings >= 1024.00){//流量大于1024m将使用G来表示
                                dings = dings / 1024.00;

                                binding.dings.setText(df.format(dings)+"G");
                            }else {
                                binding.dings.setText(df.format(dings)+"M");
                            }

                            binding.dayin.setText(dayin);

                            toast("解析成功");
                            binding.stop.setText("刷新");

                      /*  if (code.equals("200")){
                            //toast("获取用户信息成功！");
                            JSONObject data = JSONArray.parseObject(json.get("data").toString());

                        }else {
                            toast("未知错误");
                        }*/

                        }
                    });
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    // 安全取字符串，null 返回空串
    static String safeStr(String s) {
        return s == null ? "" : s;
    }

    // 安全取double，null 返回0
    static double safeDouble(String s) {
        if (s == null) return 0.0;
        try { return Double.parseDouble(s); } catch (Exception e) { return 0.0; }
    }

    //自定义吐司
    void toast(String msg) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
            }
        });
    }
}