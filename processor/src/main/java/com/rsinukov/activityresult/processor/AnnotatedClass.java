package com.rsinukov.activityresult.processor;

import com.rsinukov.activityresult.annotations.ActivityResult;
import com.rsinukov.activityresult.annotations.ActivityResults;

import javax.lang.model.element.TypeElement;
import java.util.*;

/**
 * Created by rstk on 12/8/15.
 */
public class AnnotatedClass {

    private Set<FieldToGenerate> optionalFields = new HashSet<FieldToGenerate>();
    private Set<FieldToGenerate> requiredFields = new HashSet<FieldToGenerate>();

    private AnnotatedClass(TypeElement element)
            throws IllegalStateException {
        ActivityResult annotation = element.getAnnotation(ActivityResult.class);
        ActivityResults annotationsArray = element.getAnnotation(ActivityResults.class);

        if (annotation != null) {
            addField(element, annotation);
        }
        if (annotationsArray != null) {
            for (ActivityResult ar : annotationsArray.value()) {
                addField(element, annotation);
            }
        }
    }

    private void addField(TypeElement element, ActivityResult annotation)
            throws IllegalStateException {
        boolean isMustFail = false;

        FieldToGenerate field = new FieldToGenerate(annotation, element);
        if (field.isRequired()) {
            if (requiredFields.contains(field)) isMustFail = true;
            requiredFields.add(field);
        } else {
            if (optionalFields.contains(field)) isMustFail = true;
            optionalFields.add(field);
        }

        if (isMustFail) {
            throw new IllegalArgumentException(
                    String.format("dublicate name @%s in @%s",
                            field.getName(), element.getQualifiedName().toString()));
        }
    }

}
