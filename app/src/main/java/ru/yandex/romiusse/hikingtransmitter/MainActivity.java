package ru.yandex.romiusse.hikingtransmitter;

import static java.nio.channels.SelectionKey.OP_READ;
import static java.nio.channels.SelectionKey.OP_WRITE;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.card.MaterialCardView;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Iterator;

public class MainActivity extends AppCompatActivity {

    protected static byte[] bDataIn = new byte[2048];
    protected static byte[] bDataOut = new byte[2048];
    public final int AUDIO_RECORD_CODE = 1012;
    public static Button transmitterButton;
    public Button hotspotSettings;
    public Button wifiSettings;
    public Button disconnectButton;
    public MaterialCardView firstUser;
    public MaterialCardView secondUser;
    public static ScrollView startPage;
    public static LinearLayout workPage;
    public static LinearLayout errorPage;
    public static TextView distanceText;
    public static TextView stateText;
    public static boolean sendBroadcast = false;
    public static boolean isRunning = true;
    private String userNow = "UNKNOWN";
    private static String errorStatus = "OK";

    public static MaterialCardView anotherServerIsRunning;
    public static MaterialCardView serverIsNotRunning;
    public static MaterialCardView serverIsBusy;
    public static MaterialCardView serverError;
    public static Button goToStartPage;


    static AudioRecorder audioRecorder = new AudioRecorder();
    static AudioPlayer audioPlayer = new AudioPlayer();
    static TransmitterServer transmitterServer;
    static TransmitterClient transmitterClient;


    public boolean checkPermission(String permission, int requestCode)
    {
        // Checking if permission is not granted
        if (ContextCompat.checkSelfPermission(MainActivity.this, permission) == PackageManager.PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(MainActivity.this, new String[] { permission }, requestCode);
            return false;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults)
    {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == AUDIO_RECORD_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                audioRecorder.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                Log.println(Log.ERROR, "LOG", ">>> Mic Permission Granted");
            } else {
                Toast.makeText(MainActivity.this, ">>> Mic Permission Denied", Toast.LENGTH_SHORT).show();
                Log.println(Log.ERROR, "LOG", ">>> Mic Permission Denied");
                //checkPermission(Manifest.permission.RECORD_AUDIO, AUDIO_RECORD_CODE);
            }
        }
    }


    private static class UdpServer extends AsyncTask<Void, String, Void> {


        private void startServer(){

            transmitterServer = new TransmitterServer();
            transmitterServer.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

            long lastUpdate = 0;
            String isReceiving = "NOT_RECEIVING";
            String distance = "";
            String state = "";
            String isHandshaked = "NOT_HANDSHAKED";
            String isClientConnected = "NOT_CONNECTED";

            while(isRunning) {
                bDataOut = audioRecorder.getMicData();
                transmitterServer.setIsSendBroadcastButton(sendBroadcast);
                transmitterServer.setMicOutData(bDataOut);
                bDataIn = transmitterServer.getMicInData();
                audioPlayer.setMicData(bDataIn);

                if(transmitterServer.getIsReceiving()) isReceiving = "RECEIVING";
                else isReceiving = "NOT_RECEIVING";

                if(!transmitterServer.isHandshaked() && !transmitterServer.isClientConnected()) state = "Жду подключение...";
                else if(transmitterServer.isHandshaked() && !transmitterServer.isClientConnected()) state = "Потеря связи. Переподключаюсь...";
                else if(transmitterServer.isHandshaked() && transmitterServer.isClientConnected()) state = "Канал связи установлен";

                distance = "Примерное расстояние: " + transmitterServer.getDistance() + " метров";

                if(System.currentTimeMillis() - lastUpdate > 100) {
                    publishProgress(isReceiving, distance, state);
                    lastUpdate = System.currentTimeMillis();
                }
            }

        }

        protected Void doInBackground(Void... params) {

            FindServer findServer = new FindServer();
            String status = findServer.find();
            Log.println(Log.ERROR, "SERVER_STATUS", status);
            if(status.equals("SERVER_IS_NOT_RUNNING")){
                errorStatus = "OK";
                startServer();
            }
            else{
                errorStatus = "ANOTHER_SERVER_IS_RUNNING";
            }

            if(transmitterServer != null) transmitterServer.stopServer();
            return null;

        }
        protected void onPostExecute(Void result) {
            Log.println(Log.ERROR, "LOG", "UDP Server closed");

            if(errorStatus.equals("ANOTHER_SERVER_IS_RUNNING")) errorView();
            else initView();
        }
        protected void onProgressUpdate(String... state) {

            transmitterButton.setEnabled(!state[0].equals("RECEIVING"));
            distanceText.setText(state[1]);
            stateText.setText(state[2]);
        }
    }


    static WifiManager wifiManager;
    private static class UdpClient extends AsyncTask<Void, String, Void> {

        String serverIp = "192.168.43.255";


        private long lastTimeUpdate = 0;

        private double calcDistance(){
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            double exp = 0;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                exp = (27.55 - (20 * Math.log10(wifiInfo.getFrequency())) + Math.abs(wifiInfo.getRssi())) / 20.0;
            }
            return Math.pow(10.0, exp);
        }



        private void startClient(){

            long lastUpdate = 0;
            String isReceiving = "NOT_RECEIVING";
            String state = "";
            String distance = "";
            String isHandshaked = "NOT_HANDSHAKED";
            String isClientConnected = "NOT_CONNECTED";

            transmitterClient = new TransmitterClient(serverIp);
            transmitterClient.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

            while(isRunning) {
                bDataOut = audioRecorder.getMicData();
                transmitterClient.setIsSendBroadcastButton(sendBroadcast);
                transmitterClient.setMicOutData(bDataOut);
                bDataIn = transmitterClient.getMicInData();
                audioPlayer.setMicData(bDataIn);

                if(System.currentTimeMillis() - lastTimeUpdate > 2000) {
                    double d = calcDistance();
                    distance = "Примерное расстояние: " + d + " метров";
                    transmitterClient.setDistance(d);
                    lastTimeUpdate = System.currentTimeMillis();
                }


                if(transmitterClient.IsReceiving()) isReceiving = "RECEIVING";
                else isReceiving = "NOT_RECEIVING";

                if(!transmitterClient.isHandshakeSuccessfully() && !transmitterClient.isConnected()) state = "Ищу пользователя...";
                else if(transmitterClient.isHandshakeSuccessfully() && !transmitterClient.isConnected()) state = "Потеря связи. Переподключаюсь...";
                else if(transmitterClient.isHandshakeSuccessfully() && transmitterClient.isConnected()) state = "Канал связи установлен";



                if(System.currentTimeMillis() - lastUpdate > 300) {
                    publishProgress(isReceiving, distance, state);
                    lastUpdate = System.currentTimeMillis();
                }
            }
        }

        @Override
        protected Void doInBackground(Void... params)
        {
            FindServer findServer = new FindServer();
            String status = findServer.find();
            Log.println(Log.ERROR, "SERVER_STATUS", status);
            if(status.equals("SERVER_WAS_FOUND")){
                errorStatus = "OK";
                serverIp = findServer.getServerIP();
                if(serverIp != null) startClient();
                else errorStatus = "SERVER_IS_NOT_RUNNING";
            }
            else{
                //startClient();
                errorStatus = status;
            }
            if(transmitterClient != null) transmitterClient.stopClient();
            return null;
        }

        protected void onPostExecute(Void result)
        {
            if(errorStatus.equals("OK")) initView();
            else errorView();
        }

        protected void onProgressUpdate(String... state) {
            transmitterButton.setEnabled(!state[0].equals("RECEIVING"));
            distanceText.setText(state[1]);
            stateText.setText(state[2]);
        }
    };

    private static void initView(){
        startPage.setVisibility(View.VISIBLE);
        workPage.setVisibility(View.GONE);
        errorPage.setVisibility(View.GONE);

    }

    private void workView(){
        startPage.setVisibility(View.GONE);
        workPage.setVisibility(View.VISIBLE);
        errorPage.setVisibility(View.GONE);
        isRunning = true;

        stateText.setText("Настраиваю канал связи...");
        distanceText.setText("");

        switch (userNow) {
            case "START_SERVER":
                startServer();
                break;
            case "START_CLIENT":
                startClient();
                break;
        }
    }

    private static void errorView(){
        startPage.setVisibility(View.GONE);
        workPage.setVisibility(View.GONE);
        errorPage.setVisibility(View.VISIBLE);

        anotherServerIsRunning.setVisibility(View.GONE);
        serverIsNotRunning.setVisibility(View.GONE);
        serverIsBusy.setVisibility(View.GONE);
        serverError.setVisibility(View.GONE);

        switch (errorStatus) {
            case "ANOTHER_SERVER_IS_RUNNING":
                anotherServerIsRunning.setVisibility(View.VISIBLE);
                break;
            case "SERVER_IS_NOT_RUNNING":
                serverIsNotRunning.setVisibility(View.VISIBLE);
                break;
            case "SERVER_IS_BUSY":
                serverIsBusy.setVisibility(View.VISIBLE);
                break;
            case "SERVER_ERROR":
                serverError.setVisibility(View.VISIBLE);
                break;
        }


    }

    private void startServer(){

        new UdpServer().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private void startClient(){

        new UdpClient().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        distanceText = (TextView) findViewById(R.id.textDistance);
        stateText = (TextView) findViewById(R.id.stateText);

        startPage = (ScrollView) findViewById(R.id.startPage);
        workPage = (LinearLayout) findViewById(R.id.workPage);
        errorPage = (LinearLayout) findViewById(R.id.errorPage);

        anotherServerIsRunning = (MaterialCardView) findViewById(R.id.serverIsRunningError);
        serverIsNotRunning = (MaterialCardView) findViewById(R.id.serverIsNotRunningError);
        serverIsBusy = (MaterialCardView) findViewById(R.id.serverIsBusyError);
        serverError = (MaterialCardView) findViewById(R.id.serverError);

        goToStartPage = (Button) findViewById(R.id.goToStartPage);
        goToStartPage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                initView();
            }
        });

        disconnectButton = (Button) findViewById(R.id.disconnectButton);
        disconnectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                isRunning = false;
            }
        });

        firstUser = (MaterialCardView) findViewById(R.id.firstUserCard);
        firstUser.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                userNow = "START_SERVER";
                workView();
            }
        });


        secondUser = (MaterialCardView) findViewById(R.id.secondUserCard);
        secondUser.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                userNow = "START_CLIENT";
                workView();
            }
        });

        hotspotSettings = (Button) findViewById(R.id.hotspotSettings);
        hotspotSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    Intent tetherSettings = new Intent();
                    tetherSettings.setClassName("com.android.settings", "com.android.settings.TetherSettings");
                    startActivity(tetherSettings);
                }
            }
        });


        wifiSettings = (Button) findViewById(R.id.wifiSettings);
        wifiSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
            }
        });


        transmitterButton = (Button) findViewById(R.id.button);
        transmitterButton.setOnTouchListener((view, motionEvent) -> {
            switch(motionEvent.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    sendBroadcast = true;
                    break;
                case MotionEvent.ACTION_UP:
                    sendBroadcast = false;
                    break;
            }
            return false;
        });

        wifiManager = (WifiManager) this.getApplicationContext().getSystemService(Context.WIFI_SERVICE);

        if(checkPermission(Manifest.permission.RECORD_AUDIO, AUDIO_RECORD_CODE))
            audioRecorder.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        audioPlayer.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

        initView();

    }

}