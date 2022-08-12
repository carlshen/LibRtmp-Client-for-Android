package io.antmedia.rtmp_client.test;

import android.view.SurfaceHolder;

/**
 * Base of VideoStream
 * Created by carl shen on 2022/7/28.
 */

public abstract class VideoStreamBase {

    public abstract void startLive();

    public abstract void setPreviewDisplay(SurfaceHolder surfaceHolder);

    public abstract void switchCamera();

    public abstract void stopLive();

    public abstract void release();

    public abstract void onPreviewDegreeChanged(int degree);

}
