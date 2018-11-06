package com.dodo.tools;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.hardware.usb.UsbManager;
import android.hardware.usb.UsbDeviceConnection;
import android.content.Intent;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Arrays;

public class CommSerial extends CommGeneric {
    public String type = "serial";
    private UsbSerialPort port;
    private byte[] rxBuf = new byte[1024];

    public CommSerial(){};
    public CommSerial(String name){ this.name = name; }
    
    @Override public void configSerial(String baud, String parity, String stops){
        if(isOpen.get()){
            parity = parity.toLowerCase();
            try{
                port.setParameters(java.lang.Integer.parseInt(baud), UsbSerialPort.DATABITS_8,
                    (stops.equals("1.0")||stops.equals("1")) ? UsbSerialPort.STOPBITS_1 :
                    stops.equals("1.5") ? UsbSerialPort.STOPBITS_1_5 :
                    (stops.equals("2.0")||stops.equals("2")) ? UsbSerialPort.STOPBITS_2 : UsbSerialPort.STOPBITS_1,
                    (parity.equals("none")||parity.equals("n")||parity.equals("0")) ? UsbSerialPort.PARITY_NONE :
                    (parity.equals("odd")||parity.equals("o")||parity.equals("1")) ? UsbSerialPort.PARITY_ODD :
                    (parity.equals("even")||parity.equals("e")||parity.equals("2")) ? UsbSerialPort.PARITY_EVEN :
                    (parity.equals("mark")||parity.equals("m")||parity.equals("3")) ? UsbSerialPort.PARITY_MARK :
                    (parity.equals("space")||parity.equals("s")||parity.equals("4")) ? UsbSerialPort.PARITY_SPACE : UsbSerialPort.PARITY_NONE
                );
            } catch (Exception e){
            }
        }
    };
    
    @Override public boolean open(String name){
        close();
        if((name != null)&&(!name.equals("null"))&&(name.length() > 0)&&(mPorts.containsKey(name))){
            port = mPorts.get(name);
            try{
                UsbDeviceConnection connection = mManager.openDevice( mDrivers.get(name).getDevice() );
                if(connection == null){ // no permission ?
                    mManager.requestPermission(mDrivers.get(name).getDevice(), PendingIntent.getBroadcast(mActivity, 0,
                        new Intent("com.android.example.USB_PERMISSION"), 0));
                }
                port.open(connection);
                isOpen.set(true);
                this.name = name;
            } catch (Exception e){
                stateString = e.toString();
                port = null;
            }
        }
        return isOpen.get();
    }
    @Override public void close(){
        if(isOpen.get()){ try {
            port.purgeHwBuffers(true, true);
            port.close();
            isOpen.set(false);
        } catch (java.io.IOException e){
        }}
    }

    @Override public byte[] read(){
        if(isOpen.get()){ try{
            int len = port.read(rxBuf, 0);
            return Arrays.copyOf(rxBuf, len);
        } catch (Exception e){ return new byte[0];
        }}
        return new byte[0];
    }
    @Override public int read(byte data[]){
        if(isOpen.get()){
            try{ return port.read(data, 0);
            } catch (Exception e){ return 0; }
        }
        return 0;
    }

    @Override public void write(byte data[]){
        if(isOpen.get()){ try { port.write(data, 0);
        } catch (Exception e) {}}
    }
    
    public static String getShortName(String name){
        name = name.toLowerCase();
        if(name.contains("cdc")) return "cdc";
        else if(name.contains("ch3")) return "ch34x";
        else if(name.contains("cp21")) return "cp210x";
        else if(name.contains("ftdi")) return "ftdi";
        else if(name.contains("prolific")) return "prolific";
        else return "";
    }
    public static String getPortName(UsbSerialPort port){
        return getShortName(port.getClass().getSimpleName()) + "." + port.getPortNumber();
    }
    
    private static HashMap<String,UsbSerialPort> mPorts = new HashMap<String,UsbSerialPort>();
    private static HashMap<String,UsbSerialDriver> mDrivers = new HashMap<String,UsbSerialDriver>();
    private static UsbManager mManager;
    private static Activity mActivity;
    public static List<String> getPorts(Activity activity){
        mActivity = activity;
        mPorts.clear(); mDrivers.clear();
        List<String> portsNames = new ArrayList<String>();
        mManager = (UsbManager)activity.getSystemService(Context.USB_SERVICE);
        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(mManager);
        for(UsbSerialDriver drv : availableDrivers){
            for(UsbSerialPort port : drv.getPorts()){
                portsNames.add(getPortName(port));
                mPorts.put(getPortName(port), port);
                mDrivers.put(getPortName(port), drv);
            }
        }
        return portsNames;
    }
    public static UsbSerialPort findPort(Activity activity, String name){
        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers((UsbManager)activity.getSystemService(Context.USB_SERVICE));
        for(UsbSerialDriver drv : availableDrivers){
            for(UsbSerialPort port : drv.getPorts())
                if(getPortName(port).equals(name)) return port;
        }
        return null;
    }
}
