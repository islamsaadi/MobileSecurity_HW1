package com.islam.mobilesecurityhw1;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;


public class FinalPermissionActivity extends AppCompatActivity {

    private TextView finalPermission_LBL_instructions;
    private Button finalPermission_BTN_settings;
    private Button finalPermission_BTN_exit;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_final_permission);

        findViews();
        initViews();
    }

    private void findViews() {
        finalPermission_LBL_instructions = findViewById(R.id.finalPermission_LBL_instructions);
        finalPermission_BTN_settings = findViewById(R.id.finalPermission_BTN_settings);
        finalPermission_BTN_exit = findViewById(R.id.finalPermission_BTN_exit);
    }

    private void initViews() {
        finalPermission_LBL_instructions.setText("You have denied location permission multiple times.\n\n" +
                "Please go to Settings to grant \"Precise\" location permission manually.");

        finalPermission_BTN_settings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openAppSettings();
            }
        });

        finalPermission_BTN_exit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish(); // Close this screen
            }
        });
    }

    private void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", getPackageName(), null);
        intent.setData(uri);
        startActivity(intent);
    }
}
