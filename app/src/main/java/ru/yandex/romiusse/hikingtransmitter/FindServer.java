package ru.yandex.romiusse.hikingtransmitter;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Set;

public class FindServer {

    private String serverIP = null;

    public String getServerIP(){return serverIP;}

    public String find(){
        try {
            DatagramSocket c = new DatagramSocket();
            c.setBroadcast(true);

            byte[] sendData = "TRANSMITTER_IS_RUNNING".getBytes();

            //Try the 255.255.255.255 first
            try {
                DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, InetAddress.getByName("255.255.255.255"), 8888);
                c.send(sendPacket);
                System.out.println("Client" + ">>> Request packet sent to: 255.255.255.255 (DEFAULT)");
            } catch (Exception ignored) {
            }


            DatagramChannel datagramChannel = DatagramChannel.open();
            datagramChannel.configureBlocking(false);
            Selector selector = Selector.open();
            datagramChannel.register(selector, SelectionKey.OP_WRITE);

            // Broadcast the message over all the network interfaces
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();

                if (networkInterface.isLoopback() || !networkInterface.isUp()) {
                    continue; // Don't want to broadcast to the loopback interface
                }

                for (InterfaceAddress interfaceAddress : networkInterface.getInterfaceAddresses()) {
                    InetAddress broadcast = interfaceAddress.getBroadcast();
                    System.out.println(">>>>>>>> Client" + broadcast);
                    if (broadcast == null) {
                        continue;
                    }

                    // Send the broadcast package!
                    try {
                        int count = selector.select(1);
                        if (count == 0) {
                            continue;
                        }
                        Set<SelectionKey> selectionKeys = selector.selectedKeys();
                        Iterator<SelectionKey> it = selectionKeys.iterator();
                        while (it.hasNext()) {
                            SelectionKey selectionKey = it.next();
                            DatagramChannel dl = (DatagramChannel) selectionKey.channel();
                            dl.send(ByteBuffer.wrap(sendData), new InetSocketAddress(broadcast, 8888));
                            dl.send(ByteBuffer.wrap(sendData), new InetSocketAddress("255.255.255.255", 8888));
                            //System.out.println("Client" + broadcast);
                            it.remove();
                        }
                        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, broadcast, 8888);
                        c.send(sendPacket);
                        sendPacket = new DatagramPacket(sendData, sendData.length, new InetSocketAddress("255.255.255.255", 8888));
                        c.send(sendPacket);
                    } catch (Exception ignored) {
                    }

                    System.out.println(">>> Request packet sent to: " + broadcast.getHostAddress() + "; Interface: " + networkInterface.getDisplayName());
                }
            }

            System.out.println( ">>> Done looping over all network interfaces. Now waiting for a reply!");

            //Wait for a response
            byte[] recvBuf = new byte[2048];
            DatagramPacket receivePacket = new DatagramPacket(recvBuf, recvBuf.length);
            c.setSoTimeout(2000);
            c.receive(receivePacket);

            //We have a response
            System.out.println( ">>> Broadcast response from server: " + receivePacket.getAddress().getHostAddress());
            datagramChannel.close();
            //Check if the message is   correct
            String message = new String(receivePacket.getData()).trim();
            switch (message) {
                case "TRANSMITTER_SERVER_IS_RUNNING":
                    System.out.println(">>> Server found at: " + receivePacket.getAddress().getHostAddress());
                    serverIP = receivePacket.getAddress().getHostAddress();
                    return "SERVER_WAS_FOUND";
                case "TRANSMITTER_SERVER_IS_BUSY":
                    System.out.println(">>> Server is busy: " + receivePacket.getAddress().getHostAddress());
                    return "SERVER_IS_BUSY";
            }
            return "SERVER_ERROR";

        } catch (SocketTimeoutException ex){
            System.out.println("Server is not running");
            return "SERVER_IS_NOT_RUNNING";
        } catch (IOException ex) {
            System.out.println("Error");
            return "SERVER_ERROR";
        }
    }


}
