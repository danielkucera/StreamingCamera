package eu.danman.streamingcamera;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.hardware.Camera;
import android.media.MediaRecorder;
import android.os.ParcelFileDescriptor;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ToggleButton;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;


public class MainActivity extends Activity implements SurfaceHolder.Callback {
    private final String VIDEO_PATH_NAME = "/sdcard/VGA_30fps_512vbrate.mp4";

    private MediaRecorder mMediaRecorder;
    private Camera mCamera;
    private SurfaceView mSurfaceView;
    private SurfaceHolder mHolder;
    private View mToggleButton;
    private boolean mInitSuccesful;

    SenderThread sender;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // we shall take the video in landscape orientation
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        mSurfaceView = (SurfaceView) findViewById(R.id.surfaceView);
        mHolder = mSurfaceView.getHolder();
        mHolder.addCallback(this);
        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        mToggleButton = (ToggleButton) findViewById(R.id.toggleRecordingButton);
        mToggleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            // toggle video recording
            public void onClick(View v) {

                //sender.send("jedendvatristyripatsest".getBytes());


                if (((ToggleButton) v).isChecked()) {
                    mMediaRecorder.start();
                    try {
                        Thread.sleep(10 * 1000);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    //finish();
                } else {
                    mMediaRecorder.stop();
                    mMediaRecorder.reset();
                    try {
                        initRecorder(mHolder.getSurface());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

            }
        });

        sender = new SenderThread("192.168.69.160", 1919);
        sender.start();


    }

    /* Init the MediaRecorder, the order the methods are called is vital to
     * its correct functioning */
    private void initRecorder(Surface surface) throws IOException {
        // It is very important to unlock the camera before doing setCamera
        // or it will results in a black preview
        if (mCamera == null) {
            mCamera = Camera.open();
            mCamera.unlock();
        }

        if (mMediaRecorder == null) mMediaRecorder = new MediaRecorder();
        mMediaRecorder.setPreviewDisplay(surface);
        mMediaRecorder.setCamera(mCamera);

        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.DEFAULT);
        //       mMediaRecorder.setOutputFormat(8);
        mMediaRecorder.setOutputFormat(8);
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mMediaRecorder.setVideoEncodingBitRate(512 * 1000);
        mMediaRecorder.setVideoFrameRate(30);
        mMediaRecorder.setVideoSize(640, 480);

        ParcelFileDescriptor pfd = ParcelFileDescriptor.fromDatagramSocket(sender.getSocket());

        mMediaRecorder.setOutputFile(pfd.getFileDescriptor());
//        mMediaRecorder.setOutputFile(VIDEO_PATH_NAME);

        try {
            mMediaRecorder.prepare();
        } catch (IllegalStateException e) {
            // This is thrown if the previous calls are not called with the
            // proper order
            e.printStackTrace();
        }

        mInitSuccesful = true;
    }

    class SenderThread extends Thread {

        private String serverS;

        private InetAddress server;

        private DatagramSocket socket;

        private boolean stopped = false;

        private int port;

        public SenderThread(String address, int port) {
            this.serverS = address;
            this.port = port;

        }

        public void halt() {
            this.stopped = true;
        }

        public void send(byte[] data){

            try {
                DatagramPacket output = new DatagramPacket(data, data.length, server, port);
                socket.send(output);
                Thread.yield();
            }
            catch (IOException ex) {
                System.err.println(ex);
            }

        }

        public DatagramSocket getSocket() {
            return this.socket;
        }

        public void run() {
            try {
                this.server =  InetAddress.getByName(serverS);
            } catch (UnknownHostException e) {
                e.printStackTrace();
            }
            try {
                this.socket = new DatagramSocket();
            } catch (SocketException e) {
                e.printStackTrace();
            }
            this.socket.connect(server, port);


        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        try {
            if (!mInitSuccesful)
                initRecorder(mHolder.getSurface());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        shutdown();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    }

    private void shutdown() {
        // Release MediaRecorder and especially the Camera as it's a shared
        // object that can be used by other applications
        mMediaRecorder.reset();
        mMediaRecorder.release();
        mCamera.release();

        // once the objects have been released they can't be reused
        mMediaRecorder = null;
        mCamera = null;
    }
}