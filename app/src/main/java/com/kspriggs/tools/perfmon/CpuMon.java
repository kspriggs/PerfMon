/* Modified from http://code.google.com/p/android-labs/source/browse/trunk/NetMeter/src/com/google/android/netmeter/HistoryBuffer.java
 * https://github.com/dphans/android-labs/tree/master/NetMeter
 *
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
import java.util.Vector;

import android.util.Log;
import android.view.View;
import android.widget.TextView;

public class CpuMon {
    final private int CPUAll = 0;
    final private int CPU0 = 1;
    final private int CPU1 = 2;
    final private int CPULOOP = 3;
    final private int PROCVIEWSIZE = 6;
    final private int[] viewlist = {R.id.cpuAllstat, R.id.cpu0stat, R.id.cpu1stat};
    final private int[] procviewlist = {R.id.prochead, R.id.proc1, R.id.proc2, R.id.proc3, R.id.proc4, R.id.proc5};
    final private String STAT_FILE = "/proc/stat";
    final private String CPUINFO_FILE = "/proc/cpuinfo";
    final private String TAG = "PerfMon -> CpuMon";
    final private DecimalFormat mPercentFmt = new DecimalFormat("#0.0");

    private long[] mUser;
    private long[] mSystem;
    private long[] mTotal;
    private long[] mUserCurrent;
    private long[] mSystemCurrent;
    private long[] mTotalCurrent;
    private String mCpuMaxFreq0 = "NA";
    private String mCpuFreq0 = "NA";
    private String mCpuMaxFreq1 = "NA";
    private String mCpuFreq1 = "NA";
    private String mCpuType = "NA";
    private boolean mDualCpu;
    private HistoryBuffer[] mHistory;
    private View mView;
    private BufferedReader mTopIn;
    private Vector<String> mTopResults;

    public CpuMon() {
        mUser = new long[3];
        mSystem = new long[3];
        mTotal = new long[3];
        mUserCurrent = new long[3];
        mSystemCurrent = new long[3];
        mTotalCurrent = new long[3];
        mHistory = new HistoryBuffer[3];
        mDualCpu = false;
        for (int i = 0; i < CPULOOP; ++i) {
            mHistory[i] = new HistoryBuffer();
        }
        readCpuInfo();
        readMaxFreq();
        readStats();
    }

    public HistoryBuffer[] getHistory() {
        return mHistory;
    }

    public void linkView(View view) {
        mView = view;
        readStats();
    }

    public void readCpuInfo() {
        FileReader fstream;
        try {
            fstream = new FileReader(CPUINFO_FILE);
        } catch (FileNotFoundException e) {
            Log.e(TAG, "Could not read " + CPUINFO_FILE);
            return;
        }
        BufferedReader in = new BufferedReader(fstream, 500);
        String line;
        try {
            while ((line = in.readLine()) != null) {
                if (line.contains("Atom") || line.contains("ARM")) {
                    String[] segs = line.trim().split(":");
                    mCpuType = segs[1];
                    break;
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Error checking CPU type:" + e.toString());
        }

    }

    public void readMaxFreq() {
        BufferedReader in;
        String line;
        //Read max frequency scaling
        try {
            Process process = Runtime.getRuntime().exec("/system/bin/cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_max_freq");
            in = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try {
            line = in.readLine();
            if (line != null) {
                mCpuMaxFreq0 = line;
            } else {
                mCpuMaxFreq0 = "NA";
                //Log.e(TAG, "Couldn't read current cpu freq");
            }
        } catch (IOException e) {
            Log.e(TAG, "Can't read cpu frequency");
        }

        try {
            Process process = Runtime.getRuntime().exec("/system/bin/cat /sys/devices/system/cpu/cpu1/cpufreq/scaling_max_freq");
            in = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try {
            line = in.readLine();
            if (line != null) {
                mCpuMaxFreq1 = line;
            } else {
                mCpuMaxFreq1 = "NA";
                //Log.e(TAG, "Couldn't read current cpu freq");
            }
        } catch (IOException e) {
            Log.e(TAG, "Can't read cpu frequency");
        }
    }

    public void readCurrFreq() {
        BufferedReader in;
        String line;
        try {
            Process process = Runtime.getRuntime().exec("/system/bin/cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq");
            in = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try {
            line = in.readLine();
            if (line != null) {
                mCpuFreq0 = line;
            } else {
                mCpuFreq0 = "NA";
                //Log.e(TAG, "Couldn't read current cpu freq");
            }
        } catch (IOException e) {
            Log.e(TAG, "Can't read cpu frequency");
        }

        try {
            Process process = Runtime.getRuntime().exec("/system/bin/cat /sys/devices/system/cpu/cpu1/cpufreq/scaling_cur_freq");
            in = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try {
            line = in.readLine();
            if (line != null) {
                mCpuFreq1 = line;
            } else {
                mCpuFreq1 = "NA";
                //Log.e(TAG, "Couldn't read current cpu freq");
            }
        } catch (IOException e) {
            Log.e(TAG, "Can't read cpu frequency");
        }
    }

    public void readStats() {
        FileReader fstream;
        try {
            fstream = new FileReader(STAT_FILE);
        } catch (FileNotFoundException e) {
            Log.e(TAG, "Could not read " + STAT_FILE);
            return;
        }
        BufferedReader in = new BufferedReader(fstream, 500);
        String line;
        boolean cpu = false;
        boolean cpu0 = false;
        boolean cpu1 = false;
        try {
            while ((line = in.readLine()) != null) {
                if (line.startsWith("cpu ")) {
                    updateStats(line.trim().split("[ ]+"), CPUAll);
                    cpu = true;
                    if (cpu && cpu0 && cpu1) break;
                } else if (line.startsWith("cpu0")) {
                    updateStats(line.trim().split("[ ]+"), CPU0);
                    cpu0 = true;
                    if (cpu && cpu0 && cpu1) break;
                } else if (line.startsWith("cpu1")) {
                    updateStats(line.trim().split("[ ]+"), CPU1);
                    cpu1 = true;
                    mDualCpu = true;
                    if (cpu && cpu0 && cpu1) break;
                }
            }
        } catch (IOException e) {
            Log.e(TAG, e.toString());
        }
        if (!cpu0) {
            mUserCurrent[CPU0] = 0;
            mSystemCurrent[CPU0] = 0;
            mTotalCurrent[CPU0] = 0;
        }
        if (!cpu1) {
            mUserCurrent[CPU1] = 0;
            mSystemCurrent[CPU1] = 0;
            mTotalCurrent[CPU1] = 0;
        }
        // Don't wast time running top if it won't be displayed
        if (mView != null) {
            readCurrFreq();
            mTopResults = captureTop();
        }
        return;
    }

    private void updateStats(String[] segs, int index) {
        // user = user + nice
        long user = Long.parseLong(segs[1]) + Long.parseLong(segs[2]);
        // system = system + intr + soft_irq
        long system = Long.parseLong(segs[3]) + Long.parseLong(segs[6]) + Long.parseLong(segs[7]);
        // total = user + system + idle + io_wait
        long total = user + system + Long.parseLong(segs[4]) + Long.parseLong(segs[5]);

        if (mTotal[index] != 0 || total >= mTotal[index]) {
            mUserCurrent[index] = user - mUser[index];
            mSystemCurrent[index] = system - mSystem[index];
            mTotalCurrent[index] = total - mTotal[index];
            if (mTotalCurrent[index] != 0) {
                mHistory[index].add((int) ((mUserCurrent[index] + mSystemCurrent[index]) * 100 / mTotalCurrent[index]));

            }
        }
        mUser[index] = user;
        mSystem[index] = system;
        mTotal[index] = total;
    }


    public void updateView(View updateview) {
        if (updateview != null) {
            TextView tv;
            for (int i = 0; i < CPULOOP; ++i) {
                tv = (TextView) (updateview.findViewById(viewlist[i]));
                if (tv != null && i == CPU1 && !mDualCpu) {
                    tv.setText("NA");
                } else if (tv != null) {

                    tv.setText(mPercentFmt.format(
                            (double) (mUserCurrent[i] + mSystemCurrent[i]) * 100.0 / mTotalCurrent[i]) + "% ("
                            + mPercentFmt.format(
                            (double) (mUserCurrent[i]) * 100.0 / mTotalCurrent[i]) + "/"
                            + mPercentFmt.format(
                            (double) (mSystemCurrent[i]) * 100.0 / mTotalCurrent[i]) + ")");
                }
            }
            if ((TextView) (updateview.findViewById(R.id.prochead)) != null && mTopResults != null) {
                for (int i = 0; i < PROCVIEWSIZE && i < mTopResults.size(); ++i) {
                    tv = (TextView) (updateview.findViewById(procviewlist[i]));
                    tv.setText(mTopResults.elementAt(i));
                }
            }
            tv = (TextView) (updateview.findViewById(R.id.currentfreq0));
            if (tv != null) {
                tv.setText(mCpuFreq0 + " (" + mCpuMaxFreq0 + ")");
            }
            tv = (TextView) (updateview.findViewById(R.id.currentfreq1));
            if (tv != null) {
                tv.setText(mCpuFreq1 + " (" + mCpuMaxFreq1 + ")");
            }
            tv = (TextView) (updateview.findViewById(R.id.cpudetails));
            if (tv != null) {
                tv.setText(mCpuType);
            }
        }
    }

    private Vector<String> captureTop() {
        try {
            Process process = Runtime.getRuntime().exec("/system/bin/top -n 1");
            mTopIn = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        if (mTopIn.markSupported()) {
            try {
                mTopIn.mark(100000);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        String line;
        try {
            mTopIn.reset();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        boolean foundbegin = false;
        int num_captured = 0;
        Vector<String> results = new Vector<String>();
        while ((line = getLine(mTopIn)) != null) {
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
            Log.i(TAG, "read error on getLine");
            return null;
        }
    }
}