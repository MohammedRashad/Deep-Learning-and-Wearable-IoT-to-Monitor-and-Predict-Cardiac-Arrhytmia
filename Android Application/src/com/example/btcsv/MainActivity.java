package com.example.btcsv;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.Arrays;

import android.app.Activity;
import android.app.TabActivity;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.PowerManager;
import android.util.Log;
import android.view.View;
import android.view.Surface;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.widget.Toast;
import android.widget.TabHost;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.text.TextWatcher;
import android.text.Editable;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.wifi.WifiManager;
import lecho.lib.hellocharts.view.LineChartView;
import lecho.lib.hellocharts.gesture.ContainerScrollType;
import lecho.lib.hellocharts.gesture.ZoomType;
import lecho.lib.hellocharts.model.Line;
import lecho.lib.hellocharts.model.Axis;
import lecho.lib.hellocharts.model.LineChartData;
import lecho.lib.hellocharts.model.PointValue;

import com.dodo.tools.*;

public class MainActivity extends TabActivity {
    private Activity activity = this;
    Handler mHandler = new Handler();
    
    private static final int VALUES_MAX = 20;
 
    private static final int CH_BT=0, CH_USB=1, CH_TCP=2, CH_UDP=3;
    private static final String VALUE_FORMAT = "%1$10s";
    private static final int X_LINE_N=0, X_FIRST_COL=1, X_DATE=2;

    private boolean appFocused = false;
    
    private final TextView[] mValueViews  = new TextView[VALUES_MAX];
    private final int[]      mValueColors = new int[VALUES_MAX];
    private final boolean[]  mValueEnable = new boolean[VALUES_MAX];
    
    // chart
    private LineChartView mChart;
    private int mXType,mSkipLines;
    private double mXPrev = Double.NaN;
    private boolean mChartReset = true, mChartEnable = false;
    private final List<Line> mChartLines = new ArrayList<Line>();
    private final LineChartData mChartData = new LineChartData();
    private final ArrayList<PointValue> mChartNoPoints = new ArrayList<PointValue>();
    private final ArrayList<ArrayList<PointValue>> mChartsPoints = new ArrayList<ArrayList<PointValue>>();
    private final Axis mChartAxisX = new Axis(), mChartAxisY = new Axis();
    private final boolean[] mChartVisible = new boolean[VALUES_MAX];
    private final CheckBox[] mChartVisibleCBs = new CheckBox[VALUES_MAX];
    
    // connection status and device
    private boolean mConnected = false; // connection flag
    private CommGeneric mChannel;                                      // current channel
    private PowerManager.WakeLock mWakeLock;
    
    private boolean mFirstLine = true;  // 1st line flag
    private final StringBuilder mRxLine    = new StringBuilder();      // received line buffer
    private final AtomicInteger mLineCount = new AtomicInteger(0);     // received line counter
    private final AtomicInteger mSkipLine  = new AtomicInteger(0);     // skip line counter
    private String mValuesLine             = "";
    private final ConcurrentLinkedQueue<String> mRxLines = new ConcurrentLinkedQueue<String>(); // RX lines
    private final ConcurrentLinkedQueue<String> mTxLines = new ConcurrentLinkedQueue<String>(); // TX lines
    private Button[] mCmdsValues, mCmdsChart;
    private String mCmdLine,mCmdLinePrev, mCommentLine;
    
    private boolean mAccumulate=false, mChartUpdated=false;
    private void chartUpdate(){
        mChartUpdated = true;
        mHandler.postDelayed(new Runnable(){ public void run(){
            mChartUpdated = false;
        }},50);
    }
    
    // channel poll task
    private boolean mPollRun = true;
    private Thread mPollThread = new Thread(new Runnable(){
        private byte[] mCharBuf = new byte[8192];          // RX buffer
        private double[] mValues = new double[VALUES_MAX]; // values buffer
        private TextView mTextCommentValues,mTextCommentChart;
        @Override public void run(){ while(mPollRun){
            // RX bytes
            try {
                if(mConnected){
                    int len = mChannel.read(mCharBuf);
                    for(int i=0; i<len; i++){
                        if(mCharBuf[i] == '\n'){
                            if(mFirstLine) mFirstLine = false;
                            else mRxLines.add(mRxLine.toString());
                            mRxLine.setLength(0);
                        }else if((mCharBuf[i] < ' ')||(mCharBuf[i] > 127)) mRxLine.append(" ");
                        else mRxLine.append((char)mCharBuf[i]);
                    }
                    Thread.yield();
                }
            } catch(Exception e){
            }
            
            // RX lines
            if(!mRxLines.isEmpty()){
                mValuesLine = mRxLines.poll().trim();
                if(mSkipLine.incrementAndGet() >= mSkipLines){
                    mSkipLine.set(0);
                    if(mTextCommentValues == null) mTextCommentValues = (TextView)findViewById(R.id.commentValues);
                    if(mTextCommentChart == null) mTextCommentChart = (TextView)findViewById(R.id.commentChart);
//Log.w("btcsv", "values: " + mValuesLine);
                    if(mValuesLine.startsWith("#")){ // comment line
//toast("comment: " + mValuesLine);
                        if(mValuesLine.startsWith("#!")){ // command
                            if(mValuesLine.startsWith("#!reset")){
                                mChartReset = true;  // reset chart
                                mAccumulate = false; // stop accumulation
                            }
                            
                            else if(mValuesLine.startsWith("#!acc_start")){
                                mAccumulate = true; // start accumulation
                                mChartReset = true; // reset chart
                            }
                            
                            else if(mValuesLine.startsWith("#!acc_stop")){ // stop accumulation
                                if(mAccumulate){
                                    if(mChartEnable){
                                        for(int i=0; i<mValues.length; i++)
                                            mChartLines.get(i).setValues(mChartVisible[i] ? mChartsPoints.get(i) : mChartNoPoints);
Log.w("btcsv", "update accumulated: " + mChartData.getLines().get(0).getValues().size());
chartUpdate();
                                        mChart.setLineChartData(new LineChartData(mChartData)); // update chart
                                    }
                                    mAccumulate = false;
                                }
                            }
                                    
                            else if(mValuesLine.startsWith("#!cmds:")){ // commands
                                mCmdLine = mValuesLine.substring(7);
Log.w("btcsv", "commands: " + mCmdLine);
                                if((mCmdLinePrev == null)|| !mCmdLinePrev.equals(mCmdLine)) runOnUiThread(new Runnable(){ public void run(){
                                    mCmdLinePrev = mCmdLine;
                                    if(mCmdsValues == null){
                                        mCmdsValues = new Button[]{(Button)findViewById(R.id.cmdValue1),(Button)findViewById(R.id.cmdValue2),
                                            (Button)findViewById(R.id.cmdValue3),(Button)findViewById(R.id.cmdValue4),(Button)findViewById(R.id.cmdValue5),(Button)findViewById(R.id.cmdValue6),
                                            (Button)findViewById(R.id.cmdValue7),(Button)findViewById(R.id.cmdValue8),(Button)findViewById(R.id.cmdValue9),(Button)findViewById(R.id.cmdValue10)
                                        };
                                        mCmdsChart = new Button[]{(Button)findViewById(R.id.cmdChart1),(Button)findViewById(R.id.cmdChart2),
                                            (Button)findViewById(R.id.cmdChart3),(Button)findViewById(R.id.cmdChart4),(Button)findViewById(R.id.cmdChart5),(Button)findViewById(R.id.cmdChart6),
                                            (Button)findViewById(R.id.cmdChart7),(Button)findViewById(R.id.cmdChart8),(Button)findViewById(R.id.cmdChart9),(Button)findViewById(R.id.cmdChart10)
                                        };
                                        for(int i=0; i<mCmdsValues.length; i++){
                                            mCmdsValues[i].setId(1000 + i);
                                            mCmdsValues[i].setOnClickListener(new View.OnClickListener(){ @Override public void onClick(View v){
                                                if(mConnected) mTxLines.add("#!cmd" + (v.getId()-1000) + "\r\n");
                                            }});
                                            mCmdsChart[i].setId(1000 + i);
                                            mCmdsChart[i].setOnClickListener(new View.OnClickListener(){ @Override public void onClick(View v){
                                                if(mConnected) mTxLines.add("#!cmd" + (v.getId()-1000) + "\r\n");
                                            }});
                                        }
                                        ((LinearLayout)findViewById(R.id.cmdsValues)).setVisibility(View.VISIBLE);
                                        ((LinearLayout)findViewById(R.id.cmdsChart)).setVisibility(View.VISIBLE);
                                    }
                                    String[] names = mCmdLine.split(",");
//Log.w("btcsv", "commands1: " + mCmdsValues.length + " " + names.length);
                                    for(int i=0; i<mCmdsValues.length; i++){
                                        if(i < names.length){
                                            mCmdsValues[i].setVisibility(View.VISIBLE);
                                            mCmdsValues[i].setText(names[i]);
                                            mCmdsChart[i].setVisibility(View.VISIBLE);
                                            mCmdsChart[i].setText(names[i]);
                                        }else{
                                            mCmdsValues[i].setVisibility(View.GONE);
                                            mCmdsChart[i].setVisibility(View.GONE);
                                        }
                                    }
                                }});
                            }
                        }else{ // comment
                            mCommentLine = mValuesLine.substring(1);
Log.w("btcsv", "comment: " + mCommentLine);
//toast("comment: " + mCommentLine);
                            runOnUiThread(new Runnable(){ public void run(){
                                if(!mTextCommentValues.isShown()) mTextCommentValues.setVisibility(View.VISIBLE);
                                if(!mTextCommentChart.isShown()) mTextCommentChart.setVisibility(View.VISIBLE);
                                CharSequence comment = (!mCommentLine.startsWith("<")) ? mCommentLine : // raw text
                                    android.text.Html.fromHtml(mCommentLine); // HTML
                                mTextCommentValues.setText(comment);
                                mTextCommentChart.setText(comment);
                            }});
                        }
                    }else if(!mValuesLine.isEmpty()){
                        mLineCount.incrementAndGet();
                        String[] vs = mValuesLine.replace(","," ").replace(";"," ").trim().split("\\s+");
                        Arrays.fill(mValues, Double.NaN);
if(!mAccumulate) status(mLineCount + " (" + vs.length + "): " + android.text.TextUtils.join("; ", vs));
//Log.w("btcsv", "Line: " + android.text.TextUtils.join("; ", vs));
                        for(int i=0; i<Math.min(mValues.length, vs.length); i++){
                            try{
                                // parse Hex/Bin/Double/boolean
                                if((vs[i].startsWith("0x") || vs[i].startsWith("0X"))&&(vs[i].length() > 2)) mValues[i] = Long.parseLong(vs[i].substring(2), 16);
                                else if((vs[i].startsWith("0b") || vs[i].startsWith("0B"))&&(vs[i].length() > 2)) mValues[i] = Long.parseLong(vs[i].substring(2), 2);
                                else if(vs[i].equals("true")) mValues[i] = 1;
                                else if(vs[i].equals("false")) mValues[i] = 0;
                                else mValues[i] = Double.parseDouble(vs[i]);
                            }catch(Exception e){
                            }
                        }
                        // X value
                        double x = mLineCount.get(); // get x from line count
                        if(mXType == X_FIRST_COL){   // get X from first column
                            x = mValues[0];
                            for(int i=1; i<mValues.length; i++) mValues[i-1] = mValues[i];
                            mValues[mValues.length-1] = Double.NaN;
                        }else if(mXType == X_DATE){  // get X from current time/date
                        }
                        if((mXPrev != Double.NaN)&&(x < mXPrev)) mChartReset = true;
                        mXPrev = x;
                        // update values views
                        if(!mAccumulate) runOnUiThread(new Runnable(){ public void run(){
                            for(int i=0; i<mValues.length; i++){
                                mValueViews[i].setText(String.format(VALUE_FORMAT, !mValueEnable[i] ? "." :
                                    Double.isNaN(mValues[i]) ? "-" : (""+mValues[i])
                                ));
                            }
                        }});
                        // reset chart on X loop start
                        if(mChartReset){
Log.w("btcsv", "reset");
//toast("reset");
                            mChartReset = false;
                            mLineCount.set(0);
                            mChartNoPoints.clear();
                            for(int i=0; i<VALUES_MAX; i++) mChartsPoints.get(i).clear();
                        }
                        if(mChartEnable){
                            // update chart
                            mChartNoPoints.add(new PointValue((float)x, (float)Double.NaN));
                            for(int i=0; i<mValues.length; i++){
                                mChartsPoints.get(i).add(new PointValue((float)x, (float)mValues[i]));
//Log.w("btcsv", "add point: " + x + ":" + mValues[0]);
                                if(!mAccumulate)
                                    mChartLines.get(i).setValues(mChartVisible[i] ? mChartsPoints.get(i) : mChartNoPoints);
                            }
//Log.w("btcsv", "mAccumulate: " + mAccumulate + ", mChartUpdated:" + mChartUpdated);
                            if(!mAccumulate && !mChartUpdated){ //mChartModified=true; runOnUiThread(new Runnable(){ public void run(){
//Log.w("btcsv", "update non-accumulated");
chartUpdate();
                                mChart.setLineChartData(new LineChartData(mChartData));
                            };
                        }
                    }
                }
                Thread.yield();
//                try{ Thread.sleep(1); }catch(Exception e){}
            }else{ try{ Thread.sleep(5); }catch(Exception e){}}
            
            // TX lines
            if(!mTxLines.isEmpty() && mConnected){
                try{ mChannel.write(mTxLines.poll().getBytes("UTF-8"));
                }catch(Exception e){}
                Thread.yield();
            }
        }

        // disconect
        if(mConnected && (mChannel != null)){
            mConnected = false;
            mChannel.close();
        }
    }}){{ start(); }};

    // update list of USB devices
    private void updateUsb(){
        if(((Spinner)findViewById(R.id.serialPorts)).getAdapter() == null){((Spinner)findViewById(R.id.serialPorts)).setAdapter(
            new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, new ArrayList<String>())
        );}else ((ArrayAdapter<String>)((Spinner)findViewById(R.id.serialPorts)).getAdapter()).clear();
        ((ArrayAdapter<String>)((Spinner)findViewById(R.id.serialPorts)).getAdapter()).addAll(CommSerial.getPorts(this));
    }
    // update list of Bluetooth devices
    private void updateBt(){
        if(((Spinner)findViewById(R.id.btChannels)).getAdapter() == null){((Spinner)findViewById(R.id.btChannels)).setAdapter(
            new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, new ArrayList<String>())
        );}else ((ArrayAdapter<String>)((Spinner)findViewById(R.id.btChannels)).getAdapter()).clear();
        ((ArrayAdapter<String>)((Spinner)findViewById(R.id.btChannels)).getAdapter()).addAll(CommBluetooth.pairedNames());
    }
    
    // disconnect connected channel
    private void channelDisconnect(){
        if(mConnected){
            mConnected = false;
            mChannel.close();
        }
    }

    private int valueId;
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        // add tabs
        addTab("tab1", "connection", R.id.tab1);
        addTab("tab2", "values",     R.id.tab2);
        addTab("tab3", "chart",      R.id.tab3);

        // channel type
        ((Spinner)findViewById(R.id.chType)).setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id){
                ((LinearLayout)findViewById(R.id.layBt)).setVisibility((position == CH_BT)   ? View.VISIBLE : View.GONE);
                ((LinearLayout)findViewById(R.id.laySerial)).setVisibility((position == CH_USB) ? View.VISIBLE : View.GONE);
                ((LinearLayout)findViewById(R.id.layTcp)).setVisibility((position == CH_TCP) ? View.VISIBLE : View.GONE);
                ((LinearLayout)findViewById(R.id.layUdp)).setVisibility((position == CH_UDP) ? View.VISIBLE : View.GONE);
            }
            @Override public void onNothingSelected(AdapterView<?> parent){}
        });
        
        // serial ports list update
//        ((Spinner)findViewById(R.id.serialPorts)).setLongClickable(true);
        ((Spinner)findViewById(R.id.serialPorts)).setOnLongClickListener(new View.OnLongClickListener(){ @Override public boolean onLongClick(View v){
            updateUsb();
toast("USB update...");
            return false;
        }});
        updateUsb();
        
        // UDP server port
        WifiManager wifiMgr = (WifiManager)getSystemService(WIFI_SERVICE);
        if(wifiMgr != null){
            int a = wifiMgr.getConnectionInfo().getIpAddress();
            if(java.nio.ByteOrder.nativeOrder().equals(java.nio.ByteOrder.LITTLE_ENDIAN)) a = Integer.reverseBytes(a);
            String addr = ((a >> 24) & 0xFF) + "." + ((a >> 16) & 0xFF) + "." + ((a >> 8) & 0xFF) + "." + (a & 0xFF);
            ((EditText)findViewById(R.id.udpAddr)).setText(addr);
        }
        
        // update Bluetooth list
        ((Spinner)findViewById(R.id.btChannels)).setOnLongClickListener(new View.OnLongClickListener(){ @Override public boolean onLongClick(View v){
            updateBt();
            toast("Bluetooth update...");
            return false;
        }});
        updateBt();

        // load saved preferences
        prefsLoad();

        // values tab
        LinearLayout valuesLayout = (LinearLayout)findViewById(R.id.valuesLayout);
        valuesLayout.removeAllViewsInLayout();
        
        // populate values views
        if(((CheckBox)findViewById(R.id.singleCol)).isChecked()){ // 1-column mode
            for(int row=0; row<VALUES_MAX; row++){
                TextView valueView = new TextView(this){{
                    setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 50F);
                    setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD);
                    setGravity(android.view.Gravity.CENTER_VERTICAL | android.view.Gravity.RIGHT);
                    setPadding(50, 5, 50, 5);
                }};
                valueView.setTextColor(mValueColors[row]);
                mValueViews[row] = valueView;
                valuesLayout.addView(valueView);
            }
        }else{
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            int cols = ((rotation == Surface.ROTATION_0)||(rotation == Surface.ROTATION_180)) ? 2 : 4;
            for(int row=0; row<(VALUES_MAX / cols); row++){
                LinearLayout rowLayout = new LinearLayout(this){{
                    setOrientation(LinearLayout.HORIZONTAL);
                    setGravity(android.view.Gravity.FILL_HORIZONTAL);
                }};
                for(int col=0; col<cols; col++){
                    TextView valueView = new TextView(this){{
                        LayoutParams lp = new LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);
                        lp.weight = 1.0F; lp.gravity = android.view.Gravity.CENTER_HORIZONTAL;
                        setLayoutParams(lp);
                        setTextAppearance(activity, android.R.style.TextAppearance_DeviceDefault_Large);
                        setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD);
                    }};
                    valueView.setTextColor(mValueColors[row*cols + col]);
                    mValueViews[row*cols + col] = valueView;
                    rowLayout.addView(valueView);
                }
                valuesLayout.addView(rowLayout);
            }
        }
        // value enable/disable
        for(int row=0; row<VALUES_MAX; row++){
            mValueViews[row].setText(mValueEnable[row] ? "-" : ".");
            mValueViews[row].setId(row);
            mValueViews[row].setOnLongClickListener(new View.OnLongClickListener(){
                @Override public boolean onLongClick(View view){
                    valueId = view.getId();
                    mValueEnable[valueId] = !mValueEnable[valueId];
toast("value " + (valueId+1) + (mValueEnable[valueId] ? " on" : " off"));
                    runOnUiThread(new Runnable(){ @Override public void run(){
                        mValueViews[valueId].setText(mValueEnable[valueId] ? "-" : ".");
                    }});
                    return true;
                }
            });
        }

        // chart tab
        mChart = ((LineChartView)findViewById(R.id.chart));
        LinearLayout visibleLayout = ((LinearLayout)findViewById(R.id.chartEnableLayout));
        for(int i=0; i<VALUES_MAX; i++){
            mChartVisibleCBs[i] = new CheckBox(this);
                mChartVisibleCBs[i].setText("" + (i+1));
                mChartVisibleCBs[i].setTextColor(mValueColors[i]);
                mChartVisibleCBs[i].setChecked(mChartVisible[i]);
                mChartVisibleCBs[i].setTag(Integer.valueOf(i));
                mChartVisibleCBs[i].setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener(){
                    @Override public void onCheckedChanged(CompoundButton but, boolean checked){
                        mChartVisible[(Integer)but.getTag()] = checked;
                    }
                });
            visibleLayout.addView(mChartVisibleCBs[i]);

            ArrayList<PointValue> points = new ArrayList<PointValue>();
            mChartsPoints.add(points);
            Line line = new Line(points).setStrokeWidth(2).setColor(mValueColors[i]).setHasPoints(false).setCubic(false);
            mChartLines.add(line);
        }
        mChartData.setLines(mChartLines);
        mChartData.setBaseValue(Float.NEGATIVE_INFINITY);
        mChartAxisX.setHasLines(true).setHasTiltedLabels(true).setName("x");
        mChartData.setAxisXBottom(mChartAxisX);
        mChartAxisY.setHasLines(true).setHasTiltedLabels(true).setName("y");
        mChartData.setAxisYLeft(mChartAxisY);
        mChart.setLineChartData(mChartData);
        mChart.setInteractive(true);
        mChart.setZoomEnabled(true);
        mChart.setZoomType(ZoomType.HORIZONTAL);//HORIZONTAL_AND_VERTICAL);
        mChart.setContainerScrollEnabled(true, ContainerScrollType.HORIZONTAL);
        
        // chart enable checkbox
        ((CheckBox)findViewById(R.id.chartEn)).setChecked(mChartEnable);
        ((CheckBox)findViewById(R.id.chartEn)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener(){
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked){
                if(isChecked) mChartReset = true;
                mChartEnable = isChecked;
            }
        });
        
        // chart reset button
        ((Button)findViewById(R.id.chartRes)).setOnClickListener(new View.OnClickListener(){
            @Override public void onClick(View arg0){ mChartReset = true; }
        });
        
        // connect to channel
        ((Button)findViewById(R.id.connect)).setOnClickListener(new View.OnClickListener(){
            @Override public void onClick(View v){
                if(!mConnected){ // open connection
                    new Thread(new Runnable(){ public void run(){
                        runOnUiThread(new Runnable(){ public void run(){
                            ((Button)findViewById(R.id.connect)).setEnabled(false);
                        }});
                        int chType = ((Spinner)findViewById(R.id.chType)).getSelectedItemPosition();
                        if(chType == CH_USB){
                            mChannel = new CommSerial(((Spinner)findViewById(R.id.serialPorts)).getSelectedItem().toString()){{
                                if(open()){
                                    configSerial(
                                        ((Spinner)findViewById(R.id.serialBauds)).getSelectedItem().toString(),
                                        ((Spinner)findViewById(R.id.serialParity)).getSelectedItem().toString(),
                                        ((Spinner)findViewById(R.id.serialStops)).getSelectedItem().toString()
                                    );
                                    mConnected = true;
                                }
                            }};
toast("Connecting to Serial: " + mChannel.name + " : " + mChannel.state + ((mChannel.stateString.length() > 0) ? (" : " + mChannel.stateString) : "") , true);
                        }else if(chType == CH_TCP){
                            mChannel = new CommSocket(((EditText)findViewById(R.id.tcpAddr)).getText().toString() + ":" + 
                                ((EditText)findViewById(R.id.tcpPort)).getText().toString()){{
                                if(open()) mConnected = true;
                            }};
toast("Connecting to Socket: " + mChannel.name + " : " + mConnected, true);
                        } else if(chType == CH_UDP){
                                mChannel = new CommUdpServer(((EditText)findViewById(R.id.udpPort)).getText().toString()){{
                                    if(open()) mConnected = true;
                                }};
toast("Listening UDP at " + mChannel.name + " : " + mConnected, true);
                        } else if(chType == CH_BT){
                            mChannel = new CommBluetooth(((Spinner)findViewById(R.id.btChannels)).getSelectedItem().toString()){{
                                if(open()) mConnected = true;
                            }};
toast("Connecting to Bluetooth: " + mChannel.name + " : " + mConnected, true);
                        }
                        // success?
                        if(mConnected){
                            mChartReset = true;
                            disableUI();
                            // wake lock
                            if(mWakeLock == null) mWakeLock = ((PowerManager)getSystemService(Context.POWER_SERVICE)).newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "My Tag");
                            mWakeLock.acquire();
                        }else{
                            runOnUiThread(new Runnable(){ public void run(){
                                ((Button)findViewById(R.id.connect)).setEnabled(true);
                            }});
                        }
                    }}){{ start(); }};
                }
            }
        });

        // disconnect
        ((Button)findViewById(R.id.disconnect)).setOnClickListener(new View.OnClickListener(){
            @Override public void onClick(View v){
                if(mConnected){ try{
                    channelDisconnect();
                    enableUI();
                    
                    ((View)findViewById(R.id.commentValues)).setVisibility(View.GONE);
                    ((View)findViewById(R.id.commentChart)).setVisibility(View.GONE);
                    ((View)findViewById(R.id.cmdsValues)).setVisibility(View.GONE);
                    ((View)findViewById(R.id.cmdsChart)).setVisibility(View.GONE);
                    
                    // unlock
                    if((mWakeLock != null)&& mWakeLock.isHeld()) mWakeLock.release();
                } catch(Exception e){
                }}
            }
        });

        ((Spinner)findViewById(R.id.xType)).setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id){ mXType = position; }
            @Override public void onNothingSelected(AdapterView<?> parent){}
        });
        ((Spinner)findViewById(R.id.xType)).setSelection(mXType);

        // skip lines
        ((EditText)findViewById(R.id.skipLines)).setText("" + mSkipLines);
        ((EditText)findViewById(R.id.skipLines)).addTextChangedListener(new TextWatcher(){
            public void afterTextChanged(Editable s){ try { mSkipLines = Integer.parseInt(s.toString()); } catch(Exception e){}; }
            public void beforeTextChanged(CharSequence s, int start, int count, int after){}
            public void onTextChanged(CharSequence s, int start, int before, int count){}
        });

        // save CSV
        ((Button)findViewById(R.id.csvSave)).setOnClickListener(new View.OnClickListener(){ @Override public void onClick(View v){
            saveCsv(((CheckBox)findViewById(R.id.csvDate)).isChecked());
        }});

        // start update/poll timers
//        mPollThread.start();
    }

    // add new tab
    private void addTab(String tag, String title, int contentID ){
        TabHost.TabSpec tabSpec = getTabHost().newTabSpec(tag);
        String titleString = title;
        tabSpec.setIndicator(titleString, this.getResources().getDrawable(android.R.drawable.star_on));
        tabSpec.setContent(contentID);
        getTabHost().addTab(tabSpec);
    }
    @Override public void onWindowFocusChanged(boolean hasFocus){
        appFocused = hasFocus;
        super.onWindowFocusChanged(hasFocus);
    }
    @Override protected void onPause(){
        // unlock
        if((mWakeLock != null)&& mWakeLock.isHeld()) mWakeLock.release();
        // stop poll
        mPollRun = false;
        // save prefs
        prefsSave();
        super.onPause();
    }

    // load application preferences
    private static final String PREFS_NAME = "BtCsvPrefs";
    private void prefsLoad(){
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, 0);
        
        ((Spinner)findViewById(R.id.chType)).setSelection(prefs.getInt("channel.type", 0));

        ((Spinner)findViewById(R.id.btChannels)).setSelection(prefs.getInt("bt.channel", 0));
        
        ((Spinner)findViewById(R.id.serialPorts)).setSelection(prefs.getInt("serial.port", 0));
        ((Spinner)findViewById(R.id.serialBauds)).setSelection(prefs.getInt("serial.baud", 0));
        ((Spinner)findViewById(R.id.serialParity)).setSelection(prefs.getInt("serial.parity", 0));
        ((Spinner)findViewById(R.id.serialStops)).setSelection(prefs.getInt("serial.stops", 0));
        
        ((EditText)findViewById(R.id.tcpAddr)).setText(prefs.getString("tcp.addr", "127.0.0.1"));
        ((EditText)findViewById(R.id.tcpPort)).setText(prefs.getString("tcp.port", "8888"));

        ((EditText)findViewById(R.id.udpPort)).setText(prefs.getString("udp.port", "6789"));
        
        mXType = prefs.getInt("chart.xType", 0);
        mSkipLines = prefs.getInt("chart.skipLines", 0);
        ((CheckBox)findViewById(R.id.singleCol)).setChecked(prefs.getBoolean("values.1col", false));

        for(int i=0; i<VALUES_MAX; i++) mValueEnable[i] = prefs.getBoolean("values.en" + i, true);

        mChartEnable = prefs.getBoolean("chart.en", false);
        for(int i=0; i<VALUES_MAX; i++) mChartVisible[i] = prefs.getBoolean("chart.visible"+i, true);

        String[] colors = prefs.getString("chart.colors",
            ("red;blue;#006400;black;" +
            "magenta;navy;lime;gray;" +
            "#FF8000;teal;olive;silver;" +
            "maroon;cyan;green;lightgray;" +
            "fuchsia;aqua;darkgray;purple")
        ).split(";");
        for(int i=0; i<Math.min(colors.length,VALUES_MAX); i++) mValueColors[i] = DoUtils.parseColor(colors[i]);

        ((CheckBox)findViewById(R.id.csvDate)).setChecked(prefs.getBoolean("csv.date", false));
    }
    // save application preferences
    private void prefsSave(){
        SharedPreferences.Editor ed = getSharedPreferences(PREFS_NAME, 0).edit()
        
        .putInt("channel.type", ((Spinner)findViewById(R.id.chType)).getSelectedItemPosition())
        
        .putInt("chart.xType", mXType)
        .putInt("chart.skipLines", mSkipLines)
        .putBoolean("chart.en", mChartEnable)

        .putBoolean("values.1col", ((CheckBox)findViewById(R.id.singleCol)).isChecked());
        for(int i=0; i<VALUES_MAX; i++) ed.putBoolean("values.en" + i, mValueEnable[i]);
        StringBuilder sb = new StringBuilder();
        for(int i=0; i<mValueColors.length; i++) sb.append(DoUtils.hexColor(mValueColors[i])).append(";");
        ed.putString("values.colors", sb.toString());
        for(int i=0; i<VALUES_MAX; i++) ed.putBoolean("chart.visible"+i, mChartVisible[i]);
        
        ed.putInt("bt.channel", ((Spinner)findViewById(R.id.btChannels)).getSelectedItemPosition())
        .putInt("serial.port", ((Spinner)findViewById(R.id.serialPorts)).getSelectedItemPosition())
        .putInt("serial.baud", ((Spinner)findViewById(R.id.serialBauds)).getSelectedItemPosition())
        .putInt("serial.parity", ((Spinner)findViewById(R.id.serialParity)).getSelectedItemPosition())
        .putInt("serial.stops", ((Spinner)findViewById(R.id.serialStops)).getSelectedItemPosition())
        .putString("tcp.addr", ((EditText)findViewById(R.id.tcpAddr)).getText().toString())
        .putString("tcp.port", ((EditText)findViewById(R.id.tcpPort)).getText().toString())
        .putString("udp.port", ((EditText)findViewById(R.id.udpPort)).getText().toString())

        .putBoolean("csv.date", ((CheckBox)findViewById(R.id.csvDate)).isChecked());

        ed.commit();
    }

    private int uiIds[] = {
        R.id.connect,R.id.chType, R.id.btChannels,R.id.serialPorts,R.id.serialBauds,R.id.serialParity,R.id.serialStops,
        R.id.tcpAddr,R.id.tcpPort,R.id.udpAddr, R.id.xType, R.id.skipLines, R.id.singleCol
    };
    private void disableUI(){ runOnUiThread(new Runnable(){ @Override public void run(){
        ((Button)findViewById(R.id.connect)).setVisibility(View.GONE);
        ((Button)findViewById(R.id.disconnect)).setVisibility(View.VISIBLE);

        for(int id : uiIds)findViewById(id).setEnabled(false);

                // fix screen rotation
//                    int rot = getWindowManager().getDefaultDisplay().getRotation();
//                    setRequestedOrientation( (rot == Surface.ROTATION_270) ? ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE :
//                        (rot == Surface.ROTATION_90) ? ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE :
//                        (rot == Surface.ROTATION_180) ? ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT :
//                        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
//                    );
    }}); }
    private void enableUI(){ runOnUiThread(new Runnable(){ @Override public void run(){
        ((Button)findViewById(R.id.connect)).setVisibility(View.VISIBLE);
        ((Button)findViewById(R.id.disconnect)).setVisibility(View.GONE);
        for(int id : uiIds)findViewById(id).setEnabled(true);
    }}); }

    // show toast message
    private String  mToastMsg;
    private boolean mToastLong;
    private void toast(String msg, boolean isLong){ mToastMsg = msg; mToastLong = isLong;
        runOnUiThread(new Runnable(){ @Override public void run(){
            (Toast.makeText(getApplicationContext(), mToastMsg, mToastLong ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT)).show(); }
        });
    }
    private void toast(String msg){ toast(msg,false); }
    
    // show status
    private String mStatusText;
    private void status(String st){
        mStatusText = st;
        runOnUiThread(new Runnable(){ @Override public void run(){
            ((TextView)findViewById(R.id.textStatus)).setText(mStatusText);
        }});
    }

    // request write permissions
    private void requestPerm(){
        // request write permission for Ver>22
        if(Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP_MR1){
            try{
                activity.getClass().getMethod("requestPermissions", new Class[]{ String[].class, int.class })
                .invoke(activity, new String[]{"android.permission.READ_EXTERNAL_STORAGE","android.permission.WRITE_EXTERNAL_STORAGE"}, 200);
            }catch(Exception e){
Log.w("btcsv", "perm.request: " + e.toString());
toast("Permission request error: " + e.toString(),true);
            }
        }
    }
    // save chart data to CSV
    private static final String CSV_NAME = "data";
    private void saveCsv(boolean addDate){
        if(mChartsPoints.get(0).isEmpty()){
toast("No data to write...");
            return;
        }
        requestPerm();
        String fileName = !addDate ? (CSV_NAME + ".csv") : (CSV_NAME + (new SimpleDateFormat("_yyyy.MM.dd_HH.mm")).format(Calendar.getInstance().getTime()) + ".csv");
        if(Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())){
            try{
                File file = new File( Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName);
                BufferedOutputStream fo = new BufferedOutputStream(new FileOutputStream(file,false));
                StringBuilder sb = new StringBuilder();
                for(int x=0; x<mChartsPoints.get(0).size(); x++){
                    sb.append(mChartsPoints.get(0).get(x).getX()).append(' ');
                    for(int y=0; y<mChartsPoints.size(); y++){
                        sb.append(mChartsPoints.get(y).get(x).getY()).append(' ');
                    }
                    sb.append('\n');
                }
                fo.write(sb.toString().getBytes("UTF-8"));
                fo.close();
toast("CSV written to Downloads/" + fileName, true);
            }catch(Exception e){
toast("Failed to save file Downloads/" + fileName + "\n" + e.toString());
            }
        }else{
toast("External media not available");
        }
    }
}
