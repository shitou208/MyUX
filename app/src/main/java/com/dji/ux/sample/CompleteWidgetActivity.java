package com.dji.ux.sample;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.Transformation;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.dji.mapkit.core.maps.DJIMap;
import com.dji.mapkit.core.models.DJILatLng;
import com.yarolegovich.lovelydialog.LovelyStandardDialog;
import com.yarolegovich.lovelydialog.LovelyTextInputDialog;

import ch.ielse.view.SwitchView;
import dji.sdk.base.BaseProduct;
import dji.sdk.products.Aircraft;
import dji.sdk.sdkmanager.DJISDKManager;
import dji.ux.widget.FPVWidget;
import dji.ux.widget.MapWidget;

/** Activity that shows all the UI elements together */
public class CompleteWidgetActivity extends Activity {

    private MapWidget mapWidget;
    private ViewGroup parentView;
    private FPVWidget fpvWidget;
    private FPVWidget secondaryFPVWidget;
    private FrameLayout secondaryVideoView;
    private boolean isMapMini = true;

    private int height;
    private int width;
    private int margin;
    private int deviceWidth;
    private int deviceHeight;

    Button btn_setip;
    SwitchView switchView;
    private String ipHost="none";
    private int portNum = 0;
    private Client mClient;
    private BaseProduct mProduct;

    /**Handler**/
    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 1:
                    showNormalDialog(msg.getData().getString("rece"));
                    break;
            }
            super.handleMessage(msg);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_default_widgets);

        height = DensityUtil.dip2px(this, 100);
        width = DensityUtil.dip2px(this, 150);
        margin = DensityUtil.dip2px(this, 12);

        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        deviceHeight = displayMetrics.heightPixels;
        deviceWidth = displayMetrics.widthPixels;

        mapWidget = (MapWidget) findViewById(R.id.map_widget);
        mapWidget.initAMap(new MapWidget.OnMapReadyListener() {
            @Override
            public void onMapReady(@NonNull DJIMap map) {
                map.setOnMapClickListener(new DJIMap.OnMapClickListener() {
                    @Override
                    public void onMapClick(DJILatLng latLng) {
                        onViewClick(mapWidget);
                    }
                });
            }
        });
        mapWidget.onCreate(savedInstanceState);

        parentView = (ViewGroup) findViewById(R.id.root_view);
        btn_setip = (Button) findViewById(R.id.btn_setip);

        fpvWidget = findViewById(R.id.fpv_widget);
        fpvWidget.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onViewClick(fpvWidget);
            }
        });
        secondaryVideoView = (FrameLayout) findViewById(R.id.secondary_video_view);
        secondaryFPVWidget = findViewById(R.id.secondary_fpv_widget);
        secondaryFPVWidget.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                swapVideoSource();
            }
        });
        switchView = (SwitchView)findViewById(R.id.toggle_button1) ;
        switchView.setOpened(true);
        switchView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean isOpened = switchView.isOpened();
                if(isOpened){//开
                    mClient = new Client((Aircraft) mProduct,portNum,ipHost,handler);
                    Toast.makeText(CompleteWidgetActivity.this,"开始发送数据",Toast.LENGTH_SHORT).show();
                }
                else{//关
                    mClient.stop();
                    Toast.makeText(CompleteWidgetActivity.this,"停止发送数据",Toast.LENGTH_SHORT).show();
                }
            }
        });
        btn_setip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showInputDialog();
            }
        });

        updateSecondaryVideoVisibility();
    }

    private void onViewClick(View view) {
        if (view == fpvWidget && !isMapMini) {
            resizeFPVWidget(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT, 0, 0);
            ResizeAnimation mapViewAnimation = new ResizeAnimation(mapWidget, deviceWidth, deviceHeight, width, height, margin);
            mapWidget.startAnimation(mapViewAnimation);
            isMapMini = true;
        } else if (view == mapWidget && isMapMini) {
            resizeFPVWidget(width, height, margin, 5);
            ResizeAnimation mapViewAnimation = new ResizeAnimation(mapWidget, width, height, deviceWidth, deviceHeight, 0);
            mapWidget.startAnimation(mapViewAnimation);
            isMapMini = false;
        }
    }

    private void resizeFPVWidget(int width, int height, int margin, int fpvInsertPosition) {
        RelativeLayout.LayoutParams fpvParams = (RelativeLayout.LayoutParams) fpvWidget.getLayoutParams();
        fpvParams.height = height;
        fpvParams.width = width;
        fpvParams.rightMargin = margin;
        fpvParams.bottomMargin = margin;
        fpvWidget.setLayoutParams(fpvParams);

        parentView.removeView(fpvWidget);
        parentView.addView(fpvWidget, fpvInsertPosition);
    }

    private void swapVideoSource() {
        if (secondaryFPVWidget.getVideoSource() == FPVWidget.VideoSource.SECONDARY) {
            fpvWidget.setVideoSource(FPVWidget.VideoSource.SECONDARY);
            secondaryFPVWidget.setVideoSource(FPVWidget.VideoSource.PRIMARY);
        } else {
            fpvWidget.setVideoSource(FPVWidget.VideoSource.PRIMARY);
            secondaryFPVWidget.setVideoSource(FPVWidget.VideoSource.SECONDARY);
        }
    }

    private void updateSecondaryVideoVisibility() {
        if (secondaryFPVWidget.getVideoSource() == null) {
            secondaryVideoView.setVisibility(View.GONE);
        } else {
            secondaryVideoView.setVisibility(View.VISIBLE);
        }

        initClient();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Hide both the navigation bar and the status bar.
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        mapWidget.onResume();
    }

    @Override
    protected void onPause() {
        mapWidget.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        mapWidget.onDestroy();
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mapWidget.onSaveInstanceState(outState);
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapWidget.onLowMemory();
    }

    private class ResizeAnimation extends Animation {

        private View mView;
        private int mToHeight;
        private int mFromHeight;

        private int mToWidth;
        private int mFromWidth;
        private int mMargin;

        private ResizeAnimation(View v, int fromWidth, int fromHeight, int toWidth, int toHeight, int margin) {
            mToHeight = toHeight;
            mToWidth = toWidth;
            mFromHeight = fromHeight;
            mFromWidth = fromWidth;
            mView = v;
            mMargin = margin;
            setDuration(300);
        }

        @Override
        protected void applyTransformation(float interpolatedTime, Transformation t) {
            float height = (mToHeight - mFromHeight) * interpolatedTime + mFromHeight;
            float width = (mToWidth - mFromWidth) * interpolatedTime + mFromWidth;
            RelativeLayout.LayoutParams p = (RelativeLayout.LayoutParams) mView.getLayoutParams();
            p.height = (int) height;
            p.width = (int) width;
            p.rightMargin = mMargin;
            p.bottomMargin = mMargin;
            mView.requestLayout();
        }
    }

//    private void showInputDialog(){
//        new LovelyTextInputDialog(this)
//                //.setTopColorRes(R.color.darkDeepOrange)
//                .setTitle("填写通信IP端口")
//                .setMessage("请按照'x.x.x.x:x'这种格式填写")
//                //.setIcon(R.drawable.ic_assignment_white_36dp)
//                .setInputFilter("填写格式错误", new LovelyTextInputDialog.TextFilter() {
//                    @Override
//                    public boolean check(String text) {
//                        //return text.matches("\\w+");
//                        //112.112.112.122:5698
//                        String[] ss = text.split(":");
//                        if(ss.length == 2){
//                            return ipCheck(ss[0]);
//                        }
//                        else{
//                            return false;
//                        }
//                    }
//                })
//                .setConfirmButton(android.R.string.ok, new LovelyTextInputDialog.OnTextInputConfirmListener() {
//                    @Override
//                    public void onTextInputConfirmed(String text) {
//                        String[] s = text.split(":");
//                        SharedPreferences share = CompleteWidgetActivity.super.getSharedPreferences("savedip", MODE_PRIVATE);//实例化
//                        SharedPreferences.Editor editor = share.edit();	//使处于可编辑状态
//                        editor.putString("host", s[0]);
//                        editor.putInt("port", Integer.parseInt(s[1]));	//设置保存的数据
//                        editor.commit();	//提交数据保存
//                        ipHost = s[0];
//                        portNum = Integer.parseInt(s[1]);
//                        Toast.makeText(CompleteWidgetActivity.this, "IP保存成功", Toast.LENGTH_SHORT).show();
//                    }
//                })
//                .show();
//
//    }
    private void showInputDialog() {
        /*@setView 装入一个EditView
         */
        final EditText editText = new EditText(CompleteWidgetActivity.this);
        editText.setHint("请按照'x.x.x.x:x'这种格式填写");
        AlertDialog.Builder inputDialog =
                new AlertDialog.Builder(CompleteWidgetActivity.this);
        inputDialog.setTitle("填写通信IP端口").setView(editText);
        inputDialog.setPositiveButton("确定",
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                        String[] ss = editText.getText().toString().split(":");
                        if(ss.length == 2){
                            if (ipCheck(ss[0])){
                                String[] s = ss;
                                SharedPreferences share = CompleteWidgetActivity.super.getSharedPreferences("savedip", MODE_PRIVATE);//实例化
                                SharedPreferences.Editor editor = share.edit();	//使处于可编辑状态
                                editor.putString("host", s[0]);
                                editor.putInt("port", Integer.parseInt(s[1]));	//设置保存的数据
                                editor.commit();	//提交数据保存
                                ipHost = s[0];
                                portNum = Integer.parseInt(s[1]);
                                Toast.makeText(CompleteWidgetActivity.this, "IP保存成功", Toast.LENGTH_SHORT).show();
                            }
                            else {
                                Toast.makeText(CompleteWidgetActivity.this,"输入格式错误!",Toast.LENGTH_SHORT).show();
                            }
                        }
                        else{
                            Toast.makeText(CompleteWidgetActivity.this,"输入格式错误!",Toast.LENGTH_SHORT).show();
                        }
//                        Toast.makeText(CompleteWidgetActivity.this,
//                                editText.getText().toString(),
//                                Toast.LENGTH_SHORT).show();
                    }
                }).show();
    }

    private void getsavedIP(){
        SharedPreferences share = super.getSharedPreferences("savedip", MODE_PRIVATE);
        int port = share.getInt("port", 0);// 如果没有值，则显示默认值0
        String host = share.getString("host", "none");//如果没有值，则显示默认值none
        if(port==0 && host.equals("none")){
            showInputDialog();
        }
        else {
            ipHost = host;
            portNum = port;
            System.out.println("host:" + ipHost+", portNum:"+port);
        }
    }
    /**
     * 判断IP地址的合法性，这里采用了正则表达式的方法来判断
     * return true，合法
     * */
    private boolean ipCheck(String text) {
        if (text != null && !text.isEmpty()) {
            // 定义正则表达式
            String regex = "^(1\\d{2}|2[0-4]\\d|25[0-5]|[1-9]\\d|[1-9])\\."
                    +"(1\\d{2}|2[0-4]\\d|25[0-5]|[1-9]\\d|\\d)\\."
                    +"(1\\d{2}|2[0-4]\\d|25[0-5]|[1-9]\\d|\\d)\\."
                    +"(1\\d{2}|2[0-4]\\d|25[0-5]|[1-9]\\d|\\d)$";
            // 判断ip地址是否与正则表达式匹配
            if (text.matches(regex)) {
                // 返回判断信息
                return true;
            } else {
                // 返回判断信息
                return false;
            }
        }
        return false;
    }
    private void showNormalDialog(String msg){
        /* @setIcon 设置对话框图标
         * @setTitle 设置对话框标题
         * @setMessage 设置对话框消息提示
         * setXXX方法返回Dialog对象，因此可以链式设置属性
         */
        final AlertDialog.Builder normalDialog =
                new AlertDialog.Builder(CompleteWidgetActivity.this);
        //normalDialog.setIcon(R.drawable.icon_dialog);
        normalDialog.setTitle("Message");
        normalDialog.setMessage(msg);
        normalDialog.setPositiveButton("ok",
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        //...To-do
                    }
                });
//        normalDialog.setNegativeButton("关闭",
//                new DialogInterface.OnClickListener() {
//                    @Override
//                    public void onClick(DialogInterface dialog, int which) {
//                        //...To-do
//                    }
//                });
        // 显示
        normalDialog.show();
    }
    private void initClient() {

        //System.out.println(getLocalIpAddress());
        mProduct =  DJISDKManager.getInstance().getProduct();
        //此处为通信测试时用，正常使用把true替换为后面的
        if (mProduct != null && mProduct.isConnected()) {
            mClient = new Client((Aircraft) mProduct,portNum,ipHost,handler);
            String str = mProduct instanceof Aircraft ? "DJIAircraft" : "DJIHandHeld";
            Toast.makeText(CompleteWidgetActivity.this,"Status: " + str + " connected",Toast.LENGTH_SHORT).show();
            getsavedIP();
        }
        else {
            //Toast.makeText(MainActivity.this,"no deviecs connected! ",Toast.LENGTH_SHORT).show();
            switchView.setVisibility(View.INVISIBLE);
            showNormalDialog("no device connected! ");
        }
    }

}
