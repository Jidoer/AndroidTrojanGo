package com.car.trojango.ui;

import static android.content.Context.MODE_MULTI_PROCESS;
import static android.content.Context.MODE_PRIVATE;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.net.VpnService;
import android.os.Bundle;
import android.os.Debug;
import android.os.Handler;
import android.os.RemoteException;
import android.provider.Telephony;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.car.Globals;
import com.car.LogHelper;
import com.car.ProxyConfig;
import com.car.ProxyFileHelper;
import com.car.ProxyHelper;
import com.car.common.utils.PreferenceUtils;
import com.car.trojango.Constants;
import com.car.trojango.AppService;
import com.car.trojango.R;
import com.car.trojango.adapter.FileListAdapter;
import com.car.naive.connection.TrojanConnection;
import com.car.naive.proxy.aidl.ITrojanService;
import com.car.service.ProxyService;
import com.github.clans.fab.FloatingActionButton;
import com.kangc.ServiceTool;
import com.kangc.fileread;
import com.tbruyelle.rxpermissions2.RxPermissions;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.Unbinder;
import io.reactivex.Observable;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;

public class HomeFragment extends Fragment implements TrojanConnection.Callback{


    @BindView(R.id.recyclerView)
    RecyclerView recyclerView;
    @BindView(R.id.fab)
    FloatingActionButton fab;
    @BindView(R.id.refreshView)
    SwipeRefreshLayout refreshView;
    @BindView(R.id.tv_state)
    TextView tvState;
    @BindView(R.id.tv_logcat)
    TextView tvLogcat;
    @BindView(R.id.sv_logcat)
    NestedScrollView svLogcat;

    private Unbinder bind;
    private FileListAdapter listAdapter;

    private static final String TAG = "Home";
    private static final int READ_WRITE_EXT_STORAGE_PERMISSION_REQUEST = 514;
    private static final int VPN_REQUEST_CODE = 233;
    private static final int SERVER_LIST_CHOOSE_REQUEST_CODE = 1024;
    private static final int EXEMPT_APP_CONFIGURE_REQUEST_CODE = 2077;
    private static final String CONNECTION_TEST_URL = "https://www.google.com";
    //VpnService
    private @ProxyService.ProxyState
    int proxyState = ProxyService.STATE_NONE;
    private final TrojanConnection connection = new TrojanConnection(false);
    public ITrojanService trojanService;

    private String ConfigJson;
    public static HomeFragment instance;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_home, container, false);
        bind = ButterKnife.bind(this, root);
        instance = this;
        connection.connect(getContext(),this);
        init();

        return root;
    }

    private void init() {
        Log.e("init_MAin","INIT()");
        listAdapter = new FileListAdapter();
        listAdapter.addChildClickViewIds(R.id.iv_delete, R.id.iv_edit, R.id.info_container);

        listAdapter.setOnItemChildClickListener((adapter, view, position) -> {
            if (view.getId() == R.id.iv_edit) {
                editIni(position);
            } else if (view.getId() == R.id.iv_delete) {
                deleteFile(position);
            } else if (view.getId() == R.id.info_container) {
                if (proxyState == ProxyService.STARTED || proxyState == ProxyService.STARTING || proxyState == ProxyService.STOPPING) {
                    Toast.makeText(getContext(), R.string.needStopService, Toast.LENGTH_SHORT).show();
                    return;
                }
                listAdapter.setSelectItem(listAdapter.getItem(position));

                getActivity().getSharedPreferences("Seleted",MODE_MULTI_PROCESS)
                        .edit()
                        .putInt("index",position)
                        .apply();
            }
        });
        recyclerView.setAdapter(listAdapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext(), RecyclerView.VERTICAL, false));
        refreshView.setOnRefreshListener(this::setData);

        //syncServiceState();
        updateViews(proxyState);
    }

    private void syncServiceState() {
        if(proxyState == ProxyService.STARTED) {
            // stop ProxyService
            setServiceState(R.color.colorStop, R.drawable.ic_stop_white, R.string.hasOpened,true);
            return;
        }
        setServiceState(R.color.colorPlay, R.drawable.ic_play_white, R.string.notOpened,true);
    }

    private void setServiceState(int color, int res, int text,boolean ableuse) {
        if(isAdded()) {
            fab.setColorNormal(getResources().getColor(color));
            fab.setImageResource(res);
            tvState.setText(text);
            fab.setEnabled(ableuse);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        setData();
    }

    private void editIni(int position) {
        File item = listAdapter.getItem(position);
        checkPermissions(aBoolean -> {
            if (!aBoolean) {
                Constants.tendToSettings(getContext());
                return;
            }
            Intent intent = new Intent(getContext(), IniEditActivity.class);
            intent.putExtra(getString(R.string.intent_key_file), item.getPath());
            startActivity(intent);
        });

    }

    private void deleteFile(int position) {
        File item = listAdapter.getItem(position);
        Observable.just(item)
                .map(file -> item.delete())
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<Boolean>() {
                    @Override
                    public void onSubscribe(Disposable d) {

                    }

                    @Override
                    public void onNext(Boolean aBoolean) {
                        if (aBoolean) {
                            listAdapter.removeAt(position);
                            int selet_ed = getSeleted();
                            if(selet_ed == position){
                                Log.e(TAG, "Deleted Clean Seleted");
                                setSeleted(-1);
                            }
                        } else {
                            Toast.makeText(getContext(), item.getName() + getString(R.string.actionDeleteFailed), Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onError(Throwable e) {
                        e.printStackTrace();
                        Toast.makeText(getContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onComplete() {
                        if (listAdapter.getItemCount() == 0) {
                            Log.e(TAG, "getItemCount = 0 && Clean Seleted");
                            setSeleted(-1);
                            return;
                        }
                    }
                });
    }

    private void setSeleted(int i){
        getActivity().getSharedPreferences("Seleted", MODE_MULTI_PROCESS)
                .edit()
                .putInt("index", i)
                .apply();
    }
    private int getSeleted(){
       return getActivity().getSharedPreferences("Seleted",MODE_MULTI_PROCESS).getInt("index",-1);
    }


    private void setData() {
        getFiles().subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<List<File>>() {
                    @Override
                    public void onSubscribe(Disposable d) {
                        refreshView.setRefreshing(true);

                    }

                    @Override
                    public void onNext(List<File> files) {
                        refreshView.setRefreshing(false);
                        listAdapter.setList(files);
                    }

                    @Override
                    public void onError(Throwable e) {
                        e.printStackTrace();

                    }

                    @Override
                    public void onComplete() {
                        refreshView.setRefreshing(false);

                        int selet_ed = getSeleted();
                        Log.e(TAG,"Seleted:" + selet_ed);
                        Log.e(TAG,"getItemCount:" + listAdapter.getItemCount());
                        if(selet_ed < listAdapter.getItemCount() && selet_ed >=0){
                            Log.e(TAG,"Load Seleted item Settings");
                            listAdapter.setSelectItem(listAdapter.getItem(selet_ed));
                        }

                    }
                });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        bind.unbind();
    }

    public Observable<List<File>> getFiles() {
        return Observable.create((ObservableOnSubscribe<List<File>>) emitter -> {
            File path = Constants.getIniFileParent(getContext());
            File[] files = path.listFiles();
            emitter.onNext(files != null ? Arrays.asList(files) : new ArrayList<>());
            emitter.onComplete();
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread());

    }


    private void checkPermissions(Consumer<Boolean> consumer) {
        Disposable subscribe = new RxPermissions(this)
                .request(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE)
                .subscribe(consumer);


    }

    @OnClick(R.id.fab)
    public void onViewClicked() {

        if (proxyState == ProxyService.STATE_NONE || proxyState == ProxyService.STOPPED) {

            if (listAdapter.getSelectItem() == null) {
                Toast.makeText(getContext(), R.string.notSelectIni, Toast.LENGTH_SHORT).show();
                return;
            }
            ConfigJson = listAdapter.getItem(listAdapter.getItemPosition(listAdapter.getSelectItem())).toString();
            //Toast.makeText(getContext(), ConfigJson, Toast.LENGTH_SHORT).show();
            ProxyConfig ins = Globals.getProxyConfigInstance();

            ins.setConfig(fileread.readTxt(ConfigJson));
            Globals.setProxyConfigInstance(ins);

            //Toast.makeText(MainActivity.this,Globals.getTrojanConfigInstance().getConfig(),Toast.LENGTH_LONG).show();
            ProxyFileHelper.WriteProxyConfig(Globals.getProxyConfigInstance(), Globals.getProxyConfigPath());

            ProxyFileHelper.ShowConfig(Globals.getProxyConfigPath());
            // start ProxyService
            Intent i = VpnService.prepare(getContext());
            if (i != null) {
                startActivityForResult(i, VPN_REQUEST_CODE);
            } else {
                ProxyHelper.startProxyService(getContext());
            }
        } else if (proxyState == ProxyService.STARTED) {
            // stop ProxyService
            ProxyHelper.stopProxyService(getContext());
        }
        readLog();

    }




    public boolean isRunService(Context context) {
        return ServiceTool.isServiceRunning(context,"com.car.service.ProxyService");
    }

    private Disposable readingLog = null;

    private void readLog() {
        tvLogcat.setText("");
        if (readingLog != null) return;
        HashSet<String> lst = new LinkedHashSet<String>();
        lst.add("logcat");
        lst.add("-T");
        lst.add("0");
        lst.add("-v");
        lst.add("time");
        lst.add("-s");
        lst.add("GoLog,com.car.trojango.AppService");
        readingLog = Observable.create((ObservableOnSubscribe<String>) emitter -> {

            Process process = Runtime.getRuntime().exec(lst.toArray(new String[0]));

            InputStreamReader in = new InputStreamReader(process.getInputStream());
            BufferedReader bufferedReader = new BufferedReader(in);

            String line;
            while ((line = bufferedReader.readLine()) != null) {
                emitter.onNext(line);
            }
            in.close();
            bufferedReader.close();
            emitter.onComplete();
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<String>() {
                    @Override
                    public void accept(String s) throws Exception {
                        if(tvLogcat == null)return;
                        tvLogcat.append(s);
                        tvLogcat.append("\r\n");
                        svLogcat.fullScroll(View.FOCUS_DOWN);
                    }
                }, new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable throwable) throws Exception {
                        if(tvLogcat == null) return;
                        throwable.printStackTrace();
                        tvLogcat.append(throwable.toString());
                        tvLogcat.append("\r\n");
                    }
                });


    }


    ////////// implements TrojanConnection.Callback start

    @Override
    public void onServiceConnected(ITrojanService service) {
        LogHelper.i(TAG, "onServiceConnected");
        Log.e(TAG, "onServiceConnected");
        trojanService = service;
        try {
            final int state = service.getState();
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    updateViews(state);
                }
            });
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }




    private void updateViews(int state) {
        proxyState = state;

        switch (state) {
            case ProxyService.STARTING: {
                //fab.setText(R.string.button_service__starting);
                setServiceState(R.color.colorStop, R.drawable.ic_stop_white, R.string.hasOpened,false);
                break;
            }
            case ProxyService.STARTED: {
                setServiceState(R.color.colorStop, R.drawable.ic_stop_white, R.string.hasOpened,true);
                break;
            }
            case ProxyService.STOPPING: {
                setServiceState(R.color.colorPlay, R.drawable.ic_play_white, R.string.notOpened,false);
                break;
            }
            default: {
                setServiceState(R.color.colorPlay, R.drawable.ic_play_white, R.string.notOpened,true);
                break;
            }
        }
    }

    @Override
    public void onServiceDisconnected() {
        Log.e(TAG, "onServiceConnected");
        LogHelper.i(TAG, "onServiceConnected");
        trojanService = null;
    }

    @Override
    public void onStateChanged(int state, String msg) {
        Log.e(TAG, "onStateChanged# state: " + state + " msg: " + msg);
        LogHelper.i(TAG, "onStateChanged# state: " + state + " msg: " + msg);
        updateViews(state);
    }

    @Override
    public void onTestResult(String testUrl, boolean connected, long delay, @NonNull String error) {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                showTestConnectionResult(testUrl, connected, delay, error);
            }
        });
    }
    private void showTestConnectionResult(String testUrl, boolean connected, long delay, @NonNull String error) {
        if (connected) {
            Toast.makeText(getContext(),String.valueOf(delay) + "ms", Toast.LENGTH_LONG).show();
        } else {
            LogHelper.e(TAG, "ERROR: " + error);
            Toast.makeText(getContext(),
                    getString(R.string.failed_to_connect_to__,
                            "testUrl", error),
                    Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Test connection by invoking {@link ITrojanService#testConnection(String)}. Since {@link ITrojanService}
     * is from remote process, a {@link RemoteException} might be thrown. Test result will be delivered
     * to {@link #onTestResult(String, boolean, long, String)} by {@link TrojanConnection}.
     */
    public void testConnection() {
        ITrojanService service = trojanService;
        if (service == null) {
            showTestConnectionResult(CONNECTION_TEST_URL, false, 0L, "测试前请先连接 [service is not available]");
        } else {
            try {
                service.testConnection(CONNECTION_TEST_URL);
            } catch (RemoteException e) {
                showTestConnectionResult(CONNECTION_TEST_URL, false, 0L, "[service throws RemoteException]");
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onBinderDied() {
        Log.e(TAG, "onBinderDied");
        LogHelper.i(TAG, "onBinderDied");
        connection.disconnect(getContext());
        // connect the new binder
        // todo is it necessary to re-connect?
        connection.connect(getContext(), this);
    }

    ////////// implements TrojanConnection.Callback end
}
