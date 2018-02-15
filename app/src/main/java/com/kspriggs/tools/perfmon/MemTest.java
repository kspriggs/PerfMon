package com.kspriggs.tools.perfmon;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;

import android.os.Handler;

public final class MemTest {
    public Handler mMemTestHandler;
    WriteLog mWriteLog;
    public int mMemTestSize = 0;
    public int mNumTimesAllocated = 0;
    final public static int MAX_TIMES_PER_MEMLEVEL = 9;
    public int mNumTimesFailed = 0;
    final public static int MAX_TIMES_FAILED = 9;
    public ArrayList<MemInfo> mMemInfoList;


    public final class MemInfo {
        public long mMemTestTime;
        public ByteBuffer mMemTestBuff;
        public int mAvailMem;

        MemInfo(long time, ByteBuffer b, int avail) {
            mMemTestTime = time;
            mMemTestBuff = b;
            mAvailMem = avail;
        }
    }

    MemTest(Handler h, int size) {
        mMemTestHandler = h;
        mMemTestSize = size;
        mMemInfoList = new ArrayList<MemInfo>();
    }

    public boolean add(long time, ByteBuffer b, int avail) {
        boolean result = false;
        if (mNumTimesAllocated++ < MAX_TIMES_PER_MEMLEVEL) {
            mMemInfoList.add(new MemInfo(time, null, avail));
        } else {
            mNumTimesAllocated = 0;
            mMemInfoList.add(new MemInfo(time, b, avail));
            result = true;
        }
        return result;
    }

    public int getAverageTime() {
        if (mMemInfoList.size() > 0) {
            long sum = 0;
            Iterator<MemTest.MemInfo> i = mMemInfoList.iterator();
            while (i.hasNext()) {
                sum += i.next().mMemTestTime;
            }
            return (int) (sum / mMemInfoList.size());
        } else {
            return 0;
        }
    }

    public int getWorstTime() {
        if (mMemInfoList.size() > 0) {
            long worst = 0;
            Iterator<MemTest.MemInfo> i = mMemInfoList.iterator();
            while (i.hasNext()) {
                worst = (worst < i.next().mMemTestTime) ? i.next().mMemTestTime : worst;
            }
            return (int) (worst);
        } else {
            return 0;
        }
    }

    public void close() {
        mMemTestHandler = null;
        mMemTestSize = 0;
        mMemInfoList.clear();
        if (mWriteLog != null) {
            mWriteLog.close();
            mWriteLog = null;
        }
    }
}
