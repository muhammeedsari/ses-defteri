package app.sesdefteri;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

/** Keeps long work (enhancing, speech-to-text, mixing) running when the app goes to the background. */
public class WorkService extends Service {
    private static final String CHANNEL = "work";
    private static final int ID = 9;
    private static int running;
    // the service is started only for work that lasts (> 1 s), and stopped from outside only once it is in the
    // foreground: stopping a foreground service before startForeground() makes Android kill the app
    private static boolean requested, fg;
    private static final android.os.Handler H = new android.os.Handler(android.os.Looper.getMainLooper());

    static void begin(final Context c) { begin(c, false); }

    /** now: long work (downloads, speech-to-text) asks for the service at once, while the app is surely on screen;
     *  Android refuses to start it from the background, so waiting a second lost it when the app was left at once */
    static synchronized void begin(final Context c, boolean now) {
        running++;
        if (now && !requested) { start(c.getApplicationContext()); return; }
        if (running == 1 && !requested) {
            final Context app = c.getApplicationContext();
            H.postDelayed(new Runnable() { @Override public void run() { start(app); } }, 1000);
        }
    }

    private static synchronized void start(Context c) {
        if (running == 0 || requested) return;
        requested = true;
        try {
            Intent i = new Intent(c, WorkService.class);
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i);
        } catch (Exception e) { requested = false; }
    }

    private static long since, lastProg;
    private static int lastPct = -1;
    private static Notification.Builder nb;
    private static volatile String label;

    /** 4.1: what is running, as the notification's title (null = the general title) */
    static void label(String s) { label = s == null || s.isEmpty() ? null : s; }

    /** 4.0.1: the running work's percentage on the notification (at most twice a second) */
    static void progress(Context c, double f) {
        Notification.Builder b = nb;
        if (!fg || b == null) return;
        int pct = (int) Math.max(0, Math.min(100, Math.round(f * 100)));
        long now = android.os.SystemClock.uptimeMillis();
        if (pct == lastPct || now - lastProg < 500) return;
        lastPct = pct; lastProg = now;
        try {
            b.setProgress(100, pct, false).setContentTitle(label != null ? label : Str.get(c, "workTitle")).setContentText(Str.get(c, "workText") + " · %" + pct);
            NotificationManager nm = (NotificationManager) c.getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(ID, b.build());
        } catch (Exception ignored) { }
    }

    static synchronized void end(Context c) {
        running = Math.max(0, running - 1);
        if (running == 0 && fg) {
            fg = false;
            try { c.stopService(new Intent(c, WorkService.class)); } catch (Exception ignored) { }
            // 3.4.6: long work finished while the app was out of sight (screen off): say so, also on the lock screen
            if (!MainActivity.visible && System.currentTimeMillis() - since > 8000) done(c.getApplicationContext());
        }
    }

    private static void done(Context c) {
        try {
            NotificationManager nm = (NotificationManager) c.getSystemService(NOTIFICATION_SERVICE);
            if (nm == null) return;
            Notification.Builder b;
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationChannel ch = new NotificationChannel("done", Str.get(c, "chDone"), NotificationManager.IMPORTANCE_DEFAULT);
                ch.setShowBadge(true);
                nm.createNotificationChannel(ch);
                b = new Notification.Builder(c, "done");
            } else b = new Notification.Builder(c);
            Intent open = new Intent(c, MainActivity.class);
            open.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            PendingIntent pi = PendingIntent.getActivity(c, 3, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            b.setSmallIcon(R.drawable.ic_stat_rec).setContentTitle(Str.get(c, "doneTitle")).setContentText(Str.get(c, "doneText"))
                .setAutoCancel(true).setContentIntent(pi).setVisibility(Notification.VISIBILITY_PUBLIC);
            nm.notify(10, b.build());
        } catch (Exception ignored) { }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, Str.get(this, "chWork"), NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            if (nm != null) nm.createNotificationChannel(ch);
            b = new Notification.Builder(this, CHANNEL);
        } else {
            b = new Notification.Builder(this);
        }
        Intent open = new Intent(this, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        PendingIntent pi = PendingIntent.getActivity(this, 2, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        b.setSmallIcon(R.drawable.ic_stat_rec)
            .setContentTitle(Str.get(this, "workTitle"))
            .setContentText(Str.get(this, "workText"))
            .setOngoing(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setProgress(0, 0, true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pi);
        nb = b; lastPct = -1;
        try {
            // 5.14.3: Android 15+ has a type for audio work (0x2000 = MEDIA_PROCESSING), downloads and older phones keep dataSync
            if (Build.VERSION.SDK_INT >= 35) startForeground(ID, b.build(), 0x2000);
            else if (Build.VERSION.SDK_INT >= 29) startForeground(ID, b.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            else startForeground(ID, b.build());
        } catch (Exception e) {
            stopSelf();
            return START_NOT_STICKY;
        }
        synchronized (WorkService.class) { fg = true; since = System.currentTimeMillis(); if (running == 0) { fg = false; stopSelf(); } }
        return START_NOT_STICKY;
    }

    /** Android 14/15 limit "data sync" services (6 h a day); stop quietly instead of being killed */
    public void onTimeout(int startId) { stopSelf(); }

    public void onTimeout(int startId, int type) { stopSelf(); }

    @Override
    public void onDestroy() {
        synchronized (WorkService.class) { fg = false; requested = false; nb = null; }
        try {
            if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE); else stopForeground(true);
        } catch (Exception ignored) { }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
