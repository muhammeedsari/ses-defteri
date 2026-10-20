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

    static synchronized void begin(Context c) {
        running++;
        if (running == 1) {
            try {
                Intent i = new Intent(c, WorkService.class);
                if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i);
            } catch (Exception ignored) { }
        }
    }

    static synchronized void end(Context c) {
        running = Math.max(0, running - 1);
        if (running == 0) {
            try { c.stopService(new Intent(c, WorkService.class)); } catch (Exception ignored) { }
        }
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
            .setProgress(0, 0, true)
            .setContentIntent(pi);
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(ID, b.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            else startForeground(ID, b.build());
        } catch (Exception e) {
            stopSelf();
        }
        synchronized (WorkService.class) { if (running == 0) stopSelf(); }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        try {
            if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE); else stopForeground(true);
        } catch (Exception ignored) { }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
