package com.example.mitchellramirez2;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "WeightTracker.db";
    private static final int DATABASE_VERSION = 1;

    public static final String TABLE_USERS = "users";
    public static final String COL_USER_ID = "id";
    public static final String COL_USERNAME = "username";
    public static final String COL_PASSWORD = "password";
    public static final String COL_GOAL_WEIGHT = "goal_weight";

    public static final String TABLE_WEIGHT = "weight_entries";
    public static final String COL_ENTRY_ID = "id";
    public static final String COL_WEIGHT_USER = "username";
    public static final String COL_DATE = "date";
    public static final String COL_WEIGHT = "weight";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createUsersTable = "CREATE TABLE " + TABLE_USERS + " (" +
                COL_USER_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_USERNAME + " TEXT UNIQUE, " +
                COL_PASSWORD + " TEXT, " +
                COL_GOAL_WEIGHT + " REAL DEFAULT 0)";
        db.execSQL(createUsersTable);

        String createWeightTable = "CREATE TABLE " + TABLE_WEIGHT + " (" +
                COL_ENTRY_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_WEIGHT_USER + " TEXT, " +
                COL_DATE + " TEXT, " +
                COL_WEIGHT + " REAL, " +
                "FOREIGN KEY(" + COL_WEIGHT_USER + ") REFERENCES " + TABLE_USERS + "(" + COL_USERNAME + "))";
        db.execSQL(createWeightTable);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_WEIGHT);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_USERS);
        onCreate(db);
    }

    public boolean createUser(String username, String password) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_USERNAME, username);
        values.put(COL_PASSWORD, password);
        long result = db.insert(TABLE_USERS, null, values);
        return result != -1;
    }

    public boolean checkUserExists(String username) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_USERS, new String[]{COL_USERNAME}, COL_USERNAME + "=?", new String[]{username}, null, null, null);
        boolean exists = cursor.getCount() > 0;
        cursor.close();
        return exists;
    }

    public boolean authenticateUser(String username, String password) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_USERS, new String[]{COL_USERNAME}, COL_USERNAME + "=? AND " + COL_PASSWORD + "=?", new String[]{username, password}, null, null, null);
        boolean authenticated = cursor.getCount() > 0;
        cursor.close();
        return authenticated;
    }

    public void updateGoalWeight(String username, float goalWeight) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_GOAL_WEIGHT, goalWeight);
        db.update(TABLE_USERS, values, COL_USERNAME + "=?", new String[]{username});
    }

    public float getGoalWeight(String username) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_USERS, new String[]{COL_GOAL_WEIGHT}, COL_USERNAME + "=?", new String[]{username}, null, null, null);
        float goal = 0f;
        if (cursor.moveToFirst()) {
            goal = cursor.getFloat(0);
        }
        cursor.close();
        return goal;
    }

    public boolean addWeightEntry(String username, String date, float weight) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_WEIGHT_USER, username);
        values.put(COL_DATE, date);
        values.put(COL_WEIGHT, weight);
        long result = db.insert(TABLE_WEIGHT, null, values);
        return result != -1;
    }

    public Cursor getWeightEntries(String username) {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.query(TABLE_WEIGHT, new String[]{COL_ENTRY_ID, COL_DATE, COL_WEIGHT}, COL_WEIGHT_USER + "=?", new String[]{username}, null, null, COL_DATE + " DESC");
    }

    public void updateWeightEntry(int id, String date, float weight) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_DATE, date);
        values.put(COL_WEIGHT, weight);
        db.update(TABLE_WEIGHT, values, COL_ENTRY_ID + "=?", new String[]{String.valueOf(id)});
    }

    public void deleteWeightEntry(int id) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_WEIGHT, COL_ENTRY_ID + "=?", new String[]{String.valueOf(id)});
    }
}