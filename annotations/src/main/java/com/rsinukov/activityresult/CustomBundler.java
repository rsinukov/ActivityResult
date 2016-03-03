package com.rsinukov.activityresult;

import android.os.Bundle;

public interface CustomBundler<T> {

    void put(String key, T value, Bundle bundle);

    <T> T get(String key, Bundle bundle);

}
