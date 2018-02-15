package com.kspriggs.tools.perfmon;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Vector;

import android.os.Environment;
import android.util.Log;

import com.kspriggs.tools.perfmon.HistoryBuffer.CircularBuffer;
import com.kspriggs.tools.perfmon.MemMon.ProcessMemInfo;

public class WriteLog {
    final static String TAG = "PerfMon -> WriteLog";
    boolean mExternalStorageAvailable = false;
    boolean mExternalStorageWriteable = false;
    File mLogFile;
    private BufferedWriter mWriter;

    public WriteLog(File pathDir, String filetype) throws IOException {
        checkExternalStorage();
        if (mExternalStorageWriteable) {
            try {
                mLogFile = new File(pathDir, "PerfLog_" + filetype);
                if (!mLogFile.exists()) {
                    mLogFile.createNewFile();
                }
                FileWriter writer = new FileWriter(mLogFile);
                mWriter = new BufferedWriter(writer);
                Log.i(TAG, "Created file" + mLogFile.getName());
            } catch (IOException e) {
                Log.i(TAG, "Unable to create file");
                throw e;
            }
        } else {
            throw new IOException();
        }
    }

    public String getName() {
        return mLogFile.getName();
    }

    private void checkExternalStorage() {
        String state = Environment.getExternalStorageState();

        if (Environment.MEDIA_MOUNTED.equals(state)) {
            // We can read and write the media
            mExternalStorageAvailable = mExternalStorageWriteable = true;
        } else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
            // We can only read the media
            mExternalStorageAvailable = true;
            mExternalStorageWriteable = false;
        } else {
            // Something else is wrong. It may be one of many other states, but all we need
            //  to know is we can neither read nor write
            mExternalStorageAvailable = mExternalStorageWriteable = false;
        }
    }

    public void close() {
        try {
            mWriter.close();
        } catch (IOException e) {
            Log.e(TAG, "Cant close log file");
        }
    }

    // Generic writer
    public void write(String data) {
        try {
            mWriter.write(data);
        } catch (IOException e) {
            Log.e(TAG, "Can't write to log file");
        }
    }

    // Specific Low Memory Writer
    public void write(ArrayList<String> proclist, Vector<ProcessMemInfo> histproclist, MemMon.PhoneMemInfo meminfo) {
        try {
            if (meminfo != null) {

                mWriter.write("Current /proc/meminfo data:\n");
                mWriter.write("MemTotal:\t" + Long.toString(meminfo.mMemTotal / MemMon.MEG) + "\n");
                mWriter.write("MemFree+Cached:\t" + Long.toString((meminfo.mMemFree + meminfo.mCached) / MemMon.MEG) + "\n");
                mWriter.write("MemFree:\t" + Long.toString(meminfo.mMemFree / MemMon.MEG) + "\n");
                mWriter.write("MemBuffers:\t" + Long.toString(meminfo.mBuffers / MemMon.MEG) + "\n");
                mWriter.write("MemCached:\t" + Long.toString(meminfo.mCached / MemMon.MEG) + "\n");
                mWriter.write("MemSwapTotal:\t" + Long.toString(meminfo.mSwapTotal / MemMon.MEG) + "\n");
                mWriter.write("MemSwapFree:\t" + Long.toString(meminfo.mSwapFree / MemMon.MEG) + "\n");
            }

            if (proclist != null) {
                mWriter.write("\nCurrent Procrank data:\n");
                Iterator<String> i = proclist.iterator();
                while (i.hasNext()) {
                    mWriter.write(i.next() + "\n");
                }
            }
            if (histproclist != null && !histproclist.isEmpty()) {
                mWriter.write("\nHistorical Process data:\n");
                for (Iterator<ProcessMemInfo> it = histproclist.iterator(); it.hasNext(); ) {
                    ProcessMemInfo task = it.next();
                    mWriter.write(String.format("%5d  ", task.getPss())
                            + String.format("%5d  ", task.getPssAvg())
                            + task.getName());
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Can't write to log file");
        }
    }

    // Specific History Buffer Writing
    public void write(HistoryBuffer hist) {
        try {
            CircularBuffer data = hist.getData();
            boolean test_start = false;
            int min = Integer.MAX_VALUE;
            int max = Integer.MIN_VALUE;
            for (int i = data.getSize(); i >= 0; --i) {
                if (!test_start && data.lookBackStatus(i) == CircularBuffer.TEST_START) {
                    test_start = true;
                    min = (min < data.lookBack(i) ? min : data.lookBack(i));
                    max = (max > data.lookBack(i) ? max : data.lookBack(i));
                    mWriter.write("Raw Test data (PSS MB):\t");
                    mWriter.write(Integer.toString(data.lookBack(i)) + "\t");
                } else if (test_start) {
                    min = (min < data.lookBack(i) ? min : data.lookBack(i));
                    max = (max > data.lookBack(i) ? max : data.lookBack(i));
                    mWriter.write(Integer.toString(data.lookBack(i)) + "\t");
                    if (data.lookBackStatus(i) == CircularBuffer.TEST_END) {
                        test_start = false;
                        mWriter.write("\nMaximum Value found (PSS MB):\t" + Integer.toString(max));
                        mWriter.write("\nMinimum Value found (PSS MB):\t" + Integer.toString(min) + "\n\n");
                        min = Integer.MAX_VALUE;
                        max = Integer.MIN_VALUE;
                    }
                }
            }
            if (test_start) {
                //Never found Test End - quite possible as the marker isn't set until the next
                //data collection period in the service.  So, mark end of test here.
                mWriter.write("\nMaximum Value found (PSS MB):\t" + Integer.toString(max));
                mWriter.write("\nMinimum Value found (PSS MB):\t" + Integer.toString(min) + "\n\n");
                min = Integer.MAX_VALUE;
                max = Integer.MIN_VALUE;
            }
        } catch (IOException e) {
            Log.e(TAG, "Can't write to log file");
        }
    }
}
