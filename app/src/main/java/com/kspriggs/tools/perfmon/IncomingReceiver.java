package com.kspriggs.tools.perfmon;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class IncomingReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(PerfService.RUN_BROWSER)) {
            System.out.println("GOT THE INTENT");
        }
    }
}
