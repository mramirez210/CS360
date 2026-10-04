package com.example.mitchellramirez2;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialException;

import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;

import java.util.concurrent.Executor;

public class MainActivity extends AppCompatActivity {
    private TextInputLayout tilUsername, tilPassword;
    private TextInputEditText etUsername, etPassword;
    private TextView tvStatusMessage;

    private SharedPreferences sharedPreferences;
    private DatabaseHelper databaseHelper;

    // Biometrics prompting
    private BiometricPrompt biometricPrompt;
    private BiometricPrompt.PromptInfo promptInfo;

    // Google Sign-In & Firebase Auth
    private CredentialManager credentialManager;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Check active session BEFORE layout inflation to prevent unnecessary UI rendering
        sharedPreferences = getSharedPreferences("WeightTrackerData", Context.MODE_PRIVATE);
        String currentUser = sharedPreferences.getString("current_user", null);
        if (currentUser != null && !currentUser.isEmpty() && !isBiometricAvailable()) {
            navigateToTracker();
            return;
        }

        setContentView(R.layout.activity_main);

        // Initialize Firebase Auth
        mAuth = FirebaseAuth.getInstance();

        // Initialize Views
        tilUsername = findViewById(R.id.tilUsername);
        tilPassword = findViewById(R.id.tilPassword);
        etUsername = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);
        tvStatusMessage = findViewById(R.id.tvStatusMessage);

        Button btnLogin = findViewById(R.id.btnLogin);
        Button btnCreateAccount = findViewById(R.id.btnCreateAccount);
        Button btnBiometricLogin = findViewById(R.id.btnBiometricLogin);
        Button btnGoogleLogin = findViewById(R.id.btnGoogleLogin);

        // Initializing Helpers & Managers
        databaseHelper = new DatabaseHelper(this);
        credentialManager = CredentialManager.create(this);

        // Setting up biometrics
        setupBiometricPrompt();

        if (isBiometricAvailable()) {
            btnBiometricLogin.setVisibility(View.VISIBLE);
        } else {
            btnBiometricLogin.setVisibility(View.GONE);
        }

        // Click Listeners
        btnLogin.setOnClickListener(v -> loginUser());
        btnCreateAccount.setOnClickListener(v -> createAccount());
        btnBiometricLogin.setOnClickListener(v -> biometricPrompt.authenticate(promptInfo));
        btnGoogleLogin.setOnClickListener(v -> performGoogleLogin());

        // Prompt biometric if session exists and biometrics are supported
        if (currentUser != null && !currentUser.isEmpty() && isBiometricAvailable()) {
            btnBiometricLogin.post(() -> biometricPrompt.authenticate(promptInfo));
        }
    }

    private void performGoogleLogin() {
        GetGoogleIdOption googleIdOption = new GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(getString(R.string.default_web_client_id))
                .build();

        GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build();

        credentialManager.getCredentialAsync(
                this,
                request,
                null,
                ContextCompat.getMainExecutor(this),
                new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override
                    public void onResult(GetCredentialResponse result) {
                        if (result.getCredential() instanceof CustomCredential customCredential) {
                            if (GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(customCredential.getType())) {
                                try {
                                    GoogleIdTokenCredential googleIdTokenCredential = GoogleIdTokenCredential.createFrom(customCredential.getData());
                                    String googleIdToken = googleIdTokenCredential.getIdToken();
                                    firebaseAuthWithGoogle(googleIdToken);
                                } catch (Exception e) {
                                    showStatusMessage("Google Login failed parsing credentials", true);
                                }
                            }
                        } else {
                            showStatusMessage("Unsupported credential type returned", true);
                        }
                    }

                    @Override
                    public void onError(@NonNull GetCredentialException e) {
                        showStatusMessage("Google login error: " + e.getLocalizedMessage(), true);
                    }
                }
        );
    }

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            String rawIdentifier = user.getEmail() != null ? user.getEmail() : user.getUid();
                            String userIdentifier = rawIdentifier.trim().toLowerCase(java.util.Locale.US);

                            if (!databaseHelper.checkUserExists(userIdentifier)) {
                                if (databaseHelper.createUser(userIdentifier, new char[0])) {
                                    String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(new java.util.Date());
                                    databaseHelper.addWeightEntry(userIdentifier, today, 180.0f);
                                    databaseHelper.updateGoalWeight(userIdentifier, 175.0f);
                                }
                            }

                            sharedPreferences.edit()
                                    .putString("current_user", userIdentifier)
                                    .apply();

                            Toast.makeText(MainActivity.this, "Firebase Sign-in successful!", Toast.LENGTH_SHORT).show();
                            navigateToTracker();
                        }
                    } else {
                        String errorMessage = task.getException() != null ? task.getException().getLocalizedMessage() : "Authentication failed";
                        showStatusMessage("Firebase Authentication failed: " + errorMessage, true);
                    }
                });
    }

    private void setupBiometricPrompt() {
        Executor executor = ContextCompat.getMainExecutor(this);

        biometricPrompt = new BiometricPrompt(MainActivity.this, executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                showStatusMessage("Authentication error: " + errString, true);
            }

            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                Toast.makeText(MainActivity.this, "Biometric login successful!", Toast.LENGTH_SHORT).show();
                navigateToTracker();
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                showStatusMessage("Biometric authentication failed. Try again!", true);
            }
        });

        promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Login with your fingerprint")
                .setSubtitle("Confirm your identity to continue")
                .setAllowedAuthenticators(
                        BiometricManager.Authenticators.BIOMETRIC_STRONG |
                                BiometricManager.Authenticators.BIOMETRIC_WEAK |
                                BiometricManager.Authenticators.DEVICE_CREDENTIAL
                )
                .build();
    }

    private boolean isBiometricAvailable() {
        BiometricManager biometricManager = BiometricManager.from(this);
        int canAuthenticate = biometricManager.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG |
                        BiometricManager.Authenticators.BIOMETRIC_WEAK |
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
        );
        return canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS;
    }

    private void loginUser() {
        clearErrors();

        String username = getTrimmedText(etUsername);
        String password = getTrimmedText(etPassword);

        if (hasInvalidInputs(username, password)) {
            return;
        }

        if (databaseHelper.authenticateUser(username, password.toCharArray())) {
            showStatusMessage("Welcome back, " + username + "!", false);
            Toast.makeText(this, "Login Successful!", Toast.LENGTH_SHORT).show();

            sharedPreferences.edit()
                    .putString("current_user", username)
                    .apply();
            etPassword.setText("");
            navigateToTracker();
        } else {
            showStatusMessage("Invalid Username or Password", true);
        }
    }

    private void createAccount() {
        clearErrors();

        String username = getTrimmedText(etUsername);
        String password = getTrimmedText(etPassword);

        if (hasInvalidInputs(username, password)) {
            return;
        }

        if (password.length() < 12) {
            tilPassword.setError("Password must be at least 12 characters long");
            return;
        }
        if (databaseHelper.checkUserExists(username)) {
            tilUsername.setError("Username already exists. Please login.");
        } else {
            if (databaseHelper.createUser(username, password.toCharArray())) {
                showStatusMessage("Account creation successful! You can log in.", false);
                Toast.makeText(this, "Account created!", Toast.LENGTH_SHORT).show();
                etPassword.setText("");
            } else {
                showStatusMessage("Account creation failed. Please try again!", true);
            }
        }
    }

    private boolean hasInvalidInputs(String username, String password) {
        boolean hasError = false;

        if (username.isEmpty()) {
            tilUsername.setError("Username is required!");
            hasError = true;
        }
        if (password.isEmpty()) {
            tilPassword.setError("Password is required!");
            hasError = true;
        }
        return hasError;
    }

    private void clearErrors() {
        if (tilUsername != null) tilUsername.setError(null);
        if (tilPassword != null) tilPassword.setError(null);
    }

    private String getTrimmedText(TextInputEditText editText) {
        return (editText != null && editText.getText() != null) ? editText.getText().toString().trim() : "";
    }

    private void navigateToTracker() {
        Intent intent = new Intent(MainActivity.this, WeightTracking.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void showStatusMessage(String message, boolean isError) {
        if (tvStatusMessage == null) {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            return;
        }

        tvStatusMessage.setText(message);
        tvStatusMessage.setVisibility(View.VISIBLE);

        int colorRes = isError ? android.R.color.holo_red_dark : android.R.color.holo_green_dark;
        tvStatusMessage.setTextColor(ContextCompat.getColor(this, colorRes));

        tvStatusMessage.postDelayed(() -> {
            if (!isFinishing() && !isDestroyed() && tvStatusMessage != null) {
                tvStatusMessage.setVisibility(View.GONE);
            }
        }, 3000);
    }
}