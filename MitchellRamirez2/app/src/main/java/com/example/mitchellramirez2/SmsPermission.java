package com.example.mitchellramirez2;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import java.text.SimpleDateFormat;
import java.util.Locale;


public class SmsPermission extends AppCompatActivity {
    private TextView tvPerMissionStatus, tvLastNotification;
    private SharedPreferences sharedPreferences;

    private float goalWeight;
    private String username;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sms_permission);

        tvPerMissionStatus = findViewById(R.id.tvPermissionStatus);
        tvLastNotification = findViewById(R.id.tvLastNotification);
        Button btnAllowSMS = findViewById(R.id.btnAllowSMS);
        Button btnDenySMS = findViewById(R.id.btnDenySMS);
        Button btnBackToTracker = findViewById(R.id.btnBackToTracker);

        sharedPreferences = getSharedPreferences("SMSNotification", MODE_PRIVATE);

        if (getIntent() != null) {
            goalWeight = getIntent().getFloatExtra("goal_weight", 0f);
            username = getIntent().getStringExtra("username");
        }
        updateNotificationStatusUI();

        //Enabling notifications by gathering the user's phone number
        btnAllowSMS.setOnClickListener(v -> promptForPhoneNumberAndSend());

        //Opting out of notifications
        btnDenySMS.setOnClickListener(v -> {
            sharedPreferences.edit()
                    .putBoolean("sms_notification_enabled_" + username, false)
                    .apply();
            updateNotificationStatusUI();
            Toast.makeText(this, "SMS notifications are disabled. The app will continue to function", Toast.LENGTH_LONG).show();
        });

        //Navigating back to the tracking screen
        btnBackToTracker.setOnClickListener(v -> {
           Intent intent = new Intent(SmsPermission.this, WeightTracking.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
           startActivity(intent);
           finish();
        });
    }

    private void promptForPhoneNumberAndSend() {
        String savedNumber = sharedPreferences.getString("user_phone_" + username, null);
        if (savedNumber != null && !savedNumber.isEmpty()) {
            sendGoalAchievedSmsViaIntent(savedNumber);
        } else {
            showPhoneNumberDialog();
        }
    }

    private void showPhoneNumberDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Phone number is required");
        builder.setMessage("Please enter your number to receive SMS notifications");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_PHONE);
        builder.setView(input);

        builder.setPositiveButton("Save and send", (dialog, which) -> {
            String phoneNumber = input.getText().toString().trim();
            if (!phoneNumber.isEmpty()) {
                sharedPreferences.edit()
                        .putString("user_phone_" + username, phoneNumber)
                        .putBoolean("sms_notification_enabled_" + username, true)
                        .apply();
                sendGoalAchievedSmsViaIntent(phoneNumber);
            } else {
                Toast.makeText(this, "Phone number cannot be empty", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    @SuppressLint("QueryPermissionsNeeded")
    private void sendGoalAchievedSmsViaIntent(String phoneNumber) {
        String displayUsername = (username != null) ? username : "";
        String message = getString(R.string.sms_goal_message, displayUsername, goalWeight);

        Intent smsIntent = new Intent(Intent.ACTION_SENDTO);
        smsIntent.setData(Uri.parse("smsto: " + phoneNumber));
        smsIntent.putExtra("sms_body", message);

        String timestamp = String.valueOf(new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()));
        sharedPreferences.edit()
                .putString("last_notification_" + username, timestamp)
                .apply();
        updateNotificationStatusUI();

        if (smsIntent.resolveActivity(getPackageManager()) != null) {
            startActivity(smsIntent);
        } else {
            Toast.makeText(this, "No SMS app available", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateNotificationStatusUI() {
        boolean isEnabled = sharedPreferences.getBoolean("sms_notification_enabled_" + username, false);
        String lastNotification = sharedPreferences.getString("last_notification_" + username, "None");

        if (tvPerMissionStatus != null) {
            String statusText = isEnabled ? getString(R.string.sms_enabled) : getString(R.string.sms_disabled);
            tvPerMissionStatus.setText(getString(R.string.sms_status, statusText));
        }
        if (tvLastNotification != null) {
            tvLastNotification.setText(getString(R.string.last_notification_sent, lastNotification));
        }
    }
}
