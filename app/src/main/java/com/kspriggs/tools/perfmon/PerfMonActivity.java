package com.kspriggs.tools.perfmon;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Vector;

import android.app.ActionBar;
import android.app.ActionBar.Tab;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.app.ListFragment;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;


/**
 * This is the main activity for the Performance and Memory monitoring application.
 * Fragments are used to hold various performance-related items as well as a rough
 * implementation of the unix "top" command.
 */
public class PerfMonActivity extends Activity {
    final private String TAG = "PerfMon -> PerfMonActivity";
    final public static int CHECK_BACK_KEY_PRESSED = 0;
    public static String FRAG_TYPE = "frag_type";
    public static int FRAG_TYPE_SUM = 0;
    public static int FRAG_TYPE_CPU = 1;
    public static int FRAG_TYPE_MEM = 2;
    private boolean mDualPane;
    private boolean mShowingProc;
    private boolean mShowingMemInfo;
    private boolean mNeedToStartMemInfo = false;
    public boolean mNeedToStartLowMem = false;
    private int mSortBy;
    private PerfService mPerfService;
    private ProcessesFragment mProcesses;
    private ProcessMemInfoFragment mProcessMemInfo;


    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.v(TAG, "onServiceConnected");

            // Get reference to (local) service from binder
            mPerfService = ((PerfService.PerfBinder) service).getService();
            mPerfService.setActivity(PerfMonActivity.this);
            if (mNeedToStartMemInfo && mDualPane) {
                mNeedToStartMemInfo = false;
                launchMemInfoFragment();
            }
        }

        public void onServiceDisconnected(ComponentName className) {
            Log.v(TAG, "onServiceDisconnected");
            mPerfService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.v(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        startService(new Intent(this, PerfService.class));
        setContentView(R.layout.main);

        final String action = getIntent().getAction();
        if (action != null && action.contains("com.motorola.tools.perfmon.action.LOWMEM")) {
            mNeedToStartLowMem = true;
        }
        final ActionBar bar = getActionBar();
        bar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
        bar.setDisplayOptions(0, ActionBar.DISPLAY_SHOW_TITLE);
        bar.setDisplayShowHomeEnabled(false);
        bar.setDisplayShowTitleEnabled(false);
        bar.setHomeButtonEnabled(false);


        bar.addTab(bar.newTab()
                .setText(R.string.sum_tab)
                .setTabListener(new TabListener<SumFrag>(
                        this, "sum", SumFrag.class)));

        bar.addTab(bar.newTab()
                .setText(R.string.cpu_tab)
                .setTabListener(new TabListener<CpuFrag>(
                        this, "cpu", CpuFrag.class)));

        bar.addTab(bar.newTab()
                .setText(R.string.mem_tab)
                .setTabListener(new TabListener<MemFrag>(
                        this, "mem", MemFrag.class)));

        // Check to see if we have a frame in which to embed the processes
        // fragment directly in the containing UI.
        View processesFrame = findViewById(R.id.processes);
        mDualPane = processesFrame != null && processesFrame.getVisibility() == View.VISIBLE;
        mShowingProc = false;
        mShowingMemInfo = false;
        mSortBy = Top.SORT_CPU;

        boolean showmeminfo = false;
        if (savedInstanceState != null) {
            bar.setSelectedNavigationItem(savedInstanceState.getInt("tab", 0));
            showmeminfo = savedInstanceState.getBoolean("showmeminfo");
        } else {
            // Restore preferences
            SharedPreferences settings = getSharedPreferences(TAG, 0);
            int tab = settings.getInt("tab", FRAG_TYPE_MEM);
            bar.setSelectedNavigationItem(tab);
            showmeminfo = settings.getBoolean("showmeminfo", true);
        }
        if (showmeminfo) {
            mNeedToStartMemInfo = true;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (mShowingProc) {
            MenuItem m = menu.findItem(R.id.menu_showmeminfo);
            m.setVisible(false);
            m = menu.findItem(R.id.menu_showproc);
            m.setTitle(R.string.hideproc);
            if (mSortBy == Top.SORT_CPU) {
                m = menu.findItem(R.id.menu_sortby);
                m.setTitle(R.string.sortbypss);
                m.setVisible(true);
            } else if (mSortBy == Top.SORT_PSS) {
                m = menu.findItem(R.id.menu_sortby);
                m.setTitle(R.string.sortbycpu);
                m.setVisible(true);
            }
        } else {
            MenuItem m = menu.findItem(R.id.menu_showmeminfo);
            m.setVisible(true);
            if (mShowingMemInfo) {
                m.setTitle(R.string.hidememinfo);
                m = menu.findItem(R.id.menu_showproc);
                m.setVisible(false);
                m = menu.findItem(R.id.menu_sortby);
                m.setVisible(false);
            } else {
                m.setTitle(R.string.showmeminfo);
                m = menu.findItem(R.id.menu_showproc);
                m.setVisible(true);
                m.setTitle(R.string.showproc);
                m = menu.findItem(R.id.menu_sortby);
                m.setVisible(false);
            }
        }
        return super.onPrepareOptionsMenu(menu);
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_showproc:
                launchProcessesFragment();
                break;
            case R.id.menu_showmeminfo:
                launchMemInfoFragment();
                break;
            case R.id.menu_sortby:
                if (mSortBy == Top.SORT_CPU) {
                    mSortBy = Top.SORT_PSS;
                } else if (mSortBy == Top.SORT_PSS) {
                    mSortBy = Top.SORT_CPU;
                }
                if (mDualPane && mShowingProc) {
                    mProcesses.setSortBy(mSortBy);
                }
                invalidateOptionsMenu();
                break;
            case R.id.menu_exit:
                stopService(new Intent(this, PerfService.class));
                finish();
                break;
            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        int tab = getActionBar().getSelectedNavigationIndex();
        outState.putInt("tab", tab);
        outState.putBoolean("showmeminfo", mShowingMemInfo);
    }


    @Override
    protected void onStop() {
        Log.v(TAG, "onStop");
        // TODO Auto-generated method stub
        super.onStop();
        // We need an Editor object to make preference changes.
        // All objects are from android.context.Context
        SharedPreferences settings = getSharedPreferences(TAG, 0);
        SharedPreferences.Editor editor = settings.edit();
        int tab = getActionBar().getSelectedNavigationIndex();
        editor.putInt("tab", tab);
        editor.putBoolean("showmeminfo", mShowingMemInfo);

        // Commit the edits!
        editor.commit();
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
        bindService(new Intent(this,
                PerfService.class), mConnection, Context.BIND_AUTO_CREATE);

    }


    @Override
    protected void onDestroy() {
        Log.v(TAG, "onDestroy");
        // TODO Auto-generated method stub
        super.onDestroy();
    }

    /**
     * Framework method called when activity looses foreground position
     */
    @Override
    public void onPause() {
        Log.v(TAG, "onPause");
        super.onPause();
        unbindService(mConnection);
    }

    public void launchProcessesFragment() {
        if (mDualPane) {
            mProcesses = (ProcessesFragment)
                    getFragmentManager().findFragmentById(R.id.processes);
            if (mProcesses == null) {
                // Make new fragment to show this selection.
                mProcesses = new ProcessesFragment();

                // Execute a transaction, replacing any existing fragment
                // with this one inside the frame.
                FragmentTransaction ft = getFragmentManager().beginTransaction();
                ft.replace(R.id.processes, mProcesses);
                ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
                ft.commit();
                mShowingProc = true;
            } else {
                // Execute a transaction, replacing any existing fragment
                // with this one inside the frame.
                FragmentTransaction ft = getFragmentManager().beginTransaction();
                ft.remove(mProcesses);
                ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
                ft.commit();
                mShowingProc = false;
            }
        } else {
            Intent intent = new Intent(this, ProcessesActivity.class);
            startActivityForResult(intent, CHECK_BACK_KEY_PRESSED);
            mShowingProc = true;
        }
        invalidateOptionsMenu();
    }

    public void launchMemInfoFragment() {
        if (mDualPane) {
            mProcessMemInfo = (ProcessMemInfoFragment)
                    getFragmentManager().findFragmentById(R.id.processes);
            if (mProcessMemInfo == null) {
                // Make new fragment to show this selection.
                mProcessMemInfo = new ProcessMemInfoFragment();

                // Execute a transaction, replacing any existing fragment
                // with this one inside the frame.
                FragmentTransaction ft = getFragmentManager().beginTransaction();
                ft.replace(R.id.processes, mProcessMemInfo);
                ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
                ft.commit();
                mShowingMemInfo = true;
                mProcessMemInfo.setService(mPerfService);
            } else {
                // Execute a transaction, replacing any existing fragment
                // with this one inside the frame.
                FragmentTransaction ft = getFragmentManager().beginTransaction();
                ft.remove(mProcessMemInfo);
                ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
                ft.commit();
                mShowingMemInfo = false;
            }
        } else {
            Intent intent = new Intent(this, ProcessMemInfoActivity.class);
            startActivityForResult(intent, CHECK_BACK_KEY_PRESSED);
            mShowingMemInfo = true;
        }
        invalidateOptionsMenu();
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // TODO Auto-generated method stub
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CHECK_BACK_KEY_PRESSED) {
            if (resultCode == RESULT_OK) {
                if (mShowingMemInfo == true) {
                    mShowingMemInfo = false;
                    invalidateOptionsMenu();
                }
                if (mShowingProc == true) {
                    mShowingProc = false;
                    invalidateOptionsMenu();
                }
            }
        }
    }


    public static class TabListener<T extends Fragment> implements ActionBar.TabListener {
        private final Activity mActivity;
        private final String mTag;
        private final Class<T> mClass;
        private final Bundle mArgs;
        private Fragment mFragment;

        public TabListener(Activity activity, String tag, Class<T> clz) {
            this(activity, tag, clz, null);
        }

        public TabListener(Activity activity, String tag, Class<T> clz, Bundle args) {
            mActivity = activity;
            mTag = tag;
            mClass = clz;
            mArgs = args;

            // Check to see if we already have a fragment for this tab, probably
            // from a previously saved state.  If so, deactivate it, because our
            // initial state is that a tab isn't shown.
            mFragment = mActivity.getFragmentManager().findFragmentByTag(mTag);
            if (mFragment != null && !mFragment.isDetached()) {
                FragmentTransaction ft = mActivity.getFragmentManager().beginTransaction();
                ft.detach(mFragment);
                ft.commit();
            }
        }

        public void onTabSelected(Tab tab, FragmentTransaction ft) {
            if (mFragment == null) {
                mFragment = Fragment.instantiate(mActivity, mClass.getName(), mArgs);
                //DONTWORKft.add(R.id.titles, mFragment, mTag);
                //WORKSft.add(android.R.id.content, mFragment, mTag);
                ft.add(R.id.fragment_content, mFragment, mTag);
            } else {
                ft.attach(mFragment);
            }
        }

        public void onTabUnselected(Tab tab, FragmentTransaction ft) {
            if (mFragment != null) {
                ft.detach(mFragment);
            }
        }

        public void onTabReselected(Tab tab, FragmentTransaction ft) {
            Toast.makeText(mActivity, "You are already viewing this tab!", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * This is a secondary activity to be used on smaller width screens to
     * show the processes list fragment.
     */

    public static class ProcessesActivity extends Activity {

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            final ActionBar bar = getActionBar();
            bar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
            bar.setDisplayOptions(0, ActionBar.DISPLAY_SHOW_TITLE);
            bar.setDisplayShowHomeEnabled(true);
            bar.setDisplayShowTitleEnabled(false);
            bar.setHomeButtonEnabled(true);

            if (getResources().getConfiguration().orientation
                    == Configuration.ORIENTATION_LANDSCAPE) {
                // If the screen is now in landscape mode, we can show the
                // dialog in-line with the list so we don't need this activity.
                finish();
                return;
            }

            if (savedInstanceState == null) {
                ProcessesFragment processes = new ProcessesFragment();
                processes.setArguments(getIntent().getExtras());
                getFragmentManager().beginTransaction().add(android.R.id.content, processes).commit();
            }
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {

            switch (item.getItemId()) {
                case android.R.id.home:
                    // app icon in action bar clicked; go home
                    Intent intent = new Intent(this, PerfMonActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(intent);
                    return true;
                default:
                    return super.onOptionsItemSelected(item);
            }
        }

        @Override
        public void onBackPressed() {
            setResult(RESULT_OK);
            super.onBackPressed();

        }

    }

    /**
     * This is the fragment which shows a list of processes - similar to the "top"
     * unix command.
     */

    public static class ProcessesFragment extends ListFragment {
        private static final int DELAY = 10000;
        private Top mTop;
        private ArrayAdapter<String> mAdapter;
        //private TextView tp;

        // Handler which is executed every DELAY s
        // to recalculate the task list and refresh
        // the display.
        private Handler mHandler = new Handler();
        private Runnable mRefreshTask = new Runnable() {
            public void run() {
                redrawList();
                mHandler.postDelayed(mRefreshTask, DELAY);
            }
        };

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

        }

        @Override
        public void onActivityCreated(Bundle savedInstanceState) {
            super.onActivityCreated(savedInstanceState);

            // List Header - must be initialized before setListAdapter
            TextView tp = new TextView(getActivity());
            tp.setText(R.string.toplist_header);
            getListView().addHeaderView(tp);

            mAdapter = new ArrayAdapter<String>(getActivity(),
                    R.layout.tasklist, new ArrayList<String>());
            setListAdapter(mAdapter);
        }

        /**
         * Framework method called when the activity gains forground focus.
         * <p/>
         * Periodic polling takes place between onResume and onPause.
         */
        @Override
        public void onResume() {
            super.onResume();
            mTop = new Top(Top.SORT_CPU);
            Toast.makeText(getActivity(), getText(R.string.disp_collecting), Toast.LENGTH_SHORT).show();
            mHandler.postDelayed(mRefreshTask, 1000);
        }

        /**
         * Framework method called when the activity looses foreground focus.
         */
        @Override
        public void onPause() {
            super.onPause();
            mHandler.removeCallbacks(mRefreshTask);
            mTop = null;
        }

        public boolean setSortBy(int sortby) {
            if (mTop != null) {
                mTop.setSortType(sortby);
                return true;
            }
            return false;
        }

        /**
         * Regenerate the list of processes sorted by CPU usage
         * and update the ListView through the array adapter.
         * <p/>
         * This update operation seems to be quite CPU intensive.
         */
        private void redrawList() {
            Vector<Top.Task> top_list = mTop.getTopN();
            mAdapter.clear();
            for (Iterator<Top.Task> it = top_list.iterator(); it.hasNext(); ) {
                Top.Task task = it.next();
                //if (task.getUsage() == 0) break;

                mAdapter.add(String.format("%5.1f%% ", ((double) task.getUsage() / 10.0))
                        + String.format("%+5d  ", ((int) task.getOomAdj()))
                        + String.format("%7.1f  ", ((double) task.getPss()))
                        + task.getName());
            }
        }
    }

    /**
     * This is a secondary activity to be used on smaller width screens to
     * show the MemInfo list fragment.
     */

    public static class ProcessMemInfoActivity extends Activity {
        private PerfService mPerfService;
        private ProcessMemInfoFragment mProcessMemInfo;

        private ServiceConnection mConnection = new ServiceConnection() {
            public void onServiceConnected(ComponentName className, IBinder service) {

                // Get reference to (local) service from binder
                mPerfService = ((PerfService.PerfBinder) service).getService();
                mPerfService.setActivity(ProcessMemInfoActivity.this);
                mProcessMemInfo.setService(mPerfService);
            }

            public void onServiceDisconnected(ComponentName className) {
                mPerfService = null;
            }
        };

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            final ActionBar bar = getActionBar();
            bar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
            bar.setDisplayOptions(0, ActionBar.DISPLAY_SHOW_TITLE);
            bar.setDisplayShowHomeEnabled(true);
            bar.setDisplayShowTitleEnabled(false);
            bar.setHomeButtonEnabled(true);

            if (getResources().getConfiguration().orientation
                    == Configuration.ORIENTATION_LANDSCAPE) {
                // If the screen is now in landscape mode, we can show the
                // dialog in-line with the list so we don't need this activity.
                finish();
                return;
            }

            if (savedInstanceState == null) {
                mProcessMemInfo = new ProcessMemInfoFragment();
                mProcessMemInfo.setArguments(getIntent().getExtras());
                getFragmentManager().beginTransaction().add(android.R.id.content, mProcessMemInfo).commit();
            }
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {

            switch (item.getItemId()) {
                case android.R.id.home:
                    // app icon in action bar clicked; go home
                    Intent intent = new Intent(this, PerfMonActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(intent);
                    return true;
                default:
                    return super.onOptionsItemSelected(item);
            }
        }

        @Override
        protected void onResume() {
            // TODO Auto-generated method stub
            super.onResume();
            bindService(new Intent(ProcessMemInfoActivity.this,
                    PerfService.class), mConnection, Context.BIND_AUTO_CREATE);
        }

        @Override
        protected void onPause() {
            // TODO Auto-generated method stub
            super.onPause();
            unbindService(mConnection);
        }

        @Override
        public void onBackPressed() {
            setResult(RESULT_OK);
            super.onBackPressed();

        }


    }

    /**
     * This is the fragment which shows a list of processes - similar to the "top"
     * unix command.
     */

    public static class ProcessMemInfoFragment extends ListFragment {
        private ArrayAdapter<String> mAdapter;
        private PerfService mService;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

        }

        @Override
        public void onActivityCreated(Bundle savedInstanceState) {
            super.onActivityCreated(savedInstanceState);

            // List Header - must be initialized before setListAdapter
            TextView tp = new TextView(getActivity());
            tp.setText(R.string.procranklist_header);
            getListView().addHeaderView(tp);

            mAdapter = new ArrayAdapter<String>(getActivity(),
                    R.layout.tasklist, new ArrayList<String>());
            setListAdapter(mAdapter);
            if (mService != null && mAdapter != null) mService.setProcessMemInfoAdapter(mAdapter);
        }


        @Override
        public void onListItemClick(ListView l, View v, int position, long id) {
            // TODO Auto-generated method stub
            super.onListItemClick(l, v, position, id);
            String text = ((TextView) v).getText().toString();
            String[] segs = text.trim().split("[ ]+");
            if (!segs[0].contains("PSS")) {
                // Add process name to custom watch list
                //Log.i("", segs[2]);
            }
        }

        /**
         * Framework method called when the activity gains forground focus.
         * <p/>
         * Periodic polling takes place between onResume and onPause.
         */
        @Override
        public void onResume() {
            super.onResume();
            if (mService != null) mService.setProcessMemInfoAdapter(mAdapter);
        }

        /**
         * Framework method called when the activity looses foreground focus.
         */
        @Override
        public void onPause() {
            super.onPause();
            if (mService != null) mService.setProcessMemInfoAdapter(null);
        }

        public void setService(PerfService service) {
            mService = service;
            if (mService != null && mAdapter != null) mService.setProcessMemInfoAdapter(mAdapter);
        }

    }
}