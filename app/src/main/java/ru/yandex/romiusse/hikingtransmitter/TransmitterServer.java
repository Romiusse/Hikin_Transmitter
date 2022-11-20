package ru.yandex.romiusse.hikingtransmitter;

import static java.nio.channels.SelectionKey.OP_READ;
import static java.nio.channels.SelectionKey.OP_WRITE;

import android.os.AsyncTask;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Arrays;
import java.util.Iterator;

public class TransmitterServer extends AsyncTask<Void, Void, Void>{

    private static final int BUF_SZ = 2048;
    private static final int PORT = 8888;

    private SelectionKey micClient = null;
    private Selector selector;
    private DatagramChannel channel;

    private boolean isReceiving = false;
    private boolean isBroadcasting = false;
    private boolean isSendBroadcastButton = false;
    private byte[] bDataOut = new byte[2048];
    private byte[] bDataIn = new byte[2048];
    private boolean isRunning = true;
    private long lastPulseInTime = 0;
    private static String distance = "0";
    private boolean isClientConnected = false;
    private boolean isHandshaked = false;

    public void setMicOutData(@NonNull byte[] data){
        bDataOut = data.clone();
    }
    public byte[] getMicInData(){
        return bDataIn.clone();
    }
    public boolean getIsReceiving(){
        return isReceiving;
    }

    public void stopServer(){
        isRunning = false;
    }

    public void setIsSendBroadcastButton(boolean data){
        isSendBroadcastButton = data;
    }
    public void getIsSendBroadcastButton(boolean data){
        isSendBroadcastButton = data;
    }

    public String getDistance() {
        return distance;
    }

    public boolean isClientConnected() {
        return isClientConnected;
    }

    public boolean isHandshaked() {
        return isHandshaked;
    }

    private static class Connection {
        ByteBuffer req;
        ByteBuffer resp;
        SocketAddress sa;

        public Connection() {
            req = ByteBuffer.allocate(BUF_SZ);
        }
    }

    private void read(@NonNull SelectionKey key) throws IOException {
        DatagramChannel chan = (DatagramChannel)key.channel();
        Connection con = (Connection)key.attachment();
        con.req.clear();
        con.req.put(new byte[2048]);
        con.req.clear();
        con.sa = chan.receive(con.req);
        String message = new String(con.req.array()).trim();
        //Log.println(Log.ERROR, "LOG", message);
        switch (message) {
            case "TRANSMITTER_IS_RUNNING":
                if (micClient == null)
                    write(key, "TRANSMITTER_SERVER_IS_RUNNING".getBytes());
                else
                    write(key, "TRANSMITTER_SERVER_IS_BUSY".getBytes());
                Log.println(Log.ERROR, "LOG", message);
                break;
            case "TRANSMITTER_CLIENT_CONNECTION_REQUEST":
                if (micClient == null) {
                    micClient = key;
                    isClientConnected = true;
                    isHandshaked = true;
                    write(micClient, "TRANSMITTER_SERVER_CONNECTION_RESPONSE".getBytes());
                } else {
                    write(key, "TRANSMITTER_SERVER_CONNECTION_IS_BUSY".getBytes());
                }
                Log.println(Log.ERROR, "LOG", message);
                break;
            case "TRANSMITTER_CLIENT_START_BROADCAST":
                isReceiving = true;
                Log.println(Log.ERROR, "LOG", message);
                break;
            case "TRANSMITTER_CLIENT_END_BROADCAST":
                isReceiving = false;
                Log.println(Log.ERROR, "LOG", message);
                break;
            case "TRANSMITTER_CLIENT_IM_ALIVE":
                Log.println(Log.ERROR, "LOG", message);
                break;
            case "TRANSMITTER_CLIENT_RETRY_CONNECTION":
                isClientConnected = true;
                Log.println(Log.ERROR, "LOG", "Client reconnected successful!");
                writeBroadcastStatus();
                Log.println(Log.ERROR, "LOG", message);
                break;
            default:
                if (isReceiving) receiveMicData(con);
                else if(message.charAt(0) == '!') receiveDistanceData(con);
                break;
        }

        if(micClient != null && !Arrays.equals(con.req.array(), new byte[2048])){
            lastPulseInTime = System.currentTimeMillis();
        }

        if (micClient == null) isClientConnected = true;
    }

    private void receiveDistanceData(Connection con){
        String data = new String(con.req.array()).trim();
        StringBuilder distanceString = new StringBuilder();
        for(int i = 1; i < data.length(); i++) {
            distanceString.append(data.charAt(i));
        }
        distance = distanceString.toString();

    }

    private void receiveMicData(Connection con){
        byte[] recvBuf = con.req.array();
        if(!Arrays.equals(recvBuf, bDataIn)){
            bDataIn = recvBuf.clone();
        }

    }


    private void write(SelectionKey key, byte[] data) throws IOException {
        if(key == null) return;
        DatagramChannel chan = (DatagramChannel) key.channel();
        Connection con = (Connection) key.attachment();
        ByteBuffer outBuf = ByteBuffer.allocate(2048);
        outBuf.put(data);
        chan.send(ByteBuffer.wrap(data), con.sa);
        //Log.println(Log.ERROR, "LOG", Arrays.toString(outBuf.array()));
    }

    byte[] oldBData = null;
    private void writeMic(SelectionKey key) throws IOException {
        if(micClient == null) return;
        if(!Arrays.equals(oldBData, bDataOut)) {
            DatagramChannel chan = (DatagramChannel) key.channel();
            Connection con = (Connection) key.attachment();
            Log.println(Log.ERROR, "LOG", Arrays.toString(bDataOut));
            chan.send(ByteBuffer.wrap(bDataOut), con.sa);
            oldBData = bDataOut.clone();
        }
    }

    private void writeBroadcastStatus() throws IOException {
        if(micClient == null) return;
        if(isBroadcasting) write(micClient, "TRANSMITTER_SERVER_START_BROADCAST".getBytes());
        else write(micClient, "TRANSMITTER_SERVER_END_BROADCAST".getBytes());
    }

    private void checkBroadcast() throws IOException {
        if(isSendBroadcastButton && !isBroadcasting){
            write(micClient, "TRANSMITTER_SERVER_START_BROADCAST".getBytes());
            isBroadcasting = true;
        }
        if(!isSendBroadcastButton && isBroadcasting){
            write(micClient, "TRANSMITTER_SERVER_END_BROADCAST".getBytes());
            isBroadcasting = false;
        }
    }

    private long lastPulseOutTime = 0;
    private void livePulse() throws IOException {
        if(System.currentTimeMillis() - lastPulseOutTime > 1000){
            lastPulseOutTime = System.currentTimeMillis();
            if(!isBroadcasting) {
                write(micClient, "TRANSMITTER_SERVER_IS_ALIVE".getBytes());
                Log.println(Log.ERROR, "LOG", "LIVE_PULSE");
            }
        }
    }

    private void checkLiveInPulse() throws IOException {
        if(micClient == null) return;

        if(System.currentTimeMillis() - lastPulseInTime > 5000){
            //Log.println(Log.ERROR, "LOG", "Client connection is lost!");
            isClientConnected = false;
            isReceiving = false;
            isBroadcasting = false;
            //TODO Make reset receive and broadcast settings
        }
    }

    private void init() throws IOException {
        selector = Selector.open();
        channel = DatagramChannel.open();
        InetSocketAddress isa = new InetSocketAddress(PORT);
        channel.socket().bind(isa);
        channel.configureBlocking(false);
        SelectionKey clientKey = channel.register(selector, OP_READ);
        clientKey.attach(new Connection());
    }

    protected Void doInBackground(Void... params) {
        try {
            init();
        } catch (IOException e) {
            e.printStackTrace();
        }
        while(isRunning) {
            try {

                livePulse();
                checkLiveInPulse();
                checkBroadcast();

                if (micClient != null && isBroadcasting) {
                    writeMic(micClient);
                }

                selector.select();
                Iterator<SelectionKey> selectedKeys = selector.selectedKeys().iterator();
                while (selectedKeys.hasNext()) {
                    try {
                        SelectionKey key = (SelectionKey) selectedKeys.next();
                        if (!key.isValid()) continue;
                        if (key.isReadable())
                            read(key);
                        key.interestOps(OP_READ | OP_WRITE);
                        selectedKeys.remove();

                    } catch (IOException e) {
                        System.err.println("glitch, continuing... " + (e.getMessage() != null ? e.getMessage() : ""));
                    }
                }
            } catch (IOException e) {
                System.err.println("glitch, continuing... " + (e.getMessage() != null ? e.getMessage() : ""));
            }
        }
        try {
            channel.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    protected void onPostExecute(Void result) {
        Log.println(Log.ERROR, "LOG", "UDP Server closed");
    }
}
