package com.example.mitchellramirez2;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.example.mitchellramirez2.R;
import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.telephony.SmsManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class SmsPermission extends AppCompatActivity {

    private static final int SMS_PERMISSION_CODE = 100;
    private TextView tvPermissionStatus, tvLastNotification;
    private Button btnAllowSMS, btnDenySMS, btnBackToTracker;
    private SharedPreferences sharedPreferences;
    private float goalWeight;
    private float currentWeight;
    private String username;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sms_permission);

        tvPermissionStatus = findViewById(R.id.tvPermissionStatus);
        tvLastNotification = findViewById(R.id.tvLastNotification);
        btnAllowSMS = findViewById(R.id.btnAllowSMS);
        btnDenySMS = findViewById(R.id.btnDenySMS);
        btnBackToTracker = findViewById(R.id.btnBackToTracker);

        sharedPreferences = getSharedPreferences("SMSNotifications", MODE_PRIVATE);

        if (getIntent() != null) {
            goalWeight = getIntent().getFloatExtra("goal_weight", 0f);
            currentWeight = getIntent().getFloatExtra("current_weight", 0f);
            username = getIntent().getStringExtra("username");
        }

        updatePermissionStatus();

        btnAllowSMS.setOnClickListener(v -> requestSmsPermission());

        btnDenySMS.setOnClickListener(v -> {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean("sms_permission_granted_" + username, false);
            editor.apply();
            updatePermissionStatus();
            Toast.makeText(this, "SMS notifications disabled. App will continue to function.", Toast.LENGTH_LONG).show();
        });

        btnBackToTracker.setOnClickListener(v -> {
            Intent intent = new Intent(SmsPermission.this, WeightTracking.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });
    }

    private void requestSmsPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.SEND_SMS},
                        SMS_PERMISSION_CODE);
            } else {
                permissionGranted();
            }
        } else {
            permissionGranted();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == SMS_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                permissionGranted();
            } else {
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putBoolean("sms_permission_granted_" + username, false);
                editor.apply();
                updatePermissionStatus();
                Toast.makeText(this, "SMS permission denied. Notifications will not be sent.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void permissionGranted() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean("sms_permission_granted_" + username, true);
        editor.apply();
        updatePermissionStatus();
        sendGoalAchievedSms();
    }

    private void sendGoalAchievedSms() {
        String phoneNumber = getPhoneNumberFromUser();

        if (phoneNumber == null || phoneNumber.isEmpty()) {
            Toast.makeText(this, "Please set your phone number in settings", Toast.LENGTH_LONG).show();
            return;
        }

        String message = "🎉 CONGRATULATIONS " + username + "! 🎉\n\n" +
                "You've reached your goal weight of " + goalWeight + " lbs!\n" +
                "Current weight: " + currentWeight + " lbs\n\n" +
                "Keep up the great work on your fitness journey! 💪";

        try {
            SmsManager smsManager = SmsManager.getDefault();
            smsManager.sendTextMessage(phoneNumber, null, message, null, null);

            String lastNotifTime = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
                    .format(new java.util.Date());
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString("last_notification_" + username, lastNotifTime);
            editor.apply();
            updateLastNotificationTime(lastNotifTime);

            Toast.makeText(this, "Goal achievement SMS sent! 🎉", Toast.LENGTH_LONG).show();

        } catch (Exception e) {
            Toast.makeText(this, "Failed to send SMS: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private String getPhoneNumberFromUser() {
        String savedNumber = sharedPreferences.getString("user_phone_" + username, null);
        if (savedNumber == null) {
            android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
            builder.setTitle("Phone Number Required");
            builder.setMessage("To receive SMS notifications, please enter your phone number:");

            final android.widget.EditText input = new android.widget.EditText(this);
            input.setHint("Enter phone number (e.g., 5551234567)");
            builder.setView(input);

            builder.setPositiveButton("OK", (dialog, which) -> {
                String phoneNumber = input.getText().toString().trim();
                if (!phoneNumber.isEmpty()) {
                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.putString("user_phone_" + username, phoneNumber);
                    editor.apply();
                    sendGoalAchievedSms();
                } else {
                    Toast.makeText(this, "Phone number required for SMS", Toast.LENGTH_SHORT).show();
                }
            });

            builder.setNegativeButton("Cancel", (dialog, which) -> {
                Toast.makeText(this, "SMS not sent. You can enable later in settings.", Toast.LENGTH_SHORT).show();
            });

            builder.show();
            return null;
        }
        return savedNumber;
    }

    private void updatePermissionStatus() {
        boolean hasPermission = sharedPreferences.getBoolean("sms_permission_granted_" + username, false);
        if (hasPermission) {
            tvPermissionStatus.setText("✅ SMS permission granted");
            tvPermissionStatus.setTextColor(getColor(android.R.color.holo_green_dark));
        } else {
            tvPermissionStatus.setText("❌ SMS permission not granted");
            tvPermissionStatus.setTextColor(getColor(android.R.color.holo_red_dark));
        }
        updateLastNotificationTime(sharedPreferences.getString("last_notification_" + username, null));
    }

    private void updateLastNotificationTime(String time) {
        if (time != null) {
            tvLastNotification.setText("Last notification sent: " + time);
        } else {
            tvLastNotification.setText("No notifications sent yet");
        }
    }
}