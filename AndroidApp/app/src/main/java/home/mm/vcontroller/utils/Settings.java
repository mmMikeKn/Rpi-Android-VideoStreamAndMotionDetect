package home.mm.vcontroller.utils;

import android.content.SharedPreferences;
import android.os.Environment;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public class Settings {
    public final static int MODE_SNAPSHOT_NO_MOTION_VECTORS = 0;
    public final static int MODE_SNAPSHOT_MOTION_VECTORS_DRAW_VECTORS = 1;
    //public final static int MODE_SNAPSHOT_MOTION_VECTORS_DRAW_RECTANGLES = 2;

    public static boolean isInternalNetwork = true;
    public static int vFps = 15, vBitrate = 500000, vQuality = 0, vISO = 0;
    public static double resolutionDiv = 2;

    public static String internalHost = "192.168.0.0.77";
    public static String externalHost = "ans42.ru";
    public static int tcpIpPort = 8081;
    public static int connectTimeout = 3000;
    public static String password = "qscfew";

    public static boolean startCheckMotionService = true;
    public static boolean vibrateMotionNotification = false;
    public static boolean loadMotionSnapshot = true;
    public static String motionSnapshotFileMask = "dd-MM-yy_hh-mm-ss";
    public static String notificationsRingtone = "content://settings/system/notification_sound";
    public static boolean hasNotificationsSound = false;
    public static int motionCheckPollingDt = 10;
    public static int motionThresholdCnt = 150;
    public static int motionThresholdValue = 70;
    public static int motionThresholdSAD = 300;
    public static boolean motionNoiseFilter = true;
    public static float motionSnapshotDt = 1.0f;
    public static float motionDetectTimeWindow = 1.0f;
    public static int motionNumberInTimeWindow = 3;

    public static boolean snapshotColorless = false;
    public static int modeSnapshot = MODE_SNAPSHOT_NO_MOTION_VECTORS;

    public static String cmdSpiMagicBytes = "666F";
    public static boolean hasExternalControllerSPI = true;
    public static boolean checkExternalControllerBatteryVoltage = true;
    public static float externalControllerBatteryVoltageAlarmLevel = 3.5f * 4;

    public static void loadConfig(SharedPreferences settings) throws Exception {
        Class aClass = Settings.class;
        Field[] fields = aClass.getFields();
        for (Field field : fields) {
            if ((field.getModifiers() & Modifier.FINAL) != 0) continue;
            String name = field.getName();
            String type = field.getType().getCanonicalName();
            if (type.equals("int")) {
                field.set(null, Integer.parseInt(settings.getString(name, Integer.toString(field.getInt(aClass)))));
            } else if (type.equals("double")) {
                field.set(null, Double.parseDouble(settings.getString(name, Double.toString(field.getDouble(aClass)))));
            } else if (type.equals("float")) {
                field.set(null, Float.parseFloat(settings.getString(name, Float.toString(field.getFloat(aClass)))));
            } else if (type.contains("java.lang.String")) {
                String s = settings.getString(name, field.get(aClass).toString());
                if (s != null && !s.isEmpty())
                    field.set(null, s);
            } else if (type.equals("boolean")) {
                field.set(null, settings.getBoolean(name, field.getBoolean(aClass)));
            }
        }
    }

    public static void saveConfig(SharedPreferences settings) throws Exception {
        SharedPreferences.Editor editor = settings.edit();
        Class aClass = Settings.class;
        Field[] fields = Settings.class.getFields();
        for (Field field : fields) {
            String name = field.getName();
            String type = field.getType().getCanonicalName();
            if (type.equals("int")) {
                editor.putString(name, Integer.toString(field.getInt(aClass)));
            } else if (type.equals("double")) {
                editor.putString(name, Double.toString(field.getDouble(aClass)));
            } else if (type.equals("float")) {
                editor.putString(name, Float.toString(field.getFloat(aClass)));
            } else if (type.contains("java.lang.String")) {
                editor.putString(name, field.get(aClass).toString());
            } else if (type.equals("boolean")) {
                editor.putBoolean(name, field.getBoolean(aClass));
            }
        }
        editor.apply();
    }
}
