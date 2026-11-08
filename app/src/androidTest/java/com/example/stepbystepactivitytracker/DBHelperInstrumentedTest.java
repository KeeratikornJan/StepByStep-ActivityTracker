
package com.example.stepbystepactivitytracker;

import android.content.Context;
import android.database.Cursor;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class DBHelperInstrumentedTest {

    private static final String TEST_DB =
            "activity_tracker_test.db";

    @Test
    public void testDatabaseOperations() {

        Context context = InstrumentationRegistry
                .getInstrumentation()
                .getTargetContext();

        // Clear only the test database
        context.deleteDatabase(TEST_DB);

        DBHelper db = new DBHelper(context, TEST_DB);

        try {
            // TEST 6: Insert Session
            long id1 = db.insertSession(
                    10, 5, 30, 1000
            );

            long id2 = db.insertSession(
                    20, 8, 60, 2000
            );

            assertTrue(id1 > 0);
            assertTrue(id2 > 0);

            // TEST 7: Query History
            try (Cursor cursor = db.getAllSessions()) {
                assertEquals(2, cursor.getCount());
                assertTrue(cursor.moveToFirst());

                int steps = cursor.getInt(
                        cursor.getColumnIndexOrThrow("steps")
                );
                assertEquals(20, steps);
            }

            // TEST 7: Persistence
            db.close();
            db = new DBHelper(context, TEST_DB);

            try (Cursor cursor = db.getAllSessions()) {
                assertEquals(2, cursor.getCount());
            }

            // Statistics before deletion
            DBHelper.SessionStats before =
                    db.getStatistics();

            assertEquals(30L, before.totalSteps);
            assertEquals(13L, before.totalShakes);
            assertEquals(20L, before.maxSteps);
            assertEquals(8L, before.maxShakes);

            // TEST 8: Delete Session
            assertTrue(db.deleteSession(id2));

            // TEST 8: Recalculate
            DBHelper.SessionStats after =
                    db.getStatistics();

            assertEquals(10L, after.totalSteps);
            assertEquals(5L, after.totalShakes);
            assertEquals(10L, after.maxSteps);
            assertEquals(5L, after.maxShakes);

            // Verify remaining history
            try (Cursor cursor = db.getAllSessions()) {
                assertEquals(1, cursor.getCount());
                assertTrue(cursor.moveToFirst());

                assertEquals(10, cursor.getInt(
                        cursor.getColumnIndexOrThrow("steps")
                ));
            }

        } finally {
            db.close();
            context.deleteDatabase(TEST_DB);
        }
    }
}
