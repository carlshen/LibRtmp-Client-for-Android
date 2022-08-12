package io.antmedia.rtmp_client.test;

import android.app.Activity;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.View;

import java.io.IOException;
import java.nio.ByteBuffer;

import io.antmedia.rtmp_client.RTMPMuxer;


public class LivePusherNew implements OnFrameDataCallback {
    static String TAG = "LivePusherNew";
    public static final String VCODEC = "video/avc";
    public static int vBitrate = 1200 * 1024;  // 1200 kbps
    public static final int VFPS = 24;
    public static final int VGOP = 48;
    private MediaCodecInfo vmci;
    private MediaCodec vencoder;
    private int mVideoColorFormat;
    static RTMPMuxer rtmpMuxer;
    static int rtmpWidth;
    static int rtmpHeight;
    static int rtmpBitRate;
    static int rtmpFrameRate;
    static boolean isPushing = false;

    private final AudioStream audioStream;
    private VideoStreamBase videoStream;
    private final Activity activity;

    public LivePusherNew(Activity activity,
                         VideoParam videoParam,
                         AudioParam audioParam,
                         View view,
                         CameraType cameraType) {
        this.activity = activity;
        native_init();
        audioStream = new AudioStream(this, audioParam);
        if (cameraType == CameraType.CAMERA1) {
            videoStream = new VideoStream(this, view, videoParam, activity);
        } else if (cameraType == CameraType.CAMERA2) {
            videoStream = new VideoStreamNew(this, view, videoParam, activity);
        }
    }

    public void setPreviewDisplay(SurfaceHolder surfaceHolder) {
        videoStream.setPreviewDisplay(surfaceHolder);
    }

    public void switchCamera() {
        videoStream.switchCamera();
    }

    public void setPreviewDegree(int degree) {
        videoStream.onPreviewDegreeChanged(degree);
    }

    /**
     * setting mute
     *
     * @param isMute is mute or not
     */
    public void setMute(boolean isMute) {
        audioStream.setMute(isMute);
    }

    public void startPush(String path) {
        native_start(path);
        videoStream.startLive();
        audioStream.startLive();
        isPushing = true;
    }

    public void stopPush() {
        isPushing = false;
        videoStream.stopLive();
        audioStream.stopLive();
        native_stop();
    }

    public void release() {
        videoStream.release();
        audioStream.release();
        native_release();
    }

    private int getInputSamplesFromNative() {
//        return native_getInputSamples();
        return 0;
    }

    public void setVideoCodecInfo(int width, int height, int frameRate, int bitrate) {
//        native_setVideoCodecInfo(width, height, frameRate, bitrate);
        rtmpWidth = width;
        rtmpHeight = height;
        rtmpBitRate = frameRate;
        rtmpFrameRate = bitrate;
        Log.e(TAG, "setVideoCodecInfo rtmpWidth = " + rtmpWidth);
        Log.e(TAG, "setVideoCodecInfo rtmpHeight = " + rtmpHeight);
        Log.e(TAG, "setVideoCodecInfo rtmpBitRate = " + rtmpBitRate);
        Log.e(TAG, "setVideoCodecInfo rtmpFrameRate = " + rtmpFrameRate);
    }

    private void setAudioCodecInfo(int sampleRateInHz, int channels) {
//        native_setAudioCodecInfo(sampleRateInHz, channels);
    }

    private void pushAudio(byte[] data) {
        native_pushAudio(data);
    }

    private void pushVideo(byte[] data, int cameraType) {
        native_pushVideo(data, cameraType);
    }

    @Override
    public int getInputSamples() {
        return getInputSamplesFromNative();
    }

    @Override
    public void onAudioCodecInfo(int sampleRate, int channelCount) {
        setAudioCodecInfo(sampleRate, channelCount);
    }

    @Override
    public void onAudioFrame(byte[] pcm) {
        if (pcm != null && isPushing) {
            pushAudio(pcm);
        }
    }

    @Override
    public void onVideoCodecInfo(int width, int height, int frameRate, int bitrate) {
//        setVideoCodecInfo(width, height, frameRate, bitrate);
        rtmpWidth = width;
        rtmpHeight = height;
        Log.e(TAG, "onVideoCodecInfo rtmpWidth = " + rtmpWidth);
        Log.e(TAG, "onVideoCodecInfo rtmpHeight = " + rtmpHeight);
    }

    @Override
    public void onVideoFrame(byte[] yuv, int cameraType) {
        if (yuv != null && isPushing) {
            try {
                onProcessedYuvFrame(yuv, cameraType);
            } catch (Exception e) {
                // need control the multi-frames, and process the last frames when close page, etc.
            }
        }
    }
    private void onProcessedYuvFrame(byte[] yuvFrame, int cameraType) {
        Log.e(TAG, "onProcessedYuvFrame yuvFrame length = " + yuvFrame.length);
        ByteBuffer[] inBuffers = vencoder.getInputBuffers();
        ByteBuffer[] outBuffers = vencoder.getOutputBuffers();
        long pts = System.nanoTime() / 1000;
        int inBufferIndex = vencoder.dequeueInputBuffer(0);
        if (inBufferIndex >= 0) {
            ByteBuffer bb = inBuffers[inBufferIndex];
            bb.clear();
            bb.put(yuvFrame, 0, yuvFrame.length);
            vencoder.queueInputBuffer(inBufferIndex, 0, yuvFrame.length, pts, 0);
        } else {
            Log.e(TAG, "onProcessedYuvFrame dequeueInputBuffer inBufferIndex = " + inBufferIndex);
            return;
        }

        for (; ; ) {
            MediaCodec.BufferInfo vebi = new MediaCodec.BufferInfo();
            int outBufferIndex = vencoder.dequeueOutputBuffer(vebi, 10000);
            if (outBufferIndex >= 0) {
                ByteBuffer bb = outBuffers[outBufferIndex];
                Log.e(TAG, "onProcessedYuvFrame data vebi.offset = " + vebi.offset);
                Log.e(TAG, "onProcessedYuvFrame data vebi.size = " + vebi.size);
                bb.position(vebi.offset);
                bb.limit(vebi.offset + vebi.size);
                byte[] data = new byte[vebi.size];
                bb.get(data);
                native_pushVideo(data, cameraType);
//                onEncodedAnnexbFrame(bb, vebi);
                vencoder.releaseOutputBuffer(outBufferIndex, false);
            } else {
                break;
            }
        }
    }

    // choose the video encoder by name.
    private MediaCodecInfo chooseVideoEncoder(String name) {
        int nbCodecs = MediaCodecList.getCodecCount();
        for (int i = 0; i < nbCodecs; i++) {
            MediaCodecInfo mci = MediaCodecList.getCodecInfoAt(i);
            if (!mci.isEncoder()) {
                continue;
            }

            String[] types = mci.getSupportedTypes();
            for (int j = 0; j < types.length; j++) {
                if (types[j].equalsIgnoreCase(VCODEC)) {
                    Log.i(TAG, String.format("vencoder %s types: %s", mci.getName(), types[j]));
                    if (name == null) {
                        return mci;
                    }

                    if (mci.getName().contains(name)) {
                        return mci;
                    }
                }
            }
        }

        return null;
    }

    // choose the right supported color format. @see below:
    private int chooseVideoEncoder() {
        // choose the encoder "video/avc":
        //      1. select default one when type matched.
        //      2. google avc is unusable.
        //      3. choose qcom avc.
        vmci = chooseVideoEncoder(null);
        //vmci = chooseVideoEncoder("google");
        //vmci = chooseVideoEncoder("qcom");

        int matchedColorFormat = 0;
        MediaCodecInfo.CodecCapabilities cc = vmci.getCapabilitiesForType(VCODEC);
        for (int i = 0; i < cc.colorFormats.length; i++) {
            int cf = cc.colorFormats[i];
            Log.i(TAG, String.format("vencoder %s supports color fomart 0x%x(%d)", vmci.getName(), cf, cf));

            // choose YUV for h.264, prefer the bigger one.
            // corresponding to the color space transform in onPreviewFrame
            if (cf >= cc.COLOR_FormatYUV420Planar && cf <= cc.COLOR_FormatYUV420SemiPlanar) {
                if (cf > matchedColorFormat) {
                    matchedColorFormat = cf;
                }
            }
        }

        for (int i = 0; i < cc.profileLevels.length; i++) {
            MediaCodecInfo.CodecProfileLevel pl = cc.profileLevels[i];
            Log.i(TAG, String.format("vencoder %s support profile %d, level %d", vmci.getName(), pl.profile, pl.level));
        }

        Log.e(TAG, String.format("vencoder %s choose color format 0x%x(%d)", vmci.getName(), matchedColorFormat, matchedColorFormat));
        return matchedColorFormat;
    }

    private void native_init() {
        rtmpMuxer = new RTMPMuxer();
        mVideoColorFormat = chooseVideoEncoder();
        // vencoder yuv to 264 es stream.
        try {
            vencoder = MediaCodec.createByCodecName(vmci.getName());
        } catch (IOException e) {
            Log.e(TAG, "create vencoder failed.");
            e.printStackTrace();
        }
    }

    private void native_start(String path) {
        // setup the vencoder.
        // Note: landscape to portrait, 90 degree rotation, so we need to switch width and height in configuration
        MediaFormat videoFormat = MediaFormat.createVideoFormat(VCODEC, rtmpWidth, rtmpHeight);
        videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, mVideoColorFormat);
        videoFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 0);
        videoFormat.setInteger(MediaFormat.KEY_BIT_RATE, vBitrate);
        videoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, VFPS);
        videoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, VGOP / VFPS);
        vencoder.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        // start device and encoder.
        vencoder.start();

        if (rtmpMuxer.isConnected()) {
            Log.e(TAG, "native_start rtmpMuxer is already Connected. ");
        } else {
            int ret = rtmpMuxer.open(path, rtmpWidth, rtmpHeight);
            Log.e(TAG, "native_start to open, ret = " + ret);
        }
    }

//    private native void native_setVideoCodecInfo(int width, int height, int fps, int bitrate);
//
//    private native void native_setAudioCodecInfo(int sampleRateInHz, int channels);

//    private native int native_getInputSamples();

    private void native_pushAudio(byte[] data) {
        if (rtmpMuxer != null) {
            int ret = rtmpMuxer.writeAudio(data, 0, data.length, System.currentTimeMillis());
            Log.e(TAG, "native_pushAudio ret = " + ret);
        } else {
            Log.e(TAG, "native_pushAudio rtmpMuxer is null? ");
        }
    }

    private void native_pushVideo(byte[] data, int cameraType) {
        if (rtmpMuxer != null) {
            int ret = rtmpMuxer.writeVideo(data, 0, data.length, System.currentTimeMillis());
            Log.e(TAG, "native_pushVideo ret = " + ret);
        } else {
            Log.e(TAG, "native_pushVideo rtmpMuxer is null? ");
        }
    }

    private void native_stop() {
        if (rtmpMuxer != null && rtmpMuxer.isConnected()) {
            int ret = rtmpMuxer.close();
            Log.e(TAG, "native_stop ret = " + ret);
        }
        if (vencoder != null) {
            Log.i(TAG, "stop vencoder");
            try {
                vencoder.stop();
            } catch (IllegalStateException e) {
                e.printStackTrace();
            }
        }
    }

    private void native_release() {
        if (rtmpMuxer != null) {
            int ret = rtmpMuxer.close();
            rtmpMuxer = null;
            Log.e(TAG, "native_release ret = " + ret);
        }
        if (vencoder != null) {
            Log.i(TAG, "release vencoder");
            vencoder.release();
            vencoder = null;
        }
    }

}
