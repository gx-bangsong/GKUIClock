package com.best.deskclock.timer.quick;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface QuickTimerDao {
    @Query("SELECT * FROM quick_timers")
    LiveData<List<QuickTimer>> getAllQuickTimers();

    @Insert
    void insert(QuickTimer quickTimer);

    @Update
    void update(QuickTimer quickTimer);

    @Delete
    void delete(QuickTimer quickTimer);

    @Query("DELETE FROM quick_timers WHERE id = :id")
    void deleteById(int id);
}
