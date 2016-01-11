package com.example.javacv05streamtest;

import android.app.Activity;
import android.content.Context;
import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

import java.io.File;
import java.io.IOException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;

import org.bytedeco.javacpp.avutil;
import org.bytedeco.javacv.FFmpegFrameFilter;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacpp.avcodec;
import org.bytedeco.javacv.FrameFilter;

import static org.bytedeco.javacpp.opencv_core.*;

public class MainActivity extends Activity implements OnClickListener {

    private final static String TAG = "MainActivity";

    private PowerManager.WakeLock mWakeLock;

    //	private String ffmpeg_link = "rtmp://username:password@xxx.xxx.xxx.xxx:1935/live/test.flv";
    private String ffmpeg_link = "rtmp://43.250.15.186/live_devel_test/wyx";
//    private String ffmpeg_link = "rtmp://10.5.22.93:1935/live___vhost%3Dwanhuo.tudou.com%26sid%3Dwanhuo12.500_0_0%26mode%3D1%26need_format%3D0%26act%3Dpublish%26expire%3D1447985115%26limit%3D-1%26key%3D5f5881341ccef113cb4571a0cbc47064%26st%3Dwanhuo12.500_0_0%26cip%3D10.5.16.172%26npc%3D57%26hop%3D1/wanhuo12.500_0_0";
    //private String ffmpeg_link = "/mnt/sdcard/new_stream.flv";

    private volatile FFmpegFrameRecorder recorder;
    boolean recording = false;
    long startTime = 0;

    private int sampleAudioRateInHz = 24000; // 24khz
    private int imageWidth = 864;//1280;
    private int imageHeight = 480;// 720;
    private int frameRate = 15;
    private int videoBitrate = 550000; //550k
    private int videoQuality = 30;
    private static final int GOP_LENGTH_IN_FRAMES = 60;

    private Thread audioThread;
    volatile boolean runAudioThread = true;
    private AudioRecord audioRecord;
    private AudioRecordRunnable audioRecordRunnable;

    private SurfaceView cameraView;
    private IplImage yuvIplImage = null;
    private IplImage rgbIplImage = null;
    private IplImage rotateIplImage = null;
    private Frame frame;
    //    OpenCVFrameConverter.ToIplImage toIplImage = new OpenCVFrameConverter.ToIplImage();
    FFmpegFrameFilter filter;

    private Button recordButton;
    long videoTimestamp = 0;
//    private LinearLayout mainLayout;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

//        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        setContentView(R.layout.activity_main);

        YkCameraHelper.setDisplayMetrics(getResources().getDisplayMetrics());
        initLayout();
//        initRecorder();
    }

    @Override
    protected void onResume() {
        super.onResume();
        cameraView.getHolder().addCallback(holderCallback);

        if (mWakeLock == null) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            mWakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, TAG);
            mWakeLock.acquire();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        cameraView.getHolder().removeCallback(holderCallback);

        if (mWakeLock != null) {
            mWakeLock.release();
            mWakeLock = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        recording = false;
    }


    private void initLayout() {

//        mainLayout = (LinearLayout) this.findViewById(R.id.record_layout);

        recordButton = (Button) findViewById(R.id.recorder_control);
        recordButton.setText("Start");
        recordButton.setOnClickListener(this);

        cameraView = (SurfaceView) findViewById(R.id.camera_view);

//        LinearLayout.LayoutParams layoutParam = new LinearLayout.LayoutParams(imageWidth, imageHeight);
//        mainLayout.addView(cameraView, layoutParam);
//        Log.v(TAG, "added cameraView to mainLayout");
    }

    private void initRecorder() {
        Log.w(TAG, "initRecorder");

//        if (yuvIplImage == null) {
//            yuvIplImage = IplImage.create(imageWidth, imageHeight * 3 / 2, IPL_DEPTH_8U, 1);
//        }
//        if (rgbIplImage == null) {
//            rgbIplImage = IplImage.create(imageWidth, imageHeight, IPL_DEPTH_8U, 3);
//        }
//        if (rotateIplImage == null) {
//            rotateIplImage = IplImage.create(imageHeight, imageWidth, IPL_DEPTH_8U, 3);
//        }
        if (frame == null) {
            frame = new Frame(imageWidth, imageHeight, Frame.DEPTH_UBYTE, 2);
        }
        if (filter == null) {
            filter = new FFmpegFrameFilter("transpose=clock", imageWidth, imageHeight);
            filter.setPixelFormat(avutil.AV_PIX_FMT_NV21);
            try {
                filter.start();
            } catch (FrameFilter.Exception e) {
                e.printStackTrace();
            }
        }

//        recorder = new FFmpegFrameRecorder(ffmpeg_link, imageWidth, imageHeight, 1);
        File file = new File(Environment.getExternalStorageDirectory() + "/test.flv");
//        recorder = new FFmpegFrameRecorder(file, imageHeight, imageWidth, 1);

        // push to server
        recorder = new FFmpegFrameRecorder(ffmpeg_link, imageHeight, imageWidth, 1);
        Log.v(TAG, "FFmpegFrameRecorder: " + ffmpeg_link + " imageWidth: " + imageWidth + " imageHeight " + imageHeight);
        recorder.setInterleaved(true);
        recorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
        recorder.setAudioCodec(avcodec.AV_CODEC_ID_AAC);
        recorder.setVideoOption("tune", "zerolatency");
//        ultrafast,superfast, veryfast, faster, fast,
//        medium, slow, slower, veryslow
        recorder.setVideoOption("preset", "superfast");
        recorder.setVideoOption("keyint_min", "10");
        recorder.setVideoOption("keyint", "60");
        recorder.setVideoOption("qpmin", "20");
        recorder.setVideoOption("qpmax", "45");
        recorder.setVideoOption("subme", "1");
        recorder.setVideoOption("threads", "1");

        recorder.setVideoQuality(videoQuality);
        recorder.setVideoBitrate(videoBitrate);// wyx
        recorder.setFormat("flv");
        Log.v(TAG, "recorder.setFormat(\"flv\")");
        recorder.setSampleRate(sampleAudioRateInHz);
        recorder.setGopSize(GOP_LENGTH_IN_FRAMES);
        // re-set in the surface changed method as well
        recorder.setFrameRate(frameRate);
        Log.v(TAG, "recorder.setFrameRate(frameRate)");


        // Create audio recording thread
        audioRecordRunnable = new AudioRecordRunnable();
        audioThread = new Thread(audioRecordRunnable);
    }

    // Start the capture
    public void startRecording() {
        try {
            recorder.start();

            startTime = System.currentTimeMillis();
            recording = true;
            audioThread.start();
        } catch (FFmpegFrameRecorder.Exception e) {
            e.printStackTrace();
        }
    }

    public void stopRecording() {
        // This should stop the audio thread from running
        runAudioThread = false;

        if (recorder != null && recording) {
            recording = false;
            Log.v(TAG, "Finishing recording, calling stop and release on recorder");
            try {
                recorder.stop();
                recorder.release();

            } catch (FFmpegFrameRecorder.Exception e) {
                e.printStackTrace();
            }
            recorder = null;
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Quit when back button is pushed
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (recording) {
                stopRecording();
            }
            finish();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onClick(View v) {
        if (!recording) {
            startRecording();
            Log.w(TAG, "Start Button Pushed");
            recordButton.setText("Stop");
        } else {
            stopRecording();
            Log.w(TAG, "Stop Button Pushed");
            recordButton.setText("Start");
        }
    }

    //---------------------------------------------
    // audio thread, gets and encodes audio data
    //---------------------------------------------
    class AudioRecordRunnable implements Runnable {

        @Override
        public void run() {
            // Set the thread priority
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);

            // Audio
            int bufferSize;
            short[] audioData;
            int bufferReadResult;

            bufferSize = AudioRecord.getMinBufferSize(sampleAudioRateInHz,
                    AudioFormat.CHANNEL_CONFIGURATION_MONO, AudioFormat.ENCODING_PCM_16BIT);
            audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleAudioRateInHz,
                    AudioFormat.CHANNEL_CONFIGURATION_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);

            audioData = new short[bufferSize];

            Log.d(TAG, "audioRecord.startRecording()");
            audioRecord.startRecording();

            // Audio Capture/Encoding Loop
            while (runAudioThread) {
                // Read from audioRecord
                bufferReadResult = audioRecord.read(audioData, 0, audioData.length);
                if (bufferReadResult > 0) {
                    //Log.v(TAG,"audioRecord bufferReadResult: " + bufferReadResult);

                    // Changes in this variable may not be picked up despite it being "volatile"
                    if (recording) {
                        try {
                            // Write to FFmpegFrameRecorder
                            Buffer[] buffer = {ShortBuffer.wrap(audioData, 0, bufferReadResult)};
                            recorder.recordSamples(buffer);

                        } catch (FFmpegFrameRecorder.Exception e) {
                            Log.v(TAG, e.getMessage());
                            e.printStackTrace();
                        }
                    }
                }
            }
            Log.v(TAG, "AudioThread Finished");

            /* Capture/Encoding finished, release recorder */
            if (audioRecord != null) {
                audioRecord.stop();
                audioRecord.release();
                audioRecord = null;
                Log.v(TAG, "audioRecord released");
            }
        }
    }

    SurfaceHolder.Callback holderCallback = new SurfaceHolder.Callback() {

        private Camera camera;

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            camera = YkCameraHelper.openCamera();

            try {
                camera.setDisplayOrientation(90);
                camera.setPreviewDisplay(holder);
                camera.setPreviewCallback(previewCallback);

                Camera.Parameters currentParams = camera.getParameters();
                Log.v(TAG, "Preview Framerate: " + currentParams.getPreviewFrameRate());
                Log.d(TAG, "surfaceCreated  image width=" + imageWidth + ", height=" + imageHeight);
                camera.startPreview();
            } catch (IOException e) {
                Log.e(TAG, e.getMessage());
                e.printStackTrace();
            } catch (Exception e) {
                Log.e(TAG, e.getMessage());
                e.printStackTrace();
            }
            initRecorder();
        }

        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            try {
                camera.setPreviewCallback(null);

                YkCameraHelper.releaseCamera();

            } catch (RuntimeException e) {
                Log.v(TAG, e.getMessage());
                e.printStackTrace();
            }
        }
    };

    PreviewCallback previewCallback = new PreviewCallback() {
        @Override
        public void onPreviewFrame(byte[] data, Camera camera) {
            Log.d(TAG, "onPreviewFrame()");
            long start = System.currentTimeMillis();
            long now = 0;

            if (frame != null && recording) {
                videoTimestamp = 1000 * (System.currentTimeMillis() - startTime);
                // rotate by filter
                ((ByteBuffer) frame.image[0].position(0)).put(data);
                try {
                    filter.push(frame);
                    Frame rotateFrame = filter.pull();
                    now = System.currentTimeMillis();
                    Log.d(TAG, "cost 3: " + (now - start));

                    if (rotateFrame != null) {
                        try {
                            recorder.setTimestamp(videoTimestamp);
                            recorder.record(rotateFrame);

                            now = System.currentTimeMillis();
                            Log.d(TAG, "cost 4: " + (now - start));

                        } catch (FFmpegFrameRecorder.Exception e) {
                            Log.v(TAG, e.getMessage());
                            e.printStackTrace();
                        }
                    }
                } catch (FrameFilter.Exception e) {
                    e.printStackTrace();
                }
                // end filter rotate
            }
        }
    };
}


