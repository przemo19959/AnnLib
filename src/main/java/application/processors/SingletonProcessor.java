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

import com.google.auto.service.AutoService;

import application.annotations.Singleton;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;

@AutoService(Processor.class)
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class SingletonProcessor extends AbstractProcessor {
	// Processor API
	private Messager messager;
	private Types types;
	private Elements elements;

	// own API
	private String projectPath;

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
				log(Kind.WARNING, "Hello3" + element.toString());
				IWorkspace workspace = ResourcesPlugin.getWorkspace();
				IWorkspaceRoot root = workspace.getRoot();
				IProject[] projects = root.getProjects();
				for (IProject project : projects) {
					log(Kind.WARNING, project.toString());
				}
//		        return null;
//
//				IWorkspace workspace = ResourcesPlugin.getWorkspace();
//				IPath path = Path.fromOSString("/application/main");
//				IFile file = workspace.getRoot().getFile(path);
//				CompilationUnit compilationUnit = (CompilationUnit) JavaCore.create(file);
//				ICompilationUnit element2 = JavaCore.createCompilationUnitFrom(file);
//
//				// parse
//				ASTParser parser = ASTParser.newParser(AST.JLS13);
//				parser.setResolveBindings(true);
//				parser.setKind(ASTParser.K_COMPILATION_UNIT);
//				parser.setBindingsRecovery(true);
//				parser.setSource(element2);
//				CompilationUnit astRoot = (CompilationUnit) parser.createAST(null);
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

	private void log(Kind kind, String value) {
		if (messager != null)
			messager.printMessage(kind, value);
	}
}
