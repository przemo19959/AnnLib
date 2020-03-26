package application.processors;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
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

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.Modifier.Keyword;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.nodeTypes.NodeWithType;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.type.Type;
import com.google.auto.service.AutoService;

import application.annotations.Singleton;

@AutoService(Processor.class)
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class SingletonProcessor extends AbstractProcessor {
	private static final String GET_INSTANCE_TEMPLATE1 = "if({0} == null) '{'synchronized ({1}.class) '{'if({2} == null)return new {3}();'}}'";
	private static final String GET_INSTANCE_TEMPLATE2 = "if({0} == null) {0}=new {1}();";

	// Processor API
	private Messager messager;
	//	private Types types;
	//	private Elements elements;

	// own API
	private String projectPath;
	private long start;
	private boolean codeChanged;

	@Override
	public synchronized void init(ProcessingEnvironment processingEnv) {
		super.init(processingEnv);
		Filer filer = processingEnv.getFiler();
		messager = processingEnv.getMessager();
		//		types = processingEnv.getTypeUtils();
		//		elements = processingEnv.getElementUtils();

		setProjectPath(filer);
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
				codeChanged = false;
				//TODO - 25 mar 2020:coś, zrobić żeby dołączał automatycznie folder zródłowy
				String filePath = projectPath + "/src/" + element.asType().toString().replace(".", "/") + ".java";
				log(filePath);
				//TODO - 26 mar 2020:poprawić, aby wpisywane wartości w atrybuty były walidowane

				Singleton a = element.getAnnotation(Singleton.class);
				String name = a.name();
				String methodName=a.methodName();
				boolean threadSafe=a.threadSafe();

				start = System.currentTimeMillis();
				try {
					Path path = Paths.get(filePath);
					CompilationUnit cu = StaticJavaParser.parse(path);
					String className = element.getSimpleName().toString();
					ClassOrInterfaceDeclaration cls = cu.getClassByName(className).orElse(null);
					if(cls != null) {

						//instance field
						FieldDeclaration fd = new FieldDeclaration().setModifiers(Keyword.PRIVATE, Keyword.VOLATILE, Keyword.STATIC);
						VariableDeclarator vd = new VariableDeclarator().setType(className).setName(name);
						fd.addVariable(vd);
						
						FieldDeclaration f = cls.getFields().stream()//
							.filter(fd1 -> fd1.getModifiers().equals(fd.getModifiers()) && //
											fd1.getVariable(0).getType().equals(fd.getVariable(0).getType()))//
							.findFirst().orElse(null);
						if(f != null) {
							if(f.getVariable(0).getName().equals(fd.getVariable(0).getName()) == false) {
								f.getVariable(0).setName(name);
								codeChanged = true;
							}
						} else {
							cls.addMember(fd);
							codeChanged = true;
						}

						//create constructor
						if(cls.getDefaultConstructor().orElse(null) == null) {
							cls.addConstructor(Keyword.PRIVATE);
							codeChanged = true;
						} else {
							ConstructorDeclaration cd = cls.getDefaultConstructor().get();
							if(cd.getModifiers().contains(Modifier.publicModifier())) {
								cd.setModifiers(Keyword.PRIVATE);
								codeChanged = true;
							}
						}

						//create getInstance method
						String methodClause = (threadSafe)?MessageFormat.format(GET_INSTANCE_TEMPLATE1, name, className, name, className):MessageFormat.format(GET_INSTANCE_TEMPLATE2, name,className);
						MethodDeclaration md = new MethodDeclaration()//
							.setModifiers(Keyword.PUBLIC, Keyword.STATIC)//
							.setType(className)//
							.setName(methodName);
						BlockStmt body = new BlockStmt();
						body.addStatement(methodClause);
						body.addStatement(new ReturnStmt(name));
						md.setBody(body);

						if(cls.getMethodsByName(methodName).size() == 0) {
							cls.addMember(md);
							codeChanged = true;
						} else {
							MethodDeclaration md1 = cls.getMethodsByName(methodName).get(0);
							if(md1.equals(md) == false) {
								cls.remove(md1);
								cls.addMember(md);
								codeChanged = true;
							}
						}
						rewriteCodeIfChanged(codeChanged, path.toFile(), cu.toString());
					}
				} catch (IOException e1) {
					e1.printStackTrace();
				}
				log((System.currentTimeMillis() - start) + "[ms]");
			}
		}
		return true;
	}

	private void rewriteCodeIfChanged(boolean codeChanged, File file, String newCode) {
		if(codeChanged) {
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
