package com.dji.ux.sample;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.yarolegovich.lovelydialog.LovelyStandardDialog;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import dji.common.error.DJIError;
import dji.common.error.DJISDKError;
import dji.log.DJILog;
import dji.sdk.base.BaseComponent;
import dji.sdk.base.BaseProduct;
import dji.sdk.sdkmanager.DJISDKManager;

/** Main activity that displays three choices to user */
public class MainActivity extends Activity implements View.OnClickListener, PopupMenu.OnMenuItemClickListener {
    private static final String TAG = "MainActivity";
    private static final String LAST_USED_BRIDGE_IP = "bridgeip";
    private AtomicBoolean isRegistrationInProgress = new AtomicBoolean(false);
    private static boolean isAppStarted = false;
    private DJISDKManager.SDKManagerCallback registrationCallback = new DJISDKManager.SDKManagerCallback() {

        @Override
        public void onRegister(DJIError error) {
            isRegistrationInProgress.set(false);
            if (error == DJISDKError.REGISTRATION_SUCCESS) {
                DJISDKManager.getInstance().startConnectionToProduct();

                Toast.makeText(getApplicationContext(), "SDK registration succeeded!", Toast.LENGTH_LONG).show();
                showDialog("SDK registration succeeded!",true);
            } else {

                Toast.makeText(getApplicationContext(),
                               "SDK registration failed, check network and retry!",
                               Toast.LENGTH_LONG).show();
                showDialog("SDK registration failed, check network,please retry",false);
            }
        }
        @Override
        public void onProductDisconnect() {
            Toast.makeText(getApplicationContext(),
                           "product disconnect!",
                           Toast.LENGTH_LONG).show();
        }
        @Override
        public void onProductConnect(BaseProduct product) {
            Toast.makeText(getApplicationContext(),
                           "product connect!",
                           Toast.LENGTH_LONG).show();
        }

        @Override
        public void onComponentChange(BaseProduct.ComponentKey key,
                                      BaseComponent oldComponent,
                                      BaseComponent newComponent) {
            Toast.makeText(getApplicationContext(),
                           key.toString() + " changed",
                           Toast.LENGTH_LONG).show();

        }
    };

    public static boolean isStarted() {
        return isAppStarted;
    }
    private static final String[] REQUIRED_PERMISSION_LIST = new String[] {
        Manifest.permission.VIBRATE,
        Manifest.permission.INTERNET,
        Manifest.permission.ACCESS_WIFI_STATE,
        Manifest.permission.WAKE_LOCK,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_NETWORK_STATE,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.CHANGE_WIFI_STATE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.BLUETOOTH,
        Manifest.permission.BLUETOOTH_ADMIN,
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.RECORD_AUDIO
    };
    private static final int REQUEST_PERMISSION_CODE = 12345;
    private List<String> missingPermission = new ArrayList<>();
    private EditText bridgeModeEditText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        isAppStarted = true;
        findViewById(R.id.complete_ui_widgets).setOnClickListener(this);
        findViewById(R.id.bt_customized_ui_widgets).setOnClickListener(this);
        findViewById(R.id.bt_map_widget).setOnClickListener(this);
        TextView versionText = (TextView) findViewById(R.id.version);
        versionText.setText(getResources().getString(R.string.sdk_version, DJISDKManager.getInstance().getSDKVersion()));
        bridgeModeEditText = (EditText) findViewById(R.id.edittext_bridge_ip);
        bridgeModeEditText.setText(PreferenceManager.getDefaultSharedPreferences(this).getString(LAST_USED_BRIDGE_IP,""));
        bridgeModeEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH
                    || actionId == EditorInfo.IME_ACTION_DONE
                    || event != null
                    && event.getAction() == KeyEvent.ACTION_DOWN
                    && event.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
                    if (event != null && event.isShiftPressed()) {
                        return false;
                    } else {
                        // the user is done typing.
                        handleBridgeIPTextChange();
                    }
                }
                return false; // pass on to other listeners.
            }
        });
        bridgeModeEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (s != null && s.toString().contains("\n")) {
                    // the user is done typing.
                    // remove new line characcter
                    final String currentText = bridgeModeEditText.getText().toString();
                    bridgeModeEditText.setText(currentText.substring(0, currentText.indexOf('\n')));
                    handleBridgeIPTextChange();
                }
            }
        });
        checkAndRequestPermissions();
    }

    @Override
    protected void onDestroy() {
        DJISDKManager.getInstance().destroy();
        isAppStarted = false;
        super.onDestroy();
    }

    /**
     * Checks if there is any missing permissions, and
     * requests runtime permission if needed.
     */
    private void checkAndRequestPermissions() {
        // Check for permissions
        for (String eachPermission : REQUIRED_PERMISSION_LIST) {
            if (ContextCompat.checkSelfPermission(this, eachPermission) != PackageManager.PERMISSION_GRANTED) {
                missingPermission.add(eachPermission);
            }
        }
        // Request for missing permissions
        if (missingPermission.isEmpty()) {
            startSDKRegistration();
        } else {
            ActivityCompat.requestPermissions(this,
                                              missingPermission.toArray(new String[missingPermission.size()]),
                                              REQUEST_PERMISSION_CODE);
        }
    }

    /**
     * Result of runtime permission request
     */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // Check for granted permission and remove from missing list
        if (requestCode == REQUEST_PERMISSION_CODE) {
            for (int i = grantResults.length - 1; i >= 0; i--) {
                if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    missingPermission.remove(permissions[i]);
                }
            }
        }
        // If there is enough permission, we will start the registration
        if (missingPermission.isEmpty()) {
            startSDKRegistration();
        } else {
            Toast.makeText(getApplicationContext(), "Missing permissions! Will not register SDK to connect to aircraft.", Toast.LENGTH_LONG).show();
        }
    }

    private void startSDKRegistration() {
        if (isRegistrationInProgress.compareAndSet(false, true)) {
            AsyncTask.execute(new Runnable() {
                @Override
                public void run() {
                    DJISDKManager.getInstance().registerApp(MainActivity.this, registrationCallback);
                }
            });
        }
    }

    @Override
    public void onClick(View view) {
        Class nextActivityClass;

        int id = view.getId();
        if (id == R.id.complete_ui_widgets) {
            nextActivityClass = CompleteWidgetActivity.class;
        } else if (id == R.id.bt_customized_ui_widgets) {
            nextActivityClass = CustomizedWidgetsActivity.class;
        } else {
            //nextActivityClass = MapWidgetActivity.class;
            PopupMenu popup = new PopupMenu(this, view);
            popup.setOnMenuItemClickListener(this);
            Menu popupMenu = popup.getMenu();
            MenuInflater inflater = popup.getMenuInflater();
            inflater.inflate(R.menu.map_select_menu, popupMenu);
            popupMenu.findItem(R.id.here_map).setEnabled(isHereMapsSupported());
            popupMenu.findItem(R.id.google_map).setEnabled(isGoogleMapsSupported(this));
            popup.show();
            return;
        }

        Intent intent = new Intent(this, nextActivityClass);
        startActivity(intent);
    }

    @Override
    public boolean onMenuItemClick(MenuItem menuItem) {
        Intent intent = new Intent(this, MapWidgetActivity.class);
        int mapBrand = 0;
        switch (menuItem.getItemId()) {
            case R.id.here_map:
                mapBrand = 0;
                break;
            case R.id.google_map:
                mapBrand = 1;
                break;
            case R.id.amap:
                mapBrand = 2;
                break;
        }
        intent.putExtra(MapWidgetActivity.MAP_PROVIDER, mapBrand);
        startActivity(intent);
        return false;
    }

    public static boolean isHereMapsSupported() {
        String abi;

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            abi = Build.CPU_ABI;
        } else {
            abi = Build.SUPPORTED_ABIS[0];
        }
        DJILog.d(TAG, "abi=" + abi);

        //The possible values are armeabi, armeabi-v7a, arm64-v8a, x86, x86_64, mips, mips64.
        return abi.contains("arm");
    }

    public static boolean isGoogleMapsSupported(Context context) {
        GoogleApiAvailability googleApiAvailability = GoogleApiAvailability.getInstance();
        int resultCode = googleApiAvailability.isGooglePlayServicesAvailable(context);
        return resultCode == ConnectionResult.SUCCESS;
    }

    private void handleBridgeIPTextChange() {
        // the user is done typing.
        final String bridgeIP = bridgeModeEditText.getText().toString();

        if (!TextUtils.isEmpty(bridgeIP)) {
            DJISDKManager.getInstance().enableBridgeModeWithBridgeAppIP(bridgeIP);
            Toast.makeText(getApplicationContext(),"BridgeMode ON!\nIP: " + bridgeIP,Toast.LENGTH_SHORT).show();
            PreferenceManager.getDefaultSharedPreferences(this).edit().putString(LAST_USED_BRIDGE_IP,bridgeIP).apply();
        }
    }
    private void showDialog(String msg, final Boolean isRegSuc){
        new LovelyStandardDialog(this, LovelyStandardDialog.ButtonLayout.VERTICAL)
                .setTopColorRes(R.color.centerpoint_blue)
                .setButtonsColorRes(R.color.red)
                .setIcon(R.drawable.ic_airplanemode_active_white_36dp)
                .setTitle("Initialization")
                .setMessage(msg)
                .setCancelable(false)
                .setPositiveButton(android.R.string.ok, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        //Toast.makeText(getApplicationContext(), "positive clicked", Toast.LENGTH_SHORT).show();
                        if (isRegSuc){
                            Intent intent = new Intent(MainActivity.this, CompleteWidgetActivity.class);
                            startActivity(intent);
                            //MainActivity.this.finish();
                        }
                        else {
                            MainActivity.this.finish();
                        }
                    }
                })
                //.setNegativeButton(android.R.string.no, null)
                .show();
    }
    /***
     * 脱机调试，写日志文件,test demo
     * */
    public static void writetxt(String str,Boolean isAppend) {
        String filePath = null;
        boolean hasSDCard = Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED);
        if (hasSDCard) {
            filePath = Environment.getExternalStorageDirectory().toString() + File.separator + "UXSDK_log.txt";
        } else
            filePath = Environment.getDownloadCacheDirectory().toString() + File.separator + "UXSDK_log.txt";
        try {
            File file = new File(filePath);
            if (!file.exists()) {
                File dir = new File(file.getParent());
                dir.mkdirs();
                file.createNewFile();
            }
            FileOutputStream outStream = new FileOutputStream(file,isAppend); //(file, true),第二个参数为true是追加内容，false是覆盖
            outStream.write(str.getBytes());
            outStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public static String gettime(){
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("HH:mm:ss:SSS");// HH:mm:ss
        //获取当前时间
        Date date = new Date(System.currentTimeMillis());
        return simpleDateFormat.format(date);
    }
    public void showToast(String msg){
        Toast.makeText(this,msg,Toast.LENGTH_SHORT).show();
    }

    public static String getCurrentTimeStr(){
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String dateString = simpleDateFormat.format(new Date());
        return dateString;
    }
}
