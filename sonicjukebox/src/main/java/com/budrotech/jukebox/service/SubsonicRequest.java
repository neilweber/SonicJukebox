package com.budrotech.jukebox.service;

import android.content.Context;
import android.util.Log;

import com.budrotech.jukebox.R;
import com.budrotech.jukebox.util.CancellableTask;
import com.budrotech.jukebox.util.ProgressListener;
import com.budrotech.jukebox.util.Util;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.Call;
import okhttp3.ConnectionPool;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class SubsonicRequest {
    private static final String TAG = SubsonicRequest.class.getSimpleName();

    private static final OkHttpClient sharedHttpClient = new OkHttpClient.Builder().build();
    private final OkHttpClient.Builder httpClientBuilder = sharedHttpClient.newBuilder();
    private final Request.Builder requestBuilder = new Request.Builder();
    private final HttpUrl.Builder urlBuilder;
    private final Context context;
    private long socketConnectTimeout = RESTMusicService.SOCKET_CONNECT_TIMEOUT;
    private long socketReadTimeout = RESTMusicService.SOCKET_READ_TIMEOUT_DEFAULT;

    public SubsonicRequest(Context context, String method) {
        this.context = context;
        urlBuilder = RESTMusicService.getSubsonicUrl(context, method);
    }

    public SubsonicRequest addQueryParameter(String name, String value) {
        urlBuilder.addQueryParameter(name, value);
        return this;
    }

    public void addBasicHeader(String name, String value) {
        requestBuilder.addHeader(name, value);
    }

    public long getSocketConnectTimeout() {
        return socketConnectTimeout;
    }

    public SubsonicRequest setSocketConnectTimeout(long socketConnectTimeout) {
        this.socketConnectTimeout = socketConnectTimeout;
        return this;
    }

    public long getSocketReadTimeout() {
        return socketReadTimeout;
    }

    public SubsonicRequest setSocketReadTimeout(long socketReadTimeout) {
        this.socketReadTimeout = socketReadTimeout;
        return this;
    }

    public Response getResponse(ProgressListener progressListener, CancellableTask cancellableTask) throws IOException {
        ConnectionPool connectionPool = sharedHttpClient.connectionPool();
        HttpUrl url = urlBuilder.build();
        Log.d(TAG, String.format("Connections in pool: %d; idle connections: %d", connectionPool.connectionCount(), connectionPool.idleConnectionCount()));
        Log.i(TAG, String.format("Using URL %s", url));
        httpClientBuilder
                .connectTimeout(socketConnectTimeout, TimeUnit.MILLISECONDS)
                .readTimeout(socketReadTimeout, TimeUnit.MILLISECONDS);

        // TODO Set credentials to get through apache proxies that require authentication.
//		SharedPreferences preferences = Util.getPreferences(context);
//		int instance = preferences.getInt(Constants.PREFERENCES_KEY_SERVER_INSTANCE, 1);
//		String username = preferences.getString(Constants.PREFERENCES_KEY_USERNAME + instance, null);
//		String password = preferences.getString(Constants.PREFERENCES_KEY_PASSWORD + instance, null);
//		sharedHttpClient.getCredentialsProvider().setCredentials(new AuthScope(AuthScope.ANY_HOST, AuthScope.ANY_PORT), new UsernamePasswordCredentials(username, password));

        final AtomicReference<Boolean> cancelled = new AtomicReference<Boolean>(false);
        int attempts = 0;

        Request request = requestBuilder
                .url(url)
                .build();

        while (true) {
            attempts++;

            OkHttpClient build = httpClientBuilder.build();
            final Call call = build.newCall(request);

            if (cancellableTask != null) {
                // Attempt to abort the HTTP request if the task is cancelled.
                cancellableTask.setOnCancelListener(new CancellableTask.OnCancelListener() {
                    @Override
                    public void onCancel() {
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    cancelled.set(true);
                                    call.cancel();
                                } catch (Exception e) {
                                    Log.e(TAG, "Failed to stop http task");
                                }
                            }
                        }).start();
                    }
                });
            }

            try {
                return call.execute();
            } catch (IOException x) {
                call.cancel();

                if (attempts >= RESTMusicService.HTTP_REQUEST_MAX_ATTEMPTS || cancelled.get()) {
                    throw x;
                }

                if (progressListener != null) {
                    String msg = context.getResources().getString(R.string.music_service_retry, attempts, RESTMusicService.HTTP_REQUEST_MAX_ATTEMPTS - 1);
                    progressListener.updateProgress(msg);
                }

                Log.w(TAG, String.format("Got IOException (%d), will retry", attempts), x);
                httpClientBuilder.connectTimeout((long) (build.connectTimeoutMillis() * 1.3F), TimeUnit.MILLISECONDS);
                httpClientBuilder.readTimeout((long) (build.readTimeoutMillis() * 1.5F), TimeUnit.MILLISECONDS);
                Util.sleepQuietly(2000L);
            }
        }

    }
}
