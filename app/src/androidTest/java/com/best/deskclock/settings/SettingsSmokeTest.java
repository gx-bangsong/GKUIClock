package com.best.deskclock.settings;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import com.best.deskclock.DeskClock;
import com.best.deskclock.R;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class SettingsSmokeTest {

    @Rule
    public ActivityScenarioRule<DeskClock> mActivityScenarioRule =
            new ActivityScenarioRule<>(DeskClock.class);

    @Test
    public void testAppLaunches() {
        // Just verify DeskClock launches and displays the "Clock" tab or similar
        onView(withText(R.string.menu_clock)).check(matches(isDisplayed()));
    }
}
