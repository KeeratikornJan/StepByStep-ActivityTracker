
package com.example.stepbystepactivitytracker;

import android.database.Cursor;
import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DBHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "activity_tracker.db";
    private static final int DATABASE_VERSION = 1;

    public static final String TABLE_SESSIONS = "sessions";


    public DBHelper(Context context) {
        this(context, DATABASE_NAME);
    }

    // Allow a separate database for testing
    DBHelper(Context context, String databaseName) {
        super(context, databaseName, null, DATABASE_VERSION);
    }


    @Override
    public void onCreate(SQLiteDatabase db) {
        String sql = "CREATE TABLE " + TABLE_SESSIONS + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "steps INTEGER NOT NULL, " +
                "shakes INTEGER NOT NULL, " +
                "duration_seconds INTEGER NOT NULL, " +
                "timestamp_ms INTEGER NOT NULL)";

        db.execSQL(sql);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // สำหรับปรับโครงสร้างฐานข้อมูลในอนาคต
    }

    public long insertSession(int steps, int shakes,
                              long durationSeconds, long timestampMs) {

        if (steps < 0 || shakes < 0 ||
                durationSeconds < 0 || timestampMs <= 0) {
            return -1;
        }

        SQLiteDatabase db = getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put("steps", steps);
        values.put("shakes", shakes);
        values.put("duration_seconds", durationSeconds);
        values.put("timestamp_ms", timestampMs);

        return db.insert(TABLE_SESSIONS, null, values);
    }

    public Cursor getAllSessions() {
        SQLiteDatabase db = getReadableDatabase();

        return db.query(
                TABLE_SESSIONS,
                null,
                null,
                null,
                null,
                null,
                "timestamp_ms DESC, id DESC"
        );
    }

    public boolean deleteSession(long sessionId) {
        SQLiteDatabase db = getWritableDatabase();

        int rowsDeleted = db.delete(
                TABLE_SESSIONS,
                "id = ?",
                new String[]{String.valueOf(sessionId)}
        );

        return rowsDeleted > 0;
    }

    public static class SessionStats {
        public final long totalSteps;
        public final long totalShakes;
        public final long maxSteps;
        public final long maxShakes;

        public SessionStats(long totalSteps, long totalShakes,
                            long maxSteps, long maxShakes) {
            this.totalSteps = totalSteps;
            this.totalShakes = totalShakes;
            this.maxSteps = maxSteps;
            this.maxShakes = maxShakes;
        }
    }

    public SessionStats getStatistics() {
        SQLiteDatabase db = getReadableDatabase();

        String sql = "SELECT " +
                "COALESCE(SUM(steps), 0), " +
                "COALESCE(SUM(shakes), 0), " +
                "COALESCE(MAX(steps), 0), " +
                "COALESCE(MAX(shakes), 0) " +
                "FROM " + TABLE_SESSIONS;

        try (Cursor cursor = db.rawQuery(sql, null)) {
            if (cursor.moveToFirst()) {
                return new SessionStats(
                        cursor.getLong(0),
                        cursor.getLong(1),
                        cursor.getLong(2),
                        cursor.getLong(3)
                );
            }
        }

        return new SessionStats(0, 0, 0, 0);
    }

}
