package com.car;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import androidx.annotation.Nullable;

import org.json.JSONObject;

public class ProxyConfig implements Parcelable {

    public String getConfig() {
        return config;
    }

    public void setConfig(String config) {
        this.config = config;
    }

    private String config;

    public ProxyConfig() {
        try {
            JSONObject jsonObject = new JSONObject("{\"listen\": \"socks://127.0.0.1:1081\",\"proxy\": \"https://user:password@example.com:443\"}");
            config = jsonObject.toString();
            Log.e("Test_New_Config",config);
        } catch (Throwable t) {

        }
    }

    public String name() {
        try {
            JSONObject jsonObject = new JSONObject(config);
            String addr = jsonObject.getString("proxy");
            Log.e("Test_Get_proxy",addr);
            //int port = 8889;////jsonObject.getInt("remote_port");
            return addr;//addr + ":" + String.valueOf(port);
        }catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    protected ProxyConfig(Parcel in) {
        config = in.readString();
    }

    public static final Creator<ProxyConfig> CREATOR = new Creator<ProxyConfig>() {
        @Override
        public ProxyConfig createFromParcel(Parcel in) {
            return new ProxyConfig(in);
        }

        @Override
        public ProxyConfig[] newArray(int size) {
            return new ProxyConfig[size];
        }
    };

    public String generateProxyConfigJSON() {
        try {
            JSONObject jsonObject = new JSONObject(config);
            return jsonObject.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public void fromJSON(String jsonStr) {
        try {
            JSONObject jsonObject = new JSONObject(jsonStr);
            config = jsonObject.toString();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void copyFrom(ProxyConfig that) {
        config = that.config;
    }

    public boolean isValidRunningConfig() {
        return !name().equals("");
    }


    @Override
    public boolean equals(@Nullable Object obj) {
        if (!(obj instanceof ProxyConfig)) {
            return false;
        }
        ProxyConfig that = (ProxyConfig) obj;
        return that.config == this.config;
    }

    private static boolean paramEquals(Object a, Object b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return a.equals(b);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(config);
    }
}
