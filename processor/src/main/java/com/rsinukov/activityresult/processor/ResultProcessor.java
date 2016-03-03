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

import java.io.Serializable;
import java.util.*;

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
        messager = processingEnv.getMessager();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        Elements elementUtils = processingEnv.getElementUtils();
        Filer filer = processingEnv.getFiler();

        try {
            List<AnnotatedClass> annotatedClasses = new ArrayList<AnnotatedClass>();

            for (Element element : roundEnv.getElementsAnnotatedWith(ActivityResult.class)) {
                TypeElement activityElement = (TypeElement) element;
                annotatedClasses.add(new AnnotatedClass(activityElement));
            }
            for (Element element : roundEnv.getElementsAnnotatedWith(ActivityResults.class)) {
                TypeElement activityElement = (TypeElement) element;
                annotatedClasses.add(new AnnotatedClass(activityElement));
            }

            for (AnnotatedClass annotatedClass : annotatedClasses) {
                String annotatedClassName = annotatedClass.getName().toString();
                String packageName = annotatedClassName.substring(0, annotatedClassName.lastIndexOf("."));
                String resultClassSimpleName = annotatedClass.getSimpleName() + "Result";
                ClassName resultClassName = ClassName.get(packageName, resultClassSimpleName);

                List<MethodSpec> methods = new ArrayList<MethodSpec>();
                List<FieldSpec> fields = new ArrayList<FieldSpec>();

                List<FieldToGenerate> allFields = createResultClassFields(annotatedClass);

                MethodSpec constructor = createResultClassConstructor();

                TypeMirror intentType = elementUtils.getTypeElement("android.content.Intent").asType();
                String intentSimpleClassName = "Intent";
                TypeMirror bundleType = elementUtils.getTypeElement("android.os.Bundle").asType();

                createResultClassWithIntent(
                        annotatedClass,
                        resultClassSimpleName,
                        resultClassName,
                        methods,
                        allFields,
                        intentType,
                        bundleType
                );
                createResultClassGetters(methods, fields, allFields);
                createResultClassToIntent(
                        annotatedClass,
                        methods,
                        allFields,
                        intentType,
                        intentSimpleClassName,
                        bundleType
                );

                TypeSpec.Builder resultBuilder =
                        createBuilderInnerClass(annotatedClass, resultClassSimpleName, resultClassName, allFields);

                //create result class
                TypeSpec resultClass = TypeSpec.classBuilder(resultClassSimpleName)
                        .addModifiers(Modifier.PUBLIC)
                        .addMethod(constructor)
                        .addMethods(methods)
                        .addFields(fields)
                        .addType(resultBuilder.build())
                        .build();

                JavaFile resultFile = JavaFile.builder(packageName, resultClass).build();
                resultFile.writeTo(filer);
            }
        } catch (IllegalArgumentException e) {
            messager.printMessage(Diagnostic.Kind.ERROR, e.getMessage());
        } catch (Exception e) {
            StringBuilder stackTrace = new StringBuilder();
            for (StackTraceElement element : e.getStackTrace()) {
                stackTrace.append(element.toString()).append("\n");
            }
            messager.printMessage(Diagnostic.Kind.ERROR, e.getMessage() + " \n" + stackTrace);
        }

        return true;
    }

    private List<FieldToGenerate> createResultClassFields(AnnotatedClass annotatedClass) {
        List<FieldToGenerate> allFields = new ArrayList<FieldToGenerate>(annotatedClass.getOptionalFields());
        allFields.addAll(annotatedClass.getRequiredFields());
        return allFields;
    }

    private MethodSpec createResultClassConstructor() {
        return MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PRIVATE)
                .build();
    }

    // TODO: add custom parcel functionality
    private void createResultClassWithIntent(
            AnnotatedClass annotatedClass,
            String resultClassSimpleName,
            ClassName resultClassName,
            List<MethodSpec> methods,
            List<FieldToGenerate> allFields,
            TypeMirror intentType,
            TypeMirror bundleType
    ) {
        MethodSpec.Builder withMethod = MethodSpec.methodBuilder("with")
                .returns(resultClassName)
                .addModifiers(Modifier.PUBLIC)
                .addModifiers(Modifier.STATIC)
                .addParameter(TypeName.get(intentType), "intent")
                .addStatement("$L bundle = intent.getExtras()", bundleType)
                .addStatement("$L result = new $L()", resultClassSimpleName, resultClassSimpleName);
        for (FieldToGenerate field : allFields) {

            if (field.hasCustomBundler()) {
                String bundlerVariableName = field.getName() + "Bundler";
                withMethod.addStatement(
                        "$L $L = new $L()",
                        field.getBundlerClassName(),
                        bundlerVariableName,
                        field.getBundlerClassName()
                );
                withMethod.addStatement(
                        "result.$L = $L.get(\"$L\", bundle)",
                        field.getName(),
                        bundlerVariableName,
                        field.getName()
                );
                continue;
            }

            String operation = getOperation(field);
            if (operation == null) {
                messager.printMessage(
                        Diagnostic.Kind.ERROR,
                        String.format("Can not get @%s from bundle in @%s", field.getName(), annotatedClass.getName())
                );
                // TODO: throw exception
            }

            if (operation.equals("Serializable")) {
                withMethod.addStatement(
                        "result.$L = ($L) bundle.get$L(\"$L\")",
                        field.getName(),
                        field.getRawType(),
                        operation,
                        field.getName()
                );
            } else {
                withMethod.addStatement(
                        "result.$L = bundle.get$L(\"$L\")",
                        field.getName(),
                        operation,
                        field.getName()
                );
            }
        }
        withMethod.addStatement("return result");
        methods.add(withMethod.build());
    }

    // TODO: add custom parcel functionality
    private void createResultClassToIntent(
            AnnotatedClass annotatedClass,
            List<MethodSpec> methods,
            List<FieldToGenerate> allFields,
            TypeMirror intentType,
            String intentSimpleClassName,
            TypeMirror bundleType
    ) {
        MethodSpec.Builder toIntentMethod = MethodSpec.methodBuilder("toIntent")
                .returns(TypeName.get(intentType))
                .addModifiers(Modifier.PUBLIC)
                .addStatement("$L bundle = new $L()", bundleType, bundleType);

        for (FieldToGenerate field : allFields) {
            if (field.hasCustomBundler()) {
                String bundlerVariableName = field.getName() + "Bundler";
                toIntentMethod.addStatement(
                        "$L $L = new $L()",
                        field.getBundlerClassName(),
                        bundlerVariableName,
                        field.getBundlerClassName()
                );
                toIntentMethod.addStatement(
                        "$L.put(\"$L\", $L, bundle)",
                        bundlerVariableName,
                        field.getName(),
                        field.getName()
                );
                continue;
            }

            String operation = getOperation(field);
            if (operation == null) {
                messager.printMessage(
                        Diagnostic.Kind.ERROR,
                        String.format("Can not put @%s to bundle in @%s", field.getName(), annotatedClass.getName())
                );
                // TODO: throw exception
            }
            toIntentMethod.addStatement("bundle.put$L(\"$L\", $L)", operation, field.getName(), field.getName());
        }
        toIntentMethod.addStatement("$L intent = new $L()", intentSimpleClassName, intentSimpleClassName);
        toIntentMethod.addStatement("intent.putExtras(bundle)");
        toIntentMethod.addStatement("return intent");
        methods.add(toIntentMethod.build());
    }

    private void createResultClassGetters(
            List<MethodSpec> methods,
            List<FieldSpec> fields,
            List<FieldToGenerate> allFields
    ) {
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
    }

    private TypeSpec.Builder createBuilderInnerClass(
            AnnotatedClass annotatedClass,
            String resultClassSimpleName,
            ClassName resultClassName,
            List<FieldToGenerate> allFields
    ) {
        TypeSpec.Builder resultBuilder = TypeSpec.classBuilder("Builder")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC);

        createBuilderClassConstructor(annotatedClass, resultBuilder);
        createBuilderClassFields(allFields, resultBuilder);
        createBuilderClassOptionalSetters(annotatedClass, resultBuilder);
        createBuilderClassBuild(resultClassSimpleName, resultClassName, allFields, resultBuilder);

        return resultBuilder;
    }

    private void createBuilderClassConstructor(
            AnnotatedClass annotatedClass,
            TypeSpec.Builder resultBuilder
    ) {
        MethodSpec.Builder builderConstructor = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC);
        for (FieldToGenerate field : annotatedClass.getRequiredFields()) {
            builderConstructor.addParameter(TypeName.get(field.getType()), field.getName());
            builderConstructor.addStatement("this.$L = $L", field.getName(), field.getName());
        }
        resultBuilder.addMethod(builderConstructor.build());
    }

    private void createBuilderClassFields(
            List<FieldToGenerate> allFields,
            TypeSpec.Builder resultBuilder
    ) {
        for (FieldToGenerate field : allFields) {
            resultBuilder.addField(TypeName.get(field.getType()), field.getName(), Modifier.PRIVATE);
        }
    }

    private void createBuilderClassOptionalSetters(
            AnnotatedClass annotatedClass,
            TypeSpec.Builder resultBuilder
    ) {
        for (FieldToGenerate field : annotatedClass.getOptionalFields()) {
            resultBuilder.addMethod(MethodSpec.methodBuilder("set" + toCamelCase(field.getName()))
                    .returns(ClassName.get(annotatedClass.getName().toString() + "Result", "Builder"))
                    .addModifiers(Modifier.PUBLIC)
                    .addParameter(TypeName.get(field.getType()), field.getName())
                    .addStatement("this.$L = $L", field.getName(), field.getName())
                    .addStatement("return this")
                    .build());
        }
    }

    private void createBuilderClassBuild(
            String resultClassSimpleName,
            ClassName resultClassName,
            List<FieldToGenerate> allFields,
            TypeSpec.Builder resultBuilder
    ) {
        MethodSpec.Builder builderMethodSpec = MethodSpec.methodBuilder("build")
                .returns(resultClassName)
                .addModifiers(Modifier.PUBLIC)
                .addStatement("$L result = new $L()", resultClassSimpleName, resultClassSimpleName);
        for (FieldToGenerate field : allFields) {
            builderMethodSpec.addStatement("result.$L = $L", field.getName(), field.getName());
        }
        builderMethodSpec.addStatement("return result");
        resultBuilder.addMethod(builderMethodSpec.build());
    }

    protected String getOperation(FieldToGenerate fieldToGenerate) {
        String op = ARGUMENT_TYPES.get(fieldToGenerate.getRawType());
        if (op != null) {
            if (fieldToGenerate.isArray()) {
                return op + "Array";
            } else {
                return op;
            }
        }

        Elements elements = processingEnv.getElementUtils();
        TypeMirror type = fieldToGenerate.getType();
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

        if (types.isAssignable(type, elements.getTypeElement("android.os.Parcelable").asType())) {
            return "Parcelable";
        }

        if (types.isAssignable(type, elements.getTypeElement(Serializable.class.getName()).asType())) {
            messager.printMessage(
                    Diagnostic.Kind.WARNING,
                    String.format(
                            "It's better not to use Serializable. Consider @%s implement Parcelable in @%s",
                            fieldToGenerate.getName(),
                            fieldToGenerate.getActivityElement().getQualifiedName()
                    )
            );
            return "Serializable";
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
