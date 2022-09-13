package com.car.trojango.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.RemoteException;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.MaterialDialog;
import com.car.trojango.Constants;
import com.car.trojango.R;
import com.car.trojango.adapter.FileListAdapter;
import com.car.trojango.tool.FileSelectionDialog;
import com.car.trojango.tool.tool;
import com.google.android.material.navigation.NavigationView;
import com.kangc.fileread;
import com.tbruyelle.rxpermissions2.RxPermissions;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;

import butterknife.BindView;
import butterknife.ButterKnife;
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
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import version.Version;


public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener,FileSelectionDialog.OnFileSelectListener,TrojanConnection.Callback {

    private static final String TAG = "MainActivity";
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
    public static ITrojanService trojanService;
    private ServerListDataSource serverListDataManager;
    private AlertDialog linkDialog;
    //file select
    // 定数
    private static final int MENUID_FILE                              = 0;// File menu id
    private static final int REQUEST_PERMISSION_READ_EXTERNAL_STORAGE = 1; // Identification code requesting external storage read permission
    private String m_strInitialDir = Environment.getExternalStorageDirectory().getPath(); //init file path
    public static FileListAdapter listAdapter;
    public static RecyclerView recyclerView;




    @BindView(R.id.toolbar)
    Toolbar toolbar;

    @BindView(R.id.nav_view)
    NavigationView navView;
    @BindView(R.id.drawer_layout)
    DrawerLayout drawerLayout;
    private AppBarConfiguration mAppBarConfiguration;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        copyRawResourceToDir(R.raw.cacert, Globals.getCaCertPath(), true);
        copyRawResourceToDir(R.raw.country, Globals.getCountryMmdbPath(), true);
        copyRawResourceToDir(R.raw.clash_config, Globals.getClashConfigPath(), false);


        ButterKnife.bind(this);
        setSupportActionBar(toolbar);
        mAppBarConfiguration = new AppBarConfiguration.Builder(
                R.id.nav_home)
                .setOpenableLayout(drawerLayout)
                .build();
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment);
        NavigationUI.setupActionBarWithNavController(this, navController, mAppBarConfiguration);
        NavigationUI.setupWithNavController(navView, navController);
        navView.setNavigationItemSelectedListener(this);
        init();
    }

    private void init() {
        checkPermissions(new Consumer<Boolean>() {
            @Override
            public void accept(Boolean aBoolean) throws Exception {
                if (!aBoolean) {
                    Constants.tendToSettings(MainActivity.this);
                }
            }
        });



    }


    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_new_text:
                actionNewText();
                break;
            case R.id.action_add_file:
                FileSelectionDialog dlg = new FileSelectionDialog( this, this, "json; yml" );
                dlg.show( new File( m_strInitialDir ) );
                break;
            case R.id.action_ping:
                testConnection();
                //ping();
                break;

        }
        return super.onOptionsItemSelected(item);
    }

    /*
    private void ping(){
        //new Thread(){
            //public void run(){
                 Toast.makeText(getApplicationContext(),"Ping result: "+Tool.ping(),Toast.LENGTH_SHORT).show();
          //  }
        //}.start();
    }*/


    private void actionNewText() {
        checkPermissions(aBoolean -> {
            if (!aBoolean) {
                Constants.tendToSettings(MainActivity.this);
                return;
            }
            startActivity(new Intent(MainActivity.this, IniEditActivity.class));
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment);
        return NavigationUI.navigateUp(navController, mAppBarConfiguration)
                || super.onSupportNavigateUp();
    }

    private void checkPermissions(Consumer<Boolean> consumer) {
        Disposable subscribe = new RxPermissions(this)
                .request(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.READ_PHONE_STATE)
                .subscribe(consumer);


    }


    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.logcat:
                startActivity(new Intent(this, LogcatActivity.class));
                return true;
            case R.id.about:
                showAbout();
                drawerLayout.close();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }



    private void showAbout() {
        String version;
        version = Version.version();
        new MaterialDialog.Builder(this)
                .title("TrojanCat")
                .content("Trojan-Go Client For Android\n" +
                        "Kernel version: " + version +"\n"+
                        "Version:1.0.1\n"+
                        "By: Jidoer"
                )
                .show();

    }


    private void copyRawResourceToDir(int resId, String destPathName, boolean override) {
        File file = new File(destPathName);
        if (override || !file.exists()) {
            try {
                try (InputStream is = getResources().openRawResource(resId);
                     FileOutputStream fos = new FileOutputStream(file)) {
                    byte[] buf = new byte[1024];
                    int len;
                    while ((len = is.read(buf)) > 0) {
                        fos.write(buf, 0, len);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


    //////////////////////////////////////////



    @Override
    public void onServiceConnected(final ITrojanService service) {
        LogHelper.i(TAG, "onServiceConnected");
        trojanService = service;
        Threads.instance().runOnWorkThread(new Task() {
            @Override
            public void onRun() {
                try {
                    final int state = service.getState();
                    /*
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            updateViews(state);
                        }
                    });*/
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        });
    }


    @Override
    public void onServiceDisconnected() {
        LogHelper.i(TAG, "onServiceConnected");
        trojanService = null;
    }

    @Override
    public void onStateChanged(int state, String msg) {
        LogHelper.i(TAG, "onStateChanged# state: " + state + " msg: " + msg);
        //updateViews(state);
    }

    @Override
    public void onTestResult(final String testUrl, final boolean connected, final long delay, @NonNull final String error) {
        new Thread(){
            public void run(){
                Toast.makeText(getApplicationContext(),testUrl+connected+delay+error,Toast.LENGTH_LONG).show();
            }
        }.start();
        /*runOnUiThread(new Runnable() {
            @Override
            public void run() {
                showTestConnectionResult(testUrl, connected, delay, error);
            }
        });*/
    }

    @Override
    public void onBinderDied() {
        //Trojan Call back!
        LogHelper.i(TAG, "onBinderDied");
        connection.disconnect(getApplicationContext());
        // connect the new binder
        // todo is it necessary to re-connect?
        connection.connect(getApplicationContext(), this);
    }



    /////////---------------tool--------------
    private void testConnection() {
        ITrojanService service = trojanService;
        if (service == null) {
            showTestConnectionResult(CONNECTION_TEST_URL, false, 0L, "Trojan service is not available.");
        } else {
            try {
                service.testConnection(CONNECTION_TEST_URL);
            } catch (RemoteException e) {
                showTestConnectionResult(CONNECTION_TEST_URL, false, 0L, "Trojan service throws RemoteException.");
                e.printStackTrace();
            }
        }
    }
    private void showTestConnectionResult(String testUrl, boolean connected, long delay, @NonNull String error) {
        if (connected) {
            Toast.makeText(getApplicationContext(), getString(R.string.connected_to__in__ms,
                    testUrl, String.valueOf(delay)), Toast.LENGTH_LONG).show();
        } else {
            LogHelper.e(TAG, "TestError: " + error);
            Toast.makeText(getApplicationContext(),
                    getString(R.string.failed_to_connect_to__,
                            testUrl, error),
                    Toast.LENGTH_LONG).show();
        }
    }


    public void onFileSelect( File file )
    {
        //Toast.makeText( this, "File Selected : " + file.getPath(), Toast.LENGTH_SHORT ).show();
        m_strInitialDir = file.getParent();
        tool.SaveConfig(this,fileread.getFileContent(file));
    }


    @Override
    protected void onResume()
    {
        super.onResume();
        requestReadExternalStoragePermission();

    }


    private void requestReadExternalStoragePermission()
    {
        if( PackageManager.PERMISSION_GRANTED == ContextCompat.checkSelfPermission( this, Manifest.permission.READ_EXTERNAL_STORAGE ) )
        {

            return;
        }
        //权限请求
        ActivityCompat.requestPermissions( this,
                new String[]{ Manifest.permission.READ_EXTERNAL_STORAGE },
                REQUEST_PERMISSION_READ_EXTERNAL_STORAGE );
    }

    // パーミッション要求ダイアログの操作結果
    @Override
    public void onRequestPermissionsResult( int requestCode, String[] permissions, int[] grantResults )
    {
        switch( requestCode )
        {
            case REQUEST_PERMISSION_READ_EXTERNAL_STORAGE:
                if( grantResults.length == 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED )
                {
                    // 許可されなかった場合
                    Toast.makeText( this, "Permission denied.", Toast.LENGTH_SHORT ).show();
                    finish();    // アプリ終了宣言
                    return;
                }
                break;
            default:
                break;
        }
    }

    /*
    public static void UpdateList() {
        Log.e(TAG,"UpdateList");
        //recyclerView.setAdapter(listAdapter);
        refreshView.setOnRefreshListener(this::setData);

    }
    */

}
