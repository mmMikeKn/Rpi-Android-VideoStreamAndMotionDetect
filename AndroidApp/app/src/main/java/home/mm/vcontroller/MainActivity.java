package home.mm.vcontroller;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.FragmentManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StrictMode;
import android.preference.PreferenceManager;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Date;

import home.mm.vcontroller.utils.ErrorInfoDialog;
import home.mm.vcontroller.utils.NetworkConnect;
import home.mm.vcontroller.utils.Settings;
import home.mm.vcontroller.utils.ViewControllerPWM;
import home.mm.vcontroller.utils.cmdSpiInterface;

public class MainActivity extends Activity implements TextureView.SurfaceTextureListener, cmdSpiInterface {

    public enum CMD {
        START_STREAM,
        ROTATE,
        FINISH,
        AUTH,
        MOTION_INFO,
        SPI
    }

    private final static String LOG_TAG = "Main";
    private static final Object socketlock = new Object();
    private static final Object recordVideoLock = new Object();
    private Handler mAppHandler;
    private Socket socket = null;

    private TextView mTextViewProcessMsg, mTextViewTraffic;
    private Button mBtnReconnect, mBtnNetworkScanner;
    private ImageButton mBtnSnapshot, mBtnCloseNetwork;
    private ProgressBar mConnectProgressBar;
    private RadioButton mRadioButtonSteering, mRadioButtonMoveCameraMode, mRadioButtonEmptyScreen;
    private ToggleButton mToggleButtonVideoRecording, mToggleButtonTorch;
    private View mViewCamMoveVertical, mViewCamMoveHorizontal;
    private View mViewSteeringLeft, mViewSteeringRight;

    private TextureView mCameraView;
    private MediaCodec mMediaCodec = null;
    private volatile boolean isMediaCodecStarted = false;
    private Surface mSurface = null;
    private int surfaceTextureWidth = 1920, surfaceTextureHeight = 1080;
    private DecoderInputThread decoderInputThread;
    private DecoderOutputThread decoderOutputThread;
    private BufferedOutputStream mOutputStreamVideoRecord;

    private ViewControllerPWM steeringLeft = new ViewControllerPWM((byte) 1, this);
    private ViewControllerPWM steeringRight = new ViewControllerPWM((byte) 0, this);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.v(LOG_TAG, "-------- onCreate()");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Intent(Intent.ACTION_VIEW); dirty way
            StrictMode.VmPolicy.Builder builder = new StrictMode.VmPolicy.Builder();
            StrictMode.setVmPolicy(builder.build());
        }

        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
            }
        }

        final Animation btnAnimation = AnimationUtils.loadAnimation(this, R.anim.anumation4button);
        setContentView(R.layout.activity_main);
        mAppHandler = new Handler(Looper.getMainLooper());
        mCameraView = findViewById(R.id.cameraView);
        mCameraView.setSurfaceTextureListener(this);
        mTextViewProcessMsg = findViewById(R.id.textViewProcessMsg);
        mTextViewTraffic = findViewById(R.id.textViewTraffic);
        mConnectProgressBar = findViewById(R.id.connectProgressBar);
        mBtnReconnect = findViewById(R.id.buttonReConnect);
        mBtnReconnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.startAnimation(btnAnimation);
                setTryConnectionState();
            }
        });
        mBtnNetworkScanner = findViewById(R.id.buttonNetworkScanner);
        mBtnNetworkScanner.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.startAnimation(btnAnimation);
                setNetworkScannerState();
            }
        });
        mBtnSnapshot = findViewById(R.id.imageButtonSnapshot);
        mBtnSnapshot.setOnClickListener(new View.OnClickListener() {
            @SuppressLint("NewApi")
            @Override
            public void onClick(View v) {
                String fname = "VMM-" + android.text.format.DateFormat.format("yyyy-MM-dd_hh-mm-ss", new Date()) + ".png";
                try {
                    File dirPictures = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
                    File dir = new File(dirPictures, getString(R.string.app_name));
                    if (!dir.exists()) //noinspection ResultOfMethodCallIgnored
                        dir.mkdirs();

                    Bitmap bitmap = mCameraView.getBitmap();
                    File f = new File(dir, fname);
                    OutputStream os = new FileOutputStream(f);
                    fname = f.getAbsolutePath();
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, os);
                    os.close();
                    Toast.makeText(MainActivity.this, fname, Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, String.format("Save file error. [%s]: %s", fname,
                            e.getMessage()), Toast.LENGTH_LONG).show();
                }
            }
        });
        ImageButton mBtnConfigure = findViewById(R.id.imageButtonConfigure);
        mBtnConfigure.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
                intent.putExtra("width", surfaceTextureWidth);
                intent.putExtra("height", surfaceTextureHeight);
                startActivity(intent);
            }
        });

        //========================================================================================
        mRadioButtonSteering = findViewById(R.id.radioButtonSteering);
        mRadioButtonSteering.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                int st = b ? View.VISIBLE : View.INVISIBLE;
                mViewSteeringLeft.setVisibility(st);
                mViewSteeringRight.setVisibility(st);
            }
        });
        mViewSteeringLeft = findViewById(R.id.viewSteeringLeft);
        mViewSteeringLeft.setOnTouchListener(steeringLeft);
        mViewSteeringRight = findViewById(R.id.viewSteeringRight);
        mViewSteeringRight.setOnTouchListener(steeringRight);
        //========================================================================================
        mRadioButtonMoveCameraMode = findViewById(R.id.radioButtonMoveCameraMode);
        mRadioButtonMoveCameraMode.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                int st = b ? View.VISIBLE : View.INVISIBLE;
                mViewCamMoveVertical.setVisibility(st);
                mViewCamMoveHorizontal.setVisibility(st);
            }
        });
        mViewCamMoveVertical = findViewById(R.id.viewCamMoveVertical);
        mViewCamMoveVertical.setOnTouchListener(new View.OnTouchListener() {
            @SuppressLint("ClickableViewAccessibility")
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                    float p = 1 - (motionEvent.getY() / view.getHeight()) * 2;
                    sendCmd(CMD.ROTATE + ",V," + p + "\n");
                }
                return true;
            }
        });

        mViewCamMoveHorizontal = findViewById(R.id.viewCamMoveHorizontal);
        mViewCamMoveHorizontal.setOnTouchListener(new View.OnTouchListener() {
            @SuppressLint("ClickableViewAccessibility")
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                    float p = (motionEvent.getX() / view.getWidth()) * 2 - 1;
                    sendCmd(CMD.ROTATE + ",H," + p + "\n");
                }
                return true;
            }
        });
        //========================================================================================
        mRadioButtonEmptyScreen = findViewById(R.id.radioButtonEmptyScreenMode);
        //========================================================================================
        final Animation btnRecordAnimation = AnimationUtils.loadAnimation(this, R.anim.anumation_video_recording);
        mToggleButtonVideoRecording = findViewById(R.id.toggleButtonVideoRecord);
        mToggleButtonVideoRecording.setChecked(false);
        mToggleButtonVideoRecording.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean state) {
                if (state) {
                    compoundButton.startAnimation(btnRecordAnimation);
                    closeVideoRecordingStream();
                    String fname = "VMM-" + android.text.format.DateFormat.format("yyyy-MM-dd_hh-mm-ss", new Date()) + ".mov";
                    try {
                        File dirPictures = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
                        File dir = new File(dirPictures, getString(R.string.app_name));
                        if (!dir.exists()) //noinspection ResultOfMethodCallIgnored
                            dir.mkdirs();
                        File f = new File(dir, fname);
                        fname = f.getAbsolutePath();
                        mOutputStreamVideoRecord = new BufferedOutputStream(new FileOutputStream(f));
                    } catch (Exception e) {
                        Toast.makeText(MainActivity.this, String.format("Save file error. [%s]: %s", fname,
                                e.getMessage()), Toast.LENGTH_LONG).show();
                    }
                } else {
                    closeVideoRecordingStream();
                    compoundButton.clearAnimation();
                }
            }
        });
        //========================================================================================
        mToggleButtonTorch = findViewById(R.id.toggleButtonTorch);
        mToggleButtonTorch.setChecked(false);
        //========================================================================================
        mBtnCloseNetwork = findViewById(R.id.imageButtonCloseNetwork);
        mBtnCloseNetwork.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeVideoRecordingStream();
                socketClose();
            }
        });
    }

    @Override
    protected void onPause() {
        isMediaCodecStarted = false;
        Log.v(LOG_TAG, "-------- onPause()");
        if (decoderInputThread != null) {
            decoderInputThread.interrupt();
            decoderInputThread = null;
        }
        if (decoderOutputThread != null) {
            decoderOutputThread.interrupt();
            decoderOutputThread = null;
        }
        if (mMediaCodec != null) {
            try {
                mMediaCodec.stop();
                mMediaCodec.release();
                mMediaCodec = null;
            } catch (Exception ex) {
                Log.e(LOG_TAG, "", ex);
            }
        }
        closeVideoRecordingStream();
        socketClose();
        if (Settings.startCheckMotionService) {
            Intent intent = new Intent(this, CheckMotionService.class);
            intent.setAction(CheckMotionService.INTENT_ACTION_START);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent);
            else startService(intent);
        }
        super.onPause();
    }

    @Override
    public void onStop() {
        super.onStop();
        Log.v(LOG_TAG, "-------- onStop()");
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.v(LOG_TAG, "-------- onResume()");
        try {
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
            Settings.loadConfig(settings);
            Settings.saveConfig(settings); // save default values
            mMediaCodec = MediaCodec.createDecoderByType("video/avc");
        } catch (final Exception e) {
            Log.e(LOG_TAG, "", e);
            mAppHandler.post(new Runnable() {
                @Override
                public void run() {
                    FragmentManager fm = getFragmentManager();
                    new ErrorInfoDialog().showInfo(fm, e.toString());
                }
            });
        }
        stopService(new Intent(this, CheckMotionService.class));
        setTryConnectionState();
        (decoderInputThread = new DecoderInputThread()).start();
        (decoderOutputThread = new DecoderOutputThread()).start();
    }


    private NetworkConnect.Callback networkCallback = new NetworkConnect.Callback() {

        @Override
        public void result(Socket s, String err) {
            Log.e(LOG_TAG, "NetworkConnect.Callback ");
            if (err == null) {
                setConnectionReadyState(s);
                try {
                    Settings.saveConfig(PreferenceManager.getDefaultSharedPreferences(MainActivity.this));
                } catch (Exception e) {
                    Log.e(LOG_TAG, "load config.", e);
                    final String msg = e.toString();
                    mAppHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            FragmentManager fm = getFragmentManager();
                            new ErrorInfoDialog().showInfo(fm, msg);
                        }
                    });
                }
            } else setNoConnectionState(err);
        }

        @Override
        public void showProgress(String msg) {
            mTextViewProcessMsg.setText(msg);
        }
    };

    //---------------------------------
    private void setScreenState(boolean isVideoStreamMode) {
        int st = isVideoStreamMode ? View.VISIBLE : View.INVISIBLE;
        mBtnSnapshot.setVisibility(st);
        mBtnCloseNetwork.setVisibility(st);
        mToggleButtonVideoRecording.setVisibility(st);
        mToggleButtonTorch.setVisibility(st);
        mTextViewTraffic.setVisibility(st);
        mRadioButtonMoveCameraMode.setVisibility(st);
        mRadioButtonEmptyScreen.setVisibility(st);

        if (!Settings.hasExternalControllerSPI) mRadioButtonSteering.setVisibility(View.INVISIBLE);
        else mRadioButtonSteering.setVisibility(st);

        if (isVideoStreamMode)
            st = mRadioButtonMoveCameraMode.isChecked() ? View.VISIBLE : View.INVISIBLE;
        mViewCamMoveVertical.setVisibility(st);
        mViewCamMoveHorizontal.setVisibility(st);
        if (isVideoStreamMode)
            st = mRadioButtonSteering.isChecked() ? View.VISIBLE : View.INVISIBLE;
        mViewSteeringLeft.setVisibility(st);
        mViewSteeringRight.setVisibility(st);

        st = isVideoStreamMode ? View.INVISIBLE : View.VISIBLE;
        mTextViewProcessMsg.setVisibility(st);
        mBtnReconnect.setVisibility(st);
        mBtnNetworkScanner.setVisibility(st);
        mConnectProgressBar.setVisibility(st);
    }

    private void setNetworkScannerState() {
        socketClose();
        setScreenState(false);
        mBtnReconnect.setVisibility(View.INVISIBLE);
        mBtnNetworkScanner.setVisibility(View.INVISIBLE);
        new NetworkConnect(true, networkCallback).execute();
    }

    private void setNoConnectionState(String msg) {
        Log.v(LOG_TAG, "setNoConnectionState:" + msg);
        setScreenState(false);
        mTextViewProcessMsg.setText(msg);
        mConnectProgressBar.setVisibility(View.INVISIBLE);
        if (!Settings.isInternalNetwork) mBtnNetworkScanner.setVisibility(View.INVISIBLE);
    }

    @SuppressLint("DefaultLocale")
    private void setConnectionReadyState(Socket s) {
        socket = s;
        sendCmd(String.format("%s,%s\n", CMD.AUTH.name(), Settings.password));
        sendCmd(String.format("%s,%d,%d,%d,%d,%d,%d\n", CMD.START_STREAM.name(),
                (int) (surfaceTextureWidth / Settings.resolutionDiv),
                (int) (surfaceTextureHeight / Settings.resolutionDiv),
                Settings.vFps,
                Settings.vBitrate,
                Settings.vQuality,
                Settings.vISO
        ));
        setScreenState(true);
        Toast.makeText(MainActivity.this, String.format(getString(R.string.toast_connect_info),
                Settings.isInternalNetwork ? Settings.internalHost : Settings.externalHost, Settings.tcpIpPort), Toast.LENGTH_LONG).show();
    }

    private void setTryConnectionState() {
        socketClose();
        setScreenState(false);
        mBtnReconnect.setVisibility(View.INVISIBLE);
        mBtnNetworkScanner.setVisibility(View.INVISIBLE);
        mTextViewProcessMsg.setVisibility(View.INVISIBLE);
        new NetworkConnect(false, networkCallback).execute();
    }

    @Override
    public void sendCmdSPI(final byte[] cmd) {
        if (socket != null)
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        //Log.v(LOG_TAG, "CmdSPI cmd=" + ((int) cmd[0]) + " chanel=" + ((int) cmd[1]) + " value=" + ((int) cmd[2]));
                        StringBuilder s = new StringBuilder(CMD.SPI.name()).append(",n,").append(Settings.cmdSpiMagicBytes);
                        String hh = "0123456789ABCDEF";
                        for (byte b : cmd)
                            s.append(hh.charAt((((int) b) & 0x0F0) >> 4)).append(hh.charAt(((int) b) & 0x0F));
                        s.append('\n');
                        synchronized (socketlock) {
                            socket.getOutputStream().write(s.toString().getBytes());
                            socket.getOutputStream().flush();
                        }
                    } catch (IOException e) {
                        Log.e(LOG_TAG, "sendCmdSPI", e);
                    }
                }
            }).start();
    }

    private void sendCmd(final String cmd) {
        if (socket != null)
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        synchronized (socketlock) {
                            socket.getOutputStream().write(cmd.getBytes());
                            socket.getOutputStream().flush();
                        }
                    } catch (final IOException e) {
                        Log.e(LOG_TAG, "socket write cmd");
                        socketClose();
                        mAppHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                setNoConnectionState(e.getMessage());
                            }
                        });
                    }

                }
            }).start();
    }

    private void socketClose() {
        if (socket != null) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        synchronized (socketlock) {
                            socket.getOutputStream().write((CMD.FINISH.name() +
                                    "," + Integer.toString(Settings.motionThresholdValue) +
                                    "," + Integer.toString(Settings.motionThresholdCnt) +
                                    "," + Integer.toString(Settings.motionThresholdSAD) +
                                    "," + (Settings.motionNoiseFilter ? 'Y' : 'N') +
                                    "," + Float.toString(Settings.motionSnapshotDt) +
                                    "," + Float.toString(Settings.motionDetectTimeWindow) +
                                    "," + Integer.toString(Settings.motionNumberInTimeWindow) +
                                    "\n").getBytes());
                        }
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException ignore) {
                        }
                        socket.close();
                    } catch (Exception ignored) {
                    }
                    socket = null;
                }
            }).start();
        }
    }

    private void closeVideoRecordingStream() {
        if (mOutputStreamVideoRecord != null) {
            try {
                synchronized (recordVideoLock) {
                    mOutputStreamVideoRecord.flush();
                    mOutputStreamVideoRecord.close();
                    mOutputStreamVideoRecord = null;
                }
            } catch (Exception ex) {
                Log.e(LOG_TAG, "", ex);
            }
        }
    }

    public boolean withTorch() {
        return mToggleButtonTorch.isChecked();
    }

    //--------------------------------------------------------------------------------------------------
    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
        this.mSurface = new Surface(surfaceTexture);
        surfaceTextureWidth = width;
        surfaceTextureHeight = height;
        Log.d(LOG_TAG, "onSurfaceTextureAvailable width:" + width + " height:" + height);
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int width, int height) {
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
    }

    //--------------------------------------------------------------------------------------------------
    private class DecoderOutputThread extends Thread {
        private MediaCodec.BufferInfo mBufferInfo = new MediaCodec.BufferInfo();

        @Override
        public void run() {
            //setPriority(Thread.MAX_PRIORITY);
            while (!isInterrupted()) {
                if (isMediaCodecStarted) {
                    try {
                        int status = mMediaCodec.dequeueOutputBuffer(mBufferInfo, 0);
                        if (status >= 0) {
                            long dt = System.currentTimeMillis() - mBufferInfo.presentationTimeUs;
                            if (dt > 150) Log.v(LOG_TAG, "decoder delay:" + dt);
                            mMediaCodec.releaseOutputBuffer(status, true);
                        }
                    } catch (Exception ex) {
                        Log.e(LOG_TAG, "", ex);
                    }
                }
            }
        }
    }

    private class DecoderInputThread extends Thread {
        private final static int NAL_SIZE_INC = 4096;
        private byte[] nalBuff = new byte[4096 * 2];
        private byte[] inpBuff = new byte[4096];
        int nalSz = 0;
        int numZeroes = 0;
        private boolean trafficMsgBlink = true;

        @SuppressLint("SetTextI18n")
        @Override
        public void run() {
            try {
                long bytesTraffic = 0;
                long timeTraffic = System.currentTimeMillis();
                while (!isInterrupted()) {
                    if (socket == null || mSurface == null) {
                        try {
                            Thread.sleep(50);
                        } catch (InterruptedException ignore) {
                        }
                        continue;
                    }
                    //----------------
                    int sz;
                    try {
                        sz = socket.getInputStream().read(inpBuff);
                        if (sz < 0) throw new IOException("closed socket");
                    } catch (final IOException e) {
                        Log.e(LOG_TAG, "read from InputStream");
                        socketClose();
                        mAppHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                setNoConnectionState(e.getMessage());
                            }
                        });
                        continue;
                    }

                    bytesTraffic += sz;
                    long t = System.currentTimeMillis();
                    if (t - timeTraffic > 1000) {
                        long tmp = (bytesTraffic + 500) * 1000L;
                        trafficMsgBlink = !trafficMsgBlink;
                        final String msg = (trafficMsgBlink ? '▢' : '▣') + Long.toString((tmp / (t - timeTraffic)) / 1000L) + " kb/s";
                        mAppHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                mTextViewTraffic.setText(msg);
                            }
                        });

                        bytesTraffic = 0;
                        timeTraffic = System.currentTimeMillis();
                    }

                    for (int i = 0; i < sz && !isInterrupted(); i++) {
                        if (nalSz == nalBuff.length)
                            nalBuff = Arrays.copyOf(nalBuff, nalBuff.length + NAL_SIZE_INC);
                        nalBuff[nalSz++] = inpBuff[i];
                        if (inpBuff[i] == 0) numZeroes++;
                        else {
                            if (inpBuff[i] == 1 && numZeroes == 3) {
                                if (nalSz > 4) {
                                    if (!decodeNAL()) {
                                        nalBuff[0] = nalBuff[1] = nalBuff[2] = 0;
                                        nalBuff[3] = 1;
                                    }
                                }
                                nalSz = 4;
                            }
                            numZeroes = 0;
                        }
                    }
                }
            } catch (Exception ex) {
                Log.e(LOG_TAG, "decoder", ex);
            }
        }


        private boolean decodeNAL() {
            if (nalSz < 8 || nalBuff[0] != 0 || nalBuff[1] != 0 && nalBuff[2] != 0 && nalBuff[3] != 1)
                return false;
            int type = nalBuff[4] & 0x1F;
            if (type == 7) {
                if (!isMediaCodecStarted && mMediaCodec != null) {
                    MediaFormat mediaFormat = MediaFormat.createVideoFormat("video/avc", surfaceTextureWidth, surfaceTextureHeight);
                    Log.v(LOG_TAG, "MediaFormat:" + mediaFormat);
                    mMediaCodec.configure(mediaFormat, mSurface, null, 0);
                    mMediaCodec.start();
                    isMediaCodecStarted = true;
                }
            }

            if (type > 0 && isMediaCodecStarted && mMediaCodec != null) {
                int i = mMediaCodec.dequeueInputBuffer(0);
                if (i >= 0) {
                    ByteBuffer inputBuffer = mMediaCodec.getInputBuffer(i);
                    if (inputBuffer != null) {
                        inputBuffer.put(nalBuff, 0, nalSz - 4);
                        long t = System.currentTimeMillis();
                        try {
                            if (mOutputStreamVideoRecord != null) {
                                synchronized (recordVideoLock) {
                                    mOutputStreamVideoRecord.write(nalBuff, 0, nalSz - 4);
                                }
                            }
                        } catch (IOException e) {
                            Log.e(LOG_TAG, "", e);
                        }
                        mMediaCodec.queueInputBuffer(i, 0, nalSz - 4, t, 0);
                    }
                }
            }
            return true;
        }
    }
}
