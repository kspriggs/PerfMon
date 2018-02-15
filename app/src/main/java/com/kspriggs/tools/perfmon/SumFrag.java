package com.kspriggs.tools.perfmon;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

import android.app.Activity;
import android.app.Fragment;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;


public class SumFrag extends Fragment {
    final private String TAG = "PerfMon -> SumFragment";
    static final public int PAGESIZE = 4;
    static final public int MEG = 1024;
    final private int VIEWSIZE = 3;
    final private int[] viewlist = {R.id.cpuAllstat, R.id.cpu0stat, R.id.cpu1stat};
    private View mSumView;
    private PerfService mPerfService;

    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {

            // Get reference to (local) service from binder
            mPerfService = ((PerfService.PerfBinder) service).getService();
            mPerfService.setSumView(mSumView);
        }

        public void onServiceDisconnected(ComponentName className) {
            mPerfService = null;
        }
    };

    /**
     * When creating, retrieve this instance's number from its arguments.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        mSumView = inflater.inflate(R.layout.summary_layout, container, false);
        if (mPerfService != null) {
            mPerfService.forceCpuDisplayUpdate(mSumView);
        } else {
            TextView tv;
            for (int i = 0; i < VIEWSIZE; ++i) {
                tv = (TextView) (mSumView.findViewById(viewlist[i]));
                if (tv != null) tv.setText(R.string.populating);
            }
        }
        DisplayMetrics metrics = new DisplayMetrics();
        getActivity().getWindowManager().getDefaultDisplay().getMetrics(metrics);
        int width = (int) (metrics.widthPixels * 160 / metrics.densityDpi);
        int height = (int) (metrics.heightPixels * 160 / metrics.densityDpi);
        TextView tv = (TextView) mSumView.findViewById(R.id.displayheightpx);
        if (tv != null) tv.setText(Integer.toString(metrics.heightPixels));
        tv = (TextView) mSumView.findViewById(R.id.displaywidthpx);
        if (tv != null) tv.setText(Integer.toString(metrics.widthPixels));
        tv = (TextView) mSumView.findViewById(R.id.displayheight);
        if (tv != null) tv.setText(Integer.toString(height));
        tv = (TextView) mSumView.findViewById(R.id.displaywidth);
        if (tv != null) tv.setText(Integer.toString(width));
        tv = (TextView) mSumView.findViewById(R.id.displaydensity);
        if (tv != null) tv.setText(Float.toString(metrics.densityDpi));
        populateLmkData();
        return mSumView;
    }

    /**
     * Framework method called when activity becomes the foreground activity.
     * <p/>
     * onResume/onPause implement the most narrow window of activity life-cycle
     * during which the activity is in focus and foreground.
     */
    @Override
    public void onResume() {
        super.onResume();
        Activity a = getActivity();
        Intent i = new Intent(getActivity(), PerfService.class);
        i.putExtra(PerfMonActivity.FRAG_TYPE, PerfMonActivity.FRAG_TYPE_SUM);
        a.bindService(i, mConnection, Context.BIND_AUTO_CREATE);
    }

    /**
     * Framework method called when activity looses foreground position
     */
    @Override
    public void onPause() {
        super.onPause();
        Activity a = getActivity();
        a.unbindService(mConnection);
    }

    @Override
    public void onDestroyView() {
        // TODO Auto-generated method stub
        super.onDestroyView();
        mSumView = null;
        if (mPerfService != null) mPerfService.setSumView(mSumView);
    }

    private void populateLmkData() {
        String lmk = readData("/sys/module/lowmemorykiller/parameters/minfree");
        if (lmk == null) {
            return;
        }
        String[] segs = lmk.split(",");

        TextView tv = (TextView) mSumView.findViewById(R.id.foregroundAppThreshold);
        Long val = Long.parseLong(segs[0]) * PAGESIZE / MEG;
        if (tv != null) tv.setText(Long.toString(val));
        tv = (TextView) mSumView.findViewById(R.id.visibleAppThreshold);
        val = Long.parseLong(segs[1]) * PAGESIZE / MEG;
        if (tv != null) tv.setText(Long.toString(val));
        tv = (TextView) mSumView.findViewById(R.id.secondaryServerThreshold);
        val = Long.parseLong(segs[2]) * PAGESIZE / MEG;
        if (tv != null) tv.setText(Long.toString(val));
        tv = (TextView) mSumView.findViewById(R.id.hiddenAppThreshold);
        val = Long.parseLong(segs[3]) * PAGESIZE / MEG;
        if (tv != null) tv.setText(Long.toString(val));
        tv = (TextView) mSumView.findViewById(R.id.contentProviderThreshold);
        val = Long.parseLong(segs[4]) * PAGESIZE / MEG;
        if (tv != null) tv.setText(Long.toString(val));
        tv = (TextView) mSumView.findViewById(R.id.emptyAppThreshold);
        val = Long.parseLong(segs[5]) * PAGESIZE / MEG;
        if (tv != null) tv.setText(Long.toString(val));
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
}
