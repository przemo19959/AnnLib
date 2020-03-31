package application.services;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import javax.lang.model.element.Element;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier.Keyword;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MarkerAnnotationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

import application.annotations.ThreadTemplate;
import application.processors.AnnotationException;

public class ThreadTemplateService {
	private boolean codeChanged;
	private Element annotationElement;

	//annotation fields
	private String threadName;

	public String processAnnotation(Element annotationElement, Path path, String className)
																							throws AnnotationException {
		this.annotationElement = annotationElement;
		codeChanged = false;

		ThreadTemplate annotation = annotationElement.getAnnotation(ThreadTemplate.class);
		threadName = annotation.threadName();

		try {
			CompilationUnit cu = StaticJavaParser.parse(path);
			ClassOrInterfaceDeclaration cls = cu.getClassByName(className).orElse(null);
			if(cls != null) {
				changeClassSignature(cls);
				addControlField(cls, "SUSPEND", true);
				addControlField(cls, "STOP", false);
				addConstructor(cls, className);
				addRunMethod(cls);
				if(codeChanged)
					return cu.toString();
			}
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		return "no code";
	}

	private FieldDeclaration createControlField(String fieldName, boolean value) {
		VariableDeclarator vd = new VariableDeclarator()//
			.setType(AtomicBoolean.class.getSimpleName())//
			.setName(fieldName)//
			.setInitializer("new AtomicBoolean(" + value + ")");
		FieldDeclaration fd = new FieldDeclaration()//
			.setModifiers(Keyword.PRIVATE, Keyword.STATIC, Keyword.FINAL)//
			.addVariable(vd);
		return fd;
	}

	private void addControlField(ClassOrInterfaceDeclaration cls, String fieldName, boolean value)
																									throws AnnotationException {
		FieldDeclaration fd = createControlField(fieldName, value);
		FieldDeclaration f = Utils.findWhere(cls.getFields(), fd1 -> fd1.getVariable(0).getName()
			.equals(fd.getVariable(0).getName()));
		if(f != null) {
			if(f.getModifiers().equals(fd.getModifiers())==false) {
				f.setModifiers(fd.getModifiers());
				codeChanged=true;
			}
			
			if(f.getVariable(0).getType().equals(fd.getVariable(0).getType())==false) {
				f.getVariable(0).setType(fd.getVariable(0).getType());
				codeChanged=true;
			}
			
			Expression exp=f.getVariable(0).getInitializer().orElse(null);
			if(exp!=null) {
				if(exp.toString().equals(fd.getVariable(0).getInitializer().get().toString())==false) {
					f.getVariable(0).setInitializer(fd.getVariable(0).getInitializer().get());
					codeChanged=true;
				}
			}else {
				f.getVariable(0).setInitializer(fd.getVariable(0).getInitializer().get());
				codeChanged=true;
			}
			
		} else {
			cls.addMember(fd);
			codeChanged = true;
		}
	}

	private void addConstructor(ClassOrInterfaceDeclaration cls, String className) {
		String methodClause = "";
		if(threadName.equals("") == false) {
			methodClause = "new Thread(this, \"" + threadName + "\").start();";
		} else {
			methodClause = "new Thread(this).start();";
		}
		ConstructorDeclaration cd = new ConstructorDeclaration().setName(className).setModifiers(
			Keyword.PUBLIC);
		cd.getBody().addStatement(methodClause);

		ConstructorDeclaration c = Utils.findWhere(cls.getConstructors(), c1 -> c1.getName().equals(
			cd.getName()));
		if(c != null) {
			if(c.getModifiers().equals(cd.getModifiers()) == false) {
				c.setModifiers(cd.getModifiers());
				codeChanged = true;
			}

			BlockStmt body = c.getBody();
			if(body.isEmpty()) {
				c.setBody(cd.getBody());
				codeChanged = true;
			} else {
				List<Statement> sts = body.getStatements().stream()//
					.filter(st1 -> st1.toString().startsWith("new Thread(this") && st1.toString()
						.endsWith(".start();"))//
					.collect(Collectors.toList());

				if(sts.size() > 1) {
					sts.forEach(st -> body.remove(st));
					body.addStatement(cd.getBody().getStatement(0));
					codeChanged = true;
				} else if(sts.size() == 1) {
					if(body.getStatements().indexOf(sts.get(0)) < body.getStatements().size() - 1) {
						body.remove(sts.get(0));
						body.addStatement(cd.getBody().getStatement(0));
						codeChanged = true;
					}
					if(body.getStatement(body.getStatements().size() - 1).equals(cd.getBody()
						.getStatement(0)) == false) {
						body.remove(sts.get(0));
						body.addStatement(cd.getBody().getStatement(0));
						codeChanged = true;
					}

				} else if(sts.size() == 0) {
					body.addStatement(cd.getBody().getStatement(0));
					codeChanged = true;
				}
			}

		} else {
			cls.addMember(cd);
			codeChanged = true;
		}
	}

	private void addRunMethod(ClassOrInterfaceDeclaration cls) {
		MethodDeclaration md = new MethodDeclaration()//
			.setModifiers(Keyword.PUBLIC)//
			.setType("void")//
			.setName("run")//
			.addAnnotation(new MarkerAnnotationExpr(Override.class.getSimpleName()));
		MethodDeclaration m = Utils.findWhere(cls.getMethods(), md1 -> md1.getName().equals(md
			.getName()));
		if(m != null) {
			if(m.getModifiers().equals(md.getModifiers()) == false) {
				m.setModifiers(md.getModifiers());
				codeChanged = true;
			}

			if(m.getType().equals(md.getType()) == false) {
				m.setType(md.getType());
				codeChanged = true;
			}

			AnnotationExpr a = Utils.findWhere(m.getAnnotations(), a1 -> a1.getNameAsString().equals(
				Override.class.getSimpleName()));
			if(a == null) {
				m.addAnnotation(md.getAnnotation(0));
				codeChanged = true;
			}
		} else {
			cls.addMember(md);
			codeChanged = true;
		}
	}

	private void changeClassSignature(ClassOrInterfaceDeclaration cls) {
		ClassOrInterfaceType type = new ClassOrInterfaceType();
		type.setName(Runnable.class.getSimpleName());

		if(cls.getImplementedTypes().contains(type) == false) {
			cls.addImplementedType(Runnable.class);
			codeChanged = true;
		}
	}
}
