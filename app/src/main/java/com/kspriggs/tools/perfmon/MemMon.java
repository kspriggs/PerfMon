
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
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Vector;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.MemoryInfo;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.content.Context;
import android.graphics.Color;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.TextView;

public class MemMon {
    final private String MEMINFO_FILE = "/proc/meminfo";
    final private String TAG = "PerfMon -> MemMon";
    final private int PROCVIEWSIZE = 6;
    final private int MEMLISTSIZE = PROCVIEWSIZE - 1;
    final private int[] procviewlist = {R.id.prochead, R.id.proc1, R.id.proc2, R.id.proc3, R.id.proc4, R.id.proc5};
    final private DecimalFormat mPercentFmt = new DecimalFormat("#0.0");
    final private int PROC_PID = 0;
    final private int PROC_VSS = 1;
    final private int PROC_RSS = 2;
    final private int PROC_PSS = 3;
    final private int PROC_USS = 4;
    final private int PROC_CMDLINE = 5;
    final private int PROC_SIZE = 6;
    final static public int MEG = 1024;

    private String mProcrankPath = "/system/xbin/procrank";
    private HistoryBuffer mFreeHistory;
    private HistoryBuffer mActManHistory;
    private HistoryBuffer mCustom1History;
    private HistoryBuffer mCustom2History;
    private HistoryBuffer mCustom3History;
    private MemoryInfo mMemInfo;
    private View mView;
    private BufferedReader mReadIn;
    //private Vector<String> mProcrankResults;
    private ArrayList<String> mProcrankResults;
    private HashMap<String, ProcessMemInfo> mMaxPssProcesses;
    private ArrayAdapter<String> mProcessMemInfoAdapter;
    //private Vector<String> mTopResults;


    private boolean mMonitorAllProcs = false;
    private String mCustomWatch1;
    private boolean mCustomWatch1found;
    private String mCustomWatch2;
    private boolean mCustomWatch2found;
    private String mCustomWatch3;
    private boolean mCustomWatch3found;
    private boolean mNeedProcrankData = false;


    private Activity mActivity;

    private PhoneMemInfo mPhoneMemInfo;

    public class PhoneMemInfo {
        public long mMemTotal = 0;
        public long mBuffers = 0;
        public long mCached = 0;
        public long mMemFree = 0;
        public long mSwapTotal = 0;
        public long mSwapFree = 0;
        private int mTimesLowMem = 0;
        private int mTimesLowMemCalled = 0;
        private int mTimesTrimMemCalled = 0;

        PhoneMemInfo() {
        }
    }

    public class ProcessMemInfo implements Comparable<ProcessMemInfo> {
        final private String mProcName;
        final private int mPid;
        private int mPss;
        private long mSum;
        private long mSamples;

        ProcessMemInfo(final String name, final int pid, final int pss) {
            mProcName = name;
            mPid = pid;
            mPss = pss;
            mSum = pss;
            mSamples = 1;
        }

        String getName() {
            return mProcName;
        }

        int getPss() {
            return mPss;
        }

        void updatePss(int pss) {
            mPss = (mPss > pss) ? mPss : pss;
            mSum += pss;
            mSamples++;
            if (mSum + (mSum / mSamples) * 2 > Long.MAX_VALUE) {
                // Reset average
                mSum = pss;
                mSamples = 1;
            }
        }

        int getPssAvg() {
            return (int) (mSum / mSamples);
        }

        public int compareTo(ProcessMemInfo other) {
            if (mProcName == other.mProcName) return mProcName.compareTo(other.mProcName);
            else return -(int) (mPss - other.mPss);
        }
    }

    public MemMon() {
        mFreeHistory = new HistoryBuffer();
        mActManHistory = new HistoryBuffer();
        mCustom1History = new HistoryBuffer();
        mCustom2History = new HistoryBuffer();
        mCustom3History = new HistoryBuffer();
        mMaxPssProcesses = new HashMap<String, ProcessMemInfo>();
        mCustomWatch1 = new String();
        mCustomWatch2 = new String();
        mCustomWatch3 = new String();
        mPhoneMemInfo = new PhoneMemInfo();
        mMemInfo = new MemoryInfo();
    }

    public HistoryBuffer getFreeHistory() {
        return mFreeHistory;
    }

    public HistoryBuffer getActManHistory() {
        return mActManHistory;
    }

    public HistoryBuffer getCustom1History() {
        return mCustom1History;
    }

    public HistoryBuffer getCustom2History() {
        return mCustom2History;
    }

    public HistoryBuffer getCustom3History() {
        return mCustom3History;
    }


    public void setCustomWatch1(String watchstring) {
        mCustomWatch1 = watchstring;
    }

    public void setCustomWatch2(String watchstring) {
        mCustomWatch2 = watchstring;
    }

    public void setCustomWatch3(String watchstring) {
        mCustomWatch3 = watchstring;
    }

    public void setLowMemCalled(WriteLog log) {
        mPhoneMemInfo.mTimesLowMemCalled++;
        if (log != null) {
            log.write("Activity Manager called onLowMemory.\n");
            log.write(captureProcrank(Integer.MAX_VALUE), getProcessMemInfo(), mPhoneMemInfo);
        }
    }

    public void setTrimMemCalled(WriteLog log) {
        mPhoneMemInfo.mTimesTrimMemCalled++;
        if (log != null) {
            log.write("Activity Manager called onTrimMemory.\n");
            log.write(captureProcrank(Integer.MAX_VALUE), getProcessMemInfo(), mPhoneMemInfo);
        }

    }

    public void logProcrank(WriteLog log) {
        if (log != null) {
            log.write("Memory results from Procrank tool: \n");
            log.write(captureProcrank(Integer.MAX_VALUE), getProcessMemInfo(), mPhoneMemInfo);
        }
    }

    public void setActivity(Activity a) {
        mActivity = a;
        setupProcrank();
    }

    public void linkView(View view) {
        mView = view;
        readStats();
    }

    public void setRunProcrank(boolean runstate) {
        mNeedProcrankData = runstate;
    }

    public boolean readStats() {
        FileReader fstream;
        try {
            fstream = new FileReader(MEMINFO_FILE);
        } catch (FileNotFoundException e) {
            Log.e(TAG, "Could not read " + MEMINFO_FILE);
            return false;
        }
        BufferedReader in = new BufferedReader(fstream, 500);
        String line;
        String[] segs;
        Long buffers = new Long(0);
        Long cached = new Long(0);
        mPhoneMemInfo.mMemFree = 0;
        try {
            while ((line = in.readLine()) != null) {
                if (line.startsWith("MemTotal:")) {
                    segs = line.trim().split("[ ]+");
                    mPhoneMemInfo.mMemTotal = Long.parseLong(segs[1]);
                } else if (line.startsWith("MemFree:")) {
                    segs = line.trim().split("[ ]+");
                    mPhoneMemInfo.mMemFree = Long.parseLong(segs[1]);
                } else if (line.startsWith("SwapTotal:")) {
                    segs = line.trim().split("[ ]+");
                    mPhoneMemInfo.mSwapTotal = Long.parseLong(segs[1]);
                } else if (line.startsWith("Buffers:")) {
                    segs = line.trim().split("[ ]+");
                    buffers = Long.parseLong(segs[1]);
                    mPhoneMemInfo.mBuffers = buffers;
                } else if (line.startsWith("Cached:")) {
                    segs = line.trim().split("[ ]+");
                    cached = Long.parseLong(segs[1]);
                    mPhoneMemInfo.mCached = cached;
                } else if (line.startsWith("SwapFree:")) {
                    segs = line.trim().split("[ ]+");
                    mPhoneMemInfo.mSwapFree = Long.parseLong(segs[1]);
                    break;
                }
            }
        } catch (IOException e) {
            Log.e(TAG, e.toString());
        }
        int res = (int) ((mPhoneMemInfo.mMemFree + mPhoneMemInfo.mBuffers + mPhoneMemInfo.mCached) / MEG);
        mFreeHistory.add(res);

        // Don't wast time running procrank if it isn't needed
        if (mActivity != null && (mView != null || mNeedProcrankData)) {
            collectData();
        }
        return false;
    }


    public void updateView(View updateview) {
        if (updateview != null) {
            TextView tv = (TextView) (updateview.findViewById(R.id.memtotal));
            if (tv != null)
                tv.setText(Long.toString(mPhoneMemInfo.mMemTotal / MEG) + " : " + Long.toString((mPhoneMemInfo.mMemFree + mPhoneMemInfo.mBuffers + mPhoneMemInfo.mCached) / MEG) + " (" + Long.toString(mPhoneMemInfo.mBuffers / MEG) + ", " + Long.toString(mPhoneMemInfo.mCached / MEG) + ")");
            tv = (TextView) (updateview.findViewById(R.id.availMem));
            if (tv != null)
                tv.setText(Long.toString(mMemInfo.availMem / MEG / MEG) + " : " + Boolean.toString(mMemInfo.lowMemory));
            tv = (TextView) (updateview.findViewById(R.id.lowMemory));
            if (tv != null) {
                tv.setText(Integer.toString(mPhoneMemInfo.mTimesLowMemCalled) + " : " + Integer.toString(mPhoneMemInfo.mTimesTrimMemCalled));
                if (mMemInfo.lowMemory) {
                    mPhoneMemInfo.mTimesLowMem++;
                    tv.setTextColor(Color.RED);
                }
            }
            tv = (TextView) (updateview.findViewById(R.id.swaptotal));
            if (tv != null)
                tv.setText(Long.toString(mPhoneMemInfo.mSwapTotal / MEG) + " : " + Long.toString(mPhoneMemInfo.mSwapFree / MEG));

            if (mProcrankResults != null && (TextView) (updateview.findViewById(R.id.prochead)) != null) {
                tv = (TextView) (updateview.findViewById(R.id.topmemhead));
                tv.setVisibility(View.VISIBLE);
                for (int i = 0; i < PROCVIEWSIZE && i < mProcrankResults.size(); ++i) {
                    tv = (TextView) (updateview.findViewById(procviewlist[i]));
                    tv.setVisibility(View.VISIBLE);
                    //tv.setText(mProcrankResults.elementAt(i));
                    tv.setText(mProcrankResults.get(i));
                }
            } else if (mProcrankResults == null) {
                tv = (TextView) (updateview.findViewById(R.id.topmemhead));
                tv.setVisibility(View.INVISIBLE);
                for (int i = 0; i < PROCVIEWSIZE; ++i) {
                    tv = (TextView) (updateview.findViewById(procviewlist[i]));
                    tv.setVisibility(View.INVISIBLE);
                }
            }

            if (mProcessMemInfoAdapter != null) {
                Vector<ProcessMemInfo> top_list = getProcessMemInfo();
                mProcessMemInfoAdapter.clear();
                for (Iterator<ProcessMemInfo> it = top_list.iterator(); it.hasNext(); ) {
                    ProcessMemInfo task = it.next();

                    mProcessMemInfoAdapter.add(String.format("%5d  ", task.getPss())
                            + String.format("%5d  ", task.getPssAvg())
                            + task.getName());
                }
            }
        }
    }

    private void setupProcrank() {
        try {
            Process process = Runtime.getRuntime().exec(mProcrankPath);
            mReadIn = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));
        } catch (IOException e) {
            if (!switchProcrankPath()) {
                throw new RuntimeException(e);
            }
        }
    }

    private boolean switchProcrankPath() {
        String newFileName = "/data/data/" + mActivity.getPackageName() + "/lib/" + mActivity.getString(R.string.procrank);
        try {
            Process process = Runtime.getRuntime().exec(newFileName);
            mReadIn = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));
            Log.e(TAG, "Error finding " + mProcrankPath + ", using " + newFileName);
            mProcrankPath = newFileName;
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Error using /system/bin/procrank and " + newFileName);
            //TODO - handle this case better - stop trying to re-use procrank - remember it's not available and advise user
            return false;
        }
    }

    private void collectData() {
        mCustomWatch1found = (mCustomWatch1.isEmpty()) ? true : false;
        mCustomWatch2found = (mCustomWatch2.isEmpty()) ? true : false;
        mCustomWatch3found = (mCustomWatch3.isEmpty()) ? true : false;
        ActivityManager am = (ActivityManager) mActivity.getSystemService(Context.ACTIVITY_SERVICE);
        if (am != null) {
            am.getMemoryInfo(mMemInfo);
            int thresh = (int) (mMemInfo.threshold / MEG / MEG);
            int avail = (int) (mMemInfo.availMem / MEG / MEG);
            mActManHistory.add(avail);
            mActManHistory.setThreshold(thresh);

            List<RunningAppProcessInfo> procInfos = am.getRunningAppProcesses();
            if (!mCustomWatch1found) {
                int pss = getPssFromActivityManager(mCustomWatch1, procInfos);
                if (pss != 0) {
                    mCustom1History.add(pss);
                    mCustomWatch1found = true;
                }
            }
            if (!mCustomWatch2found) {
                int pss = getPssFromActivityManager(mCustomWatch2, procInfos);
                if (pss != 0) {
                    mCustom2History.add(pss);
                    mCustomWatch2found = true;
                }
            }
            if (!mCustomWatch3found) {
                int pss = getPssFromActivityManager(mCustomWatch3, procInfos);
                if (pss != 0) {
                    mCustom3History.add(pss);
                    mCustomWatch3found = true;
                }
            }
        }

        if (mNeedProcrankData || !mCustomWatch1found || !mCustomWatch2found || !mCustomWatch3found) {
            mProcrankResults = captureProcrank(MEMLISTSIZE);
        } else {
            mProcrankResults = null;
        }
    }

    private ArrayList<String> captureProcrank(int listsize) {
        try {
            Process process = Runtime.getRuntime().exec(mProcrankPath);
            mReadIn = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        if (mReadIn.markSupported()) {
            try {
                mReadIn.mark(100000);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        String line;
        try {
            mReadIn.reset();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        ArrayList<String> results = new ArrayList<String>();
        String[] segs;
        while ((line = getLine(mReadIn)) != null) {
            if (line.contains("PID      Vss      Rss      Pss      Uss  cmdline")) {

            } else {
                segs = line.trim().split("[ ]+");
                if (segs.length == PROC_SIZE) {
                    try {
                        int pss = (int) ((Long.parseLong(segs[PROC_PSS].substring(0, segs[PROC_PSS].length() - 1))) / MEG);
                        int pid = Integer.parseInt(segs[PROC_PID]);
                        addToProcessList(segs[PROC_CMDLINE], pid, pss);
                        if (!mCustomWatch1found && line.contains(mCustomWatch1)) {
                            mCustom1History.add(pss);
                            mCustomWatch1found = true;
                        }
                        if (!mCustomWatch2found && line.contains(mCustomWatch2)) {
                            mCustom2History.add(pss);
                            mCustomWatch2found = true;
                        }
                        if (!mCustomWatch3found && line.contains(mCustomWatch3)) {
                            mCustom3History.add(pss);
                            mCustomWatch3found = true;
                        }
                    } catch (NumberFormatException e) {
                        Log.e(TAG, "Error reading procrank results");
                    }
                }
            }
            if (results.size() < listsize) {
                results.add(line);
            } else if (mCustomWatch1found && mCustomWatch2found && mCustomWatch3found && !mMonitorAllProcs) {
                break;
            }
        }
        return results;
    }

    public int getPssFromActivityManager(String processname) {
        ActivityManager am = (ActivityManager) mActivity.getSystemService(Context.ACTIVITY_SERVICE);
        List<RunningAppProcessInfo> procInfos = am.getRunningAppProcesses();
        int pss = 0;
        for (int i = 0; i < procInfos.size(); ++i) {
            if (procInfos.get(i).processName.contains(processname)) {
                int pid = procInfos.get(i).pid;
                int[] pidlist = {pid};
                android.os.Debug.MemoryInfo[] mi = am.getProcessMemoryInfo(pidlist);
                pss = mi[0].getTotalPss() / MEG;
            }
        }
        return pss;
    }

    public int getPssFromActivityManager(String processname, List<RunningAppProcessInfo> procInfos) {
        ActivityManager am = (ActivityManager) mActivity.getSystemService(Context.ACTIVITY_SERVICE);
        int pss = 0;
        for (int i = 0; i < procInfos.size(); ++i) {
            if (procInfos.get(i).processName.contains(processname)) {
                int pid = procInfos.get(i).pid;
                int[] pidlist = {pid};
                android.os.Debug.MemoryInfo[] mi = am.getProcessMemoryInfo(pidlist);
                pss = mi[0].getTotalPss() / MEG;
            }
        }
        return pss;
    }

    public void killProcess(String packageName) {
        ActivityManager am = (ActivityManager) mActivity.getSystemService(Context.ACTIVITY_SERVICE);
        am.killBackgroundProcesses(packageName);
    }

    public MemoryInfo getAvailMem() {
        ActivityManager am = (ActivityManager) mActivity.getSystemService(Context.ACTIVITY_SERVICE);
        am.getMemoryInfo(mMemInfo);
        return mMemInfo;
    }

    private void addToProcessList(String cmdline, int pid, int pss) {
        if (mProcessMemInfoAdapter != null) {
            if (mMaxPssProcesses.containsKey(cmdline)) {
                mMaxPssProcesses.get(cmdline).updatePss(pss);
            } else {
                mMaxPssProcesses.put(cmdline, new ProcessMemInfo(cmdline, pid, pss));
            }
        }
    }

    public void setProcessMemInfoAdapter(ArrayAdapter<String> adapter) {
        mProcessMemInfoAdapter = adapter;
        mMonitorAllProcs = (adapter == null) ? false : true;
    }

    public Vector<ProcessMemInfo> getProcessMemInfo() {
        Set<String> pnames = mMaxPssProcesses.keySet();

        Vector<ProcessMemInfo> results = new Vector<ProcessMemInfo>();
        for (Iterator<String> it = pnames.iterator(); it.hasNext(); ) {
            String index = it.next();
            results.add(mMaxPssProcesses.get(index));
        }
        Collections.sort(results);
        return results;
    }

    private Vector<String> captureTop() {
        try {
            Process process = Runtime.getRuntime().exec("/system/bin/top -n 1 -s vss");
            mReadIn = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        if (mReadIn.markSupported()) {
            try {
                mReadIn.mark(100000);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        String line;
        try {
            mReadIn.reset();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        boolean foundbegin = false;
        int num_captured = 0;
        Vector<String> results = new Vector<String>();
        while ((line = getLine(mReadIn)) != null) {
            if (foundbegin && line.contains("%")) {
                results.add(line);
                if (++num_captured >= 5) {
                    break;
                }
            } else if (line.contains("PID PR CPU% S  #THR     VSS     RSS PCY UID      Name")) {
                foundbegin = true;
                results.add(line);
            }
        }
        return results;
    }

    private String getLine(BufferedReader in) {
        try {
            return in.readLine();
        } catch (IOException e) {
            Log.i(TAG, "read error on");
            return null;
        }
    }
}