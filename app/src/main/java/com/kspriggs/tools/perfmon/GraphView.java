/* Modified from http://code.google.com/p/android-labs/source/browse/trunk/NetMeter/src/com/google/android/netmeter/HistoryBuffer.java
 * https://github.com/dphans/android-labs/tree/master/NetMeter
 *
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

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import com.kspriggs.tools.perfmon.HistoryBuffer.CircularBuffer;


class GraphView extends View {
    public final static int SINGLE_GRAPH = 0;
    public final static int DOUBLE_GRAPH = 1;
    public final static int TRIPLE_GRAPH = 2;
    final private int TICKS = 3;
    final private Paint mBackgroundPaint = makePaint(Color.BLACK);
    final private Paint mAxisPaint = makePaint(Color.LTGRAY);
    final private Paint mTextPaint = makePaint(Color.WHITE);
    final private Paint mLastTextPaint = makePaint(Color.GREEN);
    final private Paint mGraphPaint = makePaint(Color.GREEN);
    final private Paint mGraph2Paint = makePaint(Color.YELLOW);
    final private Paint mGraph3Paint = makePaint(Color.WHITE);
    final private Paint mMaxPaint = makePaint(Color.RED);
    final private Paint mMinPaint = makePaint(Color.CYAN);
    final private Paint mTestPaint = makePaint(Color.MAGENTA);
    final private Paint mBannerPaint = makePaint(Color.WHITE);
    final private Paint mThreshPaint = makePaint(Color.BLUE);

    private HistoryBuffer mDataCounter = null;
    private HistoryBuffer mDataCounter2 = null;
    private HistoryBuffer mDataCounter3 = null;

    private int mResolution = 0;
    private int mRefreshTicks = 0;

    private String mTitle;
    private String mFooter;
    private String mUnit;
    private int mFixedYScale = 0;


    class Projection {
        final public int mWidth;
        final public int mHeight;
        final public int mOffset;
        final public int mXrange;
        final public int mYrange;
        final private float mXscale;
        final private float mYscale;

        public Projection(int width, int height, int offset,
                          int x_range, int y_range) {
            mWidth = width;
            mHeight = height;
            mOffset = offset;
            mXrange = x_range;
            mYrange = y_range;
            mXscale = (float) (width) / x_range;
            mYscale = (float) (height) / y_range;
        }

        public float x(int x) {
            return x * mXscale + 5;
        }

        public float y(int y) {
            return mHeight - y * mYscale + mOffset;
        }
    }

    public GraphView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mTitle = new String();
        mFooter = new String();
        mUnit = new String();
    }

    public String toggleScale() {
        mResolution += 1;
        mResolution %= 7;
        if (mResolution > getMaxTimescale()) {
            mResolution = 0;
        }
        invalidate();
        mRefreshTicks = mResolution * 2 + 1;
        return getBanner();
    }

    public void refresh() {
        if (mRefreshTicks == 0) {
            invalidate();
            mRefreshTicks = mResolution * 2 + 1;
        } else {
            --mRefreshTicks;
        }
    }

    public void linkCounters(HistoryBuffer cpu, String title, String unit, String footer) {
        mDataCounter = cpu;
        mResolution = getMaxTimescale();
        mTitle = title;
        mFooter = footer;
        mUnit = unit;
        mFixedYScale = 0;
        invalidate();
    }

    public void linkCounters(HistoryBuffer cpu, String title, String unit, int fixed_yscale, String footer) {
        mDataCounter = cpu;
        mResolution = getMaxTimescale();
        mTitle = title;
        mFooter = footer;
        mUnit = unit;
        mFixedYScale = fixed_yscale;
        invalidate();
    }

    public void linkCounters(HistoryBuffer first, HistoryBuffer second, HistoryBuffer third, String title, String unit, int fixed_yscale, String footer) {
        mDataCounter = first;
        mDataCounter2 = second;
        mDataCounter3 = third;
        mResolution = getMaxTimescale();
        mTitle = title;
        mFooter = footer;
        mUnit = unit;
        mFixedYScale = fixed_yscale;
        invalidate();
    }

    public void resetCounters() {
        mDataCounter.reset();
        mDataCounter.resetTest();
        if (mDataCounter2 != null) {
            mDataCounter2.reset();
            mDataCounter2.resetTest();
        }
        if (mDataCounter3 != null) {
            mDataCounter3.reset();
            mDataCounter3.resetTest();
        }
    }

    @Override
    public void onDraw(Canvas canvas) {
        //canvas.drawPaint(mBackgroundPaint);
        if (mDataCounter == null) return;

        Projection proj = getDataScale();
        drawAxis(canvas, proj, mTitle, mUnit);

        canvas.drawText(mFooter + " - " + getBanner(),
                proj.x(proj.mXrange / 3), proj.y(0) + 12,
                mBannerPaint);

        canvas.drawText("Last Measured: " + Integer.toString(mDataCounter.getData(mResolution).getLatest()),
                proj.x(0) + 32, proj.y(0) + 12,
                mLastTextPaint);

        int y_max = getMax();
        int y_min = getMin();

        if (y_max - y_min > (proj.mYrange * 10 / 100)) {
            canvas.drawText(Integer.toString(y_max), proj.x(proj.mXrange / 4 - 10),
                    proj.y(y_max - 10),
                    mMaxPaint);

            drawMinMax(canvas, proj, mMaxPaint, mDataCounter.getData(mResolution),
                    y_max);
        }
        if (y_max - y_min > (proj.mYrange * 10 / 100)) {
            canvas.drawText(Integer.toString(y_min), proj.x(proj.mXrange / 4),
                    proj.y(y_min),
                    mMinPaint);
            drawMinMax(canvas, proj, mMinPaint, mDataCounter.getData(mResolution),
                    y_min);
        }
        if (mDataCounter2 != null) {
            int thresh = mDataCounter2.getThreshold();
            if (thresh > 0) {
                canvas.drawText("LMK Thresh:" + Integer.toString(thresh), proj.x(proj.mXrange / 4),
                        proj.y(thresh),
                        mThreshPaint);
                drawMinMax(canvas, proj, mThreshPaint, mDataCounter.getData(mResolution),
                        thresh);
            }
            drawGraph(canvas, proj, mGraph2Paint,
                    mDataCounter2.getData(mResolution));
        }
        if (mDataCounter3 != null) {
            drawGraph(canvas, proj, mGraph3Paint,
                    mDataCounter3.getData(mResolution));
        }
        drawGraph(canvas, proj, mGraphPaint,
                mDataCounter.getData(mResolution));
    }

    private Projection getDataScale() {
        int xscale = mDataCounter.getData(mResolution).getCapacity();
        if (mDataCounter2 != null) {
            int xscale2 = mDataCounter2.getData(mResolution).getCapacity();
            xscale = (xscale2 > xscale) ? xscale2 : xscale;
        }
        if (mDataCounter3 != null) {
            int xscale3 = mDataCounter3.getData(mResolution).getCapacity();
            xscale = (xscale3 > xscale) ? xscale3 : xscale;
        }
        if (mResolution == 0) {
            xscale /= 4;
        } else if (mResolution % 2 == 1) {
            xscale /= 2;
        }
        int height = (getHeight() - 15);
        int yscale = 10;
        if (mFixedYScale == 0) {
            int val = mDataCounter.getData(mResolution).getMax(xscale);
            if (mDataCounter2 != null) {
                int yscale2 = mDataCounter2.getData(mResolution).getMax(xscale);
                val = (yscale2 > val) ? yscale2 : val;
            }
            if (mDataCounter3 != null) {
                int yscale3 = mDataCounter3.getData(mResolution).getMax(xscale);
                val = (yscale3 > val) ? yscale3 : val;
            }
            if (val > yscale) yscale = val;
        } else {
            yscale = mFixedYScale;
        }
        return new Projection(getWidth() - 10,
                height - 5,
                0,
                xscale, yscale);
    }

    private int getMaxTimescale() {
        int capacity = mDataCounter.getData(5).getCapacity();
        int size = mDataCounter.getData(5).getSize();
        if (mDataCounter2 != null) {
            int val = mDataCounter2.getData(5).getSize();
            size = (val > size) ? val : size;
        }
        if (mDataCounter3 != null) {
            int val = mDataCounter3.getData(5).getSize();
            size = (val > size) ? val : size;
        }

        capacity -= capacity / 10;
        if (size > capacity / 2) return 6;
        if (size > capacity / 4) return 5;
        if (size > capacity / 8) return 4;
        if (size > capacity / 24) return 3;
        if (size > capacity / 48) return 2;
        if (size > capacity / 96) return 1;
        return 0;
    }

    private String getBanner() {
        switch (mResolution) {
            case 0:
                return "15min";
            case 1:
                return "30min";
            case 2:
                return "1hour";
            case 3:
                return "3hours";
            case 4:
                return "6hours";
            case 5:
                return "12hours";
            case 6:
                return "24hours";
            default:
                return "invalid";
        }
    }

    private int getMax() {
        int xscale = mDataCounter.getData(mResolution).getCapacity();

        if (mResolution == 0) {
            xscale /= 4;
        } else if (mResolution % 2 == 1) {
            xscale /= 2;
        }
        return mDataCounter.getData(mResolution).getMax(xscale);
    }

    private int getMin() {
        int xscale = mDataCounter.getData(mResolution).getCapacity();
        if (mResolution == 0) {
            xscale /= 4;
        } else if (mResolution % 2 == 1) {
            xscale /= 2;
        }
        return mDataCounter.getData(mResolution).getMin(xscale);
    }

    private void drawGraph(Canvas canvas,
                           Projection proj,
                           Paint color,
                           CircularBuffer data) {
        int y_start = data.lookBack(0);
        if (data.lookBackStatus(0) == CircularBuffer.TEST_START || data.lookBackStatus(0) == CircularBuffer.TEST_END) {
            canvas.drawLine(proj.x(proj.mXrange), proj.y(0), proj.x(proj.mXrange),
                    proj.y(proj.mYrange),
                    mTestPaint);
        }
        int y_end;
        for (int i = 1; i < data.getSize(); ++i) {
            y_end = data.lookBack(i);
            canvas.drawLine(proj.x(proj.mXrange - i + 1), proj.y(y_start),
                    proj.x(proj.mXrange - i), proj.y(y_end),
                    color);
            y_start = y_end;

            if (data.lookBackStatus(i) == CircularBuffer.TEST_START || data.lookBackStatus(i) == CircularBuffer.TEST_END) {
                canvas.drawLine(proj.x(proj.mXrange - i), proj.y(0), proj.x(proj.mXrange - i),
                        proj.y(proj.mYrange),
                        mTestPaint);
                /*
				if(data.getTestStart() == i)
				{
					canvas.drawText("Test", proj.x(proj.mXrange - i) + 10, proj.y(y_end) - 10,
							mTestPaint);
				}
				*/
            }
        }
    }

    private void drawMinMax(Canvas canvas,
                            Projection proj,
                            Paint color,
                            CircularBuffer data,
                            int minmax) {
        int y_start = minmax;
        int y_end = minmax;

        for (int i = 1; i < data.getSize(); ++i) {
            canvas.drawLine(proj.x(proj.mXrange - i + 1), proj.y(y_start),
                    proj.x(proj.mXrange - i), proj.y(y_end),
                    color);
        }
    }

    private void drawAxis(Canvas canvas, Projection proj,
                          String title, String unit) {

        canvas.drawLine(proj.x(0), proj.y(0),
                proj.x(proj.mXrange), proj.y(0),
                mAxisPaint);


        canvas.drawLine(proj.x(0), proj.y(0), proj.x(0),
                proj.y(proj.mYrange),
                mAxisPaint);


        int x_step = proj.mXrange / TICKS;
        int y_step = proj.mYrange / TICKS;
        for (int i = 1; i <= TICKS; ++i) {
            canvas.drawLine(proj.x(x_step * i), proj.y(0),
                    proj.x(x_step * i), proj.y(0) - 10, mAxisPaint);
            canvas.drawLine(proj.x(0), proj.y(y_step * i),
                    proj.x(0) + 10, proj.y(y_step * i), mAxisPaint);
        }
        canvas.drawText(Integer.toString(proj.mYrange) + unit,
                proj.x(0) + 10, proj.y(proj.mYrange) + 10, mTextPaint);

        canvas.drawText(title, proj.x(proj.mXrange / 3),
                proj.y(proj.mYrange) + 10,
                mTextPaint);
    }


    private Paint makePaint(int color) {
        Paint p = new Paint();
        p.setColor(color);
        p.setTextSize(12);
        return p;
    }
}