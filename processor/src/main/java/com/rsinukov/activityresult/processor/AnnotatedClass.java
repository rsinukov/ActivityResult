package com.rsinukov.activityresult.processor;

import com.rsinukov.activityresult.annotations.ActivityResult;
import com.rsinukov.activityresult.annotations.ActivityResults;

import javax.lang.model.element.Element;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import java.util.*;

/**
 * Created by rstk on 12/8/15.
 */
public class AnnotatedClass {

    private final Name name;
    private final Set<FieldToGenerate> optionalFields = new HashSet<FieldToGenerate>();
    private final Set<FieldToGenerate> requiredFields = new HashSet<FieldToGenerate>();
    private final Element element;
    private final Name className;

    public AnnotatedClass(TypeElement element)
            throws IllegalStateException {

        this.name = element.getQualifiedName();
        this.className = element.getSimpleName();
        this.element = element;

        ActivityResult annotation = element.getAnnotation(ActivityResult.class);
        ActivityResults annotationsArray = element.getAnnotation(ActivityResults.class);

        if (annotation != null && annotationsArray != null) {
            throw new IllegalArgumentException(
                    String.format("Only one annotation is allowed. Error inin @%s",
                           element.getQualifiedName().toString()));
        }

        if (annotation != null) {
            addField(element, annotation);
        }
        if (annotationsArray != null) {
            for (ActivityResult ar : annotationsArray.value()) {
                addField(element, ar);
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

    public Set<FieldToGenerate> getOptionalFields() {
        return optionalFields;
    }

    public Set<FieldToGenerate> getRequiredFields() {
        return requiredFields;
    }

    public Name getName() {
        return name;
    }

    public Element getElement() {
        return element;
    }

    public Name getClassName() {
        return className;
    }
}
