package com.dodo.tools;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class CommGeneric {
    public static String type = "generic";
    public AtomicBoolean isOpen = new AtomicBoolean(false);
    public int state = 0;
    public String stateString = "";
    public String name = "generic";
    
    public CommGeneric(){}
    public CommGeneric(String name){ this.name = name; }
    
    public boolean open(){ return open(name); }
    public boolean open(String name){ isOpen.set(true); return isOpen.get(); }
    public void close(){ isOpen.set(false); }
    
    public byte[] read(){ return new byte[0]; }
    public int read(byte data[]){ return 0; }
    public void write(byte data[]){ }
    public void write(List<Byte> data){
        if(isOpen.get()){ try {
            int i=0; byte[] ar = new byte[data.size()];
            for(byte b : data) ar[i++] = b;
            write(ar);
        } catch (Exception e) {}}
    }
    
    public void configSerial(String baud, String parity, String stops){};
}
