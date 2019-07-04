package me.leefeng.android_zip4j;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.Toast;

import java.io.File;

import me.leefeng.zip4j.core.ZipFile;
import me.leefeng.zip4j.exception.ZipException;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED){
                requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},8000);
            }else{
                fun();
            }
        }else{
            fun();
        }

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == 8000){
            if (grantResults.length>0){
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED){
                    fun();
                }else{
                    Toast.makeText(this,"您拒绝了权限",Toast.LENGTH_LONG).show();
                }
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private void fun(){
        new Thread(){
            @Override
            public void run() {
                try {
                    File file = new File(Environment.getExternalStorageDirectory(),"PutaoAbc/course/1_15_1/zip");
                    ZipFile zip = new ZipFile(new File(file.getAbsolutePath(),"resource.zip"));
                    zip.setFileNameCharset("GBK");
                    zip.extractAll(file.getAbsolutePath());
                } catch (ZipException e) {
                    e.printStackTrace();
                }
            }
        }.run();
    }
}
