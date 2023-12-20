package com.car.trojango.tool;


//import static com.car.trojango.ui.MainActivity.UpdateList;

import android.content.Intent;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.afollestad.materialdialogs.MaterialDialog;
import com.car.trojango.Constants;
import com.car.trojango.R;
import com.car.trojango.ui.LoadCat;


import java.io.File;
import java.io.FileWriter;

import io.reactivex.Observable;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

/**
 * Created by lenovo on 2021
 */

public class tool {

    public static void SaveConfig(android.content.Context ctx, String cntent) {
        File file = new File("");
        new MaterialDialog.Builder(ctx)
                .title(file == null ? R.string.titleInputFileName : R.string.titleInputFileName)
                .canceledOnTouchOutside(false)
                .autoDismiss(false)
                .negativeText(R.string.cancel)
                .onNegative((dialog, which) -> dialog.dismiss())
                .input("", file == null ? "" : file.getName(), false, new MaterialDialog.InputCallback() {
                    @Override
                    public void onInput(@NonNull MaterialDialog dialog, CharSequence input) {
                        String fileName = !input.toString().endsWith(Constants.INI_FILE_SUF) ? input + Constants.INI_FILE_SUF : input.toString();
                        saveFile(fileName, new Observer<File>() {
                            @Override
                            public void onSubscribe(Disposable d) {

                            }

                            @Override
                            public void onNext(File file) {
                                Toast.makeText(ctx.getApplicationContext(), R.string.tipSaveSuccess, Toast.LENGTH_SHORT).show();
                                dialog.dismiss();
                                Intent i = new Intent(ctx, LoadCat.class);
                                ctx.startActivity(i);
                                //UpdateList();
                                // finish();
                            }

                            @Override
                            public void onError(Throwable e) {
                                e.printStackTrace();
                                Toast.makeText(ctx, e.getMessage(), Toast.LENGTH_SHORT).show();
                            }

                            @Override
                            public void onComplete() {

                            }
                        }, ctx, cntent);

                    }
                }).show();
    }

    private static void saveFile(String fileName, Observer<File> observer, android.content.Context ctx, String cntent) {
        Toast.makeText(ctx.getApplicationContext(), "Config:\n"+cntent,Toast.LENGTH_LONG).show();
        Observable.create((ObservableOnSubscribe<File>) emitter -> {
            File file = new File(Constants.getIniFileParent(ctx), fileName);
            FileWriter writer = new FileWriter(file);
            writer.write(cntent);
            writer.close();
            emitter.onNext(file);
            emitter.onComplete();
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers
                        .mainThread())
                .subscribe(observer);
    }

}