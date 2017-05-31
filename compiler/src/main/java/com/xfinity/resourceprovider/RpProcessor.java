package com.xfinity.resourceprovider;

import com.google.auto.service.AutoService;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;
import com.sun.tools.javac.code.Symbol;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.squareup.javapoet.JavaFile.builder;
import static com.xfinity.resourceprovider.Utils.getPackageName;
import static java.util.Collections.singleton;
import static javax.lang.model.SourceVersion.latestSupported;

@AutoService(Processor.class)
public class RpProcessor extends AbstractProcessor {

    private static final String ANNOTATION = "@" + RpApplication.class.getSimpleName();

    private final Messager messager = new Messager();

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        messager.init(processingEnv);
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return singleton(RpApplication.class.getCanonicalName());
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (Element annotatedElement : roundEnv.getElementsAnnotatedWith(RpApplication.class)) {

            // annotation is only allowed on classes, so we can safely cast here
            TypeElement annotatedClass = (TypeElement) annotatedElement;
            if (!isValidClass(annotatedClass)) {
                continue;
            }

            try {
                List<String> rClassVars = new ArrayList<>();
                //lame.  this assumes that the application class is at the top level.  find a better way.
                String packageName = getPackageName(processingEnv.getElementUtils(), annotatedClass);
                String rClassName = packageName + ".R";

                roundEnv.getRootElements().stream().filter(element -> element instanceof TypeElement).forEach(element -> {
                    TypeElement typeElement = (TypeElement) element;
                    if (typeElement.getQualifiedName().toString().equals(rClassName)) {
                        element.getEnclosedElements().stream()
                               .filter(enclosedElement -> enclosedElement instanceof TypeElement)
                               .forEach(enclosedElement -> {
                                   if (enclosedElement.getSimpleName().toString().equals("string")) {
                                       List<? extends Element> enclosedStringElements = enclosedElement.getEnclosedElements();
                                       enclosedStringElements.stream()
                                                             .filter(stringElement -> stringElement instanceof Symbol.VarSymbol)
                                                             .forEach(stringElement -> rClassVars.add(stringElement.toString()));
                                   }
                               });
                    }
                });

                generateCode(annotatedClass, rClassVars);
            } catch (UnnamedPackageException | IOException e) {
                messager.error(annotatedElement, "Couldn't generate class for %s: %s", annotatedClass,
                               e.getMessage());
            }
        }

        return true;
    }

    private boolean isValidClass(TypeElement annotatedClass) {
        TypeElement applicationTypeElement = processingEnv.getElementUtils().getTypeElement("android.app.Application");
        return processingEnv.getTypeUtils().isAssignable(annotatedClass.asType(), applicationTypeElement.asType());
    }

    private void generateCode(TypeElement annotatedClass, List<String> rClassVars)
            throws UnnamedPackageException, IOException {
        String packageName = getPackageName(processingEnv.getElementUtils(), annotatedClass);
        RpCodeGenerator codeGenerator = new RpCodeGenerator(rClassVars);
        TypeSpec generatedClass = codeGenerator.generateClass();

        JavaFile javaFile = builder(packageName, generatedClass).build();
        javaFile.writeTo(processingEnv.getFiler());
    }
}
