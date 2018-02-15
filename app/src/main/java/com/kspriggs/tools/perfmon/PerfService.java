/*
 * Copyright (C) 2008 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.kspriggs.tools.perfmon;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.app.Activity;
import android.app.ActivityManager.MemoryInfo;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;

/**
 * Local service which operates in close cooperation with NetMeter activity.
 * <p/>
 * Execute monitoring through periodic polling, update in-memory history
 * buffers and update display if linkage has been established by the
 * activity after binding to the service.
 * <p/>
 * Whenever running, maintain a persistent notification in the status bar, which
 * sends an intent to (re)start NetMeter activity.
 */
public class PerfService extends Service {
    static final boolean LOGBUDDY = true;
    static final int GRAPH_ID_MEM = 0;
    static final int GRAPH_ID_CUST1 = 1;
    static final int GRAPH_ID_CUST2 = 2;
    static final int GRAPH_ID_CUST3 = 3;
    static final int GRAPH_ID_MEMALL = 4;
    static final int GRAPH_ID_CPU = 5;
    final private String TAG = "PerfMon -> PerfService";
    final private int SAMPLING_INTERVAL = 5;
    final private int NOTIFICATION_ID = 1;
    final private String CPU_TITLE_BASIC = "CPU Total (green)";
    final private String CPU_TITLE_DETAIL = "CPU Total (green) CPU 0 (yellow) CPU 1 (white)";
    final private String CPU_UNIT = " %";
    final private int CPU_YSCALE = 100;
    final private String MEM_UNIT = " PSS (MB)";
    final private int BROWSER_PROFILE_RATE = 3;
    final private int MED_BROWSER_PROFILE_SIZE = 6;
    final private int HI_BROWSER_PROFILE_SIZE = 12;
    final private int[] browser_profile_size = {MED_BROWSER_PROFILE_SIZE, HI_BROWSER_PROFILE_SIZE};
    final private String[][] browser_profile_url = {{"http://video.nytimes.com/", "http://news.google.com/", "http://www.amazon.com/", "http://espn.go.com/", "http://www.bizrate.com/", "http://www.facebook.com/motorola"},
            {"http://video.nytimes.com/", "http://news.google.com/", "http://www.amazon.com/", "http://news.yahoo.com/", "http://espn.go.com/", "http://mobile.engadget.com/", "http://www.bizrate.com/", "http://www.facebook.com/motorola", "http://www.scribd.com/", "http://mail.yahoo.com", "http://googleblog.blogspot.com/", "https://docs.google.com/a/motorola.com/spreadsheet/ccc?authkey=CKjXs6gC&pli=1&key=0AhZlOn5Kfn_-dFVHYUc0Szd0UTBpd1FoWEV1blhHMlE&hl=en&authkey=CKjXs6gC#gid=24"}};

    public static final int MED_PROFILE = 0;
    public static final int HI_PROFILE = 1;
    public static final int SIMULATE_LOWMEM_ONETIME = 0;
    public static final int SIMULATE_LOWMEM_CONTINUOUS = 1;
    public static final int SIMULATE_LOWMEM_LAUNCHAPP = 2;
    public static final String RUN_BROWSER = "com.motorola.tools.perfmon.intent.action.RUN_BROWSER";
    public MemTest mMemTestInfo;
    private LowMemLaunchApp mForceLowMemLaunchApp;

    private memHack mMemHack;
    private boolean mMemHackFirstTime = true;

    private NotificationManager mNotificationManager;

    private MemAllocThread mMemAllocThread;
    public ArrayList<ByteBuffer> mAllocatedBuff = new ArrayList<ByteBuffer>();

    static {
        System.loadLibrary("ndk1");
    }

    private native int helloLog(String logThis);

    private native int allocateMem(int action, int size);

    private native int checkMem();

    private native ByteBuffer allocateByteBuffer(int size);

    private native void freeByteBuffer(ByteBuffer buff);

    private native int causePanic(int zero);

    /**
     * Nested class that performs Memory allocation in a separate thread so as not to block UI
     */
    private class MemAllocThread extends Thread {
        private Handler mMemAllocHandler;
        private int mAllocSize;

        MemAllocThread(Handler h, int size) {
            mMemAllocHandler = h;
            mAllocSize = size;
        }

        public void run() {
            ByteBuffer bb = allocateByteBuffer(1024 * 1024 * mAllocSize); // allocate ByteBuffer
            int size = 0;
            if (bb != null) {
                size = bb.capacity();
                /*
				Byte b = 0;
				for(int i=0;i<size;++i)
				{
					bb.put(b);
					b++;
				}
				*/
				/*
				float f = 0;
				if(size%4 !=0)
				{
					size = size - size%4;
				}
				for(int i=0;i<(size/4);++i)
				{
					bb.putFloat(f);
					f++;
				}
				*/
                mAllocatedBuff.add(bb);
            }

            Message msg = mMemAllocHandler.obtainMessage();
            msg.arg1 = size;
            mMemAllocHandler.sendMessage(msg);
        }
    }

    /**
     * Binder implementation which passes through a reference to
     * this service. Since this is a local service, the activity
     * can then call directly methods on this service instance.
     */
    public class PerfBinder extends Binder {
        PerfService getService() {
            return PerfService.this;
        }
    }

    private final IBinder mBinder = new PerfBinder();

    // various stats collection objects
    private CpuMon mCpuMon;
    private MemMon mMemMon;
    private View mCpuView;
    private View mSumView;
    private View mMemView;
    private GraphView mGraph = null;
    private GraphView mCustomGraph1 = null;
    private String mCustomWatch1 = "";
    private GraphView mCustomGraph2 = null;
    private String mCustomWatch2 = "";
    private GraphView mCustomGraph3 = null;
    private String mCustomWatch3 = "";
    private long mLastTime;
    private WriteLog mBrowserTestLog;
    private int mBrowserProfileCounter = 0;
    private int mBrowserProfileUrl = 0;
    private boolean mRunBrowserProfile = false;
    private int mBrowserProfileType = MED_PROFILE;
    private boolean mReturnAfterTest = false;
    private boolean mForceLowMem = false;
    private int mForceLowMemType = SIMULATE_LOWMEM_CONTINUOUS;
    private long mForceLowMemTime = 0;
    private int mForceLowMemLaunchCount = 0;
    final public static int MAXMEMAPPS = 15;
    final public static String[] mConsumerPackageName = {"com.motorola.tools.memoryconsumer0",
            "com.motorola.tools.memoryconsumer1",
            "com.motorola.tools.memoryconsumer2",
            "com.motorola.tools.memoryconsumer3",
            "com.motorola.tools.memoryconsumer4",
            "com.motorola.tools.memoryconsumer5",
            "com.motorola.tools.memoryconsumer6",
            "com.motorola.tools.memoryconsumer7",
            "com.motorola.tools.memoryconsumer8",
            "com.motorola.tools.memoryconsumer9",
            "com.motorola.tools.memoryconsumera",
            "com.motorola.tools.memoryconsumerb",
            "com.motorola.tools.memoryconsumerc",
            "com.motorola.tools.memoryconsumerd",
            "com.motorola.tools.memoryconsumere"};
    final public static String[] mConsumerClassName = {"MemoryConsumer0Activity",
            "MemoryConsumer1Activity",
            "MemoryConsumer2Activity",
            "MemoryConsumer3Activity",
            "MemoryConsumer4Activity",
            "MemoryConsumer5Activity",
            "MemoryConsumer6Activity",
            "MemoryConsumer7Activity",
            "MemoryConsumer8Activity",
            "MemoryConsumer9Activity",
            "MemoryConsumeraActivity",
            "MemoryConsumerbActivity",
            "MemoryConsumercActivity",
            "MemoryConsumerdActivity",
            "MemoryConsumereActivity"};
    final public static int MAXMEMINTAPPS = 2;
    final public static int LARGEST_ALLOC_BLOCK = 4; //(in KB)
    final public static String[] mConsumerInteractivePackageName = {"com.motorola.tools.memoryconsumerinteractive0",
            "com.motorola.tools.memoryconsumerinteractive1"};
    final public static String[] mConsumerInteractiveClassName = {"MemoryConsumerInteractive0Activity",
            "MemoryConsumerInteractive1Activity"};
    private boolean mQuietMode = false;

    // All the polling and display updating is driven from this
    // handler which is periodically executed every SAMPLING_INTERVAL seconds.
    private Handler mHandler = new Handler();
    private Runnable mRefresh = new Runnable() {
        public void run() {

            //Log.i(TAG, "Running Service");
            // Compensate for sleep time, since this hander is not getting called
            // when the device is asleep/suspended
            long last_time = SystemClock.elapsedRealtime();
            if (last_time - mLastTime > 10 * SAMPLING_INTERVAL * 1000) {
                int padding = (int) ((last_time - mLastTime) / (SAMPLING_INTERVAL * 1000));
                mCpuMon.getHistory()[0].pad(padding);
                mCpuMon.getHistory()[1].pad(padding);
                mCpuMon.getHistory()[2].pad(padding);
            }
            mLastTime = last_time;
            mCpuMon.readStats();
            mMemMon.readStats();
            if (mCpuView != null) mCpuMon.updateView(mCpuView);
            if (mSumView != null) mCpuMon.updateView(mSumView);
            if (mMemView != null) mMemMon.updateView(mMemView);
            if (mGraph != null) mGraph.refresh();
            if (mCustomGraph1 != null) mCustomGraph1.refresh();
            if (mCustomGraph2 != null) mCustomGraph2.refresh();
            if (mCustomGraph3 != null) mCustomGraph3.refresh();

            if (mRunBrowserProfile) {
                if (--mBrowserProfileCounter == 0) runBrowserProfile();
            }

            if (mMemTestInfo != null) {
                runMemTest();
            }

            if (mForceLowMem) {
                runForceLowMemory();
            }

            if (!mQuietMode) {
                mHandler.postDelayed(new Thread(mRefresh), SAMPLING_INTERVAL * 1000);
            }
        }
    };

    /**
     * Reset the counters - triggered by the reset menu of the controller activity
     */
    public void resetCounters(int id) {
        if ((id == GRAPH_ID_MEM || id == GRAPH_ID_MEMALL || id == GRAPH_ID_CPU) && mGraph != null) {
            mGraph.resetCounters();
            mGraph.invalidate();
        }
        if ((id == GRAPH_ID_CUST1 || id == GRAPH_ID_MEMALL) && mCustomGraph1 != null) {
            mCustomGraph1.resetCounters();
            mCustomGraph1.invalidate();
        }
        if ((id == GRAPH_ID_CUST2 || id == GRAPH_ID_MEMALL) && mCustomGraph2 != null) {
            mCustomGraph2.resetCounters();
            mCustomGraph2.invalidate();
        }
        if ((id == GRAPH_ID_CUST3 || id == GRAPH_ID_MEMALL) && mCustomGraph3 != null) {
            mCustomGraph3.resetCounters();
            mCustomGraph3.invalidate();
        }

        alterNotification(false);
    }

    public void toggle(int id) {
        if ((id == GRAPH_ID_MEM || id == GRAPH_ID_MEMALL) && mGraph != null) {
            mGraph.toggleScale();
            mGraph.invalidate();
        }
        if ((id == GRAPH_ID_CUST1 || id == GRAPH_ID_MEMALL) && mCustomGraph1 != null) {
            mCustomGraph1.toggleScale();
            mCustomGraph1.invalidate();
        }
        if ((id == GRAPH_ID_CUST2 || id == GRAPH_ID_MEMALL) && mCustomGraph2 != null) {
            mCustomGraph2.toggleScale();
            mCustomGraph2.invalidate();
        }
        if ((id == GRAPH_ID_CUST3 || id == GRAPH_ID_MEMALL) && mCustomGraph3 != null) {
            mCustomGraph3.toggleScale();
            mCustomGraph3.invalidate();
        }
    }

    /**
     * Link the view objects set up by the controller activity
     * to the service so that they can be updated with the latest
     * state after each polling interval
     */
    public void setCpuView(View view, boolean detail) {
        mCpuMon.linkView(view);
        mCpuView = view;
        if (view != null) {
            mGraph = (GraphView) view.findViewById(R.id.graph);
            if (detail) {
                mGraph.linkCounters(mCpuMon.getHistory()[0], mCpuMon.getHistory()[1], mCpuMon.getHistory()[2], CPU_TITLE_DETAIL, CPU_UNIT, CPU_YSCALE, getString(R.string.cpugraph));
            } else {
                mGraph.linkCounters(mCpuMon.getHistory()[0], null, null, CPU_TITLE_BASIC, CPU_UNIT, CPU_YSCALE, getString(R.string.cpugraph));
            }
        } else {
            mGraph = null;
        }
    }

    public void setSumView(View view) {
        mCpuMon.linkView(view);
        mSumView = view;
    }

    public void setActivity(Activity a) {
        mMemMon.setActivity(a);
    }

    public void setRunProcrank(boolean runstate) {
        mMemMon.setRunProcrank(runstate);
    }

    public void setMemView(View view) {
        mMemView = view;
        if (view != null) {
            mGraph = (GraphView) view.findViewById(R.id.graph);
            mGraph.linkCounters(mMemMon.getFreeHistory(), mMemMon.getActManHistory(), null, getString(R.string.memfreegraph), MEM_UNIT, 0, getString(R.string.memgraph));
        } else {
            mGraph = null;
            mCustomGraph1 = null;
            mCustomGraph2 = null;
            mCustomGraph3 = null;
        }
        mMemMon.linkView(view);
    }

    public void setWatchCustom1(String watchstring) {
        mMemMon.setCustomWatch1(watchstring);
        mCustomWatch1 = watchstring;
        if (watchstring.isEmpty()) {
            mCustomGraph1.setVisibility(View.INVISIBLE);
            mCustomGraph1 = null;
        } else {
            mCustomGraph1 = (GraphView) mMemView.findViewById(R.id.customgraph1);
            if (mCustomGraph1 != null) {
                mCustomGraph1.setVisibility(View.VISIBLE);
                mCustomGraph1.linkCounters(mMemMon.getCustom1History(), watchstring, MEM_UNIT, getString(R.string.custom1graph));
            }
        }
    }

    public void setWatchCustom2(String watchstring) {
        mMemMon.setCustomWatch2(watchstring);
        mCustomWatch2 = watchstring;
        if (watchstring.isEmpty()) {
            mCustomGraph2.setVisibility(View.INVISIBLE);
            mCustomGraph2 = null;
        } else {
            mCustomGraph2 = (GraphView) mMemView.findViewById(R.id.customgraph2);
            if (mCustomGraph2 != null) {
                mCustomGraph2.setVisibility(View.VISIBLE);
                mCustomGraph2.linkCounters(mMemMon.getCustom2History(), watchstring, MEM_UNIT, getString(R.string.custom2graph));
            }
        }
    }

    public void setWatchCustom3(String watchstring) {
        mMemMon.setCustomWatch3(watchstring);
        mCustomWatch3 = watchstring;
        if (watchstring.isEmpty()) {
            if (mCustomGraph3 != null) mCustomGraph3.setVisibility(View.INVISIBLE);
            mCustomGraph3 = null;
        } else {
            mCustomGraph3 = (GraphView) mMemView.findViewById(R.id.customgraph3);
            if (mCustomGraph3 != null) {
                mCustomGraph3.setVisibility(View.VISIBLE);
                mCustomGraph3.linkCounters(mMemMon.getCustom3History(), watchstring, MEM_UNIT, getString(R.string.custom3graph));
            }
        }
    }

    public void forceCpuDisplayUpdate(View forceview) {
        if (mCpuMon != null) {
            mCpuMon.updateView(forceview);
            GraphView gview = (GraphView) forceview.findViewById(R.id.graph);
            if (gview != null) {
                gview.linkCounters(mCpuMon.getHistory()[0], CPU_TITLE_BASIC, CPU_UNIT, CPU_YSCALE, getString(R.string.cpugraph));
                gview.refresh();
            }
        }
    }

    public void forceMemDisplayUpdate(View forceview) {
        if (mMemMon != null) {
            mMemMon.updateView(forceview);
            GraphView gview = (GraphView) forceview.findViewById(R.id.graph);
            if (gview != null) {
                gview.linkCounters(mMemMon.getFreeHistory(), getString(R.string.memfreegraph), MEM_UNIT, getString(R.string.memgraph));
                gview.refresh();
            }
			/*
			gview = (GraphView)forceview.findViewById(R.id.customgraph1);
			if(gview != null)
			{
				gview.linkCounters(mMemMon.getCustom1History(), BRWSMEM_TITLE, MEM_UNIT);
				gview.refresh();
			}
			gview = (GraphView)forceview.findViewById(R.id.customgraph2);
			if(gview != null)
			{
				gview.linkCounters(mMemMon.getCustom2History(), BRWSMEM_TITLE, MEM_UNIT);
				gview.refresh();
			}
			*/
        }
    }

    @Override
    public void onLowMemory() {
        // TODO Auto-generated method stub
        super.onLowMemory();
        alterNotification(true);
        if (mMemMon != null) {
            WriteLog lowmemlog = null;
            try {
                lowmemlog = new WriteLog(getExternalFilesDir(null), "lowmem_" + Long.toString(System.currentTimeMillis()));
            } catch (IOException e) {
                mBrowserTestLog = null;
                Log.e(TAG, "Couldn't create log file for browser test info");
            }
            mMemMon.setLowMemCalled(lowmemlog);
            if (lowmemlog != null) lowmemlog.close();
        }
        Log.i(TAG, "onLowMemory");
    }


    @Override
    public void onTrimMemory(int level) {
        // TODO Auto-generated method stub
        //super.onTrimMemory(level);
		/*
		if(mMemMon != null)
		{
			WriteLog lowmemlog = null;
			try {
				lowmemlog = new WriteLog(getExternalFilesDir(null), "lowmem_" + Long.toString(System.currentTimeMillis()));
			} catch (IOException e) {
				mBrowserTestLog = null;
				Log.e(TAG, "Couldn't create log file for browser test info");
			}
			mMemMon.setTrimMemCalled(lowmemlog);
			if(lowmemlog != null) lowmemlog.close();
		}
		Log.i(TAG, "onTrimMemory level " + Integer.toString(level));
		*/
    }

    /**
     * Framework method called when the service is first created.
     */
    @Override
    public void onCreate() {
        mCpuMon = new CpuMon();
        mMemMon = new MemMon();

        mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        postNotification();

        mLastTime = SystemClock.elapsedRealtime();
        mHandler.postDelayed(new Thread(mRefresh), SAMPLING_INTERVAL * 1000);
    }

    /**
     * Framework method called when the service is stopped/destroyed
     */

    //@Override
    //public int onStartCommand(Intent intent, int flags, int startId) {

    // If we get killed, after returning from here, restart
    // 	Log.i(TAG, "onStartCommand");
    //     return START_STICKY;
    // }
    @Override
    public void onDestroy() {
        Log.i(TAG, "Service Destroyed");
        mNotificationManager.cancel(NOTIFICATION_ID);
        //mHandler.removeCallbacks(mRefresh);
        //mHandler.removeMessages(MSG_RUN_BROWSER);
        mHandler.removeCallbacksAndMessages(null);
    }

    /**
     * Framework method called whenever an activity binds to this service.
     */
    @Override
    public IBinder onBind(Intent arg0) {
        if (mCpuView != null) mCpuMon.updateView(mCpuView);
        if (mMemView != null) mMemMon.updateView(mMemView);
        Log.i(TAG, "Service Bound");
        return mBinder;
    }

    /**
     * Framework method called when an activity binding to the service
     * is broken.
     */
    @Override
    public boolean onUnbind(Intent arg) {
        Log.i(TAG, "Service Unbound");
        return true;
    }

    /**
     * Set up the notification in the status bar, which can be used to restart the
     * NetMeter main display activity.
     */
    private void postNotification() {
        int icon = R.drawable.perf_status;
        CharSequence tickerText = getText(R.string.app_name);
        long when = System.currentTimeMillis();

        Notification notification = new Notification(icon, tickerText, when);

        Context context = getApplicationContext();
        CharSequence contentTitle = getText(R.string.notification_title);
        CharSequence contentText = getText(R.string.notification_text);
        Intent notificationIntent = new Intent(this, PerfMonActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

        notification.setLatestEventInfo(context, contentTitle, contentText, contentIntent);

        mNotificationManager.notify(NOTIFICATION_ID, notification);

        //Ensure that service is not terminated due to memory pressure
        startForeground(NOTIFICATION_ID, notification);
    }

    public void alterNotification(boolean lowmem) {
        int icon = (lowmem) ? R.drawable.perf_status_mem : R.drawable.perf_status;
        CharSequence tickerText = getText(R.string.app_name);
        long when = System.currentTimeMillis();

        Notification notification = new Notification(icon, tickerText, when);
        if (lowmem) {
            notification.ledARGB = Color.CYAN;         // Flash color
            notification.ledOnMS = 300;                // LED's on for 300 ms
            notification.ledOffMS = 300;               // LEDs off for 300 ms
            notification.flags |= Notification.FLAG_SHOW_LIGHTS;
        }

        Context context = getApplicationContext();
        CharSequence contentTitle = getText(R.string.notification_title);
        CharSequence contentText = getText(R.string.notification_text);
        Intent notificationIntent = new Intent(this, PerfMonActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

        notification.setLatestEventInfo(context, contentTitle, contentText, contentIntent);

        mNotificationManager.notify(NOTIFICATION_ID, notification);

        //Ensure that service is not terminated due to memory pressure
        startForeground(NOTIFICATION_ID, notification);
    }

    public void launchMedBrowserProfile(boolean testreturn) {
        try {
            File f = getExternalFilesDir(null);
            mBrowserTestLog = new WriteLog(f, "browser_med");
        } catch (IOException e) {
            mBrowserTestLog = null;
            Log.e(TAG, "Couldn't create log file for browser test info");
        }
        mRunBrowserProfile = true;
        mBrowserProfileUrl = 0;
        mBrowserProfileType = MED_PROFILE;
        mReturnAfterTest = testreturn;
        setBrowserTestStart();
        runBrowserProfile();
    }

    public void launchHiBrowserProfile(boolean testreturn) {
        try {
            mBrowserTestLog = new WriteLog(getExternalFilesDir(null), "browser_hi");
        } catch (IOException e) {
            mBrowserTestLog = null;
            Log.e(TAG, "Couldn't create log file for browser test info");
        }
        mRunBrowserProfile = true;
        mBrowserProfileUrl = 0;
        mBrowserProfileType = HI_PROFILE;
        mReturnAfterTest = testreturn;
        setBrowserTestStart();
        runBrowserProfile();
    }

    private void setBrowserTestStart() {
        if (mCustomGraph1 != null && mCustomWatch1.contains("com.android.browser")) {
            mMemMon.getCustom1History().setTestStart();
        } else if (mCustomGraph2 != null && mCustomWatch2.contains("com.android.browser")) {
            mMemMon.getCustom2History().setTestStart();
        }
    }

    private void setBrowserTestEnd() {
        if (mCustomGraph1 != null && mCustomWatch1.contains("com.android.browser")) {
            mMemMon.getCustom1History().setTestEnd();
            if (mBrowserTestLog != null) {
                mBrowserTestLog.write(mMemMon.getCustom1History());
                mBrowserTestLog.close();
                mBrowserTestLog = null;
            }
        } else if (mCustomGraph2 != null && mCustomWatch2.contains("com.android.browser")) {
            mMemMon.getCustom2History().setTestEnd();
            if (mBrowserTestLog != null) {
                mBrowserTestLog.write(mMemMon.getCustom2History());
                mBrowserTestLog.close();
                mBrowserTestLog = null;
            }
        }
        //Log test completion data
    }

    private void runBrowserProfile() {
        if (mBrowserProfileUrl < browser_profile_size[mBrowserProfileType]) {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(browser_profile_url[mBrowserProfileType][mBrowserProfileUrl++]));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            mBrowserProfileCounter = BROWSER_PROFILE_RATE;

        } else {
            mRunBrowserProfile = false;
            setBrowserTestEnd();
            if (mReturnAfterTest) {
                // Profile Run over, go back to the main app
                Intent intent = new Intent(this, PerfMonActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.putExtra("browserdone", true);
                startActivity(intent);
            }
        }

    }

    public void setProcessMemInfoAdapter(ArrayAdapter<String> adapter) {
        mMemMon.setProcessMemInfoAdapter(adapter);
    }

    public void allocateMemory(Handler h, int size) {
        mMemAllocThread = new MemAllocThread(h, size);
        mMemAllocThread.start();
    }

    public int checkMemory() {
        Iterator<ByteBuffer> i = mAllocatedBuff.iterator();
        int size = 0;
        while (i.hasNext()) {
            size += i.next().capacity();
        }
        return size;
    }

    public void freeMemory() {
        Iterator<ByteBuffer> i = mAllocatedBuff.iterator();
        while (i.hasNext()) {
            freeByteBuffer(i.next());
        }
        mAllocatedBuff.clear();
        for (int k = 0; k < MAXMEMAPPS; ++k) {
            String packageName = mConsumerPackageName[k];
            mMemMon.killProcess(packageName);
        }
        for (int k = 0; k < MAXMEMINTAPPS; ++k) {
            String packageName = mConsumerInteractivePackageName[k];
            mMemMon.killProcess(packageName);
        }
    }

    public void launchMemTest(Handler h, int size) {
        mMemTestInfo = new MemTest(h, size);
        try {
            mMemTestInfo.mWriteLog = new WriteLog(getExternalFilesDir(null), "memtest_" + Long.toString(System.currentTimeMillis()));
            mMemTestInfo.mWriteLog.write("Memory Test Results\nSize Writen (MB)\tTime (ms)\tFree Sys Memory (MB)\n");
        } catch (IOException e) {
            mMemTestInfo.mWriteLog = null;
            Log.e(TAG, "Couldn't create log file for browser test info");
        }
    }

    public void runMemTest() {
        MemoryInfo minfo = mMemMon.getAvailMem();
        int availmem = (int) (minfo.availMem / MemMon.MEG / MemMon.MEG);
        int thresh = (int) (minfo.threshold / MemMon.MEG / MemMon.MEG);
        if ((availmem - mMemTestInfo.mMemTestSize) > thresh) {
            long start_time = SystemClock.elapsedRealtime();
            ByteBuffer b = allocateByteBuffer(MemMon.MEG * MemMon.MEG * mMemTestInfo.mMemTestSize);
            long end_time = SystemClock.elapsedRealtime() - start_time;
            if (b != null) {
                mMemTestInfo.mNumTimesFailed = 0;
                String logout = Integer.toString(mMemTestInfo.mMemTestSize) + "\t" + Integer.toString((int) end_time) + "\t" + Integer.toString(availmem) + "\n";
                Log.i(TAG + " Memory Test Allocation: ", logout);
                if (!mMemTestInfo.add(end_time, b, availmem)) {
                    freeByteBuffer(b);
                }
                if (mMemTestInfo.mWriteLog != null) mMemTestInfo.mWriteLog.write(logout);
            }
        } else if (++mMemTestInfo.mNumTimesFailed > MemTest.MAX_TIMES_FAILED) {
            Message msg = mMemTestInfo.mMemTestHandler.obtainMessage();
            msg.arg1 = mMemTestInfo.mMemTestSize;
            msg.arg2 = mMemTestInfo.getAverageTime();
            Bundle b = new Bundle();
            String filename = (mMemTestInfo.mWriteLog != null) ? getExternalFilesDir(null).getPath() + mMemTestInfo.mWriteLog.getName() : "No log file created";
            b.putString("filename", filename);
            msg.setData(b);
            mMemTestInfo.mMemTestHandler.sendMessage(msg);

            Iterator<MemTest.MemInfo> i = mMemTestInfo.mMemInfoList.iterator();
            while (i.hasNext()) {
                ByteBuffer buff = i.next().mMemTestBuff;
                if (buff != null) freeByteBuffer(buff);
            }
            mMemTestInfo.close();
            mMemTestInfo = null;
        }
    }

    public String logProcrank() {
        String result = "";
        if (mMemMon != null) {
            WriteLog lowmemlog = null;
            try {
                lowmemlog = new WriteLog(getExternalFilesDir(null), "procrank_" + Long.toString(System.currentTimeMillis()));
            } catch (IOException e) {
                result = "";
                Log.e(TAG, "Couldn't create log file for browser test info");
            }
            mMemMon.logProcrank(lowmemlog);
            if (lowmemlog != null) {
                result = lowmemlog.getName();
                lowmemlog.close();
            }
        }
        return result;
    }

    public void launchForceLowMemory(int lowmem_type) {
        mForceLowMem = true;
        mForceLowMemTime = 0;
        mForceLowMemType = lowmem_type;
        mForceLowMemLaunchCount = 0;
        if (lowmem_type == SIMULATE_LOWMEM_LAUNCHAPP) {
            mForceLowMemLaunchApp = new LowMemLaunchApp();
        }
        Log.i(TAG, "Starting to Force Low Memory condition of type" + Integer.toString(mForceLowMemType));
    }

    public void stopForceLowMemory(boolean clearexisting) {
        mForceLowMem = false;
        mQuietMode = false;
        if (clearexisting) {
            Log.i(TAG, "Stopping to Force Low Memory condition");
            for (int k = 0; k < MAXMEMAPPS; ++k) {
                String packageName = mConsumerPackageName[k];
                mMemMon.killProcess(packageName);
            }
            for (int k = 0; k < MAXMEMINTAPPS; ++k) {
                String packageName = mConsumerInteractivePackageName[k];
                mMemMon.killProcess(packageName);
            }
        }
    }

    public void runForceLowMemory() {
        MemoryInfo minfo = mMemMon.getAvailMem();
        if (mForceLowMemType == SIMULATE_LOWMEM_LAUNCHAPP) {
            //simulateLowMemoryLaunchApp(minfo);
            simulateLaunchApp(minfo);
        } else {
            simulateLowMemory(minfo);
        }
    }

    public void simulateLowMemory(MemoryInfo minfo) {
        final int EMPTY_APP_PSS = 5 * MemMon.MEG; //5MB is roughly the size of the Memory Consumer apps without additional allocations through the size parameter
        final int BASIC_ALLOCATION = 60 * MemMon.MEG; //Allocate this amount as a rough start to gain low memory
        final int THRESH_OFFSET = 20 * MemMon.MEG;  //Want to make the consumers such that they don't force too many immediate sigkills upon creation
        final int SAFETY = 20 * MemMon.MEG; //Can't get too close to threshold or system continually kills
        int availmem = (int) (minfo.availMem / MemMon.MEG);
        int thresh = (int) (minfo.threshold / MemMon.MEG);
        boolean consumer_launched = false;
        int k;

        if ((availmem - EMPTY_APP_PSS) > thresh + SAFETY) {
            int size = ((availmem - (thresh + THRESH_OFFSET)) > BASIC_ALLOCATION) ? BASIC_ALLOCATION : (availmem - (thresh + THRESH_OFFSET));
            size = (size < 0) ? 0 : size;
            for (k = 0; k < MAXMEMAPPS; ++k) {
                String packagename = mConsumerPackageName[k];
                String classname = mConsumerClassName[k];
                if (mMemMon.getPssFromActivityManager(packagename) == 0) {
                    Intent i = new Intent(Intent.ACTION_MAIN);
                    i.setClassName(packagename, packagename + "." + classname);
                    i.putExtra("size", size);
                    i.putExtra("max", LARGEST_ALLOC_BLOCK);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    i.putExtra("logbuddy", LOGBUDDY);
                    startActivity(i);
                    if (LOGBUDDY) logBuddyInfo();
                    if (LOGBUDDY) logMemInfo();
                    Log.i(TAG, "Allocated " + Integer.toString(size) + " MB in" + classname + " when avail is " + Integer.toString(availmem) + " and thresh is " + Integer.toString(thresh));
                    mForceLowMemTime = System.currentTimeMillis();
                    consumer_launched = true;
                    mForceLowMemLaunchCount++;
                    break;
                }
            }
        }
        if (!consumer_launched || mForceLowMemLaunchCount > (MAXMEMAPPS + 5)) {
            if (mForceLowMemType == SIMULATE_LOWMEM_ONETIME) {
                Log.i(TAG, "Entering Quiet Mode");
                mQuietMode = true;
                //Stop forcing low mem
                stopForceLowMemory(false);
                mForceLowMemType = SIMULATE_LOWMEM_CONTINUOUS;
                Log.i(TAG, "Stop Allocating Memory when avail is " + Integer.toString(availmem) + " and thresh is " + Integer.toString(thresh));
            }
            // Wait a hysteresis time and then ask user to stop forcing
            if (System.currentTimeMillis() - mForceLowMemTime > 30000) {
                //Call handler to tell MemFrag that memory appears full
            }
        }
    }

    public class LowMemLaunchApp {
        static final int LOWMEM_STARTUP = 0;
        static final int LOWMEM_LAUNCHFIRST = 1;
        static final int LOWMEM_FIRSTLARGEAPPLAUNCHED = 2;
        static final int LOWMEM_LAUNCHSECOND = 3;
        static final int LOWMEM_SECONDLARGEAPPLAUNCHED = 4;
        static final int LOWMEM_CHECKTHRESH = 5;
        static final int LOWMEM_REACHEDMEMTHRESH = 6;

        //static final int WAIT_TIME = 6; //5*6=30 seconds
        static final int WAIT_TIME = 2;
        static final int WAIT_TIME2 = 12; //1 minute

        private int state;
        private int timer;
        private boolean initial_first;
        private boolean initial_second;

        LowMemLaunchApp() {
            state = LOWMEM_STARTUP;
            timer = 0;
            Log.i(TAG, "Setting initial first");
            initial_first = true;
            initial_second = true;
        }

        public boolean isStartup() {
            return (state == LOWMEM_STARTUP);
        }

        public void launchFirst() {
            state = LOWMEM_LAUNCHFIRST;
        }

        public boolean isLaunchFirst() {
            return (state == LOWMEM_LAUNCHFIRST);
        }

        public void firstLaunched() {
            timer = 0;
            state = LOWMEM_FIRSTLARGEAPPLAUNCHED;
            Log.i(TAG, "Clearing initial first");
            initial_first = false;
        }

        public boolean isInitialFirst() {
            return initial_first;
        }

        public boolean isFirstLaunched() {
            return (state == LOWMEM_FIRSTLARGEAPPLAUNCHED);
        }

        public void launchSecond() {
            state = LOWMEM_LAUNCHSECOND;
        }

        public boolean isLaunchSecond() {
            return (state == LOWMEM_LAUNCHSECOND);
        }

        public void secondLaunched() {
            timer = 0;
            state = LOWMEM_SECONDLARGEAPPLAUNCHED;
            initial_second = false;
        }

        public boolean isInitialSecond() {
            return initial_second;
        }

        public boolean isSecondLaunched() {
            return (state == LOWMEM_SECONDLARGEAPPLAUNCHED);
        }

        public void checkThresh() {
            timer = 0;
            state = LOWMEM_CHECKTHRESH;
        }

        public boolean isCheckThresh() {
            return (state == LOWMEM_CHECKTHRESH);
        }

        public void threshReached() {
            timer = 0;
            state = LOWMEM_REACHEDMEMTHRESH;
        }

        public boolean isThreshReached() {
            return (state == LOWMEM_REACHEDMEMTHRESH);
        }

        public boolean delayFinished(int delay) {
            return (++timer >= delay);
        }
    }


    public void simulateLowMemoryLaunchApp(MemoryInfo minfo) {
        final int EMPTY_APP_PSS = 5; //5MB is roughly the size of the Memory Consumer apps without additional allocations through the size parameter
        final int BASIC_ALLOCATION = 45; //Allocate this amount as a rough start to gain low memory
        final int FIRSTAPP_ALLOCATION = 100;
        final int SECONDAPP_ALLOCATION = 100;
        final int THRESH_OFFSET = 20;  //Want to make the consumers such that they don't force too many immediate sigkills upon creation
        final int SAFETY = 20; //Can't get too close to threshold or system continually kills
        int availmem = (int) (minfo.availMem / MemMon.MEG / MemMon.MEG);
        int thresh = (int) (minfo.threshold / MemMon.MEG / MemMon.MEG);
        boolean consumer_launched = false;
		
		/* Start by pushing system to a low memory threshold such that when the two large
		 * apps are launched the overall system threshold will cause Activity Manager sigkills as we switch
		 * between the two large apps.
		 */
        if (mForceLowMemLaunchApp.isStartup()) {
            if (availmem - (2 * EMPTY_APP_PSS + FIRSTAPP_ALLOCATION + SECONDAPP_ALLOCATION) > thresh + SAFETY) {
                int size = ((availmem - (thresh + 2 * EMPTY_APP_PSS + FIRSTAPP_ALLOCATION + SECONDAPP_ALLOCATION + THRESH_OFFSET)) > BASIC_ALLOCATION) ? BASIC_ALLOCATION : (availmem - (thresh + 2 * EMPTY_APP_PSS + FIRSTAPP_ALLOCATION + SECONDAPP_ALLOCATION + THRESH_OFFSET));
                size = (size < 0) ? 0 : size;
                for (int k = 0; k < MAXMEMAPPS; ++k) {
                    String packagename = mConsumerPackageName[k];
                    String classname = mConsumerClassName[k];
                    if (mMemMon.getPssFromActivityManager(packagename) == 0) {
                        Intent i = new Intent(Intent.ACTION_MAIN);
                        i.setClassName(packagename, packagename + "." + classname);
                        i.putExtra("size", size);
                        i.putExtra("max", LARGEST_ALLOC_BLOCK);
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(i);
                        Log.i(TAG, "Allocated " + Integer.toString(size) + " MB in" + classname + " when avail is " + Integer.toString(availmem) + " and thresh is " + Integer.toString(thresh));
                        mForceLowMemTime = System.currentTimeMillis();
                        consumer_launched = true;
                        break;
                    }
                }
            } else {
                //At a sufficient threshold, move to the next state
                mForceLowMemLaunchApp.launchFirst();
            }
            if (!consumer_launched) {
                //Should check a hysteresis here but for now just move to next state
                mForceLowMemLaunchApp.launchFirst();
            }
        } else if (mForceLowMemLaunchApp.isLaunchFirst()) {
            String packagename = mConsumerInteractivePackageName[0];
            String classname = mConsumerInteractiveClassName[0];
            int pss = mMemMon.getPssFromActivityManager(packagename);
            int size = FIRSTAPP_ALLOCATION;
            if (pss != 0) {
                Log.i(TAG, "App already running and consuming" + Integer.toString(pss) + ", just restarting: " + packagename);
                size = 0;
            }
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.setClassName(packagename, packagename + "." + classname);
            mForceLowMemTime = System.currentTimeMillis();
            i.putExtra("reset", mForceLowMemLaunchApp.isInitialFirst());
            i.putExtra("size", size);
            i.putExtra("time", mForceLowMemTime);
            i.putExtra("avail", availmem);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            Log.i(TAG, "Allocated " + Integer.toString(size) + " MB in" + classname + " when avail is " + Integer.toString(availmem) + " and thresh is " + Integer.toString(thresh));
            consumer_launched = true;
            mForceLowMemLaunchApp.firstLaunched();
        } else if (mForceLowMemLaunchApp.isFirstLaunched()) {
            //Delay some time here
            if (mForceLowMemLaunchApp.delayFinished(LowMemLaunchApp.WAIT_TIME)) {
                mForceLowMemLaunchApp.launchSecond();
            }
        } else if (mForceLowMemLaunchApp.isLaunchSecond()) {
            String packagename = mConsumerInteractivePackageName[1];
            String classname = mConsumerInteractiveClassName[1];
            int pss = mMemMon.getPssFromActivityManager(packagename);
            int size = SECONDAPP_ALLOCATION;
            if (pss != 0) {
                Log.i(TAG, "App already running and consuming" + Integer.toString(pss) + ", just restarting: " + packagename);
                size = 0;
            }
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.setClassName(packagename, packagename + "." + classname);
            mForceLowMemTime = System.currentTimeMillis();
            i.putExtra("reset", mForceLowMemLaunchApp.isInitialSecond());
            i.putExtra("size", size);
            i.putExtra("time", mForceLowMemTime);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            Log.i(TAG, "Allocated " + Integer.toString(size) + " MB in" + classname + " when avail is " + Integer.toString(availmem) + " and thresh is " + Integer.toString(thresh));
            consumer_launched = true;
            mForceLowMemLaunchApp.secondLaunched();

        } else if (mForceLowMemLaunchApp.isSecondLaunched()) {
            //Delay one minute in this loop
            if (mForceLowMemLaunchApp.delayFinished(LowMemLaunchApp.WAIT_TIME)) {
                mForceLowMemLaunchApp.checkThresh();
            }
        } else if (mForceLowMemLaunchApp.isCheckThresh()) {
            if (mForceLowMemLaunchApp.delayFinished(2)) {
                if ((availmem - EMPTY_APP_PSS) > thresh + SAFETY) {
                    int size = ((availmem - (thresh + THRESH_OFFSET)) > BASIC_ALLOCATION) ? BASIC_ALLOCATION : (availmem - (thresh + THRESH_OFFSET));
                    size = (size < 0) ? 0 : size;
                    for (int k = 0; k < MAXMEMAPPS; ++k) {
                        String packagename = mConsumerPackageName[k];
                        String classname = mConsumerClassName[k];
                        if (mMemMon.getPssFromActivityManager(packagename) == 0) {
                            Intent i = new Intent(Intent.ACTION_MAIN);
                            i.setClassName(packagename, packagename + "." + classname);
                            i.putExtra("size", size);
                            i.putExtra("max", LARGEST_ALLOC_BLOCK);
                            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(i);
                            Log.i(TAG, "Allocated " + Integer.toString(size) + " MB in" + classname + " when avail is " + Integer.toString(availmem) + " and thresh is " + Integer.toString(thresh));
                            mForceLowMemTime = System.currentTimeMillis();
                            consumer_launched = true;
                            break;
                        }
                    }
                }
            }
            //Delay 2 minutes in this loop
            if (mForceLowMemLaunchApp.delayFinished(LowMemLaunchApp.WAIT_TIME2)) {
                mForceLowMemLaunchApp.launchFirst();
            }
        }
    }

    private void logBuddyInfo() {
        try {
            Process process = Runtime.getRuntime().exec("/system/bin/cat /proc/buddyinfo");
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));
            String mline;
            while ((mline = in.readLine()) != null) {
                Log.d(TAG, "BUDDYINFO: " + mline);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void logMemInfo() {
        try {
            Process process = Runtime.getRuntime().exec("/system/bin/cat /proc/meminfo");
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));
            String mline;
            while ((mline = in.readLine()) != null) {
                Log.d(TAG, "MEMINFO: " + mline);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public class memHack {
        public String mline;
        public BufferedReader mHackReader;
        //public RandomAccessFile mHackReader = null;
        public int mHackReaderRun = 0;
        public static final int MEMHACK_NORUN = 0;
        public static final int MEMHACK_RUN = 1;
        public static final int MEMHACK_RUNMOD = 2;

        private Handler mHackHandler = new Handler();
        private Runnable mHackRefresh = new Runnable() {
            public void run() {
                final String KERNEL_TRACE_FILE = "/data/trace";
                final Pattern mTypePattern = Pattern.compile("\\A\\s*+.{0,16}?-\\d++\\s++\\[\\d++\\]\\s++(\\d++\\.\\d++):\\s++(oom_kill(?:_shared)?|lmk_kill):\\s++");
                final String EVENT_LMK_KILL = "lmk_kill";
                final String EVENT_OOM_KILL = "oom_kill";
                final String EVENT_OOM_KILL_SHARED = "oom_kill_shared";
                final int MAX_WORDS_IN_LINE = 40;
                final int MAX_KERNEL_LINE_LENGTH = 200;
                WordPositionExtractor words = null;

                if (false) {
                    try {
                        System.gc();
                        Process process = Runtime.getRuntime().exec("/system/bin/cat /proc/buddyinfo");
                        BufferedReader in = new BufferedReader(
                                new InputStreamReader(process.getInputStream()));
                        while ((mline = in.readLine()) != null) {
                            Log.d(TAG, "KEVIN buddyinfo before algorithm: " + mline);
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    try {
                        Process process = Runtime.getRuntime().exec("/system/bin/cat /proc/meminfo");
                        BufferedReader in = new BufferedReader(
                                new InputStreamReader(process.getInputStream()));
                        String mline;
                        while ((mline = in.readLine()) != null) {
                            Log.d(TAG, "KEVIN MEMINFO before: " + mline);
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }

                int numtimes = 0;
                try {
                    if (mHackReaderRun == MEMHACK_RUNMOD) {
                        words = new WordPositionExtractor(MAX_WORDS_IN_LINE);
                    }
                    mHackReader = new BufferedReader(new FileReader(KERNEL_TRACE_FILE));
                    Matcher matcher = null;
                    //mHackReader = new RandomAccessFile(KERNEL_TRACE_FILE, "r");

                    while (true) {
                        mline = mHackReader.readLine();
                        if (mline == null) {
                            Log.d(TAG, "KEVIN kernel file empty");
                            break;
                        }
                        try {
                            numtimes++;
                            if (mHackReaderRun == MEMHACK_RUN) {
                                matcher = mTypePattern.matcher(mline);
                                if (!matcher.find()) continue;

                                String kernelTime = matcher.group(1);

                                String type = matcher.group(2);

                                if (type.equals(EVENT_LMK_KILL)) {
                                    Log.d(TAG, "KEVIN Matched LMK Kill" + mline + kernelTime);
                                } else if (type.equals(EVENT_OOM_KILL)) {
                                    Log.d(TAG, "KEVIN Matched OOM Kill" + mline + kernelTime);
                                } else if (type.equals(EVENT_OOM_KILL_SHARED)) {
                                    Log.d(TAG, "KEVIN Matched OOM Shared Kill" + mline + kernelTime);
                                }
                            } else if (false) {
                                if (mline.contains(EVENT_LMK_KILL)) {
                                    String[] segs = mline.trim().split("[ ]+");
                                    Float time = Float.parseFloat(segs[2].substring(0, segs[2].length() - 1));
                                    int pid = Integer.parseInt(segs[4]);
                                    String pname = segs[5];
                                    int oom_adj = Integer.parseInt(segs[6]);
                                    int size = Integer.parseInt(segs[7]);
                                    int min_adj = Integer.parseInt(segs[8]);
                                    int cache = Integer.parseInt(segs[9]);
                                    int swapfree = Integer.parseInt(segs[10]);
                                    //Log.d(TAG, "KEVIN contains lmk" + mline);
                                    Log.d(TAG, "KEVIN contains kill of: " + Float.toString(time) + " " + Integer.toString(pid) + " " + pname + " " + Integer.toString(oom_adj) + " " + Integer.toString(size) + " " +
                                            Integer.toString(min_adj) + " " + Integer.toString(cache) + " " + Integer.toString(swapfree));
                                } else if (mline.contains(EVENT_OOM_KILL)) {
                                    Log.d(TAG, "KEVIN contains oom" + mline);
                                } else if (mline.contains(EVENT_OOM_KILL_SHARED)) {
                                    Log.d(TAG, "KEVIN contains oom shared" + mline);
                                }
                            } else if (mHackReaderRun == MEMHACK_RUNMOD)//Begin IKJBMAIN-9985
                            {

                                if (mline.contains("_kill") && mline.length() <= MAX_KERNEL_LINE_LENGTH) {
                                    int availableWords = words.parseLine(mline);
                                    if (availableWords <= MAX_WORDS_IN_LINE - 1) {
                                        int wordIndex = 3; // Start scanning for "lmk_kill" from the 4th word

                                        for (; wordIndex < availableWords; wordIndex++) {

                                            // Sanity check on the "[000]" part of -> NAME [000]   175.136871: lmk_kill
                                            if (mline.charAt(words.getStartOffset(wordIndex - 2)) == '[') {

                                                String typeWord = words.getWord(wordIndex);
                                                String kernelTimeWithColon = words.getWord(wordIndex - 1);

                                                if (typeWord.equals("lmk_kill:")) {
                                                    Log.d(TAG, "KEVIN contains lmk" + mline + kernelTimeWithColon);
                                                    break;
                                                } else if (typeWord.equals("oom_kill:")) {
                                                    Log.d(TAG, "KEVIN contains oom" + mline);
                                                    break;
                                                } else if (typeWord.equals("oom_kill_shared:")) {
                                                    Log.d(TAG, "KEVIN contains oom shared" + mline);
                                                    break;
                                                }
                                            }
                                        }
                                    }

                                }
                            }

                        } catch (Exception e) {
                            Log.d(TAG, "read exception " + e.toString());
                        }
                    }
                    if (mMemHackFirstTime) System.gc();
                } catch (Throwable t) {
                    Log.d(TAG, "KEVIN exception", t);
                }
                Log.d(TAG, "KEVIN Searched kernel file memHack" + numtimes);
                try {
                    mHackReader.close();
                } catch (Throwable throwable) {
                    Log.d(TAG, "exception", throwable);
                }
                if (false) {
                    try {
                        Process process = Runtime.getRuntime().exec("/system/bin/cat /proc/buddyinfo");
                        BufferedReader in = new BufferedReader(
                                new InputStreamReader(process.getInputStream()));
                        while ((mline = in.readLine()) != null) {
                            Log.d(TAG, "KEVIN buddyinfo after algorithm: " + mline);
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    try {
                        Process process = Runtime.getRuntime().exec("/system/bin/cat /proc/meminfo");
                        BufferedReader in = new BufferedReader(
                                new InputStreamReader(process.getInputStream()));
                        String mline;
                        while ((mline = in.readLine()) != null) {
                            Log.d(TAG, "KEVIN MEMINFO after: " + mline);
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
                if (mHackReaderRun != MEMHACK_NORUN) {
                    mHackHandler.postDelayed(new Thread(mHackRefresh), 1000);
                }
            }

            //Start of IKJBMAIN-9985
            final class WordPositionExtractor {
                private final int mMaxWords;
                private final int[] mWordBegin; // start of each word
                private final int[] mWordEnd; // one beyond the last character of each word
                private int mAvailableWords;
                private String mLine;

                public WordPositionExtractor(int maxWords) {
                    mMaxWords = maxWords;
                    mWordBegin = new int[mMaxWords];
                    mWordEnd = new int[mMaxWords];
                }

                /**
                 * @param line The line whose word start/end offsets are to be parsed
                 * @return The number of words in the line. If there are more than maxWords
                 *         words, then maxWords will be returned
                 */
                public final int parseLine(String line) {
                    mLine = line;
                    mAvailableWords = 0;

                    int currentOffset = 0;
                    int maxOffset = line.length();

                    while (mAvailableWords < mMaxWords) {
                        // skip whitespace
                        for (; currentOffset < maxOffset; currentOffset++) {
                            char c = line.charAt(currentOffset);
                            if (c != ' ' && c != '\t') {
                                break;
                            }
                        }

                        if (currentOffset >= maxOffset) {
                            return mAvailableWords;
                        }

                        mWordBegin[mAvailableWords] = currentOffset++;

                        // skip word characters (i.e non-whitespace)
                        for (; currentOffset < maxOffset; currentOffset++) {
                            char c = line.charAt(currentOffset);
                            if (c == ' ' || c == '\t') {
                                break;
                            }
                        }

                        mWordEnd[mAvailableWords++] = currentOffset++;
                    }

                    return mAvailableWords;
                }

                public final String getWord(int index) {
                    return mLine.substring(mWordBegin[index], mWordEnd[index]);
                }

                public final String getLine() {
                    return mLine;
                }

                public final int getStartOffset(int index) {
                    return mWordBegin[index];
                }

                /**
                 * @param index The index of the word in the line
                 * @return The offset of one beyond the last character of the word at the specified index
                 */
                public final int getEndOffset(int index) {
                    return mWordEnd[index];
                }

                public final int getAvailableWords() {
                    return mAvailableWords;
                }
            }

        };

        private final void removeStaleHackEntries(HashMap<Integer, Long> hash) {
            long currentTimeMs = System.currentTimeMillis();
            Iterator<Long> iterator = hash.values().iterator();
            while (iterator.hasNext()) {
                if (Math.abs(currentTimeMs - iterator.next()) >= (5 * 1000 * 60)) {
                    iterator.remove();
                }
            }
        }

        public memHack() {
            mHackReaderRun = MEMHACK_NORUN;
        }

        public void run(int type) {
            Log.d(TAG, "KEVIN Starting memHack with type:" + Integer.toString(type));
            mHackReaderRun = type;
            mHackHandler.postDelayed(new Thread(mHackRefresh), 1000);
        }

        public void stop() {
            Log.d(TAG, "KEVIN Stopping memHack");
            mHackReaderRun = MEMHACK_NORUN;
        }
    }

    public void startHack(int type) {
        if (mMemHack == null) {
            mMemHack = new memHack();
        }
        mMemHackFirstTime = false;
        mMemHack.run(type);
    }

    public void stopHack() {
        if (mMemHackFirstTime) System.gc();
        mMemHackFirstTime = false;
        if (mMemHack != null) mMemHack.stop();
    }

    public void simulateLaunchApp(MemoryInfo minfo) {
        final int FIRSTAPP_ALLOCATION = 100;
        final int SECONDAPP_ALLOCATION = 100;

        int availmem = (int) (minfo.availMem / MemMon.MEG / MemMon.MEG);
        int thresh = (int) (minfo.threshold / MemMon.MEG / MemMon.MEG);
		
		/* Start by pushing system to a low memory threshold such that when the two large
		 * apps are launched the overall system threshold will cause Activity Manager sigkills as we switch
		 * between the two large apps.
		 */
        if (mForceLowMemLaunchApp.isStartup()) {
            //Don't force low memory - just launch app
            mForceLowMemLaunchApp.launchFirst();
        } else if (mForceLowMemLaunchApp.isLaunchFirst()) {
            String packagename = mConsumerInteractivePackageName[0];
            String classname = mConsumerInteractiveClassName[0];
            int pss = mMemMon.getPssFromActivityManager(packagename);
            int size = FIRSTAPP_ALLOCATION;
            if (pss != 0) {
                Log.i(TAG, "App already running and consuming" + Integer.toString(pss) + ", just restarting: " + packagename);
                size = 0;
            }
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.setClassName(packagename, packagename + "." + classname);
            mForceLowMemTime = System.currentTimeMillis();
            i.putExtra("reset", mForceLowMemLaunchApp.isInitialFirst());
            i.putExtra("size", size);
            i.putExtra("time", mForceLowMemTime);
            i.putExtra("avail", availmem);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            Log.i(TAG, "Allocated " + Integer.toString(size) + " MB in" + classname + " when avail is " + Integer.toString(availmem) + " and thresh is " + Integer.toString(thresh));
            mForceLowMemLaunchApp.firstLaunched();
        } else if (mForceLowMemLaunchApp.isFirstLaunched()) {
            //Delay some time here
            if (mForceLowMemLaunchApp.delayFinished(LowMemLaunchApp.WAIT_TIME)) {
                mForceLowMemLaunchApp.launchFirst();
            }
        } else if (mForceLowMemLaunchApp.isLaunchSecond()) {
            String packagename = mConsumerInteractivePackageName[1];
            String classname = mConsumerInteractiveClassName[1];
            int pss = mMemMon.getPssFromActivityManager(packagename);
            int size = SECONDAPP_ALLOCATION;
            if (pss != 0) {
                Log.i(TAG, "App already running and consuming" + Integer.toString(pss) + ", just restarting: " + packagename);
                size = 0;
            }
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.setClassName(packagename, packagename + "." + classname);
            mForceLowMemTime = System.currentTimeMillis();
            i.putExtra("reset", mForceLowMemLaunchApp.isInitialSecond());
            i.putExtra("size", size);
            i.putExtra("time", mForceLowMemTime);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            Log.i(TAG, "Allocated " + Integer.toString(size) + " MB in" + classname + " when avail is " + Integer.toString(availmem) + " and thresh is " + Integer.toString(thresh));
            mForceLowMemLaunchApp.secondLaunched();

        } else if (mForceLowMemLaunchApp.isSecondLaunched()) {
            //Delay one minute in this loop
            if (mForceLowMemLaunchApp.delayFinished(LowMemLaunchApp.WAIT_TIME)) {
                mForceLowMemLaunchApp.launchFirst();
            }
        }
    }
}