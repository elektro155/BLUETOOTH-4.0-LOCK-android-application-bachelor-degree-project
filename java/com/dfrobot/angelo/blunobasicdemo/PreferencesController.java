package com.dfrobot.angelo.blunobasicdemo;

import android.content.Context;
import android.content.SharedPreferences;

public class PreferencesController {

    public static final String PREF_NAME = "preferenceOfBLE";
    public static final String ENABLE_SHAKING = "shaking_enabling";
    public static final String ENABLE_PASSWORD = "password_enabling";
    public static final String PASSWORD= "PASSWORD";
    public static final String DEVICE_NAME = "Bluno";
    public static final String DEVICE_ADDRESS = "AA:BB:CC:DD:EE:FF";


    private Context context;
    private static PreferencesController instance;

    private PreferencesController(Context context) {
        this.context = context.getApplicationContext();
    }

    public static PreferencesController getInstance(Context context) {
        if (instance == null) {
            instance = new PreferencesController(context);
        }
        return instance;
    }

    public void setShaking(boolean shaking) {
        SharedPreferences settings = context.getSharedPreferences(PREF_NAME, 0);
        SharedPreferences.Editor editor = settings.edit();
        editor.putBoolean(ENABLE_SHAKING , shaking);
        editor.commit();
    }

    public Boolean getShaking() {
        SharedPreferences settings = context.getSharedPreferences(PREF_NAME, 0);
        return settings.getBoolean(ENABLE_SHAKING , true);
    }

    public void setSavingPassword(boolean passSave) {
        SharedPreferences settings = context.getSharedPreferences(PREF_NAME, 0);
        SharedPreferences.Editor editor = settings.edit();
        editor.putBoolean(ENABLE_PASSWORD , passSave);
        editor.commit();
    }

    public Boolean getSavingPassword() {
        SharedPreferences settings = context.getSharedPreferences(PREF_NAME, 0);
        return settings.getBoolean(ENABLE_PASSWORD , true);
    }

    public void setPassword(String newPassword) {
        SharedPreferences settings = context.getSharedPreferences(PREF_NAME, 0);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(PASSWORD, newPassword);
        editor.commit();
    }

    public String getPassword() {
        SharedPreferences settings = context.getSharedPreferences(PREF_NAME, 0);
        return settings.getString(PASSWORD, "password");
    }

    public void setDeviceName(String name) {
        SharedPreferences settings = context.getSharedPreferences(PREF_NAME, 0);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(DEVICE_NAME, name);
        editor.commit();
    }

    public String getDeviceName() {
        SharedPreferences settings = context.getSharedPreferences(PREF_NAME, 0);
        return settings.getString(DEVICE_NAME, "");
    }

    public void setDeviceAddress(String address) {
        SharedPreferences settings = context.getSharedPreferences(PREF_NAME, 0);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(DEVICE_ADDRESS, address);
        editor.commit();
    }

    public String getDeviceAddress() {
        SharedPreferences settings = context.getSharedPreferences(PREF_NAME, 0);
        return settings.getString(DEVICE_ADDRESS, "");
    }

}
