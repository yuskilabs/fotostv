package com.yuskilabs.fotostv;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

/** Tras una actualización pedida desde la app, vuelve a abrirla. */
public class ReopenReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        SharedPreferences sp = c.getSharedPreferences("update", Context.MODE_PRIVATE);
        long t = sp.getLong("pending", 0);
        sp.edit().remove("pending").commit();
        if (t == 0 || System.currentTimeMillis() - t > 15 * 60 * 1000) return;
        try {
            Intent l = new Intent(c, SettingsActivity.class);
            l.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(l);
        } catch (Throwable ignored) {}
    }
}
