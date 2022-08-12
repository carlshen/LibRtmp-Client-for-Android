package io.antmedia.rtmp_client.test;

import android.app.Application;

public class TestApplication extends Application {

    private static TestApplication context;

    @Override
    public void onCreate() {
        super.onCreate();
        context = this;
    }

    public static TestApplication getInstance() {
        return context;
    }

}
