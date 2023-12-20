package com.car.trojango.ui;

import static android.net.ConnectivityManager.TYPE_WIFI;
import static androidx.constraintlayout.widget.Constraints.TAG;

import android.Manifest;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.RemoteException;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import com.afollestad.materialdialogs.MaterialDialog;
import com.car.Globals;
import com.car.LogHelper;
import com.car.ProxyConfig;
import com.car.ProxyFileHelper;
import com.car.naive.connection.TrojanConnection;
import com.car.naive.proxy.aidl.ITrojanService;
import com.car.trojango.Constants;
import com.car.trojango.R;
import com.car.trojango.adapter.FileListAdapter;
import com.car.trojango.tool.FileSelectionDialog;
import com.car.trojango.tool.IPUtils;
import com.car.trojango.tool.tool;
import com.google.android.material.navigation.NavigationView;
import com.kangc.fileread;
import com.tbruyelle.rxpermissions2.RxPermissions;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

import butterknife.BindView;
import butterknife.ButterKnife;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import version.Version;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener ,FileSelectionDialog.OnFileSelectListener{

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


        Globals.Init(MainActivity.this);

        copyRawResourceToDir(R.raw.cacert, Globals.getCaCertPath(), true);
        copyRawResourceToDir(R.raw.country, Globals.getCountryMmdbPath(), true);
        copyRawResourceToDir(R.raw.clash_config, Globals.getClashConfigPath(), false);
        File config = new File(Globals.getProxyConfigPath());
        if(!config.exists()){
            //Just Debug!
            copyRawResourceToDir(R.raw.config, Globals.getProxyConfigPath(), true);
        }

        Log.e("MainActivity","Config:"+Globals.getProxyConfigPath());

        ProxyConfig cacheConfig = ProxyFileHelper.readConfig(Globals.getProxyConfigPath());
        if (cacheConfig == null) {
            LogHelper.e(TAG, "read null config");
        } else {
            Globals.setProxyConfigInstance(cacheConfig);
        }
        if (!Globals.getProxyConfigInstance().isValidRunningConfig()) {
            LogHelper.e(TAG, "Invalid config!");
        }


        File file = new File(Globals.getProxyConfigPath());
        if (file.exists()) {
            try {
                try (FileInputStream fis = new FileInputStream(file)) {
                    byte[] content = new byte[(int) file.length()];
                    fis.read(content);
                    String contentStr = new String(content);
                    ProxyConfig ins = Globals.getProxyConfigInstance();
                    ins.fromJSON(contentStr);

                    //configText.setText(ins.getConfig());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }



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
                FileSelectionDialog dlg = new FileSelectionDialog( this, this, "json; yml; txt" );
                dlg.show( new File( m_strInitialDir ) );
                break;
            case R.id.action_ping:
                HomeFragment.instance.testConnection();
                break;
        }
        return super.onOptionsItemSelected(item);
    }



    public void onFileSelect( File file )
    {
        //Toast.makeText( this, "File Selected : " + file.getPath(), Toast.LENGTH_SHORT ).show();
        m_strInitialDir = file.getParent();
        tool.SaveConfig(this, fileread.getFileContent(file));
    }


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
                .request(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE)
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
                        "Kernel version: v0.10.6(" + version +")\n"+
                        "Version:1.0.2\n"+
                        "By: Jidoer\n"+
                        "-----------------\n"+
                        "IP Address:"+getLocalIpAddress()
                )
                .show();

    }

    // 获取ip地址
    private String getLocalIpAddress() {
        return IPUtils.getIPAddress(true);
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


}
