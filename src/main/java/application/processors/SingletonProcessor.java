package application.processors;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
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
import javax.tools.StandardLocation;
import javax.tools.Diagnostic.Kind;

import com.google.auto.service.AutoService;

import application.annotations.Singleton;
import application.services.SingletonService;

@AutoService(Processor.class)
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class SingletonProcessor extends AbstractProcessor {

	// Processor API
	private Messager messager;

	// own API
	private String projectPath;
	private long start;

	//services
	private SingletonService singletonService;

	@Override
	public synchronized void init(ProcessingEnvironment processingEnv) {
		super.init(processingEnv);
		Filer filer = processingEnv.getFiler();
		messager = processingEnv.getMessager();

		setProjectPath(filer);
		singletonService = new SingletonService();
	}

	private void setProjectPath(Filer filer) {
		try {
			URI uri = filer.getResource(StandardLocation.SOURCE_OUTPUT, "", "").toUri();
			projectPath = Paths.get(uri).getParent().toString().replace("\\", "/");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
		for(TypeElement annotation:annotations) {
			for(Element element:roundEnv.getElementsAnnotatedWith(annotation)) {
				//TODO - 25 mar 2020:coś, zrobić żeby dołączał automatycznie folder zródłowy
				String filePath = projectPath + "/src/" + element.asType().toString().replace(".", "/") + ".java";
				log(filePath);
				//TODO - 26 mar 2020:poprawić, aby wpisywane wartości w atrybuty były walidowane

				Path path = Paths.get(filePath);
				String newCode = "no code";
				switch (annotation.getSimpleName().toString()) {
					case "Singleton" : {
						start = System.currentTimeMillis();
						newCode = singletonService.processAnnotation(element.getAnnotation(Singleton.class), //
							path, element.getSimpleName().toString());
						log("Singleton: "+(System.currentTimeMillis() - start) + "[ms]");
						break;
					}
					default :
						break;
				}
				rewriteCodeIfChanged(path.toFile(), newCode);
			}
		}
		return true;
	}

	private void rewriteCodeIfChanged(File file, String newCode) {
		if(newCode.equals("no code") == false) {
			try (BufferedWriter bw = new BufferedWriter(new FileWriter(file))) {
				bw.write(newCode);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	public Set<String> getSupportedAnnotationTypes() {
		Set<String> result = new HashSet<>();
		result.add(Singleton.class.getCanonicalName());
		return result;
	}

	private void log(String value) {
		if(messager != null)
			messager.printMessage(Kind.ERROR, value);
	}
}
