package com.example.mitchellramirez2;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Base64;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "WeightTracker.db";
    private static final int DATABASE_VERSION = 4;

    private static DatabaseHelper instance;

    // Table Users
    public static final String TABLE_USERS = "users";
    public static final String COL_USER_ID = "id";
    public static final String COL_USERNAME = "username";
    public static final String COL_PASSWORD_HASH = "password_hash";
    public static final String COL_SALT = "salt";
    public static final String COL_GOAL_WEIGHT = "goal_weight";

    // Table Weight Entries
    public static final String TABLE_WEIGHT = "weight_entries";
    public static final String COL_ENTRY_ID = "id";
    public static final String COL_WEIGHT_USER_ID = "user_id";
    public static final String COL_DATE = "date";
    public static final String COL_WEIGHT = "weight";

    // PBKDF2 Constants
    private static final int ITERATIONS = 600000;
    private static final int KEY_LENGTH = 256;
    private static final int SALT_LENGTH = 32;

    public static synchronized DatabaseHelper getInstance(Context context) {
        if (instance == null) {
            instance = new DatabaseHelper(context.getApplicationContext());
        }
        return instance;
    }

    // Public constructor kept for backwards compatibility if needed directly
    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createUsersTable = "CREATE TABLE " + TABLE_USERS + " (" +
                COL_USER_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_USERNAME + " TEXT UNIQUE NOT NULL, " +
                COL_PASSWORD_HASH + " TEXT NOT NULL, " +
                COL_SALT + " TEXT NOT NULL, " +
                COL_GOAL_WEIGHT + " REAL DEFAULT 0)";
        db.execSQL(createUsersTable);

        String createWeightTable = "CREATE TABLE " + TABLE_WEIGHT + " (" +
                COL_ENTRY_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_WEIGHT_USER_ID + " INTEGER NOT NULL, " +
                COL_DATE + " TEXT NOT NULL, " +
                COL_WEIGHT + " REAL NOT NULL, " +
                "FOREIGN KEY(" + COL_WEIGHT_USER_ID + ") REFERENCES " +
                TABLE_USERS + "(" + COL_USER_ID + ") ON DELETE CASCADE ON UPDATE CASCADE)";
        db.execSQL(createWeightTable);

        String createIndex = "CREATE INDEX idx_weight_user_date ON " + TABLE_WEIGHT +
                "(" + COL_WEIGHT_USER_ID + ", " + COL_DATE + " ASC)";
        db.execSQL(createIndex);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_WEIGHT);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_USERS);
        onCreate(db);
    }

    // Resolves integer user_id from username
    public int getUserId(String username) {
        SQLiteDatabase db = this.getReadableDatabase();
        int userId = -1;
        try (Cursor cursor = db.query(TABLE_USERS, new String[]{COL_USER_ID},
                COL_USERNAME + "=?", new String[]{username}, null, null, null)) {
            if (cursor.moveToFirst()) {
                userId = cursor.getInt(0);
            }
        }
        return userId;
    }

    public boolean createUser(String username, char[] password) {
        ContentValues values = new ContentValues();
        values.put(COL_USERNAME, username);

        if (password == null || password.length == 0) {
            values.put(COL_PASSWORD_HASH, "OAUTH_EXTERNAL_USER");
            values.put(COL_SALT, "NONE");
        } else {
            byte[] salt = generateSalt();
            byte[] rawHash = hashPassword(password, salt);

            String hash = Base64.encodeToString(rawHash, Base64.NO_WRAP);

            values.put(COL_PASSWORD_HASH, hash);
            values.put(COL_SALT, Base64.encodeToString(salt, Base64.NO_WRAP));

            Arrays.fill(rawHash, (byte) 0);
            Arrays.fill(salt, (byte) 0);
        }

        // Clear password buffer after creation
        if (password != null) {
            Arrays.fill(password, '\0');
        }

        SQLiteDatabase db = this.getWritableDatabase();
        long id = db.insert(TABLE_USERS, null, values);
        return id != -1;
    }

    public boolean checkUserExists(String username) {
        SQLiteDatabase db = this.getReadableDatabase();
        try (Cursor cursor = db.query(TABLE_USERS, new String[]{COL_USERNAME},
                COL_USERNAME + "=?", new String[]{username}, null, null, null)) {
            return cursor.getCount() > 0;
        }
    }

    public boolean authenticateUser(String username, char[] password) {
        SQLiteDatabase db = this.getReadableDatabase();
        String storedHashBase64 = null;
        String storedSaltBase64 = null;

        try (Cursor cursor = db.query(TABLE_USERS,
                new String[]{COL_PASSWORD_HASH, COL_SALT},
                COL_USERNAME + "=?", new String[]{username}, null, null, null)) {

            if (cursor.moveToFirst()) {
                storedHashBase64 = cursor.getString(cursor.getColumnIndexOrThrow(COL_PASSWORD_HASH));
                storedSaltBase64 = cursor.getString(cursor.getColumnIndexOrThrow(COL_SALT));
            }
        }

        if (storedHashBase64 == null || storedSaltBase64 == null) {
            Arrays.fill(password, '\0');
            return false;
        }

        byte[] salt = Base64.decode(storedSaltBase64, Base64.NO_WRAP);
        byte[] storedHash = Base64.decode(storedHashBase64, Base64.NO_WRAP);
        byte[] computedHash = hashPassword(password, salt);

        boolean authenticated = constantTimeAreEqual(storedHash, computedHash);

        Arrays.fill(salt, (byte) 0);
        Arrays.fill(storedHash, (byte) 0);
        Arrays.fill(computedHash, (byte) 0);
        Arrays.fill(password, '\0');

        return authenticated;
    }

    // --- Goal Weight Methods ---
    public void updateGoalWeight(int userId, float goalWeight) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_GOAL_WEIGHT, goalWeight);
        db.update(TABLE_USERS, values, COL_USER_ID + "=?", new String[]{String.valueOf(userId)});
    }

    public void updateGoalWeight(String username, float goalWeight) {
        int userId = getUserId(username);
        if (userId != -1) {
            updateGoalWeight(userId, goalWeight);
        }
    }

    public float getGoalWeight(int userId) {
        SQLiteDatabase db = this.getReadableDatabase();
        float goal = 0f;
        try (Cursor cursor = db.query(TABLE_USERS, new String[]{COL_GOAL_WEIGHT},
                COL_USER_ID + "=?", new String[]{String.valueOf(userId)}, null, null, null)) {
            if (cursor.moveToFirst()) {
                goal = cursor.getFloat(0);
            }
        }
        return goal;
    }

    public float getGoalWeight(String username) {
        int userId = getUserId(username);
        return userId != -1 ? getGoalWeight(userId) : 0f;
    }

    // --- Weight Entries Methods ---
    public void addWeightEntry(int userId, String date, float weight) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_WEIGHT_USER_ID, userId);
        values.put(COL_DATE, date);
        values.put(COL_WEIGHT, weight);
        db.insert(TABLE_WEIGHT, null, values);
    }

    public void addWeightEntry(String username, String date, float weight) {
        int userId = getUserId(username);
        if (userId != -1) {
            addWeightEntry(userId, date, weight);
        }
    }

    public Cursor getWeightEntries(int userId) {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.query(
                TABLE_WEIGHT,
                new String[]{COL_ENTRY_ID, COL_DATE, COL_WEIGHT},
                COL_WEIGHT_USER_ID + "=?",
                new String[]{String.valueOf(userId)},
                null, null, COL_DATE + " ASC, " + COL_ENTRY_ID + " ASC");
    }

    public Cursor getWeightEntries(String username) {
        int userId = getUserId(username);
        return getWeightEntries(userId);
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

    private byte[] generateSalt() {
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[SALT_LENGTH];
        random.nextBytes(salt);
        return salt;
    }

    private byte[] hashPassword(char[] password, byte[] salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, KEY_LENGTH);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] hash = factory.generateSecret(spec).getEncoded();
            spec.clearPassword();
            return hash;
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new RuntimeException("Error hashing password", e);
        }
    }

    private boolean constantTimeAreEqual(byte[] a, byte[] b) {
        if (a.length != b.length) return false;
        int result = 0;
        for (int i = 0; i < a.length; i++) {
            result |= a[i] ^ b[i];
        }
        return result == 0;
    }
}