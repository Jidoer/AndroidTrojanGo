// ITrojanService.aidl
package com.car.naive.proxy.aidl;
import com.car.naive.proxy.aidl.ITrojanServiceCallback;
// Declare any non-default types here with import statements

interface ITrojanService {
    int getState();
    void testConnection(String testUrl);
    void showDevelopInfoInLogcat();
    oneway void registerCallback(in ITrojanServiceCallback callback);
    oneway void unregisterCallback(in ITrojanServiceCallback callback);
}
