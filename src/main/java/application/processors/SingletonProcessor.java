package application.processors;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
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
	private String projectPath;
	private long start;

	@Override
	public synchronized void init(ProcessingEnvironment processingEnv) {
		super.init(processingEnv);
		Filer filer = processingEnv.getFiler();
		messager = processingEnv.getMessager();
		types = processingEnv.getTypeUtils();
		elements = processingEnv.getElementUtils();

		setProjectPath(filer);
	}

	private void setProjectPath(Filer filer) {
		try {
			URI uri = filer.getResource(StandardLocation.SOURCE_OUTPUT, "", "").toUri();
			projectPath = Paths.get(uri).getParent().toString();
			projectPath = projectPath.replace("\\", "/");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
		boolean codeChanged = false;
		for(TypeElement annotation:annotations) {
			for(Element element:roundEnv.getElementsAnnotatedWith(annotation)) {
				codeChanged = false;
				//TODO - 25 mar 2020:coś, zrobić żeby dołączał automatycznie folder zródłowy
				String filePath = projectPath + "/src/" + element.asType().toString().replace(".", "/") + ".java";
				log(filePath);

				start = System.nanoTime();
				try {
					Path path = Paths.get(filePath);
					List<String> lines = Files.readAllLines(path);
					if(lines != null && lines.size() > 0) {
						//						lines.forEach(line->log(line));
						String className = element.getSimpleName().toString();

						//add field
						String fieldClause = "\tprivate volatile static " + className + " INSTANCE;\n";
						if(getFirstLineContaining(lines, fieldClause) == null) {
							String baseElement = getFirstLineContaining(lines, "class " + className);
							if(baseElement != null) {
								int baseEleLineIndex = lines.indexOf(baseElement);
								log("class: " + baseEleLineIndex + "");
								lines.add(baseEleLineIndex + 1, fieldClause);
								codeChanged = true;
							}
						}

						//add private constructor
						String constructorClause = "\tprivate " + className + "() {\n";
						if(getFirstLineContaining(lines, constructorClause) == null) {
							String baseElement = getFirstLineContaining(lines, fieldClause);
							if(baseElement != null) {
								int baseEleLineIndex = lines.indexOf(baseElement);
								log("field: " + baseEleLineIndex + "");
								lines.add(baseEleLineIndex + 1, "\n");//new line
								lines.add(baseEleLineIndex + 2, constructorClause + "\t}\n");
								codeChanged = true;
							}
						}

						//static getInstance
						List<String> methodClause = Arrays.asList("\tpublic static " + className + " getInstance() {", //
							"\t\tif(INSTANCE == null) {", "\t\t\tsynchronized (" + className + ".class) {", //
							"\t\t\t\tif(INSTANCE == null)", "\t\t\t\t\treturn new " + className + "();", //
							"\t\t\t}", "\t\t}", "\t\treturn INSTANCE;", "\t}");
						if(getFirstLineContaining(lines, methodClause.get(0)) == null) {
							String baseElement = getFirstLineContaining(lines, constructorClause);
							if(baseElement != null) {
								int baseEleLineIndex = lines.indexOf(baseElement);
								int numOfOpenedBracket = 1;
								int i = 1;
								for(i = 1;;i++) {
									String line = lines.get(baseEleLineIndex + i);
									numOfOpenedBracket += getNumOfValuesIfExists(line, '{');
									numOfOpenedBracket -= getNumOfValuesIfExists(line, '}');
									if(numOfOpenedBracket == 0)
										break;
								}
								log((baseEleLineIndex + i + 1) + "");
								lines.add(baseEleLineIndex + i,"\n");//new line
								lines.addAll(baseEleLineIndex + i + 1, methodClause);
								codeChanged = true;
							}
						}

					}
					if(codeChanged)
						Files.write(path, lines, StandardCharsets.UTF_8);
				} catch (IOException e1) {
					e1.printStackTrace();
				}
				log((System.nanoTime() - start) + "[ns]");
			}
		}
		return true;
	}

	private int getNumOfValuesIfExists(String line, char value) {
		if(line.contains(value + ""))
			return (int) Arrays.stream(line.split("")).filter(s -> s.charAt(0) == value).count();
		return 0;
	}

	private String getFirstLineContaining(List<String> lines, String value) {
		return lines.stream()//
			.filter(line -> line.contains(value.trim()))//so that whitespaces can be included in value
			.findFirst().orElse(null);
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
