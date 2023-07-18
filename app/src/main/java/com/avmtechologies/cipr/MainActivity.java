package com.avmtechologies.cipr;

import androidx.appcompat.app.AppCompatActivity;

import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;

import com.avmtechologies.cipr.fragments.CiprArFragment;

public class MainActivity extends AppCompatActivity {
    private static final double MIN_OPEN_GL_VERSION = 3.0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        String openGlVersion = activityManager.getDeviceConfigurationInfo().getGlEsVersion();

        if (Double.parseDouble(openGlVersion) >= MIN_OPEN_GL_VERSION) {
            getSupportFragmentManager().
                    beginTransaction()
                    .replace(R.id.fragmentContainer, new CiprArFragment())
                    .commit();
        } else {
            new AlertDialog.Builder(this)
                    .setTitle("Device is not supported")
                    .setMessage("OpenGL ES 3.0 or higher is required. The device is running OpenGL ES $openGlVersion.")
                    .setPositiveButton(android.R.string.ok, (dialogInterface, i) -> finish())
                    .show();
        }
    }
}