package home.mm.vcontroller;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.Toast;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import home.mm.vcontroller.utils.Settings;

public class CheckMotionService extends Service {
    final String LOG_TAG = "CheckMotion";
    private Thread thread;
    private static int NOTIFICATION_MOTION = 1;
    private static int NOTIFICATION_FAIL = 2;
    private static int NOTIFICATION_START_FOREGROUND = 3;

    public static String INTENT_ACTION_START = "START";
    public static String INTENT_ACTION_DELETE_SNAPSHOT = "DELETE_SNAPSHOT";
    public static String INTENT_ACTION_COLLAPSE_ALL = "COLLAPSE_ALL";
    public static String INTENT_ACTION_UP_LIST = "UP_LIST";
    public static String INTENT_ACTION_DOWN_LIST = "DOWN_LIST";
    public static String INTENT_ACTION_NOTIFICATION_NUMBER = "item_for_delete";
    private float voltage = -1;

    private RenderScript mRS;

    NotificationManager mNotificationManager;

    List<NotificationInfo> mNotificationsList = new ArrayList<>();

    private class NotificationInfo {
        String snapshotFileName, info;
        long startTime;
        float voltage;

        NotificationInfo(String snapshotFileName, String info, float voltage) {
            this.snapshotFileName = snapshotFileName;
            this.info = info;
            startTime = SystemClock.elapsedRealtime();
            this.voltage = voltage;
        }
    }

    public void onCreate() {
        super.onCreate();
        mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        mNotificationsList.clear();
        setAlarmNotification(-1);
        Log.d(LOG_TAG, "onCreate");
        try {
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
            Settings.loadConfig(settings);
        } catch (final Exception e) {
            setErrorNotification("Settings loading error", e);
            Log.e(LOG_TAG, "", e);
        }
        mRS = RenderScript.create(this);
    }

    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(LOG_TAG, "ACTION:" + intent.getAction());
        if (INTENT_ACTION_DELETE_SNAPSHOT.equals(intent.getAction())) {
            int p = intent.getIntExtra(INTENT_ACTION_NOTIFICATION_NUMBER, mNotificationsList.size() - 1);
            Log.d(LOG_TAG, "--------- start ACTION_DELETE_SNAPSHOT [" + p + "]--------");
            if (mNotificationsList.get(p).snapshotFileName != null) {
                String fname = mNotificationsList.get(p).snapshotFileName;
                if (new File(fname).delete())
                    Toast.makeText(this, fname + " deleted", Toast.LENGTH_SHORT).show();
                else Toast.makeText(this, "Fail to delete " + fname, Toast.LENGTH_LONG).show();
            }
            mNotificationsList.remove(p);
            if (p > 0) p--;
            setAlarmNotification(p);
            Log.d(LOG_TAG, "--------- end ACTION_DELETE_SNAPSHOT --------");
        } else if (INTENT_ACTION_UP_LIST.equals(intent.getAction())) {
            int p = intent.getIntExtra(INTENT_ACTION_NOTIFICATION_NUMBER, mNotificationsList.size() - 1);
            Log.d(LOG_TAG, "--------- start ACTION_UP [" + p + "]--------");
            if (p > 0) p--;
            setAlarmNotification(p);
        } else if (INTENT_ACTION_DOWN_LIST.equals(intent.getAction())) {
            int p = intent.getIntExtra(INTENT_ACTION_NOTIFICATION_NUMBER, mNotificationsList.size() - 1);
            Log.d(LOG_TAG, "--------- start ACTION_DOWN [" + p + "]--------");
            if (p < (mNotificationsList.size() - 1)) p++;
            setAlarmNotification(p);
        } else if (INTENT_ACTION_COLLAPSE_ALL.equals(intent.getAction())) {
            mNotificationsList.clear();
            setAlarmNotification(-1);
        } else {
            if (thread != null) {
                try {
                    thread.interrupt();
                    thread.join();
                } catch (InterruptedException ignore) {
                }
            }

            thread = new Thread(new Runnable() {
                public void run() {
                    Log.d(LOG_TAG, "--------- start motion checker --------");
                    while (!Thread.interrupted()) {
                        try {
                            checkMotion();
                            TimeUnit.SECONDS.sleep(Settings.motionCheckPollingDt);
                        } catch (InterruptedException ignore) {
                            break;
                        }
                    }
                    Log.d(LOG_TAG, "------- end of motion checker");
                }
            });
            thread.start();
        }
        return START_STICKY;
    }

    public void onDestroy() {
        super.onDestroy();
        if (thread != null) thread.interrupt();
        mNotificationManager.cancel(NOTIFICATION_MOTION);
        mNotificationManager.cancel(NOTIFICATION_FAIL);
        mRS.finish();
        Log.d(LOG_TAG, "onDestroy");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }


    //----------------------------------------------------------------------------------------------
    @SuppressLint("DefaultLocale")
    @SuppressWarnings("ResultOfMethodCallIgnored")
    void checkMotion() {
        Socket socket = null;
        try {
            socket = new Socket();
            InetSocketAddress socketAddress = new InetSocketAddress(
                    Settings.isInternalNetwork ? Settings.internalHost : Settings.externalHost, Settings.tcpIpPort);
            socket.connect(socketAddress, Settings.connectTimeout);
            socket.setSoTimeout(3000);
            socket.getOutputStream().write(String.format("%s,%s\n", MainActivity.CMD.AUTH.name(), Settings.password).getBytes());
            socket.getOutputStream().flush();
            DataInputStream in = new DataInputStream(socket.getInputStream());
            if (Settings.hasExternalControllerSPI && Settings.checkExternalControllerBatteryVoltage) {
                StringBuilder s = new StringBuilder(MainActivity.CMD.SPI.name())
                        .append(",r,")
                        .append(Settings.cmdSpiMagicBytes)
                        .append("000000\n");
                socket.getOutputStream().write(s.toString().getBytes());
                socket.getOutputStream().flush();
                Log.d(LOG_TAG, "send: " + s.toString());
                byte[] b = new byte[2 + 3];
                in.readFully(b);
                voltage = ((int) b[0] & 0x0FF) / 10.0f;
                Log.v(LOG_TAG, "voltage level:" + voltage);
                if (voltage < Settings.externalControllerBatteryVoltageAlarmLevel && voltage > 5)
                    setErrorNotification("Too low voltage level: " + getVoltageInfo(voltage), null);
            }
            Character mode = '0';
            if (Settings.loadMotionSnapshot)
                mode = Settings.modeSnapshot == Settings.MODE_SNAPSHOT_NO_MOTION_VECTORS ? 'S' : 'F';
            socket.getOutputStream().write((MainActivity.CMD.MOTION_INFO.name() + "," + mode + "\n").getBytes());
            socket.getOutputStream().flush();

            int magicHd = in.readShort();
            if (magicHd != 0x37F4) {
                Log.e(LOG_TAG, "magicHd:" + Integer.toString(magicHd));
                throw new IllegalArgumentException("Illegal answer data");
            }
            long time = in.readInt();
            int motionValue = in.readShort();
            String fname = null;
            if (time != 0) {
                int fileLength = in.readInt();
                if (time > 0) {
                    Date d = new Date(time * 1000L);
                    Log.v(LOG_TAG, "motion " + motionValue + " " + d.toString());
                    if (Settings.loadMotionSnapshot) {
                        //---------
                        if (fileLength < 64 || fileLength > 1000000)
                            throw new IllegalArgumentException("Wrong snapshot body size:" + fileLength);
                        byte fbody[] = new byte[fileLength];
                        in.readFully(fbody);
                        //----------
                        short mvRows = in.readShort();
                        short mvColumns = in.readShort();
                        int[] mv = new int[mvRows * mvColumns];
                        for (int i = 0; i < mv.length; i++) mv[i] = in.readInt();
                        fname = saveSnapshot(fbody, d, mv, mvRows, mvColumns);
                    } else {
                        //noinspection StatementWithEmptyBody
                        while (in.read() >= 0) {
                        }
                    }
                    mNotificationsList.add(new NotificationInfo(fname,
                            "(" + motionValue + ") " + android.text.format.DateFormat.format("dd.MM.yy hh:mm:ss", d)
                            , voltage));
                }
            }
            setAlarmNotification(mNotificationsList.size() - 1);
        } catch (java.net.SocketTimeoutException ex) {
            setErrorNotification(android.text.format.DateFormat.format("hh:mm:ss", new Date()).toString() + " " + ex.getMessage(), null);
        } catch (Exception ex) {
            setErrorNotification(android.text.format.DateFormat.format("dd.MM.yy hh:mm:ss", new Date()).toString(), ex);
            Log.v(LOG_TAG, "connect fail", ex);
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException ignore) {
                }
            }
        }
    }

    static final String NOTIFICATION_CHANNEL_INFO = "home.mm.vcontroller.NOTIFICATION_CHANNEL_INFO";
    static final String NOTIFICATION_CHANNEL_ALARM = "home.mm.vcontroller.NOTIFICATION_CHANNEL_ALARM";
    static final String NOTIFICATION_CHANNEL_ERROR = "home.mm.vcontroller.NOTIFICATION_CHANNEL_ERROR";

    void setErrorNotification(String error, Exception ex) {
        Log.e(LOG_TAG, "notification:" + error, ex);
        NotificationCompat.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel notificationChannel = new NotificationChannel(NOTIFICATION_CHANNEL_ERROR, "Error notifications", NotificationManager.IMPORTANCE_DEFAULT);
            notificationChannel.setDescription("Motion detection fail");
            notificationChannel.enableLights(false);
            notificationChannel.enableVibration(false);
            notificationChannel.setSound(null, null);
            notificationChannel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
            mNotificationManager.createNotificationChannel(notificationChannel);
        }
        builder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ERROR);

        String bigText = null;
        if (ex != null) {
            StringWriter w = new StringWriter();
            ex.printStackTrace(new PrintWriter(w));
            bigText = w.toString();
        }
        builder.setSmallIcon(R.drawable.ic_motion_alarm)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setCategory(Notification.CATEGORY_ALARM)
                .setContentTitle(error)
                .setColor(Color.MAGENTA)
                .setAutoCancel(true);
        if (bigText != null)
            builder.setStyle(new NotificationCompat.BigTextStyle().bigText(bigText));
        Notification notification = builder.build();
        mNotificationManager.notify(NOTIFICATION_FAIL, notification);
    }

    @SuppressLint("DefaultLocale")
    private String getVoltageInfo(float voltage) {
        return String.format("%2.1fv (%2.2fv)", voltage, voltage / 4);
    }

    void setAlarmNotification(int listPos) {
        boolean isInfoOnly = listPos < 0 || listPos >= mNotificationsList.size();

        NotificationCompat.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (isInfoOnly) {
                NotificationChannel notificationChannel = new NotificationChannel(NOTIFICATION_CHANNEL_INFO, "Info notifications", NotificationManager.IMPORTANCE_LOW);
                notificationChannel.setDescription("Motion detector is ready");
                notificationChannel.enableVibration(false);
                notificationChannel.enableLights(false);
                notificationChannel.setSound(null, null);
                notificationChannel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
                mNotificationManager.createNotificationChannel(notificationChannel);
            } else {
                NotificationChannel notificationChannel = new NotificationChannel(NOTIFICATION_CHANNEL_ALARM, "Alarm notifications", NotificationManager.IMPORTANCE_HIGH);
                notificationChannel.setDescription("Motion Alarm notification");
                notificationChannel.enableLights(true);
                notificationChannel.setLightColor(Color.RED);
                notificationChannel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
                notificationChannel.enableVibration(Settings.vibrateMotionNotification);
                if (Settings.vibrateMotionNotification)
                    notificationChannel.setVibrationPattern(new long[]{0, 1000});
                if (Settings.hasNotificationsSound)
                    notificationChannel.setSound(Uri.parse(Settings.notificationsRingtone), null);
                else notificationChannel.setSound(null, null);
                mNotificationManager.createNotificationChannel(notificationChannel);
            }
        }
        builder = new NotificationCompat.Builder(this, isInfoOnly ? NOTIFICATION_CHANNEL_INFO : NOTIFICATION_CHANNEL_ALARM);
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        builder.setOngoing(true)
                .setContentIntent(pendingIntent)
                .setSmallIcon(R.drawable.ic_motion_alarm);

        //builder.setOnlyAlertOnce(Settings.hasNotificationsSound);

        //-----------------------------------------------------------
        if (isInfoOnly) {
            String voltageStr = voltage > 0 ? getVoltageInfo(voltage) : "";
            builder.setContentTitle("Motion detector " + voltageStr)
                    .setShowWhen(true)
                    .setUsesChronometer(true)
                    .setCategory(Notification.CATEGORY_SERVICE);
            startForeground(NOTIFICATION_START_FOREGROUND, builder.build());
            return;
        } else {
            builder.setColor(Color.RED).setShowWhen(true)
                    .setCategory(Notification.CATEGORY_ALARM);
            if (Settings.vibrateMotionNotification) builder.setVibrate(new long[]{0, 1000});
            if (Settings.hasNotificationsSound)
                builder.setSound(Uri.parse(Settings.notificationsRingtone));

            String info = mNotificationsList.get(listPos).info;
            String title = "Motion alarm (" + (listPos + 1) + "/" + mNotificationsList.size() + ")";
            String snapshotFileName = mNotificationsList.get(listPos).snapshotFileName;
            long startTime = mNotificationsList.get(listPos).startTime;
            float voltage = mNotificationsList.get(listPos).voltage;

            if (snapshotFileName != null) {
                RemoteViews notificationLayoutLong = new RemoteViews(getPackageName(), R.layout.notification_with_snapshot_large);
                RemoteViews notificationLayoutShort = new RemoteViews(getPackageName(), R.layout.notification_with_snapshot_short);

                notificationLayoutLong.setChronometer(R.id.notification_chronometer, startTime, null, true);
                notificationLayoutLong.setTextViewText(R.id.notification_title, title);
                notificationLayoutLong.setTextViewText(R.id.notification_info, info);
                notificationLayoutShort.setChronometer(R.id.notification_chronometer, startTime, null, true);
                notificationLayoutShort.setTextViewText(R.id.notification_title, title);
                notificationLayoutShort.setTextViewText(R.id.notification_info, info);
                if (voltage < 0) {
                    notificationLayoutLong.setViewVisibility(R.id.notification_voltage, View.INVISIBLE);
                    notificationLayoutShort.setViewVisibility(R.id.notification_voltage, View.INVISIBLE);
                } else {
                    String voltageStr = getVoltageInfo(voltage);
                    notificationLayoutLong.setTextViewText(R.id.notification_voltage, voltageStr);
                    notificationLayoutLong.setViewVisibility(R.id.notification_voltage, View.VISIBLE);
                    notificationLayoutShort.setTextViewText(R.id.notification_voltage, voltageStr);
                    notificationLayoutShort.setViewVisibility(R.id.notification_voltage, View.VISIBLE);
                }

                Bitmap bitmap = BitmapFactory.decodeFile(snapshotFileName);
                builder.setLargeIcon(bitmap);
                notificationLayoutLong.setImageViewBitmap(R.id.notification_snapshot, bitmap);

                intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(Uri.fromFile(new File(snapshotFileName)), "image/*");
                pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
                notificationLayoutLong.setOnClickPendingIntent(R.id.notification_snapshot, pendingIntent);

                intent = new Intent(this, CheckMotionService.class);
                intent.setAction(INTENT_ACTION_DELETE_SNAPSHOT);
                intent.putExtra(INTENT_ACTION_NOTIFICATION_NUMBER, listPos);
                pendingIntent = PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
                notificationLayoutLong.setOnClickPendingIntent(R.id.notification_imageButtonDelete, pendingIntent);

                if (listPos > 0) {
                    intent = new Intent(this, CheckMotionService.class);
                    intent.setAction(INTENT_ACTION_UP_LIST);
                    intent.putExtra(INTENT_ACTION_NOTIFICATION_NUMBER, listPos);
                    pendingIntent = PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
                    notificationLayoutLong.setOnClickPendingIntent(R.id.notification_imageButtonUp, pendingIntent);
                    notificationLayoutLong.setViewVisibility(R.id.notification_imageButtonUp, View.VISIBLE);
                } else
                    notificationLayoutLong.setViewVisibility(R.id.notification_imageButtonUp, View.INVISIBLE);
                if (listPos < (mNotificationsList.size() - 1)) {
                    intent = new Intent(this, CheckMotionService.class);
                    intent.setAction(INTENT_ACTION_DOWN_LIST);
                    intent.putExtra(INTENT_ACTION_NOTIFICATION_NUMBER, listPos);
                    pendingIntent = PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
                    notificationLayoutLong.setOnClickPendingIntent(R.id.notification_imageButtonDown, pendingIntent);
                    notificationLayoutLong.setViewVisibility(R.id.notification_imageButtonDown, View.VISIBLE);
                } else
                    notificationLayoutLong.setViewVisibility(R.id.notification_imageButtonDown, View.INVISIBLE);

                intent = new Intent(this, CheckMotionService.class);
                intent.setAction(INTENT_ACTION_COLLAPSE_ALL);
                pendingIntent = PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
                notificationLayoutLong.setOnClickPendingIntent(R.id.notification_imageButtonCollapseAll, pendingIntent);
                builder.setCustomContentView(notificationLayoutShort);
                builder.setCustomBigContentView(notificationLayoutLong);
            } else {
                builder.setContentTitle(title)
                        .setColor(Color.RED)
                        .setContentText(info)
                        .setUsesChronometer(true)
                        .setWhen(startTime);

                intent = new Intent(this, CheckMotionService.class);
                intent.setAction(INTENT_ACTION_COLLAPSE_ALL);
                intent.putExtra(INTENT_ACTION_NOTIFICATION_NUMBER, listPos);
                pendingIntent = PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
                builder.addAction(R.drawable.ic_collapse_all, getString(R.string.collapse_all_notifications), pendingIntent);
                if (listPos > 0) {
                    intent = new Intent(this, CheckMotionService.class);
                    intent.setAction(INTENT_ACTION_UP_LIST);
                    intent.putExtra(INTENT_ACTION_NOTIFICATION_NUMBER, listPos);
                    PendingIntent upPendingIntent = PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
                    builder.addAction(R.drawable.ic_arrow_up_bold, getString(R.string.prior_notification), upPendingIntent);
                }
                if (listPos < (mNotificationsList.size() - 1)) {
                    intent = new Intent(this, CheckMotionService.class);
                    intent.setAction(INTENT_ACTION_DOWN_LIST);
                    intent.putExtra(INTENT_ACTION_NOTIFICATION_NUMBER, listPos);
                    PendingIntent downPendingIntent = PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
                    builder.addAction(R.drawable.ic_arrow_down_bold, getString(R.string.after_notification), downPendingIntent);
                }
            }
        }
        mNotificationManager.notify(NOTIFICATION_MOTION, builder.build());
    }

    @SuppressLint("DefaultLocale")
    private String saveSnapshot(byte[] fbody, Date d, int mv[], int mvRows, int mvColumns) throws IOException {
        File dirPictures = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        File dir = new File(dirPictures, getString(R.string.app_name));
        if (!dir.exists()) //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        File f = new File(dir, "/SS-" + android.text.format.DateFormat.format(Settings.motionSnapshotFileMask, d) + ".jpg");
        String fname = f.getAbsolutePath();

        if (Settings.modeSnapshot == Settings.MODE_SNAPSHOT_NO_MOTION_VECTORS || mv.length == 0) {
            FileOutputStream os = new FileOutputStream(f);
            os.write(fbody);
            os.close();
        } else {
            int p = 0;
            int maxSAD = -0x7FFF, minSAD = 0x7FFF;
            long overSAD = 0;
            for (int i = 0; i < mvRows; i++) {
                for (int j = 0; j < mvColumns; j++) {
                    short sad = (short) (mv[p++] & 0xFFFF);
                    maxSAD = Math.max(sad, maxSAD);
                    minSAD = Math.min(sad, minSAD);
                    overSAD += Math.abs(sad);
                }
            }
            Log.v(LOG_TAG, "SAD over:" + overSAD / mv.length + " min:" + minSAD + " max:" + maxSAD);

            Bitmap bitmap = BitmapFactory.decodeByteArray(fbody, 0, fbody.length);
            Allocation img = Allocation.createFromBitmap(mRS, bitmap);
            ScriptC_mv2img rs = new ScriptC_mv2img(mRS);
            if (Settings.snapshotColorless) rs.forEach_copyImage2Mono(img, img);
            rs.invoke_setupInputImg(img,
                    Settings.motionThresholdValue,
                    Settings.motionThresholdSAD,
                    Settings.motionNoiseFilter,
                    mvColumns, mvRows);
            Allocation mvAllocation = Allocation.createSized(mRS, Element.I32(mRS), mv.length);
            mvAllocation.copyFrom(mv);

            if (Settings.modeSnapshot == Settings.MODE_SNAPSHOT_MOTION_VECTORS_DRAW_VECTORS)
                rs.forEach_addMv2Image(mvAllocation, mvAllocation);
            else rs.forEach_addRectangles2Image(mvAllocation, mvAllocation);

            img.copyTo(bitmap);
/*
            Allocation cntAllocation = Allocation.createSized(mRS, Element.I32(mRS), 1);
            rs.forEach_getCnt(cntAllocation);
            int cntArray[] = new int[1];
            cntAllocation.copyTo(cntArray);
            Log.v(LOG_TAG, "-------------->Cnt:"+cntArray[0]);
*/
            OutputStream os = new FileOutputStream(f);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, os);
            os.close();
        }
        return fname;
    }
}
