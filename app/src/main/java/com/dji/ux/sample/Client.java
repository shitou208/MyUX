package com.dji.ux.sample;

import android.provider.Settings;
import android.support.annotation.NonNull;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.net.*;
import java.io.*;


import dji.common.battery.BatteryState;
import dji.common.camera.SystemState;
import dji.common.error.DJIError;
import dji.common.flightcontroller.Attitude;
import dji.common.flightcontroller.FlightControllerState;
import dji.common.flightcontroller.GPSSignalLevel;
import dji.common.flightcontroller.LocationCoordinate3D;
import dji.common.gimbal.GimbalState;
import dji.common.util.CommonCallbacks;
import dji.sdk.battery.Battery;
import dji.sdk.products.Aircraft;
import android.os.Handler;
import android.os.Bundle;
import android.os.Message;

import static java.lang.Thread.sleep;

public class Client {
    private static final String TAG = "MyClient";
//    private static final String HOST_NAME = "172.20.10.4";
//    private static final int PORT_NUM = 6666;

    private Aircraft mProduct;
    private int portNum;
    private String ipAddress;
    private boolean mStart = true;
    private Handler handler;//回传消息的handler
    private Boolean isRecording = false;
    public static String waypoint_longitude;
    public static String waypoint_latitude;
    public static String waypoint_altitude;


    public Client(Aircraft product,int port,String address,Handler mainHandler) {
        mProduct = product;
        portNum = port;
        ipAddress = address;
        handler = mainHandler;
        //String ip = getLocalIpAddress();

        final JSONObject jsonObject = new JSONObject();
        //JSONObject jsonObject = new JSONObject();
        mProduct.getFlightController().setStateCallback(new FlightControllerState.Callback(){
            @Override
            public void onUpdate(@NonNull FlightControllerState flightControllerState) {

                JSONObject GPSJson = new JSONObject();
                //String write_txt = "-- "+MainActivity.gettime()+" --"+"\n";
                try {
//                    String temp1 = String.valueOf(flightControllerState.getVelocityX())+","+String.valueOf(flightControllerState.getVelocityY())+","+String.valueOf(flightControllerState.getVelocityZ())+"\n";
//                    write_txt+=temp1;
//                    if (isRecording){
//                            MainActivity.writetxt(write_txt,true);
//                    }
                    if(flightControllerState.getGPSSignalLevel() != null) {
                        GPSSignalLevel gpsLevel = flightControllerState.getGPSSignalLevel();
                        GPSJson.put("gpsLevel", gpsLevel.toString());
                    }
                    if(flightControllerState.getAircraftLocation() != null) {
                        LocationCoordinate3D location = flightControllerState.getAircraftLocation();
//                        GPSJson.put("longitude", String.valueOf(location.getLongitude()));
//                        GPSJson.put("latitude", String.valueOf(location.getLatitude()));
//                        GPSJson.put("altitude", String.valueOf(location.getAltitude()));
                        GPSJson.put("longitude", location.getLongitude());
                        GPSJson.put("latitude", location.getLatitude());
                        GPSJson.put("altitude", location.getAltitude());

                        //执行waypoint的test代码
                        waypoint_longitude = String.valueOf(location.getLongitude());
                        waypoint_latitude = String.valueOf(location.getLatitude());
                        waypoint_altitude = String.valueOf(location.getAltitude());

//                        String temp2 = "longitude:"+String.valueOf(location.getLongitude())+",latitude:"+String.valueOf(location.getLatitude())+",altitude:"+String.valueOf(location.getAltitude())+"\n";
//                        write_txt+=temp2;
//                        if (isRecording){
//                            MainActivity.writetxt(write_txt,true);
//                        }
                    }
                    if(flightControllerState.getAttitude() != null) {
                        Attitude attitude = flightControllerState.getAttitude();
                        GPSJson.put("pitch", attitude.pitch);
                        GPSJson.put("roll", attitude.roll);
                        GPSJson.put("yaw", attitude.yaw);
                    }

                    GPSJson.put("velocityX", flightControllerState.getVelocityX());
                    GPSJson.put("velocityY", flightControllerState.getVelocityY());
                    GPSJson.put("velocityZ", flightControllerState.getVelocityZ());

                    // Update the values in GPS key
                    jsonObject.put("GPS", GPSJson);
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        List<Battery> batteries = mProduct.getBatteries();
        for(final Battery battery : batteries) {
            battery.setStateCallback(new BatteryState.Callback() {
                @Override
                public void onUpdate(BatteryState batteryState) {
                    JSONObject batteryJson = new JSONObject();
                    try {
                        batteryJson.put("BatteryEnergyRemainingPercent", batteryState.getChargeRemainingInPercent());
                        batteryJson.put("Voltage", batteryState.getVoltage());
                        batteryJson.put("Current", batteryState.getCurrent());

                        // Update the values in Battery key
                        jsonObject.put("Battery" + battery.getIndex(), batteryJson);
                    }
                    catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            });
        }

        mProduct.getGimbal().setStateCallback(new GimbalState.Callback() {
            @Override
            public void onUpdate(@NonNull GimbalState gimbalState) {
                JSONObject gimbalJson = new JSONObject();
                try {
                    if(gimbalState.getAttitudeInDegrees() != null) {
                        dji.common.gimbal.Attitude attitude = gimbalState.getAttitudeInDegrees();
                        gimbalJson.put("pitch", String.valueOf(attitude.getPitch()));
                        gimbalJson.put("roll", String.valueOf(attitude.getRoll()));
                        gimbalJson.put("yaw", String.valueOf(attitude.getYaw()));
                    }

                    // Update the values in Gimbal key
                    jsonObject.put("Gimbal", gimbalJson);
                }
                catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });

        mProduct.getCamera().setSystemStateCallback(new SystemState.Callback() {
            @Override
            public void onUpdate(@NonNull SystemState systemState) {
                JSONObject cameraJson = new JSONObject();
                try {
                    if(systemState.isRecording()) {
                        cameraJson.put("isRecording", "yes");
                        isRecording = true;
                        //sendMsg("start recording");
                    }
                    else {
                        cameraJson.put("isRecording", "no");
                        isRecording = false;
                        //sendMsg("stop recording");
                    }

                    // Update the values in Gimbal key
                    jsonObject.put("Camera", cameraJson);
                }
                catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });

//        new Thread(new Runnable() {
//            @Override
//            public void run() {
//                try {
//                    sleep(5000);
//                    MainActivity.writetxt(jsonObject.toString(),true);
//                    MainActivity.writetxt("\n",true);
//                    //sendMsg("drone data saved to UXSDK_log.txt");
//                }catch (Exception e){
//                    e.printStackTrace();
//                }
//            }
//        }).start();


        //test
        //final JSONObject jsonObject_t = testJson();

        // 发送数据的线程
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // Set up client socket
                    Socket socket = new Socket(ipAddress, portNum);

                    // Input and Output Streams
                    final DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                    while(mStart) {
                        try {
                            // Send the JsonObject every 2s
                            //Log.i(TAG, jsonObject_t.toString());
                            out.writeUTF(jsonObject.toString());

                            sleep(2000);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    //关闭socket，释放资源
                    out.close();
                    socket.close();
                }
                // Error Handling
                catch (UnknownHostException e) {
                    Log.e(TAG, "Don't know about host " + ipAddress);
                }
                catch (IOException e) {
                    Log.e(TAG, "Couldn't get I/O for the connection to " + ipAddress);
                    Log.e(TAG, "Maybe the Server is not online");
                    e.printStackTrace();
                }
                System.out.println("Send Thread run finish");
            }
        }).start();
        System.out.println("Send thread start……");

        // 接收数据的线程
        new Thread(new Runnable() {
            @Override
            public void run() {

//                ServerSocket serverSocket = null;
//                try {
//                    serverSocket = new ServerSocket(6666);
//                    serverSocket.setSoTimeout(1000000);
//                }catch (Exception e){
//
//                }
//
//                while(true)
//                {
//                    try
//                    {
//                        System.out.println("等待远程连接，端口号为：" + serverSocket.getLocalPort() + "...");
//                        Socket server = serverSocket.accept();
//                        System.out.println("远程主机地址：" + server.getRemoteSocketAddress());
//                        DataInputStream in = new DataInputStream(server.getInputStream());
//                        sendMsg(in.readUTF());
//                        //System.out.println(in.readUTF());
//                        server.close();
//                    }catch(SocketTimeoutException s)
//                    {
//                        System.out.println("Socket timed out!");
//                        break;
//                    }catch(IOException e)
//                    {
//                        e.printStackTrace();
//                        break;
//                    }
//                }

                try {
                    // Set up client socket
                    Socket socket = new Socket(ipAddress, 6665 );
                    //先发一条数据
                    final DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                    out.writeUTF("hello");

                    // 读Sock里面的数据
                    InputStream s = socket.getInputStream();
                    byte[] buf = new byte[1024];
                    int len = 0;
                    while (true) {
                        if ((len = s.read(buf)) != -1) {
                            System.out.println(new String(buf, 0, len));
                            sendMsg(new String(buf, 0, len));
                        }
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }

                System.out.println("Receive Thread run finish");
            }
        }).start();
        System.out.println("Receive thread start……");
        //MainActivity.writetxt("write log test: it is ok");
    }

    void stop() {
        mStart = false;
    }

    //起飞任务
    void takeoff(){
        mProduct.getFlightController().startTakeoff(new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError djiError) {
                //MainActivity.writetxt("\nTakeoff result: "+djiError.getDescription(),true);
            }
        });
    }

    // the test json string, only for test
    JSONObject testJson(){
        JSONObject jsonObject_test = new JSONObject();

        JSONObject GPSJson = new JSONObject();
        JSONObject batteryJson = new JSONObject();
        JSONObject gimbalJson = new JSONObject();
        try{
            GPSJson.put("gpsLevel", "10");
            GPSJson.put("longitude", String.valueOf("74.5"));
            GPSJson.put("latitude", String.valueOf("56.8"));
            GPSJson.put("altitude", String.valueOf("623.5"));
            GPSJson.put("pitch", String.valueOf("90"));
            GPSJson.put("roll", String.valueOf("90"));
            GPSJson.put("yaw", String.valueOf("90"));
            GPSJson.put("velocityX", String.valueOf("100"));
            GPSJson.put("velocityY", String.valueOf("100"));
            GPSJson.put("velocityZ", String.valueOf("100"));

            batteryJson.put("BatteryEnergyRemainingPercent", 80);
            batteryJson.put("Voltage", 30);
            batteryJson.put("Current", 30);

            gimbalJson.put("pitch", String.valueOf("90"));
            gimbalJson.put("roll", String.valueOf("90"));
            gimbalJson.put("yaw", String.valueOf("90"));

            // Update the values in Gimbal key
            jsonObject_test.put("Gimbal", gimbalJson);
            // Update the values in Battery key
            jsonObject_test.put("Battery1", batteryJson);
            // Update the values in GPS key
            jsonObject_test.put("GPS", GPSJson);
            // 发送app的IP端口给主机
            //jsonObject_test.put("AppHostIp",appipAddress+":6060");
        }
        catch (JSONException e) {
            e.printStackTrace();
        }
        return jsonObject_test;
    }

    private void sendMsg(String receive) {
        Message msg = new Message();
        msg.what = 1;
        //使用Bundle绑定数据
        Bundle bundleData = new Bundle();
        bundleData.putString("rece", receive);
        msg.setData(bundleData);
        handler.sendMessage(msg);
    }
}
