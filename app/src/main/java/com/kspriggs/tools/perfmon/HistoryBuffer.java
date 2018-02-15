/* Modified from http://code.google.com/p/android-labs/source/browse/trunk/NetMeter/src/com/google/android/netmeter/HistoryBuffer.java
 * https://github.com/dphans/android-labs/tree/master/NetMeter
 *
 */
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

public class HistoryBuffer {

    private CircularBuffer mHourly = null;
    private CircularBuffer mSixHours = null;
    private CircularBuffer mDaily = null;
    private int mThreshold = 0;

    class CircularBuffer {
        public final static int NOT_SET = 0;
        public final static int TEST_START = 1;
        public final static int TEST_END = 2;
        final private double EMA_FILTER = 0.5;
        private CircData[] mCircData;
        final private int mCapacity;
        final private int mSampleRate;
        private int mSize = 0;
        private int mWritePos = 0;
        private int mSum;
        private int mSampleCount;
        private double mEMA = 0;
        private int mSetTest = NOT_SET;

        public class CircData {
            public int mData;
            public int mTestBounds;

            public CircData() {
                mData = 0;
                mTestBounds = NOT_SET;
            }
        }

        public CircularBuffer(int size, int sampling) {
            mCircData = new CircData[size];
            for (int i = 0; i < size; ++i) {
                mCircData[i] = new CircData();
            }
            mCapacity = size;
            mSampleRate = sampling;
            mSum = 0;
            mSampleCount = 0;
        }

        final public void add(int element) {
                    /*
                	 * This implements a running average - don't want this....
                        mSum += element;
                        if (++mSampleCount < mSampleRate) return;
                        mEMA = (1.0 - EMA_FILTER) * mEMA + EMA_FILTER * (mSum / mSampleRate);
                        if (mSize < mCapacity) {
                                mCircData[mWritePos] = (int)mEMA;
                                ++mSize;
                        } else {
                                mCircData[mWritePos] = (int)mEMA;
                        }
                        ++mWritePos;
                        mWritePos %= mCapacity;
                        mSum = 0;
                        mSampleCount = 0;
                        mSum += element;
                        */
            if (++mSampleCount < mSampleRate) return;
            mCircData[mWritePos].mData = element;
            if (mSetTest != NOT_SET) {
                mCircData[mWritePos].mTestBounds = mSetTest;
                mSetTest = NOT_SET;
            }
            ++mWritePos;
            if (mSize < mCapacity) ++mSize;
            mWritePos %= mCapacity;
            //mSum = 0;
            mSampleCount = 0;
        }

        final public void reset() {
            mSize = 0;
            mWritePos = 0;
            mSum = 0;
            mSetTest = NOT_SET;
        }

        final public void resetTest() {
            for (int i = 0; i < mSize; ++i) {
                mCircData[i].mTestBounds = NOT_SET;
            }
        }

        final public void setTestStart() {
            mSetTest = TEST_START;
        }

        final public void setTestEnd() {
            mSetTest = TEST_END;
        }

        final public int getLatest() {
            if (mSize == 0) return 0;
            if (mWritePos == 0) return mCircData[mCapacity - mWritePos - 1].mData;
            return mCircData[mWritePos - 1].mData;
        }

        final public int lookBackStatus(int steps) {
            if (mSize == 0) return NOT_SET;
            if (steps > mWritePos - 1) {
                return mCircData[mCapacity - (steps - (mWritePos - 1))].mTestBounds;
            } else {
                return mCircData[mWritePos - 1 - steps].mTestBounds;
            }
        }

        final public int lookBack(int steps) {
            if (mSize == 0) return 0;
            if (steps > mWritePos - 1) {
                return mCircData[mCapacity - (steps - (mWritePos - 1))].mData;
            } else {
                return mCircData[mWritePos - 1 - steps].mData;
            }
        }

        final public int getSize() {
            return mSize;
        }

        final public int getCapacity() {
            return mCapacity;
        }

        final public int getMax(int window) {
            int max = Integer.MIN_VALUE;
            if (window >= mSize) {
                window = mSize - 1;
            }
            for (int i = 0; i < window; ++i) {
                if (lookBack(i) > max) {
                    max = lookBack(i);
                }
            }
            return max;
        }

        final public int getMin(int window) {
            int min = Integer.MAX_VALUE;
            if (window >= mSize) {
                window = mSize - 1;
            }
            for (int i = 0; i < window; ++i) {
                if (lookBack(i) < min) {
                    min = lookBack(i);
                }
            }
            return min;
        }
    }

    public HistoryBuffer() {
        mHourly = new CircularBuffer(720, 1);
        mSixHours = new CircularBuffer(360, 12);
        mDaily = new CircularBuffer(720, 24);
    }

    public void add(int element) {
        mHourly.add(element);
        mSixHours.add(element);
        mDaily.add(element);
    }

    final public void setThreshold(int thresh) {
        mThreshold = thresh;
    }

    final public int getThreshold() {
        return mThreshold;
    }

    public void reset() {
        mHourly.reset();
        mSixHours.reset();
        mDaily.reset();
        mThreshold = 0;
    }

    public void resetTest() {
        mHourly.resetTest();
        mSixHours.resetTest();
        mDaily.resetTest();
    }

    public void setTestStart() {
        mHourly.setTestStart();
        mSixHours.setTestStart();
        mDaily.setTestStart();
    }

    public void setTestEnd() {
        mHourly.setTestEnd();
        mSixHours.setTestEnd();
        mDaily.setTestEnd();
    }

    public void pad(int count) {
        for (int i = 0; i < count; i++) {
            add(0);
        }
    }

    public CircularBuffer getData(int resolution) {
        switch (resolution) {
            case 0:
            case 1:
            case 2:
                return mHourly;
            case 3:
            case 4:
                return mSixHours;
            default:
                return mDaily;
        }
    }

    public CircularBuffer getData() {
        return getData(getMaxTimescale());
    }

    private int getMaxTimescale() {
        int capacity = getData(5).getCapacity();
        int size = getData(5).getSize();

        capacity -= capacity / 10;
        if (size > capacity / 2) return 6;
        if (size > capacity / 4) return 5;
        if (size > capacity / 8) return 4;
        if (size > capacity / 24) return 3;
        if (size > capacity / 48) return 2;
        if (size > capacity / 96) return 1;
        return 0;
    }
}