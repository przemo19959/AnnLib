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
import java.util.stream.Collectors;

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
import com.google.common.base.Preconditions;

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
	private boolean codeChanged;

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
		for(TypeElement annotation:annotations) {
			for(Element element:roundEnv.getElementsAnnotatedWith(annotation)) {
				codeChanged = false;
				//TODO - 25 mar 2020:coś, zrobić żeby dołączał automatycznie folder zródłowy
				String filePath = projectPath + "/src/" + element.asType().toString().replace(".", "/") + ".java";
				log(filePath);

				//				log(annotation.asType().getAnnotation(Singleton.class));
				Singleton a = element.getAnnotation(Singleton.class);
				String name = a.name();

				start = System.nanoTime();
				try {
					Path path = Paths.get(filePath);
					List<String> lines = Files.readAllLines(path);
					if(lines != null && lines.size() > 0) {
						String className = element.getSimpleName().toString();

						//add field
						List<String> fieldClause = Arrays.asList("\tprivate volatile static " + className + " " + name + ";");
						addStatementToCode(lines, fieldClause, false, Arrays.asList("class " + className));

						//						log(lines.stream().map(line->">"+line).collect(Collectors.joining("\n")));

						//add private constructor
						List<String> constructorClause = Arrays.asList("\tprivate " + className + "() {", "\t}");
						addStatementToCode(lines, constructorClause, false, fieldClause);

						//						log(lines.stream().map(line->">"+line).collect(Collectors.joining("\n")));

						//static getInstance
						List<String> methodClause = Arrays.asList("\tpublic static " + className + " getInstance() {", //
							"\t\tif(" + name + " == null) {", "\t\t\tsynchronized (" + className + ".class) {", //
							"\t\t\t\tif(" + name + " == null)", "\t\t\t\t\treturn new " + className + "();", //
							"\t\t\t}", "\t\t}", "\t\treturn " + name + ";", "\t}");
						addStatementToCode(lines, methodClause, true, constructorClause);
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

	private List<String> addStatementToCode(List<String> inputCode, List<String> newCode, boolean changingInside, List<String> previousStatement) {
		if(newCode.size() == 0 || previousStatement.size() == 0)
			throw new IllegalArgumentException();
		if(getFirstLineContaining(inputCode, newCode.get(0)) == null) {
			String baseElement = getFirstLineContaining(inputCode, previousStatement.get(0));
			if(baseElement != null) {
				int baseEleLineIndex = inputCode.indexOf(baseElement);
				int newCodeIndex = baseEleLineIndex + ((previousStatement.size() > 1) ? getIndexOfClosingBracket(inputCode, baseEleLineIndex) : 0);
				inputCode.add(newCodeIndex + 1, ""); //empty line
				inputCode.addAll(newCodeIndex + 2, newCode);
				codeChanged = true;
			}
		} else {
			//TODO - 25 mar 2020: poprawić, tak aby bloki kodów, w których zmieniane są środki działały poprawnie.
			if(changingInside) {
				int firstIndex=inputCode.indexOf(getFirstLineContaining(inputCode, newCode.get(0)));
				for(int i=1;i<newCode.size();i++) {
					inputCode.set(firstIndex++, newCode.get(i));
				}
				codeChanged = true;
				
//				for(int i = 0;i < newCode.size();i++) {
//					if(getFirstLineContaining(inputCode, newCode.get(i)) == null) {
//						String baseElement = getFirstLineContaining(inputCode, (i == 0) ? previousStatement.get(0) : newCode.get(i - 1));
//						if(baseElement != null) {
//							int baseEleLineIndex = inputCode.indexOf(baseElement);
//							int newCodeIndex = baseEleLineIndex + ((previousStatement.size() > 1) ? getIndexOfClosingBracket(inputCode, baseEleLineIndex) : 0);
//							inputCode.add(newCodeIndex + 1, ""); //empty line
//							inputCode.addAll(newCodeIndex + 2, newCode);
//							codeChanged = true;
//						}
//					}
//				}
			}
		}

		return inputCode;
	}

	private int getIndexOfClosingBracket(List<String> inputCode, int firstStatementIndex) {
		int numOfOpenedBracket = 1;
		int i = 1;
		for(i = 1;;i++) {
			String line = inputCode.get(firstStatementIndex + i);
			numOfOpenedBracket += getNumOfValuesIfExists(line, '{');
			numOfOpenedBracket -= getNumOfValuesIfExists(line, '}');
			if(numOfOpenedBracket == 0)
				break;
		}
		return i;
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
