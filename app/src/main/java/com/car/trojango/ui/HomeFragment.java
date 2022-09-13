package com.car.trojango.ui;

import android.Manifest;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.car.trojango.Constants;
import com.car.trojango.R;
import com.car.trojango.adapter.FileListAdapter;
import com.github.clans.fab.FloatingActionButton;
import com.kangc.fileread;
import com.tbruyelle.rxpermissions2.RxPermissions;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.Unbinder;
import io.github.trojan_gfw.igniter.Globals;
import io.github.trojan_gfw.igniter.LogHelper;
import io.github.trojan_gfw.igniter.ProxyService;
import io.github.trojan_gfw.igniter.TrojanConfig;
import io.github.trojan_gfw.igniter.TrojanHelper;
import io.github.trojan_gfw.igniter.common.os.Task;
import io.github.trojan_gfw.igniter.common.os.Threads;
import io.github.trojan_gfw.igniter.connection.TrojanConnection;
import io.github.trojan_gfw.igniter.proxy.aidl.ITrojanService;
import io.github.trojan_gfw.igniter.servers.data.ServerListDataSource;
import io.github.trojan_gfw.igniter.tile.ProxyHelper;
import io.reactivex.Observable;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;

import static android.content.Context.ACTIVITY_SERVICE;

public class HomeFragment extends Fragment /*implements TrojanConnection.Callback*/ {

    private static final String TAG = "Home";
    private static final int READ_WRITE_EXT_STORAGE_PERMISSION_REQUEST = 514;
    private static final int VPN_REQUEST_CODE = 233;
    private static final int SERVER_LIST_CHOOSE_REQUEST_CODE = 1024;
    private static final int EXEMPT_APP_CONFIGURE_REQUEST_CODE = 2077;
    private static final String CONNECTION_TEST_URL = "https://www.google.com";

    private String shareLink;
    private ViewGroup rootViewGroup;
    private EditText configText;
    private Switch clashSwitch;
    private TextView clashLink;
    private Button startStopButton;
    private EditText trojanURLText;
    private @ProxyService.ProxyState
    int proxyState = ProxyService.STATE_NONE;
    private final TrojanConnection connection = new TrojanConnection(false);
    public ITrojanService trojanService;
    private ServerListDataSource serverListDataManager;
    private AlertDialog linkDialog;
    private int IfStart = 0;

    private String ConfigJson = "";


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


    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_home, container, false);
        bind = ButterKnife.bind(this, root);
        init();
        return root;
    }

    private void init() {
        listAdapter = new FileListAdapter();
        listAdapter.addChildClickViewIds(R.id.iv_delete, R.id.iv_edit, R.id.info_container);
        MainActivity.listAdapter = listAdapter; // this->Main
        MainActivity.recyclerView = recyclerView;
        listAdapter.setOnItemChildClickListener((adapter, view, position) -> {
            if (view.getId() == R.id.iv_edit) {
                editIni(position);
            } else if (view.getId() == R.id.iv_delete) {
                deleteFile(position);
            } else if (view.getId() == R.id.info_container) {
                if (isRunService(getContext())) {
                    Toast.makeText(getContext(), R.string.needStopService, Toast.LENGTH_SHORT).show();
                    return;
                }
                listAdapter.setSelectItem(listAdapter.getItem(position));
            }
        });
        recyclerView.setAdapter(listAdapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext(), RecyclerView.VERTICAL, false));
        refreshView.setOnRefreshListener(this::setData);

        syncServiceState();

        if (isRunService(getContext())) { //加入自动readLog
            readLog();
        }
    }

    private void syncServiceState() {
        if (!isRunService(getContext())) {
            setServiceState(R.color.colorPlay, R.drawable.ic_play_white, R.string.notOpened);
        } else {
            setServiceState(R.color.colorStop, R.drawable.ic_stop_white, R.string.hasOpened);
        }
    }

    private void setServiceState(int color, int res, int text) {
        fab.setColorNormal(getResources().getColor(color));
        fab.setImageResource(res);
        tvState.setText(text);
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

                    }
                });
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
                .request(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.READ_PHONE_STATE)
                .subscribe(consumer);


    }

    @OnClick(R.id.fab)
    public void onViewClicked() {

        if (isRunService(getContext())) {
            Log.i(TAG, "Server is running!");
            ProxyHelper.stopProxyService(getContext());
            IfStart = 0;
            setServiceState(R.color.colorPlay, R.drawable.ic_play_white, R.string.notOpened);
        } else {
            Log.i(TAG, "Server is NOT running!");
            if (listAdapter.getSelectItem() == null) {
                Toast.makeText(getContext(), R.string.notSelectIni, Toast.LENGTH_SHORT).show();
                return;
            }

            ConfigJson = listAdapter.getItem(listAdapter.getItemPosition(listAdapter.getSelectItem())).toString();
            //Toast.makeText(getContext(), ConfigJson, Toast.LENGTH_SHORT).show();
            TrojanConfig ins = Globals.getTrojanConfigInstance();

            ins.setConfig(fileread.readTxt(ConfigJson));
            Globals.setTrojanConfigInstance(ins);

            //Toast.makeText(MainActivity.this,Globals.getTrojanConfigInstance().getConfig(),Toast.LENGTH_LONG).show();
            TrojanHelper.WriteTrojanConfig(Globals.getTrojanConfigInstance(), Globals.getTrojanConfigPath());
            TrojanHelper.ShowConfig(Globals.getTrojanConfigPath());

            /// getContext().stopService(new Intent(getContext(), FrpcService.class));
            Intent i = VpnService.prepare(getContext());
            if (i != null) {
                startActivityForResult(i, VPN_REQUEST_CODE);
            } else {
                ProxyHelper.startProxyService(getContext());
            }
            readLog();
            // stop ProxyService
            ///  Intent service = new Intent(getContext(), FrpcService.class);
            /// service.putExtra(getResources().getString(R.string.intent_key_file), listAdapter.getSelectItem().getPath());
            ///getContext().startService(service);
            IfStart = 1;
            setServiceState(R.color.colorStop, R.drawable.ic_stop_white, R.string.hasOpened);
        }


    }


    public boolean isRunService(Context context) {
        if (com.kangc.ServiceTool.isServiceRunning(getContext(), "io.github.trojan_gfw.igniter.ProxyService")) {
            return true;
        }
        return false;
        /*
        ActivityManager manager = (ActivityManager) context.getSystemService(ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            String simpleName = ProxyService.class.getName();//FrpcService.class.getName();
            if (simpleName.equals(service.process)) {
                return true;
            }
        }
        return false;
        */
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
        lst.add("GoLog,io.github.trojan_gfw.igniter.ProxyService");
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
                        tvLogcat.append(s);
                        tvLogcat.append("\r\n");
                        svLogcat.fullScroll(View.FOCUS_DOWN);
                    }
                }, new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable throwable) throws Exception {
                        throwable.printStackTrace();
                        tvLogcat.append(throwable.toString());
                        tvLogcat.append("\r\n");
                    }
                });

    }



}




  /*
        Message msg = Message.obtain();
        msg.what = 0;
        initer.sendMessage(msg);
  public Handler initer = new Handler(new Handler.Callback() {

        @Override
        public boolean handleMessage(@NonNull Message msg) {
            init();
            return true;
        }
    });
*/

