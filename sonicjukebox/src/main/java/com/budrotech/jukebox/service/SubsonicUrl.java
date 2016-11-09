package com.budrotech.jukebox.service;

import android.content.Context;
import android.content.SharedPreferences;

import com.budrotech.jukebox.util.Constants;
import com.budrotech.jukebox.util.Util;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

public class SubsonicUrl {
    private final StringBuilder builder = new StringBuilder(8192);

    public SubsonicUrl(Context context, String method) {
        SharedPreferences preferences = Util.getPreferences(context);

        int instance = preferences.getInt(Constants.PREFERENCES_KEY_SERVER_INSTANCE, 1);
        String serverUrl = preferences.getString(Constants.PREFERENCES_KEY_SERVER_URL + instance, null);
        String username = preferences.getString(Constants.PREFERENCES_KEY_USERNAME + instance, null);
        String password = preferences.getString(Constants.PREFERENCES_KEY_PASSWORD + instance, null);

        // Slightly obfuscate password
        password = "enc:" + Util.utf8HexEncode(password);

        builder.append(serverUrl);
        if (builder.charAt(builder.length() - 1) != '/') {
            builder.append('/');
        }

        builder.append("rest/").append(method).append(".view");
        builder.append("?u=").append(username);
        builder.append("&p=").append(password);
        builder.append("&v=").append(Constants.REST_PROTOCOL_VERSION);
        builder.append("&c=").append(Constants.REST_CLIENT_ID);

    }

    public void addParameter(String name, Object value) {
        try {
            builder.append('&').append(name).append('=');
            builder.append(URLEncoder.encode(String.valueOf(value), "UTF-8"));
        } catch (UnsupportedEncodingException ex) {
            throw new RuntimeException(ex);
        }

    }
}
