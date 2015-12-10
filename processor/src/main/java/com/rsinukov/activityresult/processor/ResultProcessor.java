package com.rsinukov.activityresult.processor;

import com.rsinukov.activityresult.annotations.ActivityResult;
import com.rsinukov.activityresult.annotations.ActivityResults;
import com.squareup.javapoet.*;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import javax.xml.stream.events.StartElement;

import java.io.IOException;
import java.io.Serializable;
import java.util.*;

/**
 * Created by rstk on 11/20/15.
 */
public class ResultProcessor extends AbstractProcessor {
    private static final Map<String, String> ARGUMENT_TYPES = new HashMap<String, String>(20);

    static {
        ARGUMENT_TYPES.put("java.lang.String", "String");
        ARGUMENT_TYPES.put("int", "Int");
        ARGUMENT_TYPES.put("java.lang.Integer", "Int");
        ARGUMENT_TYPES.put("long", "Long");
        ARGUMENT_TYPES.put("java.lang.Long", "Long");
        ARGUMENT_TYPES.put("double", "Double");
        ARGUMENT_TYPES.put("java.lang.Double", "Double");
        ARGUMENT_TYPES.put("short", "Short");
        ARGUMENT_TYPES.put("java.lang.Short", "Short");
        ARGUMENT_TYPES.put("float", "Float");
        ARGUMENT_TYPES.put("java.lang.Float", "Float");
        ARGUMENT_TYPES.put("byte", "Byte");
        ARGUMENT_TYPES.put("java.lang.Byte", "Byte");
        ARGUMENT_TYPES.put("boolean", "Boolean");
        ARGUMENT_TYPES.put("java.lang.Boolean", "Boolean");
        ARGUMENT_TYPES.put("char", "Char");
        ARGUMENT_TYPES.put("java.lang.Character", "Char");
        ARGUMENT_TYPES.put("java.lang.CharSequence", "CharSequence");
        ARGUMENT_TYPES.put("android.os.Bundle", "Bundle");
        ARGUMENT_TYPES.put("android.os.Parcelable", "Parcelable");
    }

    private Types typeUtils;
    private Elements elementUtils;
    private Filer filer;
    private Messager messager;

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        Set<String> supportTypes = new LinkedHashSet<String>();
        supportTypes.add(ActivityResults.class.getCanonicalName());
        supportTypes.add(ActivityResult.class.getCanonicalName());
        return supportTypes;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        typeUtils = processingEnv.getTypeUtils();
        elementUtils = processingEnv.getElementUtils();
        filer = processingEnv.getFiler();
        messager = processingEnv.getMessager();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        Elements elementUtils = processingEnv.getElementUtils();
        Types typeUtils = processingEnv.getTypeUtils();
        Filer filer = processingEnv.getFiler();

        try {
            List<AnnotatedClass> annotatedClasses = new ArrayList<AnnotatedClass>();

            for (Element element : roundEnv.getElementsAnnotatedWith(ActivityResult.class)) {
                TypeElement enclosingElement = (TypeElement) element;
                annotatedClasses.add(new AnnotatedClass(enclosingElement));
            }
            for (Element element : roundEnv.getElementsAnnotatedWith(ActivityResults.class)) {
                TypeElement enclosingElement = (TypeElement) element;
                annotatedClasses.add(new AnnotatedClass(enclosingElement));
            }

            for (AnnotatedClass ac : annotatedClasses) {

                List<MethodSpec> methods = new ArrayList<MethodSpec>();
                List<FieldSpec> fields = new ArrayList<FieldSpec>();

                MethodSpec constructor = MethodSpec.constructorBuilder()
                        .addModifiers(Modifier.PRIVATE)
                        .build();

                List<FieldToGenerate> allFields = new ArrayList<FieldToGenerate>(ac.getOptionalFields());
                allFields.addAll(ac.getRequiredFields());

                for (FieldToGenerate field : allFields) {
                    MethodSpec methodSpec = MethodSpec
                            .methodBuilder("get" + toCamelCase(field.getName()))
                            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                            .returns(TypeName.get(field.getType()))
                            .addStatement("return $L", field.getName())
                            .build();
                    methods.add(methodSpec);

                    FieldSpec fieldSpec = FieldSpec
                            .builder(TypeName.get(field.getType()), field.getName(), Modifier.PRIVATE)
                            .build();
                    fields.add(fieldSpec);
                }

                TypeSpec.Builder resultBuilder = TypeSpec.classBuilder("Builder")
                        .addModifiers(Modifier.PUBLIC, Modifier.STATIC);

                MethodSpec.Builder builderConstructor = MethodSpec.constructorBuilder()
                        .addModifiers(Modifier.PUBLIC);
                for (FieldToGenerate field : ac.getRequiredFields()) {
                    builderConstructor.addParameter(TypeName.get(field.getType()), field.getName());
                }
                resultBuilder.addMethod(builderConstructor.build());

                for (FieldToGenerate field : allFields) {
                    resultBuilder.addField(TypeName.get(field.getType()), field.getName(), Modifier.PRIVATE);
                }

                for (FieldToGenerate field : ac.getOptionalFields()) {
                    resultBuilder.addMethod(MethodSpec.methodBuilder("set" + toCamelCase(field.getName()))
                            .addModifiers(Modifier.PUBLIC)
                            .addParameter(TypeName.get(field.getType()), field.getName())
                            .addStatement("this.$L = $L", field.getName(), field.getName())
                            .build());
                }

                TypeSpec resultClass = TypeSpec.classBuilder(ac.getClassName().toString() + "Result")
                        .addModifiers(Modifier.PUBLIC)
                        .addMethod(constructor)
                        .addMethods(methods)
                        .addFields(fields)
                        .addType(resultBuilder.build())
                        .build();

                String annotatedClassName = ac.getName().toString();
                String packageName = annotatedClassName.substring(0, annotatedClassName.lastIndexOf("."));
                JavaFile resultFile = JavaFile.builder(packageName, resultClass).build();
                resultFile.writeTo(filer);
            }
        } catch (IllegalArgumentException e) {
            messager.printMessage(Diagnostic.Kind.ERROR, e.getMessage());
        } catch (Exception e) {
            StringBuilder stackTrace = new StringBuilder();
            for (StackTraceElement element: e.getStackTrace()){
                stackTrace.append(element.toString()).append("\n");
            }
            messager.printMessage(Diagnostic.Kind.ERROR, e.getMessage() + " \n" + stackTrace);
        }

        return true;
    }

    protected String getOperation(FieldToGenerate arg) {
        String op = ARGUMENT_TYPES.get(arg.getRawType());
        if (op != null) {
            if (arg.isArray()) {
                return op + "Array";
            } else {
                return op;
            }
        }

        Elements elements = processingEnv.getElementUtils();
        TypeMirror type = arg.getElement().asType();
        Types types = processingEnv.getTypeUtils();
        String[] arrayListTypes = new String[]{
                String.class.getName(), Integer.class.getName(), CharSequence.class.getName()
        };
        String[] arrayListOps =
                new String[]{"StringArrayList", "IntegerArrayList", "CharSequenceArrayList"};
        for (int i = 0; i < arrayListTypes.length; i++) {
            TypeMirror tm = getArrayListType(arrayListTypes[i]);
            if (types.isAssignable(type, tm)) {
                return arrayListOps[i];
            }
        }

        if (types.isAssignable(type,
                getWildcardType(ArrayList.class.getName(), "android.os.Parcelable"))) {
            return "ParcelableArrayList";
        }
        TypeMirror sparseParcelableArray =
                getWildcardType("android.util.SparseArray", "android.os.Parcelable");

        if (types.isAssignable(type, sparseParcelableArray)) {
            return "SparseParcelableArray";
        }

        if (types.isAssignable(type, elements.getTypeElement(Serializable.class.getName()).asType())) {
            return "Serializable";
        }

        if (types.isAssignable(type, elements.getTypeElement("android.os.Parcelable").asType())) {
            return "Parcelable";
        }

        return null;
    }


    private TypeMirror getWildcardType(String type, String elementType) {
        TypeElement arrayList = processingEnv.getElementUtils().getTypeElement(type);
        TypeMirror elType = processingEnv.getElementUtils().getTypeElement(elementType).asType();
        return processingEnv.getTypeUtils()
                .getDeclaredType(arrayList, processingEnv.getTypeUtils().getWildcardType(elType, null));
    }

    private TypeMirror getArrayListType(String elementType) {
        TypeElement arrayList = processingEnv.getElementUtils().getTypeElement("java.util.ArrayList");
        TypeMirror elType = processingEnv.getElementUtils().getTypeElement(elementType).asType();
        return processingEnv.getTypeUtils().getDeclaredType(arrayList, elType);
    }

    private static String toCamelCase(String string) {
        StringBuilder sb = new StringBuilder(string);
        sb.replace(0, 1, string.substring(0, 1).toUpperCase());
        return sb.toString();

    }
}
