package com.kspriggs.tools.perfmon;

import android.app.Activity;
import android.app.Fragment;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;


public class CpuFrag extends Fragment {
    final private String TAG = "PerfMon -> CpuFragment";
    final private int VIEWSIZE = 6;
    final private int[] viewlist = {R.id.cpuAllstat, R.id.cpu0stat, R.id.cpu1stat, R.id.cpudetails, R.id.currentfreq0, R.id.currentfreq1};
    private View mCpuView;
    private PerfService mPerfService;
    private boolean mGraphModeDetail = false;

    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.v(TAG, "onServiceConnected");
            // Get reference to (local) service from binder
            mPerfService = ((PerfService.PerfBinder) service).getService();
            mPerfService.setCpuView(mCpuView, mGraphModeDetail);
            mPerfService.forceCpuDisplayUpdate(mCpuView);
        }

        public void onServiceDisconnected(ComponentName className) {
            Log.v(TAG, "onServiceDisconnected");
            mPerfService = null;
        }
    };

    /**
     * When creating, retrieve this instance's number from its arguments.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.v(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        Log.v(TAG, "onCreateView");
        mCpuView = inflater.inflate(R.layout.cpu_layout, container, false);
        if (mPerfService != null) {
            mPerfService.forceCpuDisplayUpdate(mCpuView);
        } else {
            TextView tv;
            for (int i = 0; i < VIEWSIZE; ++i) {
                tv = (TextView) (mCpuView.findViewById(viewlist[i]));
                if (tv != null) tv.setText(R.string.populating);
            }
        }
        return mCpuView;
    }

    /**
     * Framework method called when activity becomes the foreground activity.
     * <p/>
     * onResume/onPause implement the most narrow window of activity life-cycle
     * during which the activity is in focus and foreground.
     */
    @Override
    public void onResume() {
        Log.v(TAG, "onResume");
        super.onResume();
        Activity a = getActivity();
        Intent i = new Intent(getActivity(), PerfService.class);
        i.putExtra(PerfMonActivity.FRAG_TYPE, PerfMonActivity.FRAG_TYPE_CPU);
        a.bindService(i, mConnection, Context.BIND_AUTO_CREATE);
    }

    /**
     * Framework method called when activity looses foreground position
     */
    @Override
    public void onPause() {
        Log.v(TAG, "onPause");
        super.onPause();
        Activity a = getActivity();
        a.unbindService(mConnection);
    }

    @Override
    public void onDestroyView() {
        Log.v(TAG, "onDestroyView");
        // TODO Auto-generated method stub
        super.onDestroyView();
        mCpuView = null;
        if (mPerfService != null) mPerfService.setCpuView(mCpuView, mGraphModeDetail);
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // TODO Auto-generated method stub
        switch (item.getItemId()) {
            case R.id.menu_cpumode:
                if (mGraphModeDetail) {
                    mGraphModeDetail = false;
                } else {
                    mGraphModeDetail = true;
                }
                mPerfService.setCpuView(mCpuView, mGraphModeDetail);
                getActivity().invalidateOptionsMenu();
                break;
            case R.id.menu_reset:
                mPerfService.resetCounters(PerfService.GRAPH_ID_CPU);
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        // TODO Auto-generated method stub
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.cpu_menu, menu);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        // TODO Auto-generated method stub
        super.onPrepareOptionsMenu(menu);
        MenuItem m = menu.findItem(R.id.menu_cpumode);
        if (m != null) {
            if (mGraphModeDetail) {
                m.setTitle(R.string.graphcpubasic);
            } else {
                m.setTitle(R.string.graphcpudetail);
            }
        }
    }


}
