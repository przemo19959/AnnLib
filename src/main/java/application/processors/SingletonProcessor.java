package application.processors;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.StandardLocation;
import javax.tools.Diagnostic.Kind;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.google.auto.service.AutoService;

import application.annotations.Singleton;

@AutoService(Processor.class)
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class SingletonProcessor extends AbstractProcessor {
	// Processor API
	private Messager messager;
	private Types types;
	private Elements elements;

	// own API
	private JavaParser jp;
	private String projectPath;

	@Override
	public synchronized void init(ProcessingEnvironment processingEnv) {
		super.init(processingEnv);
		Filer filer = processingEnv.getFiler();
		messager = processingEnv.getMessager();
		types = processingEnv.getTypeUtils();
		elements = processingEnv.getElementUtils();

		setProjectPath(filer);
		jp=new JavaParser();
	}

	private void setProjectPath(Filer filer) {
		try {
			URI uri = filer.createResource(StandardLocation.CLASS_OUTPUT, "", "tmp", (Element[]) null).toUri();
			projectPath = Paths.get(uri).getParent().getParent().toString();
			projectPath = projectPath.substring(0, projectPath.lastIndexOf("\\")).replace("\\", "/");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
		for (TypeElement annotation : annotations) {
			for (Element element : roundEnv.getElementsAnnotatedWith(annotation)) {
//				log(projectPath+"/"+element.asType().toString().replace(".", "/"));
				try {
					CompilationUnit cu=jp.parse(Paths.get(projectPath+"/"+element.asType().toString().replace(".", "/"))).getResult().orElse(null);
					if(cu!=null) {
						System.out.println(element.asType().getClass().getSimpleName());
//						cu.getClassByName(className)
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		return false;
	}

	@Override
	public Set<String> getSupportedAnnotationTypes() {
		Set<String> result = new HashSet<>();
		result.add(Singleton.class.getCanonicalName());
		return result;
	}

	private void log(String value) {
		if (messager != null)
			messager.printMessage(Kind.ERROR, value);
	}
}
