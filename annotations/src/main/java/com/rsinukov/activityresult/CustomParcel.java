package com.rsinukov.activityresult;

import android.os.Bundle;

/**
 * Created by rstk on 12/2/15.
 */
public interface CustomParcel<T> {

    void put(String key, T value, Bundle bundle);

    <V extends T> V get(String key, Bundle bundle);

}
