package com.rsinukov.activityresult.processor;

import com.rsinukov.activityresult.CustomBundler;
import com.rsinukov.activityresult.EmptyBundler;
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

public class FieldToGenerate implements Comparable<FieldToGenerate> {

    private final String name;
    private String typeString;
    private TypeMirror type;
    private final boolean isRequired;
    private String bundlerClassName;
    private final TypeElement activityElement;

    public FieldToGenerate(ActivityResult annotation, TypeElement activityElement)
            throws IllegalArgumentException {
        this.name = annotation.name();
        this.isRequired = annotation.isRequired();
        this.activityElement = activityElement;

        if (annotation.name() == null || annotation.name().length() == 0) { // TODO: change to Utils.isEmpty()
            throw new IllegalArgumentException(
                    String.format(
                            "name() in @%s for class %s must me set to not empty value",
                            ActivityResult.class.getSimpleName(),
                            activityElement.getQualifiedName().toString()
                    )
            );
        }

        try {
            Class<? extends CustomBundler> bundlerClass = annotation.parcel();
            bundlerClassName = getFullQualifiedParcelName(bundlerClass);
        } catch (MirroredTypeException mte) {
            TypeMirror bundlerTypeMirror = mte.getTypeMirror();
            bundlerClassName = getFullQualifiedParcelName(bundlerTypeMirror, activityElement);
        }
        try {
            Class fieldTypeClazz = annotation.type();
            typeString = fieldTypeClazz.getCanonicalName();
        } catch (MirroredTypeException mte) {
            TypeMirror fieldTypeMirror = mte.getTypeMirror();
            typeString = fieldTypeMirror.toString();
            type = mte.getTypeMirror();
        }
    }

    private String getFullQualifiedParcelName(TypeMirror bundlerClass, TypeElement activityElement)
            throws IllegalArgumentException {
        if (bundlerClass == null) {
            throw new IllegalArgumentException(
                    String.format("Could not get the CustomBundler class from @%s in @%s", name, activityElement)
            );
        }

        if (bundlerClass.toString().equals(EmptyBundler.class.getCanonicalName())) {
            return EmptyBundler.class.getCanonicalName();
        }

        if (bundlerClass.getKind() != TypeKind.DECLARED) {
            throw new IllegalArgumentException(
                    String.format(
                            "@%s is not a class in @%s ",
                            bundlerClass.toString(),
                            activityElement.getSimpleName()
                    )
            );
        }

        if (!isPublicClass((DeclaredType) bundlerClass)) {
            throw new IllegalArgumentException(
                    String.format("The %s must be a public class to be a valid CustomBundler", bundlerClass.toString())
            );
        }

        if (!hasPublicEmptyConstructor((DeclaredType) bundlerClass)) {
            throw new IllegalArgumentException(
                    String.format(
                            "The %s must provide a public empty default constructor to be a valid CustomBundler",
                            bundlerClass.toString()
                    )
            );
        }

        return bundlerClass.toString();
    }

    private String getFullQualifiedParcelName(Class<? extends CustomBundler> clazz) throws IllegalArgumentException {
        if (clazz.equals(EmptyBundler.class)) {
            return EmptyBundler.class.getCanonicalName();
        }

        if (!Modifier.isPublic(clazz.getModifiers())) {
            throw new IllegalArgumentException(
                    String.format(
                            "The %s must be a public class to be a valid CustomBundler",
                            clazz.getCanonicalName()
                    )
            );
        }

        if (!hasPublicEmptyConstructor(clazz)) {
            throw new IllegalArgumentException(
                    String.format(
                            "The %s must provide a public empty default constructor to be a valid CustomBundler",
                            clazz.getCanonicalName()
                    )
            );
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

    private boolean hasPublicEmptyConstructor(Class<? extends CustomBundler> clazz) {
        Constructor<?>[] constructors = clazz.getConstructors();

        for (Constructor c : constructors) {
            boolean isPublicConstructor = Modifier.isPublic(c.getModifiers());
            Class<?>[] pType = c.getParameterTypes();

            if (pType.length == 0 && isPublicConstructor) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get the full qualified name of the CustomBundler class.
     *
     * @return null if no custom parcel has been set. Otherwise the fully qualified name of the
     * custom parcel class.
     */
    public String getBundlerClassName() {
        return bundlerClassName.equals(EmptyBundler.class.getCanonicalName()) ? null : bundlerClassName;
    }

    public boolean hasCustomBundler() {
        return getBundlerClassName() != null;
    }

    public String getName() {
        return name;
    }

    public boolean isRequired() {
        return isRequired;
    }

    public TypeMirror getType() {
        return type;
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
        return name + "/" + typeString;
    }

    public String getRawType() {
        if (isArray()) {
            return typeString.substring(0, typeString.length() - 2);
        }
        return typeString;
    }

    public boolean isArray() {
        return typeString.endsWith("[]");
    }

    public TypeElement getActivityElement() {
        return activityElement;
    }

    @Override
    public int compareTo(FieldToGenerate o) {
        return name.compareTo(o.name);
    }
}
