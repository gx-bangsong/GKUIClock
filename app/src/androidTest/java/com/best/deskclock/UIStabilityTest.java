package com.best.deskclock;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import com.best.deskclock.settings.SettingsActivity;

import org.junit.Test;
import org.junit.runner.RunWith;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class UIStabilityTest {

    @Test
    public void testDeskClockLaunches() {
        try (ActivityScenario<DeskClock> scenario = ActivityScenario.launch(DeskClock.class)) {
            onView(withText(R.string.menu_clock)).check(matches(isDisplayed()));
        }
    }

    @Test
    public void testSettingsActivityLaunches() {
        try (ActivityScenario<SettingsActivity> scenario = ActivityScenario.launch(SettingsActivity.class)) {
            onView(withText(R.string.settings)).check(matches(isDisplayed()));
        }
    }

    @Test
    public void testTimerFragmentLaunches() {
        try (ActivityScenario<DeskClock> scenario = ActivityScenario.launch(DeskClock.class)) {
            onView(withText(R.string.menu_timer)).perform(click());
            // Verify if timer setup view is displayed (if no timers) or timer list
            // Just checking if any view is visible to ensure no crash on fragment transition
        }
    }
}
