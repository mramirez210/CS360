package com.example.mitchellramirez2;

import com.example.mitchellramirez2.R;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.material.textfield.TextInputEditText;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class WeightTracking extends AppCompatActivity {

    private TextView tvCurrentUser, tvGoalWeight;
    private LinearLayout gridContainer;
    private Button btnAddEntry, btnLogout, btnSetGoal;
    private SharedPreferences sharedPreferences;
    private String currentUsername;
    private DatabaseHelper databaseHelper;
    private float goalWeight = 0f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_weight_tracking);

        tvCurrentUser = findViewById(R.id.tvCurrentUser);
        tvGoalWeight = findViewById(R.id.tvGoalWeight);
        gridContainer = findViewById(R.id.gridContainer);
        btnAddEntry = findViewById(R.id.btnAddEntry);
        btnLogout = findViewById(R.id.btnLogout);
        btnSetGoal = findViewById(R.id.btnSetGoal);

        databaseHelper = new DatabaseHelper(this);
        sharedPreferences = getSharedPreferences("WeightTrackerData", Context.MODE_PRIVATE);
        currentUsername = sharedPreferences.getString("current_user", "Guest");
        tvCurrentUser.setText("Logged in as: " + currentUsername);

        loadGoalWeight();
        refreshGrid();

        btnAddEntry.setOnClickListener(v -> showAddEntryDialog());
        btnSetGoal.setOnClickListener(v -> showSetGoalDialog());

        btnLogout.setOnClickListener(v -> {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.remove("current_user");
            editor.apply();
            finish();
        });
    }

    private void loadGoalWeight() {
        goalWeight = databaseHelper.getGoalWeight(currentUsername);
        if (goalWeight > 0) {
            tvGoalWeight.setText("Goal Weight: " + goalWeight + " lbs");
        } else {
            tvGoalWeight.setText("Goal Weight: Not set");
        }
    }

    private void refreshGrid() {
        gridContainer.removeAllViews();
        Cursor cursor = databaseHelper.getWeightEntries(currentUsername);

        if (cursor.getCount() == 0) {
            TextView emptyText = new TextView(this);
            emptyText.setText("No weight entries yet. Tap + ADD ENTRY to get started!");
            emptyText.setPadding(16, 32, 16, 32);
            emptyText.setTextColor(0xFF999999);
            emptyText.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            gridContainer.addView(emptyText);
            cursor.close();
            return;
        }

        while (cursor.moveToNext()) {
            int id = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_ENTRY_ID));
            String date = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_DATE));
            float weight = cursor.getFloat(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_WEIGHT));

            View rowView = LayoutInflater.from(this).inflate(R.layout.row_weight_entry, null);

            TextView tvDate = rowView.findViewById(R.id.rowTvDate);
            TextView tvWeight = rowView.findViewById(R.id.rowTvWeight);
            Button btnDelete = rowView.findViewById(R.id.rowBtnDelete);

            tvDate.setText(date);
            tvWeight.setText(String.format(Locale.US, "%.1f lbs", weight));

            btnDelete.setOnClickListener(v -> {
                databaseHelper.deleteWeightEntry(id);
                refreshGrid();
                Toast.makeText(this, "Entry deleted", Toast.LENGTH_SHORT).show();
            });

            rowView.setOnLongClickListener(v -> {
                showUpdateEntryDialog(id, date, weight);
                return true;
            });

            gridContainer.addView(rowView);

            View divider = new View(this);
            divider.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1));
            divider.setBackgroundColor(0xFFDDDDDD);
            gridContainer.addView(divider);
        }
        cursor.close();
    }

    private void showAddEntryDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_entry, null);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();

        TextInputEditText etDate = dialogView.findViewById(R.id.dialogEtDate);
        TextInputEditText etWeight = dialogView.findViewById(R.id.dialogEtWeight);
        Button btnCancel = dialogView.findViewById(R.id.dialogBtnCancel);
        Button btnSave = dialogView.findViewById(R.id.dialogBtnSave);

        String currentDate = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        etDate.setText(currentDate);

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnSave.setOnClickListener(v -> {
            String date = etDate.getText().toString().trim();
            String weightStr = etWeight.getText().toString().trim();

            if (date.isEmpty() || weightStr.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                float weight = Float.parseFloat(weightStr);
                if (weight <= 0) throw new NumberFormatException();

                databaseHelper.addWeightEntry(currentUsername, date, weight);
                refreshGrid();
                checkGoalAchieved(weight);
                Toast.makeText(this, "Entry added", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Please enter a valid weight", Toast.LENGTH_SHORT).show();
            }
        });

        dialog.show();
    }

    private void showUpdateEntryDialog(int id, String currentDate, float currentWeightValue) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_entry, null);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();

        TextInputEditText etDate = dialogView.findViewById(R.id.dialogEtDate);
        TextInputEditText etWeight = dialogView.findViewById(R.id.dialogEtWeight);
        Button btnCancel = dialogView.findViewById(R.id.dialogBtnCancel);
        Button btnSave = dialogView.findViewById(R.id.dialogBtnSave);

        etDate.setText(currentDate);
        etWeight.setText(String.valueOf(currentWeightValue));

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnSave.setOnClickListener(v -> {
            String date = etDate.getText().toString().trim();
            String weightStr = etWeight.getText().toString().trim();

            if (date.isEmpty() || weightStr.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                float weight = Float.parseFloat(weightStr);
                if (weight <= 0) throw new NumberFormatException();

                databaseHelper.updateWeightEntry(id, date, weight);
                refreshGrid();
                checkGoalAchieved(weight);
                Toast.makeText(this, "Entry updated", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Please enter a valid weight", Toast.LENGTH_SHORT).show();
            }
        });

        dialog.show();
    }

    private void showSetGoalDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_set_goal, null);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();

        TextInputEditText etGoalWeight = dialogView.findViewById(R.id.dialogEtGoalWeight);
        Button btnCancel = dialogView.findViewById(R.id.dialogBtnCancel);
        Button btnSave = dialogView.findViewById(R.id.dialogBtnSave);

        if (goalWeight > 0) {
            etGoalWeight.setText(String.valueOf(goalWeight));
        }

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnSave.setOnClickListener(v -> {
            String weightStr = etGoalWeight.getText().toString().trim();

            if (weightStr.isEmpty()) {
                Toast.makeText(this, "Please enter a goal weight", Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                goalWeight = Float.parseFloat(weightStr);
                if (goalWeight <= 0) throw new NumberFormatException();

                databaseHelper.updateGoalWeight(currentUsername, goalWeight);
                tvGoalWeight.setText("Goal Weight: " + goalWeight + " lbs");
                Toast.makeText(this, "Goal weight set!", Toast.LENGTH_SHORT).show();
                dialog.dismiss();

                checkAllEntriesForGoal();
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Please enter a valid weight", Toast.LENGTH_SHORT).show();
            }
        });

        dialog.show();
    }

    private void checkGoalAchieved(float currentWeight) {
        if (goalWeight > 0 && currentWeight <= goalWeight) {
            Intent smsIntent = new Intent(this, SmsPermission.class);
            smsIntent.putExtra("goal_weight", goalWeight);
            smsIntent.putExtra("current_weight", currentWeight);
            smsIntent.putExtra("username", currentUsername);
            startActivity(smsIntent);
        }
    }

    private void checkAllEntriesForGoal() {
        Cursor cursor = databaseHelper.getWeightEntries(currentUsername);
        while (cursor.moveToNext()) {
            float weight = cursor.getFloat(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_WEIGHT));
            if (weight <= goalWeight) {
                Intent smsIntent = new Intent(this, SmsPermission.class);
                smsIntent.putExtra("goal_weight", goalWeight);
                smsIntent.putExtra("current_weight", weight);
                smsIntent.putExtra("username", currentUsername);
                startActivity(smsIntent);
                break;
            }
        }
        cursor.close();
    }
}