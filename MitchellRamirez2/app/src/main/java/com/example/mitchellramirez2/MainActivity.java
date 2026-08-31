package com.example.mitchellramirez2;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

// ADD THIS IMPORT
import com.google.android.material.textfield.TextInputEditText;

public class MainActivity extends AppCompatActivity {
    private TextInputEditText etUsername, etPassword;
    private Button btnLogin, btnCreateAccount;
    private TextView tvStatusMessage;
    private SharedPreferences sharedPreferences;
    private DatabaseHelper databaseHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // These will now map perfectly to your updated XML views without crashing
        etUsername = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        btnCreateAccount = findViewById(R.id.btnCreateAccount);
        tvStatusMessage = findViewById(R.id.tvStatusMessage);

        databaseHelper = new DatabaseHelper(this);
        sharedPreferences = getSharedPreferences("WeightTrackerData", Context.MODE_PRIVATE);

        btnLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loginUser();
            }
        });

        btnCreateAccount.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                createAccount();
            }
        });
    }

    private void loginUser() {
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (username.isEmpty() || password.isEmpty()) {
            showStatusMessage("Please enter both username and password", true);
            return;
        }

        if (databaseHelper.authenticateUser(username, password)) {
            showStatusMessage("Welcome back, " + username + "!", false);
            Toast.makeText(this, "Login successful!", Toast.LENGTH_SHORT).show();

            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString("current_user", username);
            editor.apply();

            etPassword.setText("");

            Intent intent = new Intent(MainActivity.this, WeightTracking.class);
            startActivity(intent);
        } else {
            showStatusMessage("Invalid username or password", true);
        }
    }

    private void createAccount() {
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (username.isEmpty() || password.isEmpty()) {
            showStatusMessage("Please enter both username and password", true);
            return;
        }

        if (password.length() < 4) {
            showStatusMessage("Password must be at least 4 characters long", true);
            return;
        }

        if (databaseHelper.checkUserExists(username)) {
            showStatusMessage("Username already exists. Please log in instead.", true);
        } else {
            if (databaseHelper.createUser(username, password)) {
                showStatusMessage("Account created successfully! You can now log in.", false);
                Toast.makeText(this, "Account created!", Toast.LENGTH_SHORT).show();
                etPassword.setText("");
            } else {
                showStatusMessage("Account creation failed.", true);
            }
        }
    }

    private void showStatusMessage(String message, boolean isError) {
        tvStatusMessage.setText(message);
        tvStatusMessage.setVisibility(View.VISIBLE);

        if (isError) {
            tvStatusMessage.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
        } else {
            tvStatusMessage.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
        }

        tvStatusMessage.postDelayed(new Runnable() {
            @Override
            public void run() {
                tvStatusMessage.setVisibility(View.GONE);
            }
        }, 3000);
    }
}