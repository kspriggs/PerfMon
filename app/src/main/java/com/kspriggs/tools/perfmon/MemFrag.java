package com.kspriggs.tools.perfmon;

import java.util.List;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.MemoryInfo;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.SystemClock;
import android.text.Editable;
import android.text.InputType;
import android.util.Log;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;


public class MemFrag extends Fragment {
    final private String TAG = "PerfMon -> MemFragment";
    final private int VIEWSIZE = 4;
    final private int[] viewlist = {R.id.memtotal, R.id.swaptotal, R.id.lowMemory, R.id.availMem};
    static final int MED_BROWS_DIALOG = 0;
    static final int HI_BROWS_DIALOG = 1;

    private View mMemView;
    private PerfService mPerfService;
    private boolean mWatchCustom1 = false;
    private String mWatchCustom1Val = "";
    private boolean mWatchCustom2 = false;
    private String mWatchCustom2Val = "";
    private boolean mCustom3GraphAvail = false;
    private boolean mWatchCustom3 = false;
    private String mWatchCustom3Val = "";
    private ActionMode mActionMode;
    private boolean mAllocatingMem = false;
    private boolean mTestingMem = false;
    private long mtimeAllocating;
    private boolean mForcingLowMem = false;
    private boolean mForcingLowMemAppSwitch = false;
    private boolean mForceLowMemOneTime = false;
    private LowMemLaunchApp mForceLowMemLaunchApp;
    private boolean mShowProcrank;

    static final int ACTIVITY_MEASURE_LAUNCH = 0;

    //BEGIN HACK
    public int mHackReaderToggle = PerfService.memHack.MEMHACK_NORUN;
    //END HACK

    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.v(TAG, "onServiceConnected");

            // Get reference to (local) service from binder
            mPerfService = ((PerfService.PerfBinder) service).getService();
            mPerfService.setMemView(mMemView);

            if (mWatchCustom1) {
                mPerfService.setWatchCustom1(mWatchCustom1Val);
            }
            if (mWatchCustom2) {
                mPerfService.setWatchCustom2(mWatchCustom2Val);
            }
            if (mCustom3GraphAvail && mWatchCustom3) {
                mPerfService.setWatchCustom3(mWatchCustom3Val);
            }
            if (mForceLowMemOneTime) {
                startForceLowMem(PerfService.SIMULATE_LOWMEM_ONETIME);
                mForceLowMemOneTime = false;
            }
            if (mShowProcrank) {
                mPerfService.setRunProcrank(mShowProcrank);
            }
            mPerfService.forceMemDisplayUpdate(mMemView);
        }

        public void onServiceDisconnected(ComponentName className) {
            Log.v(TAG, "onServiceDisconnected");
            mPerfService = null;
        }
    };

    public class MemReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(PerfService.RUN_BROWSER)) {
                //createRunTestDialog("crap");
            }
        }
    }

    final Handler memAllocHandler = new Handler() {
        public void handleMessage(Message msg) {
            int size = msg.arg1;
            long time = SystemClock.elapsedRealtime() - mtimeAllocating;
            Toast.makeText(getActivity(), "Allocated: " + Integer.toString(size / 1024 / 1024) + " MBytes in " + Long.toString(time) + "ms", Toast.LENGTH_LONG).show();
            mAllocatingMem = false;
            getActivity().invalidateOptionsMenu();
        }
    };

    final Handler memTestHandler = new Handler() {
        public void handleMessage(Message msg) {
            int size = msg.arg1;
            int averagetime = msg.arg2;
            Bundle extras = msg.getData();
            String filename = extras.getString("filename");
            showDialog(R.string.memtestresulttitle, "Average time to reserve " + Integer.toString(size) + " MB was " + Integer.toString(averagetime) + " ms." + "See details in: " + filename);
            mTestingMem = false;
            getActivity().invalidateOptionsMenu();
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
        if (savedInstanceState != null) {
            if (savedInstanceState.containsKey("watch1")) {
                mWatchCustom1 = savedInstanceState.getBoolean("watch1");
            }
            if (savedInstanceState.containsKey("watch2")) {
                mWatchCustom2 = savedInstanceState.getBoolean("watch2");
            }
            if (savedInstanceState.containsKey("watch3")) {
                mWatchCustom3 = savedInstanceState.getBoolean("watch3");
            }
            if (savedInstanceState.containsKey("watch1val")) {
                mWatchCustom1Val = savedInstanceState.getString("watch1val");
            }
            if (savedInstanceState.containsKey("watch2val")) {
                mWatchCustom2Val = savedInstanceState.getString("watch2val");
            }
            if (savedInstanceState.containsKey("watch3val")) {
                mWatchCustom3Val = savedInstanceState.getString("watch3val");
            }
            if (savedInstanceState.containsKey("forcelowmem")) {
                mForcingLowMem = savedInstanceState.getBoolean("forcelowmem");
            }
            if (savedInstanceState.containsKey("forcelowmemappswitch")) {
                mForcingLowMemAppSwitch = savedInstanceState.getBoolean("forcelowmemappswitch");
            }
            if (savedInstanceState.containsKey("showprocrank")) {
                mShowProcrank = savedInstanceState.getBoolean("showprocrank");
            }

        } else {
            // Restore preferences
            SharedPreferences settings = getActivity().getSharedPreferences(TAG, Context.MODE_PRIVATE);
            //mWatchCustom1 = settings.getBoolean("watch1", true);
            //mWatchCustom2 = settings.getBoolean("watch2", true);
            mWatchCustom1 = settings.getBoolean("watch1", true);
            mWatchCustom2 = settings.getBoolean("watch2", true);
            mWatchCustom3 = settings.getBoolean("watch3", false);
            //mWatchCustom1Val = settings.getString("watch1val", "com.android.browser");
            //mWatchCustom2Val = settings.getString("watch2val", "com.motorola.blur.home");
            mWatchCustom1Val = settings.getString("watch1val", "settings");
            mWatchCustom2Val = settings.getString("watch2val", "systemui");
            mWatchCustom3Val = settings.getString("watch3val", "");
            mForcingLowMem = settings.getBoolean("forcelowmem", false);
            mForcingLowMemAppSwitch = settings.getBoolean("forcelowmemappswitch", false);
            mShowProcrank = settings.getBoolean("showprocrank", false);
        }
        if (((PerfMonActivity) getActivity()).mNeedToStartLowMem) {
            mForceLowMemOneTime = true;
            ((PerfMonActivity) getActivity()).mNeedToStartLowMem = false;
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        Log.v(TAG, "onCreateView");
        mMemView = inflater.inflate(R.layout.mem_layout, container, false);

        startActionMode(mMemView.findViewById(R.id.graph));
        //startActionMode(mMemView.findViewById(R.id.customgraph1));
        //startActionMode(mMemView.findViewById(R.id.customgraph2));
        View v = mMemView.findViewById(R.id.customgraph3);
        mCustom3GraphAvail = (v == null) ? false : true;

        return mMemView;
    }

    private void startActionMode(View view) {
        if (view != null) {
            view.setOnLongClickListener(new View.OnLongClickListener() {
                // Called when the user long-clicks on someView
                public boolean onLongClick(View view) {
                    if (mActionMode != null) {
                        return false;
                    }

                    // Start the CAB using the ActionMode.Callback defined above
                    mActionMode = getActivity().startActionMode(mMemGraphContext);
                    view.setSelected(true);
                    return true;
                }
            });
        }
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
        i.putExtra(PerfMonActivity.FRAG_TYPE, PerfMonActivity.FRAG_TYPE_MEM);
        a.bindService(i, mConnection, Context.BIND_AUTO_CREATE);

        if (mPerfService != null) {

            mPerfService.forceMemDisplayUpdate(mMemView);
        } else {
            TextView tv;
            for (int j = 0; j < VIEWSIZE; ++j) {
                tv = (TextView) (mMemView.findViewById(viewlist[j]));
                if (tv != null) {
                    tv.setText(R.string.populating);
                }
            }
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.mem_menu, menu);
    }


    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        // TODO Auto-generated method stub
        super.onPrepareOptionsMenu(menu);
        if (mShowProcrank) {
            MenuItem m = menu.findItem(R.id.menu_showprocrank);
            m.setTitle(R.string.hideprocrank);
        } else {
            MenuItem m = menu.findItem(R.id.menu_showprocrank);
            m.setTitle(R.string.showprocrank);
        }
        if (mWatchCustom1) {
            MenuItem m = menu.findItem(R.id.menu_watchcustom1);
            m.setVisible(true);
            m.setTitle(R.string.hidecustom1);
            m = menu.findItem(R.id.menu_setcustom1);
            m.setTitle(R.string.changecustom1);
        } else {
            MenuItem m = menu.findItem(R.id.menu_watchcustom1);
            m.setTitle(R.string.watchcustom1);
            if (mWatchCustom1Val.isEmpty()) {
                m.setVisible(false);
                m = menu.findItem(R.id.menu_setcustom1);
                m.setTitle(R.string.setcustom1);
            } else {
                m.setVisible(true);
                m = menu.findItem(R.id.menu_setcustom1);
                m.setTitle(R.string.changecustom1);
            }
        }
        if (mWatchCustom2) {
            MenuItem m = menu.findItem(R.id.menu_watchcustom2);
            m.setVisible(true);
            m.setTitle(R.string.hidecustom2);
            m = menu.findItem(R.id.menu_setcustom2);
            m.setTitle(R.string.changecustom2);
        } else {
            MenuItem m = menu.findItem(R.id.menu_watchcustom2);
            m.setTitle(R.string.watchcustom2);
            if (mWatchCustom2Val.isEmpty()) {
                m.setVisible(false);
                m = menu.findItem(R.id.menu_setcustom2);
                m.setTitle(R.string.setcustom2);
            } else {
                m.setVisible(true);
                m = menu.findItem(R.id.menu_setcustom2);
                m.setTitle(R.string.changecustom2);
            }
        }
        if (mCustom3GraphAvail) {
            if (mWatchCustom3) {
                MenuItem m = menu.findItem(R.id.menu_watchcustom3);
                m.setVisible(true);
                m.setTitle(R.string.hidecustom3);
                m = menu.findItem(R.id.menu_setcustom3);
                m.setTitle(R.string.changecustom3);
            } else {
                MenuItem m = menu.findItem(R.id.menu_watchcustom3);
                m.setTitle(R.string.watchcustom3);
                if (mWatchCustom3Val.isEmpty()) {
                    m.setVisible(false);
                    m = menu.findItem(R.id.menu_setcustom3);
                    m.setTitle(R.string.setcustom3);
                } else {
                    m.setVisible(true);
                    m = menu.findItem(R.id.menu_setcustom3);
                    m.setTitle(R.string.changecustom3);
                }
            }
        } else {
            MenuItem m = menu.findItem(R.id.menu_watchcustom3);
            m.setVisible(false);
            m = menu.findItem(R.id.menu_setcustom3);
            m.setVisible(false);
        }
        if (mAllocatingMem) {
            MenuItem m = menu.findItem(R.id.menu_reservemem);
            m.setVisible(false);
            /*
			m = menu.findItem(R.id.menu_checkmem);
			m.setVisible(false);
			*/
            m = menu.findItem(R.id.menu_freemem);
            m.setVisible(false);
        } else {
            MenuItem m = menu.findItem(R.id.menu_reservemem);
            m.setVisible(true);
			/*
			m = menu.findItem(R.id.menu_checkmem);
			m.setVisible(true);
			*/
            m = menu.findItem(R.id.menu_freemem);
            m.setVisible(true);
        }
        if (mTestingMem) {
            MenuItem m = menu.findItem(R.id.menu_runmemtest);
            m.setVisible(false);
        } else {
            MenuItem m = menu.findItem(R.id.menu_runmemtest);
            m.setVisible(true);
        }
        if (mForcingLowMem) {
            MenuItem m = menu.findItem(R.id.menu_runlowmem);
            m.setTitle(R.string.stoplowmem);

            m = menu.findItem(R.id.menu_runlowmemappswitch);
            m.setVisible(false);
        } else {
            MenuItem m = menu.findItem(R.id.menu_runlowmem);
            m.setTitle(R.string.runlowmem);

            m = menu.findItem(R.id.menu_runlowmemappswitch);
            m.setVisible(true);
        }

        if (mForcingLowMemAppSwitch) {
            MenuItem m = menu.findItem(R.id.menu_runlowmemappswitch);
            m.setTitle(R.string.stoplowmemappswitch);

            m = menu.findItem(R.id.menu_runlowmem);
            m.setVisible(false);
        } else {
            MenuItem m = menu.findItem(R.id.menu_runlowmemappswitch);
            m.setTitle(R.string.runlowmemappswitch);

            m = menu.findItem(R.id.menu_runlowmem);
            m.setVisible(true);
        }
        if (mHackReaderToggle == PerfService.memHack.MEMHACK_RUN) {
            MenuItem m = menu.findItem(R.id.menu_runtrace);
            m.setTitle(R.string.stoptrace);
            m.setVisible(true);
            m = menu.findItem(R.id.menu_runtracemod);
            m.setVisible(false);
        } else if (mHackReaderToggle == PerfService.memHack.MEMHACK_RUNMOD) {
            MenuItem m = menu.findItem(R.id.menu_runtrace);
            m.setVisible(false);
            m = menu.findItem(R.id.menu_runtracemod);
            m.setTitle(R.string.stoptrace);
            m.setVisible(true);
        } else if (mHackReaderToggle == PerfService.memHack.MEMHACK_NORUN) {
            MenuItem m = menu.findItem(R.id.menu_runtrace);
            m.setTitle(R.string.runtrace);
            m.setVisible(true);
            m = menu.findItem(R.id.menu_runtracemod);
            m.setTitle(R.string.runtracemod);
            m.setVisible(true);
        }
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // TODO Auto-generated method stub
        switch (item.getItemId()) {
            case R.id.menu_showprocrank:
                if (mShowProcrank) {
                    mShowProcrank = false;
                } else {
                    mShowProcrank = true;
                }
                mPerfService.setRunProcrank(mShowProcrank);
                getActivity().invalidateOptionsMenu();
                break;
            case R.id.menu_watchcustom1:
                if (mWatchCustom1) {
                    mPerfService.setWatchCustom1("");
                    mWatchCustom1 = false;
                } else {
                    mPerfService.setWatchCustom1(mWatchCustom1Val);
                    mWatchCustom1 = true;
                }
                getActivity().invalidateOptionsMenu();
                break;
            case R.id.menu_watchcustom2:
                if (mWatchCustom2) {
                    mPerfService.setWatchCustom2("");
                    mWatchCustom2 = false;
                } else {
                    mPerfService.setWatchCustom2(mWatchCustom2Val);
                    mWatchCustom2 = true;
                }
                getActivity().invalidateOptionsMenu();
                break;
            case R.id.menu_watchcustom3:
                if (mWatchCustom3) {
                    mPerfService.setWatchCustom3("");
                    mWatchCustom3 = false;
                } else {
                    mPerfService.setWatchCustom3(mWatchCustom3Val);
                    mWatchCustom3 = true;
                }
                getActivity().invalidateOptionsMenu();
                break;
            case R.id.menu_setcustom1:
                AlertDialog.Builder alert = new AlertDialog.Builder(getActivity());
                alert.setTitle(R.string.setcustomtitle);
                alert.setMessage(R.string.setcustomtext);
                // Set an EditText view to get user input
                final EditText input = new EditText(getActivity());
                alert.setView(input);
                alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        Editable value = input.getText();
                        mWatchCustom1Val = value.toString();
                        mWatchCustom1 = true;
                        mPerfService.setWatchCustom1(mWatchCustom1Val);
                        mPerfService.resetCounters(PerfService.GRAPH_ID_CUST1);
                        getActivity().invalidateOptionsMenu();
                    }
                });

                alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        // Canceled.
                    }
                });
                alert.show();
                break;
            case R.id.menu_setcustom2:
                alert = new AlertDialog.Builder(getActivity());
                alert.setTitle(R.string.setcustomtitle);
                alert.setMessage(R.string.setcustomtext);
                // Set an EditText view to get user input
                final EditText input2 = new EditText(getActivity());
                alert.setView(input2);
                alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        Editable value = input2.getText();
                        mWatchCustom2Val = value.toString();
                        mWatchCustom2 = true;
                        mPerfService.setWatchCustom2(mWatchCustom2Val);
                        mPerfService.resetCounters(PerfService.GRAPH_ID_CUST2);
                        getActivity().invalidateOptionsMenu();
                    }
                });

                alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        // Canceled.
                    }
                });
                alert.show();
                break;
            case R.id.menu_setcustom3:
                alert = new AlertDialog.Builder(getActivity());
                alert.setTitle(R.string.setcustomtitle);
                alert.setMessage(R.string.setcustomtext);
                // Set an EditText view to get user input
                final EditText input3 = new EditText(getActivity());
                alert.setView(input3);
                alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        Editable value = input3.getText();
                        mWatchCustom3Val = value.toString();
                        mWatchCustom3 = true;
                        mPerfService.setWatchCustom3(mWatchCustom3Val);
                        mPerfService.resetCounters(PerfService.GRAPH_ID_CUST3);
                        getActivity().invalidateOptionsMenu();
                    }
                });

                alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        // Canceled.
                    }
                });
                alert.show();
                break;
            case R.id.menu_testdesc:
                showDialog(R.string.helptitle, getString(R.string.helptext));
                break;
            case R.id.menu_runtrace:
                if (mHackReaderToggle == PerfService.memHack.MEMHACK_NORUN) {
                    runMemHack(PerfService.memHack.MEMHACK_RUN);
                    mHackReaderToggle = PerfService.memHack.MEMHACK_RUN;
                } else {
                    stopMemHack();
                    mHackReaderToggle = PerfService.memHack.MEMHACK_NORUN;
                }
                getActivity().invalidateOptionsMenu();
                break;
            case R.id.menu_runtracemod:
                if (mHackReaderToggle == PerfService.memHack.MEMHACK_NORUN) {
                    runMemHack(PerfService.memHack.MEMHACK_RUNMOD);
                    mHackReaderToggle = PerfService.memHack.MEMHACK_RUNMOD;
                } else {
                    stopMemHack();
                    mHackReaderToggle = PerfService.memHack.MEMHACK_NORUN;
                }
                getActivity().invalidateOptionsMenu();
                break;
            case R.id.menu_runmedbrowser:
                // First Setup a Custom Watcher to Monitor the com.android.browser process
                setupCustomWatchGraph(item);
                mPerfService.launchMedBrowserProfile(true);
                break;
            case R.id.menu_runhibrowser:
                // First Setup a Custom Watcher to Monitor the com.android.browser process
                setupCustomWatchGraph(item);
                mPerfService.launchHiBrowserProfile(true);
                break;
            case R.id.menu_launchmedbrowser:
                // First Setup a Custom Watcher to Monitor the com.android.browser process
                setupCustomWatchGraph(item);
                mPerfService.launchMedBrowserProfile(false);
                break;
            case R.id.menu_launchhibrowser:
                // First Setup a Custom Watcher to Monitor the com.android.browser process
                setupCustomWatchGraph(item);
                mPerfService.launchHiBrowserProfile(false);
                break;
            case R.id.menu_reservemem:
                alert = new AlertDialog.Builder(getActivity());
                alert.setTitle(R.string.reservememtitle);
                alert.setMessage(R.string.reservememtext);
                // Set an EditText view to get user input
                final EditText input4 = new EditText(getActivity());
                input4.setInputType(InputType.TYPE_CLASS_NUMBER);
                alert.setView(input4);
                alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        Editable value = input4.getText();
                        final int requestedsize = Integer.parseInt(value.toString());
                        mAllocatingMem = true;
                        mtimeAllocating = SystemClock.elapsedRealtime();
                        mPerfService.allocateMemory(memAllocHandler, requestedsize);
                        getActivity().invalidateOptionsMenu();
                    }
                });

                alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        // Canceled.
                    }
                });
                alert.show();
                break;
			/*
		case R.id.menu_checkmem:
			int size = mPerfService.checkMemory();
			Toast.makeText(getActivity(), "checkMem returned: " + Integer.toString(size/1024/1024), Toast.LENGTH_SHORT).show();
			break;
			*/
            case R.id.menu_freemem:
                mPerfService.freeMemory();
                break;
            case R.id.menu_runmemtest:
                alert = new AlertDialog.Builder(getActivity());
                alert.setTitle(R.string.memtesttitle);
                alert.setMessage(R.string.memtesttext);
                // Set an EditText view to get user input
                final EditText input5 = new EditText(getActivity());
                input5.setInputType(InputType.TYPE_CLASS_NUMBER);
                alert.setView(input5);
                alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        Editable value = input5.getText();
                        final int requestedsize = Integer.parseInt(value.toString());
                        mTestingMem = true;
                        mtimeAllocating = SystemClock.elapsedRealtime();
                        mPerfService.launchMemTest(memTestHandler, requestedsize);
                        getActivity().invalidateOptionsMenu();
                    }
                });

                alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        // Canceled.
                    }
                });
                alert.show();
                break;
            case R.id.menu_reset:
                mPerfService.resetCounters(PerfService.GRAPH_ID_MEMALL);
                break;
            case R.id.menu_toggle:
                mPerfService.toggle(PerfService.GRAPH_ID_MEMALL);
                break;
            case R.id.menu_runlowmem:
                if (!mForcingLowMem) {
                    startForceLowMem(PerfService.SIMULATE_LOWMEM_CONTINUOUS);
                } else {
                    mForcingLowMem = false;
                    mPerfService.stopForceLowMemory(true);
                    getActivity().invalidateOptionsMenu();
                }
                break;
            case R.id.menu_runlowmemappswitch:
                if (!mForcingLowMemAppSwitch) {
                    startForceLowMem(PerfService.SIMULATE_LOWMEM_LAUNCHAPP);
                } else {
                    mForcingLowMemAppSwitch = false;
                    //mPerfService.stopForceLowMemory(true);
                    stopLowMemAppSwitch();
                    getActivity().invalidateOptionsMenu();
                }
                break;
            case R.id.menu_logprocrank:
                String r = mPerfService.logProcrank();
                if (r.isEmpty()) {
                    Toast.makeText(getActivity(), "Log file not created - check availability of SD card!", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(getActivity(), "Wrote Procrank output to " + r, Toast.LENGTH_LONG).show();
                }
                break;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        // TODO Auto-generated method stub
        super.onSaveInstanceState(outState);
        outState.putBoolean("watch1", mWatchCustom1);
        outState.putBoolean("watch2", mWatchCustom2);
        outState.putBoolean("watch3", mWatchCustom3);
        outState.putString("watch1val", mWatchCustom1Val);
        outState.putString("watch2val", mWatchCustom2Val);
        outState.putString("watch3val", mWatchCustom3Val);
        outState.putBoolean("forcelowmem", mForcingLowMem);
        outState.putBoolean("forcelowmemappswitch", mForcingLowMemAppSwitch);
    }

    @Override
    public void onPause() {
        Log.v(TAG, "onPause");
        super.onPause();
        Activity a = getActivity();
        a.unbindService(mConnection);
    }


    @Override
    public void onStop() {
        Log.v(TAG, "onStop");
        // TODO Auto-generated method stub
        super.onStop();
        SharedPreferences settings = getActivity().getSharedPreferences(TAG, 0);
        SharedPreferences.Editor editor = settings.edit();
        editor.putBoolean("watch1", mWatchCustom1);
        editor.putBoolean("watch2", mWatchCustom2);
        editor.putBoolean("watch3", mWatchCustom3);
        editor.putString("watch1val", mWatchCustom1Val);
        editor.putString("watch2val", mWatchCustom2Val);
        editor.putString("watch3val", mWatchCustom3Val);
        editor.putBoolean("forcelowmem", mForcingLowMem);
        editor.putBoolean("forcelowmemappswitch", mForcingLowMemAppSwitch);
        editor.commit();
    }

    @Override
    public void onDestroyView() {
        Log.v(TAG, "onDestroyView");
        // TODO Auto-generated method stub
        super.onDestroyView();
        mMemView = null;
        if (mPerfService != null) mPerfService.setMemView(mMemView);
    }


    @Override
    public void onDestroy() {
        Log.v(TAG, "onDestroy");
        // TODO Auto-generated method stub
        super.onDestroy();
    }

    private void setupCustomWatchGraph(MenuItem item) {
        if (mWatchCustom1) {
            if (!mWatchCustom1Val.contains("com.android.browser")) {
                if (mWatchCustom2 && !mWatchCustom2Val.contains("com.android.browser")) {
                    // Watching 1 with something other than Browser
                    // Watching 2 with something other than Browser
                    // Replace 1 with Browser
                    mWatchCustom1Val = "com.android.browser";
                    mPerfService.setWatchCustom1(mWatchCustom1Val);
                    mPerfService.resetCounters(PerfService.GRAPH_ID_CUST1);
                } else if (!mWatchCustom2) {
                    // Watching 1 with something other than Browser
                    // Not Watching 2
                    // Setup 2 to Watch Browser
                    mWatchCustom2Val = "com.android.browser";
                    mPerfService.setWatchCustom2(mWatchCustom2Val);
                    mWatchCustom2 = true;
                    item.setTitle(R.string.hidecustom2);
                }
            }
        } else if (mWatchCustom2) {
            if (!mWatchCustom2Val.contains("com.android.browser")) {
                // Not Watching 1
                // Watching 2 with Something other than Browser
                // Setup 1 to Watch Browser
                mWatchCustom1Val = "com.android.browser";
                mPerfService.setWatchCustom1(mWatchCustom1Val);
                mWatchCustom1 = true;
                item.setTitle(R.string.hidecustom1);
            }
        } else {
            // Not Watching 1
            // Not Watching 2
            // Setup 1 to Watch Browser
            mWatchCustom1Val = "com.android.browser";
            mPerfService.setWatchCustom1(mWatchCustom1Val);
            mWatchCustom1 = true;
            item.setTitle(R.string.hidecustom1);
        }
    }

    private ActionMode.Callback mMemGraphContext = new ActionMode.Callback() {

        // Called when the action mode is created; startActionMode() was called
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            // Inflate a menu resource providing context menu items
            MenuInflater inflater = mode.getMenuInflater();
            inflater.inflate(R.menu.mem_context_menu, menu);
            return true;
        }

        // Called each time the action mode is shown. Always called after onCreateActionMode, but
        // may be called multiple times if the mode is invalidated.
        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false; // Return false if nothing is done
        }

        // Called when the user selects a contextual menu item
        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            switch (item.getItemId()) {
                case R.id.menu_reset:
                    mPerfService.resetCounters(PerfService.GRAPH_ID_MEM);
                    mode.finish(); // Action picked, so close the CAB
                    return true;
                case R.id.menu_toggle:
                    mPerfService.toggle(PerfService.GRAPH_ID_MEM);
                    mode.finish(); // Action picked, so close the CAB
                    return true;
                default:
                    return false;
            }
        }

        // Called when the user exits the action mode
        @Override
        public void onDestroyActionMode(ActionMode mode) {
            mActionMode = null;
        }
    };

    public static class MyAlertDialogFragment extends DialogFragment {

        public static MyAlertDialogFragment newInstance(int title, String text) {
            MyAlertDialogFragment frag = new MyAlertDialogFragment();
            Bundle args = new Bundle();
            args.putInt("title", title);
            args.putString("text", text);
            frag.setArguments(args);
            return frag;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            int title = getArguments().getInt("title");
            String text = getArguments().getString("text");


            return new AlertDialog.Builder(getActivity())
                    .setTitle(title)
                    .setMessage(text)
                    .setPositiveButton(R.string.ok,
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int whichButton) {

                                }
                            }
                    )
                    .create();
        }
    }

    void showDialog(int title, String text) {
        DialogFragment newFragment = MyAlertDialogFragment.newInstance(
                title, text);
        newFragment.show(getFragmentManager(), "dialog");
    }

    private void startForceLowMem(int type) {
        PackageManager pm = getActivity().getPackageManager();
        int installed = 0;
        final int finalservicetype = type;
        for (int k = 0; k < PerfService.MAXMEMAPPS; ++k) {
            String packagename = PerfService.mConsumerPackageName[k];
            try {
                pm.getPackageInfo(packagename, PackageManager.GET_ACTIVITIES);
                installed++;
            } catch (PackageManager.NameNotFoundException e) {
                Toast.makeText(getActivity(), packagename + " not installed.", Toast.LENGTH_LONG).show();
            }
        }
        if (installed == PerfService.MAXMEMAPPS) {
            if (type == PerfService.SIMULATE_LOWMEM_ONETIME) {
                mForcingLowMem = true;
                mPerfService.launchForceLowMemory(finalservicetype);
                getActivity().invalidateOptionsMenu();
            } else if (type == PerfService.SIMULATE_LOWMEM_CONTINUOUS) {
                AlertDialog.Builder alert = new AlertDialog.Builder(getActivity());
                alert.setTitle(R.string.lowmemtesttitle);
                alert.setMessage(R.string.lowmemtesttext);
                alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        mForcingLowMem = true;
                        mPerfService.launchForceLowMemory(finalservicetype);
                        getActivity().invalidateOptionsMenu();
                    }
                });

                alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        // Canceled.
                    }
                });
                alert.show();
            } else if (type == PerfService.SIMULATE_LOWMEM_LAUNCHAPP) {
                AlertDialog.Builder alert = new AlertDialog.Builder(getActivity());
                alert.setTitle(R.string.lowmemappswitchtesttitle);
                alert.setMessage(R.string.lowmemappswitchtesttext);
                alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        mForcingLowMemAppSwitch = true;
                        //mPerfService.launchForceLowMemory(finalservicetype);
                        startLowMemAppSwitch();
                        getActivity().invalidateOptionsMenu();
                    }
                });

                alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        // Canceled.
                    }
                });
                alert.show();
            }
        } else {
            showDialog(R.string.lowmemtesttitle, "Memory Consumer apks are not all installed - can't execute test.");
        }
    }

    public class LowMemLaunchApp {
        final private String TAG = "PerfMon -> LowMemLaunchApp";
        final public int MAXMEMAPPS = 15;
        final public String[] mConsumerPackageName = {"com.motorola.tools.memoryconsumer0",
                "com.motorola.tools.memoryconsumer1",
                "com.motorola.tools.memoryconsumer2",
                "com.motorola.tools.memoryconsumer3",
                "com.motorola.tools.memoryconsumer4",
                "com.motorola.tools.memoryconsumer5",
                "com.motorola.tools.memoryconsumer6",
                "com.motorola.tools.memoryconsumer7",
                "com.motorola.tools.memoryconsumer8",
                "com.motorola.tools.memoryconsumer9",
                "com.motorola.tools.memoryconsumera",
                "com.motorola.tools.memoryconsumerb",
                "com.motorola.tools.memoryconsumerc",
                "com.motorola.tools.memoryconsumerd",
                "com.motorola.tools.memoryconsumere"};
        final public String[] mConsumerClassName = {"MemoryConsumer0Activity",
                "MemoryConsumer1Activity",
                "MemoryConsumer2Activity",
                "MemoryConsumer3Activity",
                "MemoryConsumer4Activity",
                "MemoryConsumer5Activity",
                "MemoryConsumer6Activity",
                "MemoryConsumer7Activity",
                "MemoryConsumer8Activity",
                "MemoryConsumer9Activity",
                "MemoryConsumeraActivity",
                "MemoryConsumerbActivity",
                "MemoryConsumercActivity",
                "MemoryConsumerdActivity",
                "MemoryConsumereActivity"};
        final public int MAXMEMINTAPPS = 2;
        final public String[] mConsumerInteractivePackageName = {"com.motorola.tools.memoryconsumerinteractive0",
                "com.motorola.tools.memoryconsumerinteractive1"};
        final public String[] mConsumerInteractiveClassName = {"MemoryConsumerInteractive0Activity",
                "MemoryConsumerInteractive1Activity"};

        static final int LOWMEM_FORCELOWMEM = 0;
        static final int LOWMEM_LAUNCHFIRST = 1;
        static final int LOWMEM_FIRSTLARGEAPPLAUNCHED = 2;
        static final int LOWMEM_LAUNCHSECOND = 3;
        static final int LOWMEM_SECONDLARGEAPPLAUNCHED = 4;
        static final int LOWMEM_CHECKTHRESH = 5;
        static final int LOWMEM_REACHEDMEMTHRESH = 6;

        final int EMPTY_APP_PSS = 5; //5MB is roughly the size of the Memory Consumer apps without additional allocations through the size parameter
        final int BASIC_ALLOCATION = 45; //Allocate this amount as a rough start to gain low memory
        final int THRESH_OFFSET = 20;  //Want to make the consumers such that they don't force too many immediate sigkills upon creation
        final int SAFETY = 20; //Can't get too close to threshold or system continually kills
        final static public int MEG = 1024;

        static final int WAIT_TIME = 10000;
        static final int WAIT_TIME2 = 20000;

        final int FIRSTAPP_ALLOCATION = 100;
        final int SECONDAPP_ALLOCATION = 100;

        private int state;
        private boolean initial_first;
        private boolean initial_second;
        private boolean simulate_lowmemory;

        private Handler mHandler = new Handler();
        private Runnable mRefresh = new Runnable() {
            public void run() {
                if (isForceLowMem()) {
                    forceLowMem();
                } else if (isFirstLaunched()) {
                    if (simulate_lowmemory) {
                        launchSecond();
                    } else {
                        launchFirst();
                    }
                } else if (isSecondLaunched()) {
                    forceLowMem();
                }
                //mHandler.postDelayed(mRefresh, SAMPLING_INTERVAL * 1000);
            }
        };

        LowMemLaunchApp() {
            state = LOWMEM_FORCELOWMEM;
            Log.i(TAG, "Setting initial first");
            initial_first = true;
            initial_second = true;
            simulate_lowmemory = true;
        }

        public void startup() {
            if (simulate_lowmemory) {
                forceLowMem();
            } else {
                launchFirst();
            }
        }

        public int getPSS(String processname) {
            ActivityManager am = (ActivityManager) getActivity().getSystemService(Context.ACTIVITY_SERVICE);
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

        public void forceLowMem() {
            state = LOWMEM_FORCELOWMEM;
            ActivityManager am = (ActivityManager) getActivity().getSystemService(Context.ACTIVITY_SERVICE);
            MemoryInfo minfo = new MemoryInfo();
            am.getMemoryInfo(minfo);
            boolean consumer_launched = false;
            int availmem = (int) (minfo.availMem / MemMon.MEG / MemMon.MEG);
            int thresh = (int) (minfo.threshold / MemMon.MEG / MemMon.MEG);

            if ((availmem - EMPTY_APP_PSS) > thresh + SAFETY) {
                int size = ((availmem - (thresh + THRESH_OFFSET)) > BASIC_ALLOCATION) ? BASIC_ALLOCATION : (availmem - (thresh + THRESH_OFFSET));
                size = (size < 0) ? 0 : size;
                for (int k = 0; k < MAXMEMAPPS; ++k) {
                    String packagename = mConsumerPackageName[k];
                    String classname = mConsumerClassName[k];
                    if (getPSS(packagename) == 0) {
                        Intent i = new Intent(Intent.ACTION_MAIN);
                        i.setClassName(packagename, packagename + "." + classname);
                        i.putExtra("size", size);
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(i);
                        Log.i(TAG, "Allocated " + Integer.toString(size) + " MB in" + classname + " when avail is " + Integer.toString(availmem) + " and thresh is " + Integer.toString(thresh));
                        consumer_launched = true;
                        //Delay some time and then launch again.
                        mHandler.postDelayed(mRefresh, WAIT_TIME);
                        break;
                    }
                }
                if (!consumer_launched) {
                    //Should check a hysteresis here but for now just move to next state
                    launchFirst();
                }
            } else {
                //At a sufficient threshold, move to the next state
                launchFirst();
            }
        }

        public boolean isForceLowMem() {
            return (state == LOWMEM_FORCELOWMEM);
        }

        public void launchFirst() {
            state = LOWMEM_LAUNCHFIRST;

            ActivityManager am = (ActivityManager) getActivity().getSystemService(Context.ACTIVITY_SERVICE);
            MemoryInfo minfo = new MemoryInfo();
            am.getMemoryInfo(minfo);

            int availmem = (int) (minfo.availMem / MemMon.MEG / MemMon.MEG);
            int thresh = (int) (minfo.threshold / MemMon.MEG / MemMon.MEG);

            String packagename = mConsumerInteractivePackageName[0];
            String classname = mConsumerInteractiveClassName[0];
            int size = FIRSTAPP_ALLOCATION;
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.setClassName(packagename, packagename + "." + classname);
            i.putExtra("reset", mForceLowMemLaunchApp.isInitialFirst());
            if (!mForceLowMemLaunchApp.isInitialFirst()) {
                size = 0;
                i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            }
            i.putExtra("size", size);
            i.putExtra("time", System.currentTimeMillis());
            i.putExtra("avail", availmem);
            startActivityForResult(i, ACTIVITY_MEASURE_LAUNCH);
            Log.i(TAG, "launchFirst : Allocated " + Integer.toString(size) + " MB in" + classname + " when avail is " + Integer.toString(availmem) + " and thresh is " + Integer.toString(thresh));
        }

        public boolean isLaunchFirst() {
            return (state == LOWMEM_LAUNCHFIRST);
        }

        public void firstLaunched() {
            state = LOWMEM_FIRSTLARGEAPPLAUNCHED;
            initial_first = false;

            //Delay some time and then launch again.
            mHandler.postDelayed(mRefresh, WAIT_TIME);
        }

        public boolean isInitialFirst() {
            return initial_first;
        }

        public boolean isFirstLaunched() {
            return (state == LOWMEM_FIRSTLARGEAPPLAUNCHED);
        }

        public void launchSecond() {
            state = LOWMEM_LAUNCHSECOND;

            ActivityManager am = (ActivityManager) getActivity().getSystemService(Context.ACTIVITY_SERVICE);
            MemoryInfo minfo = new MemoryInfo();
            am.getMemoryInfo(minfo);

            int availmem = (int) (minfo.availMem / MemMon.MEG / MemMon.MEG);
            int thresh = (int) (minfo.threshold / MemMon.MEG / MemMon.MEG);

            String packagename = mConsumerInteractivePackageName[1];
            String classname = mConsumerInteractiveClassName[1];
            int size = SECONDAPP_ALLOCATION;
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.setClassName(packagename, packagename + "." + classname);
            i.putExtra("reset", mForceLowMemLaunchApp.isInitialSecond());
            if (!mForceLowMemLaunchApp.isInitialSecond()) {
                size = 0;
                i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            }
            i.putExtra("size", size);
            i.putExtra("time", System.currentTimeMillis());
            i.putExtra("avail", availmem);
            startActivityForResult(i, ACTIVITY_MEASURE_LAUNCH);
            Log.i(TAG, "launchSecond : Allocated " + Integer.toString(size) + " MB in" + classname + " when avail is " + Integer.toString(availmem) + " and thresh is " + Integer.toString(thresh));
        }

        public boolean isLaunchSecond() {
            return (state == LOWMEM_LAUNCHSECOND);
        }

        public void secondLaunched() {
            state = LOWMEM_SECONDLARGEAPPLAUNCHED;
            initial_second = false;

            //Delay some time and then launch again.
            mHandler.postDelayed(mRefresh, WAIT_TIME2);
        }

        public boolean isInitialSecond() {
            return initial_second;
        }

        public boolean isSecondLaunched() {
            return (state == LOWMEM_SECONDLARGEAPPLAUNCHED);
        }

        public void checkThresh() {
            state = LOWMEM_CHECKTHRESH;
        }

        public boolean isCheckThresh() {
            return (state == LOWMEM_CHECKTHRESH);
        }

        public void threshReached() {
            state = LOWMEM_REACHEDMEMTHRESH;
        }

        public boolean isThreshReached() {
            return (state == LOWMEM_REACHEDMEMTHRESH);
        }

        public void updateStateConsumerInteractiveReturned() {
            if (isLaunchFirst()) {
                firstLaunched();
            } else if (isLaunchSecond()) {
                secondLaunched();
            }
        }

        public void stop() {
            if (mHandler != null) {
                mHandler.removeCallbacksAndMessages(null);
            }
            ActivityManager am = (ActivityManager) getActivity().getSystemService(Context.ACTIVITY_SERVICE);


            for (int k = 0; k < MAXMEMAPPS; ++k) {
                String packageName = mConsumerPackageName[k];
                am.killBackgroundProcesses(packageName);
            }
            for (int k = 0; k < MAXMEMINTAPPS; ++k) {
                String packageName = mConsumerInteractivePackageName[k];
                am.killBackgroundProcesses(packageName);
            }
        }
    }

    public void startLowMemAppSwitch() {
        mForceLowMemLaunchApp = new LowMemLaunchApp();
        mForceLowMemLaunchApp.startup();
    }

    public void stopLowMemAppSwitch() {
        if (mForceLowMemLaunchApp != null) mForceLowMemLaunchApp.stop();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        // TODO Auto-generated method stub
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == ACTIVITY_MEASURE_LAUNCH) {
            if (resultCode == Activity.RESULT_OK) {
                mForceLowMemLaunchApp.updateStateConsumerInteractiveReturned();
            }
        }
    }

    public void runMemHack(int type) {
        Log.d(TAG, "KEVIN start testing trace");
        mPerfService.startHack(type);
        if (type == PerfService.memHack.MEMHACK_RUN) {
            showDialog(R.string.tracetesttitle, getString(R.string.starttracetesttext));
        } else if (type == PerfService.memHack.MEMHACK_RUNMOD) {
            showDialog(R.string.tracetesttitle, getString(R.string.starttracemodtesttext));
        }
    }

    public void stopMemHack() {
        Log.d(TAG, "KEVIN stop testing trace");
        mPerfService.stopHack();
        showDialog(R.string.tracetesttitle, getString(R.string.stoptracetesttext));
    }
}
