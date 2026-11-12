package com.example.stepbystepactivitytracker;

import android.content.Context;
import android.database.Cursor;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Edge cases for items 5-8: validation, ordering, empty state and delete/recalculate. */
@RunWith(AndroidJUnit4.class)
public class DBHelperEdgeCaseTest {

    private static final String TEST_DB = "activity_tracker_edge_test.db";

    private Context context;
    private DBHelper db;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.deleteDatabase(TEST_DB);
        db = new DBHelper(context, TEST_DB);
    }

    @After
    public void tearDown() {
        db.close();
        context.deleteDatabase(TEST_DB);
    }

    private int count() {
        try (Cursor c = db.getAllSessions()) {
            return c.getCount();
        }
    }

    // ---------- ข้อ 6: Insert validation ----------

    @Test
    public void insertRejectsNegativeValues() {
        assertEquals(-1, db.insertSession(-1, 0, 10, 1000));
        assertEquals(-1, db.insertSession(0, -1, 10, 1000));
        assertEquals(-1, db.insertSession(0, 0, -1, 1000));
        assertEquals(0, count());
    }

    @Test
    public void insertRejectsInvalidTimestamp() {
        assertEquals(-1, db.insertSession(1, 1, 1, 0));
        assertEquals(-1, db.insertSession(1, 1, 1, -5));
        assertEquals(0, count());
    }

    @Test
    public void insertAcceptsZeroStepsShakesAndDuration() {
        assertTrue(db.insertSession(0, 0, 0, 1000) > 0);
        assertEquals(1, count());
    }

    // ---------- ข้อ 5: Statistics ----------

    @Test
    public void statisticsAreZeroWhenEmpty() {
        DBHelper.SessionStats s = db.getStatistics();
        assertEquals(0L, s.totalSteps);
        assertEquals(0L, s.totalShakes);
        assertEquals(0L, s.maxSteps);
        assertEquals(0L, s.maxShakes);
    }

    @Test
    public void maxStepsAndMaxShakesCanComeFromDifferentSessions() {
        db.insertSession(100, 2, 60, 1000);
        db.insertSession(10, 50, 60, 2000);

        DBHelper.SessionStats s = db.getStatistics();
        assertEquals(110L, s.totalSteps);
        assertEquals(52L, s.totalShakes);
        assertEquals(100L, s.maxSteps);
        assertEquals(50L, s.maxShakes);
    }

    // ---------- ข้อ 7: Query order ----------

    @Test
    public void historyIsNewestFirst() {
        db.insertSession(1, 0, 1, 1000);
        db.insertSession(3, 0, 1, 3000);
        db.insertSession(2, 0, 1, 2000);

        try (Cursor c = db.getAllSessions()) {
            int steps = c.getColumnIndexOrThrow("steps");
            assertTrue(c.moveToFirst());
            assertEquals(3, c.getInt(steps));
            assertTrue(c.moveToNext());
            assertEquals(2, c.getInt(steps));
            assertTrue(c.moveToNext());
            assertEquals(1, c.getInt(steps));
        }
    }

    @Test
    public void sameTimestampFallsBackToNewestId() {
        db.insertSession(1, 0, 1, 5000);
        db.insertSession(2, 0, 1, 5000);

        try (Cursor c = db.getAllSessions()) {
            assertTrue(c.moveToFirst());
            assertEquals(2, c.getInt(c.getColumnIndexOrThrow("steps")));
        }
    }

    // ---------- ข้อ 8: Delete & Recalculate ----------

    @Test
    public void deleteUnknownIdReturnsFalseAndKeepsData() {
        db.insertSession(10, 5, 30, 1000);

        assertFalse(db.deleteSession(9999));
        assertEquals(1, count());
    }

    @Test
    public void deleteSameIdTwiceSucceedsOnlyOnce() {
        long id = db.insertSession(10, 5, 30, 1000);

        assertTrue(db.deleteSession(id));
        assertFalse(db.deleteSession(id));
    }

    @Test
    public void deletingTheRecordSessionLowersTheMax() {
        long small = db.insertSession(10, 1, 30, 1000);
        long big = db.insertSession(500, 40, 30, 2000);

        assertEquals(500L, db.getStatistics().maxSteps);

        assertTrue(db.deleteSession(big));

        DBHelper.SessionStats s = db.getStatistics();
        assertEquals(10L, s.maxSteps);
        assertEquals(1L, s.maxShakes);
        assertEquals(10L, s.totalSteps);
        assertTrue(db.deleteSession(small));
    }

    @Test
    public void deletingEverythingResetsStatisticsToZero() {
        long a = db.insertSession(10, 5, 30, 1000);
        long b = db.insertSession(20, 8, 60, 2000);

        assertTrue(db.deleteSession(a));
        assertTrue(db.deleteSession(b));

        assertEquals(0, count());
        DBHelper.SessionStats s = db.getStatistics();
        assertEquals(0L, s.totalSteps);
        assertEquals(0L, s.maxSteps);
    }

    @Test
    public void dataSurvivesReopeningTheDatabase() {
        db.insertSession(42, 7, 90, 1234);
        db.close();

        db = new DBHelper(context, TEST_DB);

        try (Cursor c = db.getAllSessions()) {
            assertEquals(1, c.getCount());
            assertTrue(c.moveToFirst());
            assertEquals(42, c.getInt(c.getColumnIndexOrThrow("steps")));
            assertEquals(7, c.getInt(c.getColumnIndexOrThrow("shakes")));
            assertEquals(90, c.getLong(c.getColumnIndexOrThrow("duration_seconds")));
            assertEquals(1234, c.getLong(c.getColumnIndexOrThrow("timestamp_ms")));
        }
    }
}
