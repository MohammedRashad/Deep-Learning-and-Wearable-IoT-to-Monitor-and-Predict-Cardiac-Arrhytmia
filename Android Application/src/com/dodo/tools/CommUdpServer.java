package com.dodo.tools;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.Arrays;
import java.lang.Thread;
import java.lang.Runnable;
import java.net.DatagramSocket;
import java.net.DatagramPacket;

import android.util.Log;

public class CommUdpServer extends CommGeneric {
    public static String type = "socket_server_udp";
    
    private DatagramSocket socket;
    private DatagramPacket rxPacket = new DatagramPacket(new byte[1024],1024);
    private ConcurrentLinkedQueue<byte[]> rxQueue = new ConcurrentLinkedQueue<byte[]>();
    
    public CommUdpServer(){}
    public CommUdpServer(String name){ this.name = name; }

    private void startListen(){
        new Thread(new Runnable(){ public void run(){
            while(isOpen.get() && socket.isBound()){
                try {
                    socket.receive(rxPacket);
                    if(rxPacket.getLength() > 0)
                        rxQueue.add(Arrays.copyOf(rxPacket.getData(), rxPacket.getLength()));
                } catch(Exception e){
                }
            }
        }}){{ start(); }};
    }
    
    @Override public boolean open(String port){
Log.i("CommUdpServer", "opening at port: " + port);
        close();
        try{
            int p = Integer.parseInt(port);
            socket = new DatagramSocket(p);
            isOpen.set(true);
            startListen();
Log.i("CommUdpServer", "listening at port: " + port);
        }catch(Exception e){
Log.e("CommUdpServer", "opening error: " + e.toString());
        }
        return isOpen.get();
    }
    @Override public void close(){
        if(isOpen.get()){ try{
            isOpen.set(false);
            socket.close();
            rxQueue.clear();
        }catch(Exception e){ stateString += e.toString(); }}
    }

    @Override public byte[] read(){
        if(isOpen.get() && !rxQueue.isEmpty()) return rxQueue.poll();
        else return new byte[0];
    }
    @Override public int read(byte data[]){
        if(isOpen.get() && !rxQueue.isEmpty()){
            byte[] buf = rxQueue.poll();
            int len = Math.min(buf.length, data.length);
            System.arraycopy(buf,0, data,0, len);
            return len;
        }
        return 0;
    }
    @Override public void write(byte data[]){
    }
}
