package com.dodo.tools;

import java.lang.Integer;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Arrays;
import java.io.InputStream;
import java.io.OutputStream;

public class CommSocket extends CommGeneric {
    public static String type = "socket";
    private Socket socket;
    private InputStream inputStream;
    private OutputStream outputStream;
    
    public CommSocket(){}
    public CommSocket(String name){ this.name = name; }
    
    @Override public boolean open(String name){
        close();
        try{
            int port = 8888;
            int colPos = name.indexOf(":");
            if(colPos > 0){
                port = Integer.parseInt(name.substring(colPos + 1));
                name = name.substring(0, colPos);
            }
            socket = new Socket(InetAddress.getByName(name), port);
            inputStream = socket.getInputStream();
            outputStream = socket.getOutputStream();
            isOpen.set(true);
            this.name = name;
        }catch(Exception e){
            stateString += " : " + e.toString();
        }
        return isOpen.get();
    }
    @Override public void close(){
        if(isOpen.get()){ try{
            socket.close();
            isOpen.set(false);
        }catch(Exception e){}
        }
    }

    @Override public byte[] read(){
        if(isOpen.get()){ try{
            int len = inputStream.available();
            byte[] data = new byte[len];
            len = inputStream.read(data);
            if(len != data.length) return Arrays.copyOf(data, len);
            else return data;
        }catch(Exception e){ return new byte[0]; }}
        return new byte[0];
    }
    @Override public int read(byte data[]){
        if(isOpen.get()){ try{
            return inputStream.read(data, 0, Math.min(data.length, inputStream.available()));
        }catch(Exception e){ return 0; }}
        return 0;
    }
    @Override public void write(byte data[]){
        if(isOpen.get()){ try { outputStream.write(data);
        }catch(Exception e){ stateString = e.toString(); }}
    }
}
