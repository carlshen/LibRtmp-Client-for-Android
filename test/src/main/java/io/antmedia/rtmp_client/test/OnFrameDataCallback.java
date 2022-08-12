package io.antmedia.rtmp_client.test;

/**
 * Video/Audio frame callback
 * Created by carl shen on 2022/7/28.
 */

public interface OnFrameDataCallback {

    int getInputSamples();

    void onAudioFrame(byte[] pcm);

    void onAudioCodecInfo(int sampleRate, int channelCount);

    void onVideoFrame(byte[] yuv, int cameraType);

    void onVideoCodecInfo(int width, int height, int frameRate, int bitrate);
}
