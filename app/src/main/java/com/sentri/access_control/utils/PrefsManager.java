package com.sentri.access_control.utils;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

/**
 * SharedPreferences wrapper with typed getters/setters for user session data.
 */
public class PrefsManager {
    private static final String PREFS_NAME = "user_prefs";

    private static final String KEY_USER_EMAIL = "user_email";
    private static final String KEY_CURRENT_BIZ = "currentBiz";
    private static final String KEY_CURRENT_BIZ_NAME = "currentBizName";
    private static final String KEY_CURRENT_BIZ_ACCESS_LEVEL = "currentBizAccessLevel";
    private static final String KEY_BIZ_IDS = "bizIds";
    private static final String KEY_BIZ_NAMES = "bizNames";
    private static final String KEY_BIZ_ACCESS_LEVELS = "bizAccessLevels";
    private static final String KEY_USER_PHOTO_URL = "user_photo_url";
    private static final String KEY_BUSINESS_PREFIX = "business_prefix";

    private final SharedPreferences prefs;

    public PrefsManager(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // --- User Email ---
    public String getUserEmail() {
        return prefs.getString(KEY_USER_EMAIL, "");
    }

    public void setUserEmail(String email) {
        prefs.edit().putString(KEY_USER_EMAIL, email).apply();
    }

    // --- Current Business ---
    public String getCurrentBizId() {
        return prefs.getString(KEY_CURRENT_BIZ, null);
    }

    public void setCurrentBizId(String bizId) {
        prefs.edit().putString(KEY_CURRENT_BIZ, bizId).apply();
    }

    public String getCurrentBizName() {
        return prefs.getString(KEY_CURRENT_BIZ_NAME, null);
    }

    public void setCurrentBizName(String bizName) {
        prefs.edit().putString(KEY_CURRENT_BIZ_NAME, bizName).apply();
    }

    public String getCurrentBizAccessLevel() {
        return prefs.getString(KEY_CURRENT_BIZ_ACCESS_LEVEL, "");
    }

    public void setCurrentBizAccessLevel(String level) {
        prefs.edit().putString(KEY_CURRENT_BIZ_ACCESS_LEVEL, level).apply();
    }

    // --- Business Lists ---
    public List<String> getBizIds() {
        return parseJsonArray(prefs.getString(KEY_BIZ_IDS, null));
    }

    public void setBizIds(List<String> ids) {
        prefs.edit().putString(KEY_BIZ_IDS, new JSONArray(ids).toString()).apply();
    }

    public List<String> getBizNames() {
        return parseJsonArray(prefs.getString(KEY_BIZ_NAMES, null));
    }

    public void setBizNames(List<String> names) {
        prefs.edit().putString(KEY_BIZ_NAMES, new JSONArray(names).toString()).apply();
    }

    public List<String> getBizAccessLevels() {
        return parseJsonArray(prefs.getString(KEY_BIZ_ACCESS_LEVELS, null));
    }

    public void setBizAccessLevels(List<String> levels) {
        prefs.edit().putString(KEY_BIZ_ACCESS_LEVELS, levels != null ? new JSONArray(levels).toString() : null).apply();
    }

    // --- User Photo ---
    public String getUserPhotoUrl() {
        return prefs.getString(KEY_USER_PHOTO_URL, "");
    }

    public void setUserPhotoUrl(String url) {
        prefs.edit().putString(KEY_USER_PHOTO_URL, url).apply();
    }

    // --- Business Prefix ---
    public String getBusinessPrefix() {
        return prefs.getString(KEY_BUSINESS_PREFIX, null);
    }

    public void setBusinessPrefix(String prefix) {
        prefs.edit().putString(KEY_BUSINESS_PREFIX, prefix).apply();
    }

    // --- Session Management ---
    public void saveSession(String email, String currentBiz, String currentBizName, 
                           List<String> bizIds, List<String> bizNames,
                           List<String> bizAccessLevels, String currentAccessLevel, 
                           String photoUrl) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(KEY_USER_EMAIL, email);
        editor.putString(KEY_CURRENT_BIZ, currentBiz);
        editor.putString(KEY_CURRENT_BIZ_NAME, currentBizName);
        editor.putString(KEY_BIZ_IDS, new JSONArray(bizIds).toString());
        editor.putString(KEY_BIZ_NAMES, new JSONArray(bizNames).toString());
        editor.putString(KEY_BIZ_ACCESS_LEVELS, bizAccessLevels != null ? new JSONArray(bizAccessLevels).toString() : null);
        editor.putString(KEY_CURRENT_BIZ_ACCESS_LEVEL, currentAccessLevel);
        editor.putString(KEY_USER_PHOTO_URL, photoUrl);
        editor.apply();
    }

    public void clearSession() {
        prefs.edit().clear().apply();
    }

    public boolean hasSession() {
        return prefs.getString(KEY_USER_EMAIL, null) != null 
                && prefs.getString(KEY_BIZ_IDS, null) != null;
    }

    // --- Utility ---
    public static List<String> parseJsonArray(String json) {
        ArrayList<String> list = new ArrayList<>();
        if (json == null) return list;
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                list.add(arr.getString(i));
            }
        } catch (Exception ignored) {}
        return list;
    }
}
