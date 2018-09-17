package home.mm.vcontroller.utils;

import android.annotation.SuppressLint;
import android.os.AsyncTask;
import android.util.Log;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.concurrent.ConcurrentLinkedQueue;

public class NetworkConnect extends AsyncTask<Void, String, String> {
    private final static String LOG_TAG = "NetworkConnect";
    private Socket socket = null;
    private int scanningPortsCnt = 0;

    public interface Callback {
        public void result(Socket s, String err);
        public void showProgress(String msg);
    }

    private boolean scanMode;
    private Callback callback;

    public NetworkConnect(boolean scanMode, Callback cb) {
        this.callback = cb;
        this.scanMode = scanMode;
    }

    @Override
    protected void onProgressUpdate(String... values) {
        super.onProgressUpdate(values);
        callback.showProgress(values[0]);
    }

    private byte[] getMyIP() throws SocketException {
        Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();
        while (en.hasMoreElements()) {
            Enumeration<InetAddress> ena = en.nextElement().getInetAddresses();
            while (ena.hasMoreElements()) {
                InetAddress a = ena.nextElement();
                if (!a.isLoopbackAddress() && a instanceof Inet4Address) return a.getAddress();
            }
        }
        return null;
    }

    @SuppressLint("DefaultLocale")
    private String scan() {
        final byte[] myIP;
        try {
            myIP = getMyIP();
            Log.d(LOG_TAG, "scanner. my IP:" + InetAddress.getByAddress(myIP));
        } catch (Exception ex) {
            Log.v(LOG_TAG, "Get IP error", ex);
            return "Get localhost IP error. "+ ex.getMessage();
        }
        if (myIP == null) return "Can't get local IP. Turn on WAP";
        int loopIp = myIP[3];
        final ConcurrentLinkedQueue<InetAddress> queue = new ConcurrentLinkedQueue<>();
        for (int i = 1; i < 255; i++) {
            if (i != loopIp) {
                try {
                    myIP[3] = (byte) i;
                    queue.add(InetAddress.getByAddress(myIP));
                } catch (UnknownHostException e) {
                    Log.e(LOG_TAG, "unpredictable error", e);
                    return "unpredictable error" + e.getMessage();
                }
            }
        }

        for (int i = 0; i < queue.size(); i++)
            new Thread(new Runnable() {
                @Override
                public void run() {
                    InetAddress a;
                    while ((a = queue.poll()) != null && socket == null) {
                        //Log.v(LOG_TAG, "check:" + a.getHostAddress());
                        scanningPortsCnt++;
                        try {
                            Socket s = new Socket();
                            SocketAddress socketAddress = new InetSocketAddress(a, Settings.tcpIpPort);
                            s.connect(socketAddress, Settings.connectTimeout);
                            socket = s;
                            Settings.internalHost = a.getHostAddress();
                            Log.v(LOG_TAG, "check OK:" + a.getHostAddress());
                        } catch (Exception ex) {
                            //Log.v(LOG_TAG, "check fail:" + ex.getMessage());
                        }
                    }
                }
            }).start();
        int cnt = -1;
        while (!queue.isEmpty() && socket == null) {
            try {
                Thread.sleep(100);
                if(scanningPortsCnt != cnt) {
                    cnt = scanningPortsCnt;
                    publishProgress("scanned:" + cnt);
                }
            } catch (InterruptedException ignore) {
            }
        }
        if (socket == null) return String.format("No scanner result for: %02d.%02d.%02d.* (%02d):%d",
                0x0ff & myIP[0], 0x0ff & myIP[1], 0x0ff & myIP[2], 0x0ff & myIP[3], Settings.tcpIpPort);
        return null;
    }

    @SuppressLint("DefaultLocale")
    private String connect() {
        try {
            socket = new Socket();
            InetSocketAddress socketAddress = new InetSocketAddress(
                    Settings.isInternalNetwork ? Settings.internalHost : Settings.externalHost, Settings.tcpIpPort);
            socket.connect(socketAddress, Settings.connectTimeout);
        } catch (Exception ex) {
            Log.v(LOG_TAG, "connect fail", ex);
            socket = null;
            return String.format("try to connect '%s:%d' %s", Settings.isInternalNetwork ? Settings.internalHost : Settings.externalHost, Settings.tcpIpPort, ex.getMessage());
        }
        return null;
    }

    @Override
    protected String doInBackground(Void... voids) {
        return scanMode ? scan() : connect();
    }

    @Override
    protected void onPostExecute(String msg) {
        callback.result(socket, msg);
    }
}
