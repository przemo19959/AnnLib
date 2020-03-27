package application.services;

import java.io.IOException;
import java.nio.file.Path;
import java.text.MessageFormat;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.Modifier.Keyword;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;

import application.annotations.Singleton;

public class SingletonService {
	private static final String GET_INSTANCE_TEMPLATE1 = "if({0} == null) '{'synchronized ({1}.class) '{'if({2} == null)return new {3}();'}}'";
	private static final String GET_INSTANCE_TEMPLATE2 = "if({0} == null) {0}=new {1}();";
	private boolean codeChanged;

	//annotation fields
	private String name;
	private String methodName;
	private boolean threadSafe;

	public String processAnnotation(Singleton annotation, Path path, String className) {
		codeChanged = false;
		name = annotation.name();
		methodName = annotation.methodName();
		threadSafe = annotation.threadSafe();

		try {
			CompilationUnit cu = StaticJavaParser.parse(path);
			ClassOrInterfaceDeclaration cls = cu.getClassByName(className).orElse(null);
			if(cls != null) {
				addOrRenameSingletonInstanceField(cls, className);
				addOrPrivateDefaultConstuctor(cls);
				addOrModifyGetInstanceMethod(cls, className);
				if(codeChanged)
					return cu.toString();
			}
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		return "no code";
	}

	private void addOrRenameSingletonInstanceField(ClassOrInterfaceDeclaration cls, String className) {
		//create correct fieldDeclaration
		FieldDeclaration fd = new FieldDeclaration().setModifiers(Keyword.PRIVATE, Keyword.VOLATILE, Keyword.STATIC);
		VariableDeclarator vd = new VariableDeclarator().setType(className).setName(name);
		fd.addVariable(vd);

		//find field with same signature (excluding field name)
		FieldDeclaration f = cls.getFields().stream()//
			.filter(fd1 -> fd1.getModifiers().equals(fd.getModifiers()) && //
							fd1.getVariable(0).getType().equals(fd.getVariable(0).getType()))//
			.findFirst().orElse(null);

		if(f != null) { //if exists, change name to proper
			if(f.getVariable(0).getName().equals(fd.getVariable(0).getName()) == false) { //if names are different
				f.getVariable(0).setName(name);
				codeChanged = true;
			}
		} else { //if doesn't exist, add field to class
			cls.addMember(fd);
			codeChanged = true;
		}
	}

	private void addOrPrivateDefaultConstuctor(ClassOrInterfaceDeclaration cls) {
		if(cls.getDefaultConstructor().orElse(null) == null) {
			cls.addConstructor(Keyword.PRIVATE); //add private constructor if no default constructor
			codeChanged = true;
		} else {
			ConstructorDeclaration cd = cls.getDefaultConstructor().get();
			if(cd.getModifiers().contains(Modifier.publicModifier())) { //if existing constuctor is public
				cd.setModifiers(Keyword.PRIVATE);
				codeChanged = true;
			}
		}
	}

	private void addOrModifyGetInstanceMethod(ClassOrInterfaceDeclaration cls, String className) {
		//create correct methodDeclaration
		String methodClause = (threadSafe) ? MessageFormat.format(GET_INSTANCE_TEMPLATE1, name, className, name, className) : MessageFormat.format(GET_INSTANCE_TEMPLATE2, name, className);
		MethodDeclaration md = new MethodDeclaration()//
			.setModifiers(Keyword.PUBLIC, Keyword.STATIC)//
			.setType(className)//
			.setName(methodName);
		BlockStmt body = new BlockStmt();
		body.addStatement(methodClause);
		body.addStatement(new ReturnStmt(name));
		md.setBody(body);

		//find method with same signature (excluding method name)
		MethodDeclaration m = cls.getMethods().stream()//
			.filter(md1 -> md1.getModifiers().equals(md.getModifiers()) && //
							md1.getType().equals(md.getType()))//
			.findFirst().orElse(null);

		if(m != null) { //if exists, change name and body to proper
			if(m.getName().equals(md.getName()) == false) { //if names are different
				m.setName(methodName);
				codeChanged = true;
			}
			BlockStmt body1 = m.getBody().orElse(null);
			//if body doesn't exist, add
			if(body1 == null) {
				m.setBody(md.getBody().get());
				codeChanged = true;
			} else {
				if(m.getBody().get().equals(md.getBody().get()) == false) {
					m.setBody(md.getBody().get());
					codeChanged = true;
				}
			}
		} else { //if doesn't exist, add method to class
			cls.addMember(md);
			codeChanged = true;
		}
	}
}
