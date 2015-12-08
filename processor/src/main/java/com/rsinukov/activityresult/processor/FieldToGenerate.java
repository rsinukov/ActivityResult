package com.rsinukov.activityresult.processor;

import com.rsinukov.activityresult.CustomParcel;
import com.rsinukov.activityresult.EmptyParcel;
import com.rsinukov.activityresult.annotations.ActivityResult;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.List;

/**
 * Created by rstk on 12/8/15.
 */
public class FieldToGenerate implements Comparable<FieldToGenerate> {

    private final String name;
    private final String type;
    private final boolean required;
    private final TypeElement element;
    private String bundlerClass;

    public FieldToGenerate(ActivityResult annotation, TypeElement element)
            throws IllegalArgumentException {
        this.name = annotation.name();
        this.type = annotation.type().toString();
        this.required = annotation.isRequired();
        this.element = element;

        if (annotation.name() == null || annotation.name().length() == 0) { //TODO: change to isEmpty()
            throw new IllegalArgumentException(
                    String.format("name() in @%s for class %s must me set to not empty value",
                            ActivityResult.class.getSimpleName(), element.getQualifiedName().toString()));
        }

        try {
            Class<? extends CustomParcel> clazz = annotation.parcel();
            bundlerClass = getFullQualifiedParcelName(clazz);
        } catch (MirroredTypeException mte) {
            TypeMirror baggerClass = mte.getTypeMirror();
            bundlerClass = getFullQualifiedParcelName(baggerClass);
        }
    }

    private String getFullQualifiedParcelName(TypeMirror baggerClass)
            throws IllegalArgumentException {
        if (baggerClass == null) {
            throw new IllegalArgumentException(String.format("Could not get the ArgsBundler class from @%s in @%s", name, element));
        }

        if (baggerClass.toString().equals(EmptyParcel.class.getCanonicalName())) {
            return EmptyParcel.class.getCanonicalName();
        }

        if (baggerClass.getKind() != TypeKind.DECLARED) {
            throw new IllegalArgumentException(String.format("@ %s is not a class in %s ",
                    CustomParcel.class.getSimpleName(), element.getSimpleName()));
        }

        if (!isPublicClass((DeclaredType) baggerClass)) {
            throw new IllegalArgumentException(String.format(
                    "The %s must be a public class to be a valid CustomParcel", baggerClass.toString()));
        }

        if (!hasPublicEmptyConstructor((DeclaredType) baggerClass)) {
            throw new IllegalArgumentException(String.format(
                    "The %s must provide a public empty default constructor to be a valid CustomParcel",
                    baggerClass.toString()));
        }

        return baggerClass.toString();
    }

    private String getFullQualifiedParcelName(Class<? extends CustomParcel> clazz)
            throws IllegalArgumentException {
        if (clazz.equals(EmptyParcel.class)) {
            return EmptyParcel.class.getCanonicalName();
        }

        if (!Modifier.isPublic(clazz.getModifiers())) {
            throw new IllegalArgumentException(String.format(
                    "The %s must be a public class to be a valid CustomParcel",
                    clazz.getCanonicalName()));
        }

        Constructor<?>[] constructors = clazz.getConstructors();

        boolean foundDefaultConstructor = false;
        for (Constructor c : constructors) {
            boolean isPublicConstructor = Modifier.isPublic(c.getModifiers());
            Class<?>[] pType = c.getParameterTypes();

            if (pType.length == 0 && isPublicConstructor) {
                foundDefaultConstructor = true;
                break;
            }
        }

        if (!foundDefaultConstructor) {
            throw new IllegalArgumentException(String.format(
                    "The %s must provide a public empty default constructor to be a valid CustomParcel",
                    clazz.getCanonicalName()));
        }

        return clazz.getCanonicalName();
    }

    private boolean isPublicClass(DeclaredType type) {
        Element element = type.asElement();
        return element.getModifiers().contains(javax.lang.model.element.Modifier.PUBLIC);
    }

    private boolean hasPublicEmptyConstructor(DeclaredType type) {
        Element element = type.asElement();

        List<? extends Element> containing = element.getEnclosedElements();

        for (Element e : containing) {
            if (e.getKind() == ElementKind.CONSTRUCTOR) {
                ExecutableElement c = (ExecutableElement) e;

                if ((c.getParameters() == null || c.getParameters().isEmpty()) && c.getModifiers()
                        .contains(javax.lang.model.element.Modifier.PUBLIC)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Get the full qualified name of the CustomParcel class.
     *
     * @return null if no custom parcel has been set. Otherwise the fully qualified name of the
     * custom parcel class.
     */
    public String getBundlerClass() {
        return bundlerClass.equals(EmptyParcel.class.getCanonicalName()) ? null : bundlerClass;
    }

    public boolean hasCustomBundler() {
        return getBundlerClass() != null;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public Element getElement() {
        return element;
    }

    public boolean isRequired() {
        return required;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FieldToGenerate)) return false;

        FieldToGenerate that = (FieldToGenerate) o;

        return name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public String toString() {
        return name + "/" + type;
    }

    public String getRawType() {
        if (isArray()) {
            return type.substring(0, type.length() - 2);
        }
        return type;
    }

    public boolean isArray() {
        return type.endsWith("[]");
    }

    public boolean isPrimitive() {
        return element.asType().getKind().isPrimitive();
    }

    @Override
    public int compareTo(FieldToGenerate o) {
        return name.compareTo(o.name);
    }
}
