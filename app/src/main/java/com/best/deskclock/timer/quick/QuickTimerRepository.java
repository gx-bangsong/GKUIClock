package com.best.deskclock.timer.quick;

import android.content.Context;
import androidx.lifecycle.LiveData;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class QuickTimerRepository {
    private static volatile QuickTimerRepository sInstance;
    private final QuickTimerDao mQuickTimerDao;
    private final LiveData<List<QuickTimer>> mAllQuickTimers;
    private final ExecutorService mExecutorService;

    private QuickTimerRepository(Context context) {
        QuickTimerDatabase db = QuickTimerDatabase.getDatabase(context);
        mQuickTimerDao = db.quickTimerDao();
        mAllQuickTimers = mQuickTimerDao.getAllQuickTimers();
        mExecutorService = Executors.newSingleThreadExecutor();
    }

    public static QuickTimerRepository getInstance(Context context) {
        if (sInstance == null) {
            synchronized (QuickTimerRepository.class) {
                if (sInstance == null) {
                    sInstance = new QuickTimerRepository(context);
                }
            }
        }
        return sInstance;
    }

    public LiveData<List<QuickTimer>> getAllQuickTimers() {
        return mAllQuickTimers;
    }

    public void insert(QuickTimer quickTimer) {
        mExecutorService.execute(() -> mQuickTimerDao.insert(quickTimer));
    }

    public void delete(QuickTimer quickTimer) {
        mExecutorService.execute(() -> mQuickTimerDao.delete(quickTimer));
    }

    public void deleteById(int id) {
        mExecutorService.execute(() -> mQuickTimerDao.deleteById(id));
    }
}
