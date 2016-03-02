package com.rsinukov.activityresult.annotations;

import com.rsinukov.activityresult.CustomBundler;
import com.rsinukov.activityresult.EmptyBundler;

public @interface ActivityResult
{
    String name();
    Class type();
    boolean isRequired() default true;
    Class<? extends CustomBundler> parcel() default EmptyBundler.class;
}
