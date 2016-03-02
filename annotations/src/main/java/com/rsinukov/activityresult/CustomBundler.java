package com.rsinukov.activityresult;

import android.os.Bundle;

public interface CustomBundler<T> {

    void put(String key, T value, Bundle bundle);

    <V extends T> V get(String key, Bundle bundle);

}
