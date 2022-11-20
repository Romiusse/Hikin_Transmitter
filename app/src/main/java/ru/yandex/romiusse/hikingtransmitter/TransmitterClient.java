package ru.yandex.romiusse.hikingtransmitter;

import android.media.AudioFormat;
import android.os.AsyncTask;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Set;

public class TransmitterClient extends AsyncTask<Void, Void, Void> {
    // Определите буферы, которые отправляют и получают данные
    private ByteBuffer inbuffer = ByteBuffer.allocate(2048);

    private boolean isHandshakeSuccessfully = false;
    private boolean isHandshakeBusy = false;
    static AudioFormat af;

    private boolean isConnected = true;
    private long lastPulseInTime = System.currentTimeMillis();
    private boolean isSendBroadcastButton = false;
    private boolean isBroadcasting = false;
    private boolean isReceiving = false;
    private long lastPulseOutTime = 0;
    private long lastDistanceSendTime = 0;
    private boolean isRunning = true;
    private double distance = 0;

    private byte[] bDataOut = new byte[2048];
    private byte[] bDataIn = new byte[2048];

    private InetSocketAddress serverIP;

    public boolean isHandshakeBusy() {
        return isHandshakeBusy;
    }
    public boolean isHandshakeSuccessfully() {
        return isHandshakeSuccessfully;
    }

    public void setDistance(double distance) {
        this.distance = distance;
    }

    public boolean isConnected(){
        return isConnected;
    }

    public void setMicOutData(@NonNull byte[] data){
        bDataOut = data.clone();
    }
    public byte[] getMicInData(){
        return bDataIn.clone();
    }
    public boolean IsReceiving(){
        return isReceiving;
    }

    public void stopClient(){
        isRunning = false;
    }

    public void setIsSendBroadcastButton(boolean data){
        isSendBroadcastButton = data;
    }
    public void getIsSendBroadcastButton(boolean data){
        isSendBroadcastButton = data;
    }


    public TransmitterClient(String ip){
        serverIP = new InetSocketAddress(ip, 8888);
    }


    /**
     * Инициализировать дейтаграмчанел и отправлять данные
     */
    protected Void doInBackground(Void... params){
        try {

            DatagramChannel datagramChannel = DatagramChannel.open();
            datagramChannel.configureBlocking(false);
            // определите селектор
            Selector selector = Selector.open();
            datagramChannel.register(selector, SelectionKey.OP_WRITE);
            while (isRunning) {

                int count = selector.select(1);
                if (count == 0) {
                    continue;
                }
                Set<SelectionKey> selectionKeys = selector.selectedKeys();
                Iterator<SelectionKey> it = selectionKeys.iterator();
                while (it.hasNext()) {
                    SelectionKey selectionKey = it.next();

                    checkBroadcastButton(selectionKey);

                    if(!isHandshakeSuccessfully) handshake(selectionKey);
                    if(isConnected){
                        livePulse(selectionKey);
                        sendDistance(selectionKey);
                        handlerRead(selectionKey);
                        if(isBroadcasting) writeMicData(selectionKey);
                    }
                    else reconnect(selectionKey);
                    it.remove();
                }
            }
            datagramChannel.close();
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }

        return null;
    }

    private void checkBroadcastButton(SelectionKey key){
        if(isSendBroadcastButton && !isBroadcasting){
            isBroadcasting = true;
            handlerWrite(key, "TRANSMITTER_CLIENT_START_BROADCAST".getBytes());
        }
        if(isBroadcasting && !isSendBroadcastButton){
            isBroadcasting = false;
            handlerWrite(key, "TRANSMITTER_CLIENT_END_BROADCAST".getBytes());

        }
    }

    private void handshake(SelectionKey key){
        try{
            DatagramChannel datagramChannel = (DatagramChannel) key.channel();
            byte[] sendData = "TRANSMITTER_CLIENT_CONNECTION_REQUEST".getBytes();
            datagramChannel.send(ByteBuffer.wrap(sendData), serverIP);
        }
        catch (IOException ioException) {
            ioException.printStackTrace();
        }
    }

    /**
     * Отправить сообщения на стороне сервера
     * @param key
     */

    private void reconnect(SelectionKey key) {
        try {
            if(key.isWritable()){
                DatagramChannel datagramChannel = (DatagramChannel) key.channel();


                byte[] sendData = "TRANSMITTER_CLIENT_RETRY_CONNECTION".getBytes();
                datagramChannel.send(ByteBuffer.wrap(sendData), serverIP);
                //Получить данные о боковом ответе сервера
                inbuffer.clear();
                inbuffer.put(new byte[2048]);
                inbuffer.clear();
                datagramChannel.receive(inbuffer);
                byte[] recvBuf = inbuffer.array();

                if(!Arrays.equals(recvBuf, new byte[2048])){
                    isConnected = true;
                    lastPulseInTime = System.currentTimeMillis();

                    System.out.println("Connected!");

                }
                else{
                    System.out.println("Trying to connect to the server...");
                }


            }
        }
        catch (IOException ioException) {
            ioException.printStackTrace();
        }
    }

    byte[] oldBData = null;
    private void writeMicData(SelectionKey key){
        try{
            if(!Arrays.equals(oldBData, bDataOut)) {
                DatagramChannel datagramChannel = (DatagramChannel) key.channel();
                datagramChannel.send(ByteBuffer.wrap(bDataOut), serverIP);
                oldBData = bDataOut.clone();
            }

        }
        catch (IOException ioException) {
            ioException.printStackTrace();
        }
    }

    private void handlerWrite(SelectionKey key, byte[] data) {
        try{
            DatagramChannel datagramChannel = (DatagramChannel) key.channel();
            datagramChannel.send(ByteBuffer.wrap(data), serverIP);

        }
        catch (IOException ioException) {
            ioException.printStackTrace();
        }
    }

    private void livePulse(SelectionKey key) throws IOException {
        if(System.currentTimeMillis() - lastPulseOutTime > 1000){
            lastPulseOutTime = System.currentTimeMillis();
            if(!isBroadcasting)
                handlerWrite(key, "TRANSMITTER_CLIENT_IM_ALIVE".getBytes());
        }
    }
    private void sendDistance(SelectionKey key){
        if(System.currentTimeMillis() - lastDistanceSendTime > 2000){
            lastDistanceSendTime = System.currentTimeMillis();
            handlerWrite(key, ("!" + Double.toString(distance)).getBytes());
        }
    }


    byte[] lastRecvBuf = null;
    private void handlerRead(SelectionKey key) {
        try {
            if(key.isWritable()){
                DatagramChannel datagramChannel = (DatagramChannel) key.channel();
                //Получить данные о боковом ответе сервера
                inbuffer.clear();
                inbuffer.put(new byte[2048]);
                inbuffer.clear();
                datagramChannel.receive(inbuffer);
                byte[] recvBuf = inbuffer.array();


                if(!Arrays.equals(recvBuf, new byte[2048])){
                    lastPulseInTime = System.currentTimeMillis();

                    if(isReceiving && !Arrays.equals(lastRecvBuf, recvBuf)){
                        bDataIn = recvBuf.clone();
                        lastRecvBuf = recvBuf.clone();
                    }
                }


                if(System.currentTimeMillis() - lastPulseInTime > 5000){
                    System.out.println("Connection lost!");
                    isConnected = false;
                    return;
                }

                String message = new String(recvBuf).trim();
                switch (message) {
                    case "TRANSMITTER_SERVER_CONNECTION_RESPONSE":
                        isHandshakeSuccessfully = true;
                    case "TRANSMITTER_SERVER_CONNECTION_IS_BUSY":
                        isHandshakeBusy = true;
                        break;
                    case "TRANSMITTER_SERVER_START_BROADCAST":
                        System.out.println("TRANSMITTER_SERVER_START_BROADCAST");
                        isReceiving = true;
                        return;
                    case "TRANSMITTER_SERVER_END_BROADCAST":
                        System.out.println("TRANSMITTER_SERVER_END_BROADCAST");
                        isReceiving = false;
                        return;
                    case "TRANSMITTER_SERVER_IS_ALIVE":
                        System.out.println("TRANSMITTER_SERVER_IS_ALIVE");
                        return;
                }


            }
        }
        catch (IOException ioException) {
            ioException.printStackTrace();
        }
    }
    protected void onPostExecute(Void result) {
        Log.println(Log.ERROR, "LOG", "UDP Client closed");
    }

}
