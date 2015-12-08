package com.rsinukov.activityresult.annotations;

import com.rsinukov.activityresult.CustomParcel;
import com.rsinukov.activityresult.EmptyParcel;

/**
 * Created by rstk on 12/8/15.
 */
public @interface ActivityResult
{
    String name();
    Class type();
    boolean isRequired() default true;
    Class<? extends CustomParcel> parcel() default EmptyParcel.class;
}
