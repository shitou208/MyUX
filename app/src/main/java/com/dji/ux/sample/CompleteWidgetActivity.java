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

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import ch.ielse.view.SwitchView;
import dji.common.error.DJIError;
import dji.common.mission.waypoint.Waypoint;
import dji.common.mission.waypoint.WaypointAction;
import dji.common.mission.waypoint.WaypointActionType;
import dji.common.mission.waypoint.WaypointMission;
import dji.common.mission.waypoint.WaypointMissionFinishedAction;
import dji.common.mission.waypoint.WaypointMissionFlightPathMode;
import dji.common.mission.waypoint.WaypointMissionGotoWaypointMode;
import dji.common.mission.waypoint.WaypointMissionHeadingMode;
import dji.common.mission.waypoint.WaypointMissionState;
import dji.common.util.CommonCallbacks;
import dji.sdk.base.BaseProduct;
import dji.sdk.mission.MissionControl;
import dji.sdk.mission.waypoint.WaypointMissionOperator;
import dji.sdk.mission.waypoint.WaypointMissionOperatorListener;
import dji.sdk.products.Aircraft;
import dji.sdk.sdkmanager.DJISDKManager;
import dji.ux.widget.FPVWidget;
import dji.ux.widget.MapWidget;

import static dji.internal.logics.CommonUtil.ONE_METER_OFFSET;

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

    //waypoint
    private WaypointMissionOperator waypointMissionOperator;
    private WaypointMission mission;
    private WaypointMissionOperatorListener listener;
    private ArrayList myPointList;
    private float flyAltitude;
    private String testJson = "";
    private AlertDialog wayPointDialog;

    /**Handler**/
    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 1:
                    String sss = msg.getData().getString("rece");
                    if(gettaskFlag(sss)==0){
                        mClient.takeoff();
                    }
                    else {
                        //showNormalDialog(sss);
                        MainActivity.writetxt("\nReceived WayPoints data:\n",true);
                        MainActivity.writetxt(sss+"\n",true);
                        myPointList = getWayPoints(sss);
                        MainActivity.writetxt("\nParse point list from json data:\n",true);
                        MainActivity.writetxt(myPointList+"\n",true);
//                    for(int k=0;k<myPointList.size();k++){
//                        MainActivity.writetxt(myPointList.get(k).toString()+"\n",true);
//                        showNormalDialog(myPointList.get(k).toString());
//                    }
                        execute_waypoint_task(0,myPointList);//load waypoint task
                        showNormalDialog("666");
                    }
                    //showNormalDialog(msg.getData().getString("rece"));
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
        MainActivity.writetxt("**************************\n",true);

        //test
//        myPointList = getWayPoints(js);
//        System.out.println("List: "+myPointList);
//        for(int i = 0 ; i < myPointList.size() ; i++){
//            System.out.println(myPointList.get(i));
//            showNormalDialog(myPointList.get(i).toString());
//            MainActivity.writetxt(myPointList.get(i).toString()+"\n",true);
//        }
        //延时弹窗，用于测试waypoint任务
//        createWayPointDialog();
//        final Handler handler2 = new Handler();
//        Runnable runnable2 = new Runnable() {
//            @Override
//            public void run() {
//                handler2.postDelayed(this, 60000);
//                wayPointDialog.show();
//                //timer_tv_2.setText("Timer2-->" + getSystemTime());
//                //mytext.setText(getDatePoor());
//            }
//        };
//        handler2.postDelayed(runnable2, 60000);
        //mytest();
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
    private void showNormalDialog(final String msg){
        /* @setIcon 设置对话框图标
         * @setTitle 设置对话框标题
         * @setMessage 设置对话框消息提示
         * setXXX方法返回Dialog对象，因此可以链式设置属性
         */
        final AlertDialog.Builder normalDialog =
                new AlertDialog.Builder(CompleteWidgetActivity.this);
        //normalDialog.setIcon(R.drawable.icon_dialog);
        normalDialog.setTitle("Message");
        normalDialog.setMessage(msg.replace("666","已经Load WayPoint任务，点击确定执行！"));
        normalDialog.setPositiveButton("ok",
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        //...To-do
                        if (msg.equals("666")){
                            //showResultToast("执行了waypoint任务");
                            execute_waypoint_task(1,myPointList);//start waypoint task
                        }
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

    //解析json数据
    /*** json 样例
     * {"0000Lat":29.9988,
     *  "0000Lng":106.52332,
     *  "0001Lat":29.3666,
     *  "0001Lng":106.669,
     *     .......
     *  "baseAltitude":20.0,
     *  "way_point_num":5
     * }
     * */
    private ArrayList getWayPoints(String jsons){
        //String[] rr = jsons.split("\\{");
        ArrayList points = new ArrayList();
        try {
            JSONObject jsonObject = new JSONObject(jsons);
            int nums = jsonObject.getInt("way_point_num");
            flyAltitude = Float.parseFloat(jsonObject.getString("altitude"));
            for (int i = 0; i< nums; i++) {
                double lat = jsonObject.getDouble(i+"Lat");
                double lng = jsonObject.getDouble(i+"Lng");
                points.add(lat+","+lng);
                //System.out.println("list add: "+lat+","+lng);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return points;
    }
    private int gettaskFlag(String jsons){
        int num = -1;
        try {
            JSONObject jsonObject = new JSONObject(jsons);
            num = jsonObject.getInt("mission");//0,takeoff; 1,waypoint
        } catch (Exception e) {
            e.printStackTrace();
        }
        return num;
    }

    //执行waypoint任务
    private void execute_waypoint_task(int flag,ArrayList pointList){ //0 load, 1 start
        if (waypointMissionOperator == null) {
            waypointMissionOperator = MissionControl.getInstance().getWaypointMissionOperator();
        }

        switch (flag) {
            case 0:
                // Example of loading a Mission
                System.out.println("**** load waypoint ****");
                mission = createWaypointMission(pointList);
                DJIError djiError = waypointMissionOperator.loadMission(mission);
                // Example of uploading a Mission
                if (WaypointMissionState.READY_TO_RETRY_UPLOAD.equals(waypointMissionOperator.getCurrentState())
                        || WaypointMissionState.READY_TO_UPLOAD.equals(waypointMissionOperator.getCurrentState())) {
                    waypointMissionOperator.uploadMission(new CommonCallbacks.CompletionCallback() {
                        @Override
                        public void onResult(DJIError djiError) {
                            showResultToast(djiError.getDescription());
                            MainActivity.writetxt("\nLoad result: "+djiError.getDescription(),true);
                        }
                    });
                } else {
                    showResultToast("Not ready!");
                }
                break;
            case 1:
                // Example of starting a Mission
                if (mission != null) {
                    waypointMissionOperator.startMission(new CommonCallbacks.CompletionCallback() {
                        @Override
                        public void onResult(DJIError djiError) {
                            showResultToast(djiError.getDescription());
                            MainActivity.writetxt("\nExecution result:"+djiError.getDescription(),true);
                        }
                    });
                } else {
                    showResultToast("Prepare Mission First!");
                }
                break;
        }
    }

    //起飞任务
//    private void takeoff(){
//        Aircraft myproduct = (Aircraft) mProduct;
//        myproduct.getFlightController().startPrecisionTakeoff(new CommonCallbacks.CompletionCallback() {
//            @Override
//            public void onResult(DJIError djiError) {
//                showResultToast(djiError.getDescription());
//                MainActivity.writetxt("\nTakeoff result: "+djiError.getDescription(),true);
//            }
//        });
//    }

    private void proData(String s){
        s=s.replace(" ","");
    }

    private void showResultToast(String msg){
        Toast.makeText(CompleteWidgetActivity.this,msg,Toast.LENGTH_SHORT).show();
    }

    private WaypointMission createWaypointMission(ArrayList pointList) {
        System.out.println("**** create waypoints ****");
        WaypointMission.Builder builder = new WaypointMission.Builder();
        List<Waypoint> waypointList = new ArrayList<>();
//        double baseLatitude = 22;
//        double baseLongitude = 113;
//        Object latitudeValue = KeyManager.getInstance().getValue((FlightControllerKey.create(HOME_LOCATION_LATITUDE)));
//        Object longitudeValue =
//                KeyManager.getInstance().getValue((FlightControllerKey.create(HOME_LOCATION_LONGITUDE)));
//        if (latitudeValue != null && latitudeValue instanceof Double) {
//            baseLatitude = (double) latitudeValue;
//        }
//        if (longitudeValue != null && longitudeValue instanceof Double) {
//            baseLongitude = (double) longitudeValue;
//        }

        final float baseAltitude = 20.0f;
        builder.autoFlightSpeed(3f);
        builder.maxFlightSpeed(10f);
        builder.setExitMissionOnRCSignalLostEnabled(false);
        builder.finishedAction(WaypointMissionFinishedAction.NO_ACTION);
        builder.flightPathMode(WaypointMissionFlightPathMode.NORMAL);
        builder.gotoFirstWaypointMode(WaypointMissionGotoWaypointMode.SAFELY);
        builder.headingMode(WaypointMissionHeadingMode.AUTO);
        builder.repeatTimes(1);
        System.out.println("pointlist size:"+pointList.size());
        for (int j=0;j<pointList.size();j++){
            String[] p = pointList.get(j).toString().split(",");
            System.out.println("out："+p[0]+","+p[1]);
            final Waypoint eachWaypoint = new Waypoint(Double.parseDouble(p[0]),Double.parseDouble(p[1]),flyAltitude);
            System.out.println("mission info: "+eachWaypoint.toString()+", altitude:"+eachWaypoint.altitude);
            MainActivity.writetxt("\ncreate mission:"+eachWaypoint.toString()+", altitude:"+eachWaypoint.altitude,true);
            waypointList.add(eachWaypoint);
        }



//        Random randomGenerator = new Random(System.currentTimeMillis());
//        List<Waypoint> waypointList = new ArrayList<>();
//        for (int i = 0; i < numberOfWaypoint; i++) {
//            final double variation = (Math.floor(i / 4) + 1) * 2 * ONE_METER_OFFSET;
//            final float variationFloat = (baseAltitude + (i + 1) * 2);
//            final Waypoint eachWaypoint = new Waypoint(baseLatitude + variation * Math.pow(-1, i) * Math.pow(0, i % 2),
//                    baseLongitude + variation * Math.pow(-1, (i + 1)) * Math.pow(0, (i + 1) % 2),
//                    variationFloat);
//            for (int j = 0; j < numberOfAction; j++) {
//                final int randomNumber = randomGenerator.nextInt() % 6;
//                switch (randomNumber) {
//                    case 0:
//                        eachWaypoint.addAction(new WaypointAction(WaypointActionType.STAY, 1));
//                        break;
//                    case 1:
//                        eachWaypoint.addAction(new WaypointAction(WaypointActionType.START_TAKE_PHOTO, 1));
//                        break;
//                    case 2:
//                        eachWaypoint.addAction(new WaypointAction(WaypointActionType.START_RECORD, 1));
//                        eachWaypoint.addAction(new WaypointAction(WaypointActionType.STOP_RECORD, 1));
//                        break;
//                    case 3:
//                        eachWaypoint.addAction(new WaypointAction(WaypointActionType.GIMBAL_PITCH,
//                                randomGenerator.nextInt() % 45 - 45));
//                        break;
//                    case 4:
//                        eachWaypoint.addAction(new WaypointAction(WaypointActionType.ROTATE_AIRCRAFT,
//                                randomGenerator.nextInt() % 180));
//                        break;
//                    default:
//                        eachWaypoint.addAction(new WaypointAction(WaypointActionType.START_TAKE_PHOTO, 1));
//                        break;
//                }
//            }
//            waypointList.add(eachWaypoint);
//        }

        builder.waypointList(waypointList).waypointCount(waypointList.size());
        return builder.build();
    }

    //设置弹窗dialog，用于测试waypoint任务
    private void createWayPointDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(CompleteWidgetActivity.this);
        builder.setTitle("WayPointTask");
        builder.setMessage("要执行WayPoint任务吗?");
        //点击对话框以外的区域是否让对话框消失
        builder.setCancelable(false);
        //设置正面按钮
        builder.setPositiveButton("是的", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                //Toast.makeText(context, "你点击了是的", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
                flyAltitude = Float.parseFloat(Client.waypoint_altitude);
                double lat = Float.parseFloat(Client.waypoint_latitude);
                double lng = Float.parseFloat(Client.waypoint_longitude);
                ArrayList tempList = new ArrayList();//longtitude经度，lat维度
                double lat1 = lat+0.0001;
                double lng1 = lng;
                double lat2 =lat1;
                double lng2 =lng1+0.0001;
                double lat3 =lat2-0.0001;
                double lng3 =lng2;
                double lat4 =lat3;
                double lng4 =lng3-0.0001;
                tempList.add(lat1+","+lng1); // point1
                tempList.add(lat2+","+lng2); // point2
                tempList.add(lat3+","+lng3); // point3
                tempList.add(lat4+","+lng4); // point4

                execute_waypoint_task( 0, tempList);//load waypoint task
                showNormalDialog("666");
                MainActivity.writetxt("WayPoints List: \n",true);
                MainActivity.writetxt(tempList.toString(),true);
            }
        });
        //设置反面按钮
        builder.setNegativeButton("不是", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                //Toast.makeText(context, "你点击了不是", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            }
        });

        wayPointDialog = builder.create();
        //显示对话框
        //dialog.show();
    }

    //写死json字符串，进行解析测试
    private void mytest(){
//        String sss = "{\n" +
//                "    \"0Lat\": 36.654081,\n" +
//                "    \"0Lng\": 117.033885,\n" +
//                "    \"altitude\": 8.800000190734863,\n" +
//                "    \"way_point_num\": 1\n" +
//                "}";
        String sss = "{\n" +
                "    \"0Lat\": 36.654081,\n" +
                "    \"0Lng\": 117.033885,\n" +
                "    \"1Lat\": 36.654023,\n" +
                "    \"1Lng\": 117.035753,\n" +
                "    \"altitude\": 8.800000190734863,\n" +
                "    \"way_point_num\": 2\n" +
                "}";

        //MainActivity.writetxt("Received WayPoints List:\n",true);
        //MainActivity.writetxt(sss+"\n\n",true);
        flyAltitude = 8.800000190734863f;
        myPointList = getWayPoints(sss);
        System.out.println("debug:"+myPointList);
//                    for(int k=0;k<myPointList.size();k++){
//                        MainActivity.writetxt(myPointList.get(k).toString()+"\n",true);
//                        showNormalDialog(myPointList.get(k).toString());
//                    }
        execute_waypoint_task(0,myPointList);//load waypoint task
        showNormalDialog("666");
    }

}