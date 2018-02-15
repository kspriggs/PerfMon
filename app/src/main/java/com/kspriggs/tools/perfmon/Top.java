/* Modified from http://code.google.com/p/android-labs/source/browse/trunk/NetMeter/src/com/google/android/netmeter/HistoryBuffer.java
 * https://github.com/dphans/android-labs/tree/master/NetMeter
*/

package com.kspriggs.tools.perfmon;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import android.util.Log;

class Top {
    final private String TAG = "PerfMon -> Top";

    class Task implements Comparable<Task> {
        final private String mCmd;
        final private long mPss;
        final private long mUsage;
        final private int mOomAdj;
        private int mSortType;


        Task(final String command, final long usage, final int oomadj, final long pss, int sort) {
            mCmd = command;
            mUsage = usage;
            mOomAdj = oomadj;
            mPss = pss;
            mSortType = sort;
        }

        Task delta(Task prev, long total) {
            return new Task(mCmd, (mUsage - prev.mUsage) * 1000 / total, mOomAdj, mPss, mSortType);
        }

        String getName() {
            return mCmd;
        }

        long getUsage() {
            return mUsage;
        }

        int getOomAdj() {
            return mOomAdj;
        }

        long getPss() {
            return mPss;
        }

        public int compareTo(Task other) {
            if (mSortType == SORT_CPU) {
                if (mUsage == other.mUsage) return mCmd.compareTo(other.mCmd);
                else return -(int) (mUsage - other.mUsage);
            } else if (mSortType == SORT_PSS) {
                if (mPss == other.mPss) return mCmd.compareTo(other.mCmd);
                else return -(int) (mPss - other.mPss);
            } else {
                return 0;
            }
        }

    }

    static final public int SORT_PSS = 0;
    static final public int SORT_CPU = 1;
    Map<Integer, Task> mPrevState;
    long mPrevCpuTime;
    private BufferedReader mProcrankIn;
    private int mSortType;


    Top(int sort_type) {
        mPrevCpuTime = readCpuTime();
        mPrevState = readProcInfo();
        mSortType = sort_type;
    }

    public void setSortType(int sort_type) {
        mSortType = sort_type;
    }

    public Vector<Task> getTopN() {
        Map<Integer, Task> current = readProcInfo();
        long cpu_time = readCpuTime();
        long delta_time = cpu_time - mPrevCpuTime;

        Set<Integer> pids = current.keySet();
        pids.retainAll(mPrevState.keySet());

        Vector<Task> results = new Vector<Task>();
        for (Iterator<Integer> it = pids.iterator(); it.hasNext(); ) {
            int index = it.next();
            results.add(current.get(index).delta(mPrevState.get(index), delta_time));
        }
        Collections.sort(results);

        mPrevState = current;
        mPrevCpuTime = cpu_time;
        return results;
    }

    private Map<Integer, Task> readProcInfo() {
        Map<Integer, Task> stats = new HashMap<Integer, Task>();
        File proc_dir = new File("/proc/");

        String files[] = proc_dir.list();
        captureProcrank();
        for (int i = 0; i < files.length; ++i) {
            if (files[i].matches("[0-9]+") == true) {

                String stat = readData("/proc/" + files[i] + "/stat");
                if (stat == null) continue;
                String[] segs = stat.split("[ ]+");
                long runtime = Long.parseLong(segs[13]) + Long.parseLong(segs[14]);

                String cmdline = segs[1].substring(1, segs[1].length() - 1);
                if (cmdline.contains("app_process")) {
                    String pkg_name = readData("/proc/" + files[i] + "/cmdline");

                    cmdline = cleanCmdline(pkg_name);
                }

                stat = readData("/proc/" + files[i] + "/oom_adj");
                int oom_adj = (stat != null) ? Integer.parseInt(stat) : 0;

                //long psstot = readPssFromProc("/proc/" + files[i] + "/smaps");
                long psstot = readPss(cmdline);


                stats.put(Integer.parseInt(files[i]),
                        new Task(cmdline, runtime, oom_adj, psstot, mSortType));
            }
        }
        return stats;
    }

    private long readCpuTime() {
        String cpustat = readData("proc/stat");
        if (cpustat == null) {
            return 0;
        }
        String[] segs = cpustat.split("[ ]+");

        return Long.parseLong(segs[1]) + Long.parseLong(segs[2])
                + Long.parseLong(segs[3]) + Long.parseLong(segs[4]);

    }

    private void captureProcrank() {
        try {
            Process process = Runtime.getRuntime().exec("/system/xbin/procrank");
            mProcrankIn = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        if (mProcrankIn.markSupported()) {
            try {
                mProcrankIn.mark(100000);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private long readPss(String cmdline) {
        String line;
        long pss_total = 0;

        try {
            mProcrankIn.reset();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        String[] segs;
        int pssloc = 0;
        while ((line = getLine(mProcrankIn)) != null) {
            if (pssloc == 0 && line.contains("PID") && line.contains("Pss")) {
                segs = line.split("[ ]+");
                for (int i = 0; i < segs.length; i++) {
                    if (segs[i].contains("Pss")) {
                        pssloc = i;
                    }
                }
            } else if (line.contains(cmdline)) {
                segs = line.split("[ ]+");
                pss_total += Long.parseLong(segs[pssloc].substring(0, (segs[pssloc].length() - 1)));
                break;
            }
        }
        return pss_total;
    }

    //This method does not seem to function properly as most /proc/smap files are zero length when viewed
    //from an android app.
    private long readPssFromProc(String filename) {
        FileReader fstream;
        String line;
        long pss_total = 0;
        try {
            fstream = new FileReader(filename);
        } catch (FileNotFoundException e) {
            Log.i(TAG, "File access error " + filename);
            return 0;
        }

        BufferedReader in = new BufferedReader(fstream);
        while ((line = getLine(in)) != null) {
            if (line.contains("Pss:")) {
                String[] segs = line.split("[ ]+");
                pss_total += Long.parseLong(segs[1]);
            }
        }
        try {
            in.close();
        } catch (IOException e) {
            Log.i(TAG, "BufferedReader");
            return 0;
        }
        try {
            fstream.close();
        } catch (IOException e) {
            Log.i(TAG, "Error closing BufferedReader");
            return 0;
        }
        return pss_total;
    }

    private String getLine(BufferedReader in) {
        try {
            return in.readLine();
        } catch (IOException e) {
            Log.i(TAG, "read error on");
            return null;
        }
    }

    private String readData(String filename) {
        FileReader fstream;
        try {
            fstream = new FileReader(filename);
        } catch (FileNotFoundException e) {
            Log.i(TAG, "File access error " + filename);
            return null;
        }

        BufferedReader in = new BufferedReader(fstream, 500);
        try {
            return in.readLine();
        } catch (IOException e) {
            Log.i(TAG, "read error on " + filename);
            return null;
        }
    }

    private String cleanCmdline(String raw) {
        if (raw == null) {
            return "<invalid>";
        }
        for (int i = 0; i < raw.length(); i++) {
            if (Character.isIdentifierIgnorable(raw.charAt(i))) {
                return raw.substring(0, i);
            }
        }
        return raw;
    }
}
