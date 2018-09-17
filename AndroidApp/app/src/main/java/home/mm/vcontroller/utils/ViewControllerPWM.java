package home.mm.vcontroller.utils;

import android.annotation.SuppressLint;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

public class ViewControllerPWM implements Runnable, View.OnTouchListener {
    private volatile short val = 0;
    private byte index;
    private cmdSpiInterface api;
    private Thread thread = null;

    public ViewControllerPWM(byte index, cmdSpiInterface api) {
        this.index = index;
        this.api = api;
    }

    private void send() {
        byte spiCmd[] = new byte[3];
        spiCmd[0] = 0x01;
        if(api.withTorch()) spiCmd[0] |= 0x10;
        spiCmd[1] = index;
        spiCmd[2] = (byte)val;
        api.sendCmdSPI(spiCmd);
    }

    private void stopThread() {
        if(thread != null) {
            thread.interrupt();
            try {
                thread.join();
            } catch (InterruptedException ignore) {
            }
        }
        thread = null;
    }

    @Override
    public void run() {
        try {
            while (!Thread.interrupted()) {
                send();
                Thread.sleep(150);
            }
        } catch (Exception ignore) {
        }
    }


    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        float p = 100-(motionEvent.getY() / view.getHeight())*200;
        if(p > 100) p = 100;
        if(p < -100) p = -100;
        val = (short)p;
        switch (motionEvent.getAction()) {
            case MotionEvent.ACTION_DOWN:
                stopThread();
                thread = new Thread(this);
                thread.start();
                break;
            case MotionEvent.ACTION_MOVE:
                break;
            case MotionEvent.ACTION_UP:
                stopThread();
                val = 0;
                break;
        }
        return true;
    }
}

