package com.example.mitchellramirez2;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.LimitLine;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.google.android.material.textfield.TextInputEditText;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class WeightTracking extends AppCompatActivity {

    private TextView tvGoalWeight;
    private LinearLayout gridContainer;
    private LineChart weightChart;

    private SharedPreferences sharedPreferences;
    private String currentUsername;
    private DatabaseHelper databaseHelper;
    private float goalWeight = 0f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_weight_tracking);

        TextView tvCurrentUser = findViewById(R.id.tvCurrentUser);
        tvGoalWeight = findViewById(R.id.tvGoalWeight);
        gridContainer = findViewById(R.id.gridContainer);
        weightChart = findViewById(R.id.weightChart);

        Button btnAddEntry = findViewById(R.id.btnAddEntry);
        Button btnLogout = findViewById(R.id.btnLogout);
        Button btnSetGoal = findViewById(R.id.btnSetGoal);

        databaseHelper = DatabaseHelper.getInstance(this);
        sharedPreferences = getSharedPreferences("WeightTrackerData", Context.MODE_PRIVATE);
        currentUsername = sharedPreferences.getString("current_user", "Guest");

        tvCurrentUser.setText(getString(R.string.label_user_prefix, currentUsername));

        loadGoalWeight();
        refreshGridAndChart();

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
            tvGoalWeight.setText(getString(R.string.label_goal_prefix, goalWeight));
        } else {
            tvGoalWeight.setText(getString(R.string.label_goal_not_set));
        }
    }

    @SuppressLint("SetTextI18n")
    private void refreshGridAndChart() {
        gridContainer.removeAllViews();
        android.util.Log.d("WeightTracking", "Loading entries for user: " + currentUsername);

        Cursor cursor = databaseHelper.getWeightEntries(currentUsername);
        List<Entry> chartEntries = new ArrayList<>();

        if (cursor == null || cursor.getCount() == 0) {
            TextView emptyText = new TextView(this);
            emptyText.setText("No weight entries yet for " + currentUsername + ". Tap + ADD ENTRY to get started!");
            emptyText.setPadding(16, 32, 16, 32);
            emptyText.setTextColor(0xFF999999);
            emptyText.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            gridContainer.addView(emptyText);

            updateChart(chartEntries);
            if (cursor != null) cursor.close();
            return;
        }

        int index = 1;
        while (cursor.moveToNext()) {
            int id = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_ENTRY_ID));
            String date = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_DATE));
            float weight = cursor.getFloat(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_WEIGHT));

            chartEntries.add(new Entry(index++, weight));

            @SuppressLint("InflateParams") View rowView = LayoutInflater.from(this).inflate(R.layout.row_weight_entry, null);

            TextView tvDate = rowView.findViewById(R.id.rowTvDate);
            TextView tvWeight = rowView.findViewById(R.id.rowTvWeight);
            Button btnDelete = rowView.findViewById(R.id.rowBtnDelete);

            tvDate.setText(date);
            tvWeight.setText(String.format(Locale.US, "%.1f lbs", weight));

            btnDelete.setOnClickListener(v -> {
                databaseHelper.deleteWeightEntry(id);
                refreshGridAndChart();
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

        updateChart(chartEntries);
    }

    private void updateChart(List<Entry> chartEntries) {
        if (weightChart == null) return;

        weightChart.getAxisLeft().removeAllLimitLines();
        if (goalWeight > 0) {
            LimitLine goalLine = new LimitLine(goalWeight, "Goal (" + goalWeight + " lbs)");
            goalLine.setLineWidth(2f);
            goalLine.setLineColor(Color.RED);
            goalLine.setTextColor(Color.RED);
            goalLine.setTextSize(12f);
            weightChart.getAxisLeft().addLimitLine(goalLine);
        }

        if (chartEntries.isEmpty()) {
            weightChart.clear();
            weightChart.setNoDataText("No weight records to display");
            weightChart.invalidate();
            return;
        }

        LineDataSet dataSet = new LineDataSet(chartEntries, "Weight progress");
        dataSet.setColor(Color.BLUE);
        dataSet.setLineWidth(2.5f);
        dataSet.setCircleColor(Color.BLUE);
        dataSet.setCircleRadius(5f);
        dataSet.setDrawValues(true);
        dataSet.setValueTextSize(10f);

        LineData lineData = new LineData(dataSet);
        weightChart.setData(lineData);

        weightChart.getDescription().setEnabled(false);
        weightChart.getAxisRight().setEnabled(false);
        weightChart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
        weightChart.getXAxis().setGranularity(1f);

        weightChart.notifyDataSetChanged();
        weightChart.invalidate();
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
            String date = Objects.requireNonNull(etDate.getText()).toString().trim();
            String weightStr = Objects.requireNonNull(etWeight.getText()).toString().trim().replace(",", ".");

            if (date.isEmpty() || weightStr.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                float weight = Float.parseFloat(weightStr);
                if (weight <= 0) throw new NumberFormatException();

                databaseHelper.addWeightEntry(currentUsername, date, weight);
                refreshGridAndChart();
                checkGoalAchieved(weight);
                Toast.makeText(this, "Entry added", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Please enter a valid weight number (e.g., 175.5)", Toast.LENGTH_SHORT).show();
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
            String date = Objects.requireNonNull(etDate.getText()).toString().trim();
            String weightStr = Objects.requireNonNull(etWeight.getText()).toString().trim().replace(",", ".");

            if (date.isEmpty() || weightStr.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                float weight = Float.parseFloat(weightStr);
                if (weight <= 0) throw new NumberFormatException();

                databaseHelper.updateWeightEntry(id, date, weight);
                refreshGridAndChart();
                checkGoalAchieved(weight);
                Toast.makeText(this, "Entry updated", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Please enter a valid weight number (e.g., 175.5)", Toast.LENGTH_SHORT).show();
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
            String weightStr = Objects.requireNonNull(etGoalWeight.getText()).toString().trim().replace(",", ".");

            if (weightStr.isEmpty()) {
                Toast.makeText(this, "Please enter a goal weight", Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                goalWeight = Float.parseFloat(weightStr);
                if (goalWeight <= 0) throw new NumberFormatException();

                databaseHelper.updateGoalWeight(currentUsername, goalWeight);
                tvGoalWeight.setText(getString(R.string.label_goal_prefix, goalWeight));
                Toast.makeText(this, "Goal weight set!", Toast.LENGTH_SHORT).show();
                dialog.dismiss();

                refreshGridAndChart();
                checkAllEntriesForGoal();
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Please enter a valid weight number (e.g., 175.5)", Toast.LENGTH_SHORT).show();
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
        try (Cursor cursor = databaseHelper.getWeightEntries(currentUsername)) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    float weight = cursor.getFloat(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_WEIGHT));
                    if (goalWeight > 0 && weight <= goalWeight) {
                        Intent smsIntent = new Intent(this, SmsPermission.class);
                        smsIntent.putExtra("goal_weight", goalWeight);
                        smsIntent.putExtra("current_weight", weight);
                        smsIntent.putExtra("username", currentUsername);
                        startActivity(smsIntent);
                        break;
                    }
                }
            }
        }
    }
}