package com.rsinukov.activityresult.processor;

import com.rsinukov.activityresult.annotations.ActivityResult;
import com.rsinukov.activityresult.annotations.ActivityResults;

import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import java.util.*;

public class AnnotatedClass {

    private final Name name;
    private final Set<FieldToGenerate> optionalFields = new HashSet<FieldToGenerate>();
    private final Set<FieldToGenerate> requiredFields = new HashSet<FieldToGenerate>();
    private final Name simpleName;

    public AnnotatedClass(TypeElement activityElement)
            throws IllegalStateException {

        this.name = activityElement.getQualifiedName();
        this.simpleName = activityElement.getSimpleName();

        ActivityResult annotation = activityElement.getAnnotation(ActivityResult.class);
        ActivityResults annotationsArray = activityElement.getAnnotation(ActivityResults.class);

        if (annotation != null && annotationsArray != null) {
            throw new IllegalArgumentException(
                    String.format(
                            "Only one annotation is allowed. Error in @%s",
                            activityElement.getQualifiedName().toString()
                    )
            );
        }

        if (annotation != null) {
            addField(activityElement, annotation);
        }
        if (annotationsArray != null) {
            for (ActivityResult ar : annotationsArray.value()) {
                addField(activityElement, ar);
            }
        }
    }

    private void addField(TypeElement element, ActivityResult annotation) throws IllegalStateException {
        boolean mustFailOnDublicate = false;

        FieldToGenerate field = new FieldToGenerate(annotation, element);
        if (field.isRequired()) {
            if (isAlreadyExists(field)) {
                mustFailOnDublicate = true;
            }
            requiredFields.add(field);
        } else {
            if (isAlreadyExists(field)) {
                mustFailOnDublicate = true;
            }
            optionalFields.add(field);
        }

        if (mustFailOnDublicate) {
            throw new IllegalArgumentException(
                    String.format(
                            "Dublicate name @%s in @%s",
                            field.getName(), element.getQualifiedName().toString()
                    )
            );
        }
    }

    private boolean isAlreadyExists(FieldToGenerate field) {
        return requiredFields.contains(field) || optionalFields.contains(field);
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

    public Name getSimpleName() {
        return simpleName;
    }
}
