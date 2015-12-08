package com.rsinukov.activityresult.annotations;

/**
 * Created by rstk on 12/8/15.
 */
public @interface ActivityResult
{
    String name();
    Class type();
    boolean isRequired() default true;
}
