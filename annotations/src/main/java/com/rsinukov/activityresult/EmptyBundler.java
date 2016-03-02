package com.rsinukov.activityresult;

import android.os.Bundle;

public class EmptyBundler implements CustomBundler<Object>
{
    @Override
    public void put(String key, Object value, Bundle bundle)
    {
    }

    @Override
    public <V> V get(String key, Bundle bundle)
    {
        return null;
    }
}
