package com.dodo.tools;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.ParcelUuid;

public class CommBluetooth extends CommGeneric {
    private static final ParcelUuid UUID_SPP = ParcelUuid.fromString("00001101-0000-1000-8000-00805F9B34FB");
    public  static final String type = "bluetooth";

    private BluetoothDevice device;
    private BluetoothSocket socket;
    private InputStream     inputStream;
    private OutputStream    outputStream;

    public CommBluetooth(){}
    public CommBluetooth(String name){ this.name = name; }
    
    @Override public boolean open(String name){
        close();
        try{
            device = deviceByName(name);
            if(device != null){
                socket = device.createRfcommSocketToServiceRecord(UUID_SPP.getUuid());
                socket.connect();
                inputStream = socket.getInputStream();
                outputStream = socket.getOutputStream();
                isOpen.set(true);
                this.name = name;
            }else{
                stateString = "Device == null";
            }
        }catch(Exception e){ stateString = e.toString(); }
        return isOpen.get();
    }
    @Override public void close(){
        if(isOpen.get()){
            try{ socket.close();
            }catch(Exception e){}
            finally{ isOpen.set(false); }
        }
    }

    @Override public byte[] read(){
        stateString = "";
        if(isOpen.get()){ try{
            int len = inputStream.available();
            byte[] data = new byte[len];
            len = inputStream.read(data);
            if(len != data.length) return Arrays.copyOf(data, len);
            else return data;
        }catch(Exception e){ stateString = e.toString(); }}
        return new byte[0];
    }
    @Override public int read(byte data[]){
        stateString = "";
        if(isOpen.get()){ try{
            return inputStream.read(data, 0, Math.min(data.length, inputStream.available()));
        }catch(Exception e){ stateString = e.toString(); }}
        return 0;
    }
    @Override public void write(byte data[]){
        stateString = "";
        if(isOpen.get()){ try { outputStream.write(data);
        }catch(Exception e){ stateString = e.toString(); }}
    }
    
    // get device name
    public static String deviceName(BluetoothDevice dev){
        return (dev.getName() == null) ? dev.getAddress() : (dev.getName() + " (" + dev.getAddress() + ")");
    }
    // get device by name
    public static BluetoothDevice deviceByName(String name){
        if((name != null)&&(!name.equals("null")))
            for(BluetoothDevice dev : pairedDevices())
                if(deviceName(dev).equals(name)) return dev;
        return null;
    }
    // get list of paired devices
    public static List<BluetoothDevice> pairedDevices(){
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if(adapter != null){
            ArrayList<BluetoothDevice> devs = new ArrayList<BluetoothDevice>();
            for(BluetoothDevice dev : adapter.getBondedDevices()){
                ParcelUuid[] uuids = dev.getUuids();
                if((uuids != null)&&(uuids.length > 0)&&(uuids[0].equals(UUID_SPP)))
                    devs.add(dev);
            }
            return devs;
        } else return new ArrayList<BluetoothDevice>();
    }
    // get list of paired devices names
    public static List<String> pairedNames(){
        ArrayList<String> names = new ArrayList<String>();
        for(BluetoothDevice dev : pairedDevices()) names.add(deviceName(dev));
        Collections.sort(names);
        return names;
    }
}
