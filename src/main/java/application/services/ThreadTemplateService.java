package application.services;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

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
import application.services.general.ServiceTemplate;
import application.services.general.Utils;

public class ThreadTemplateService extends ServiceTemplate<ThreadTemplate> {

	//annotation fields
	private String threadName;
	private boolean doBeforeStart;
	private boolean doAfterStop;

	@Override
	public void step1_injectAnnotation(ThreadTemplate annotation) {
		threadName = annotation.threadName();
		doBeforeStart = annotation.doBeforeStart();
		doAfterStop = annotation.doAfterStop();
	}

	@Override
	public void step2_processing(ClassOrInterfaceDeclaration cls, String className) {
		changeClassSignature(cls);
		addField(cls, createThreadField());
		addControlField(cls, createControlField("SUSPEND", true));
		addConstructor(cls, className);

		addSuspendMethod(cls, "suspend", "SUSPEND");
		addResumeMethod(cls, createResumeMethod());
		addResumeMethod(cls, createStopMethod());

		addDoInThreadMethod(cls, "doInThread");
		if(doBeforeStart)
			addDoInThreadMethod(cls, "doBeforeStart");
		else {
			if(cls.getMethodsByName("doBeforeStart").size() > 0) {
				cls.remove(cls.getMethodsByName("doBeforeStart").get(0));
			}
		}
		if(doAfterStop)
			addDoInThreadMethod(cls, "doAfterStop");
		else {
			if(cls.getMethodsByName("doAfterStop").size() > 0) {
				cls.remove(cls.getMethodsByName("doAfterStop").get(0));
			}
		}
		addRunMethod(cls);
	}

	private FieldDeclaration createThreadField() {
		VariableDeclarator vd = new VariableDeclarator()//
			.setType(Thread.class.getSimpleName())//
			.setName("t");
		FieldDeclaration fd = new FieldDeclaration()//
			.setModifiers(Keyword.PRIVATE, Keyword.VOLATILE)//
			.addVariable(vd);
		return fd;
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

	private void addField(ClassOrInterfaceDeclaration cls, FieldDeclaration fd) throws AnnotationException {
		FieldDeclaration f = Utils.findWhere(cls.getFields(), fd1 -> Utils.nameEqual(0, fd1, fd));
		if(f != null) {
			if(Utils.modifiersEqual(f, fd) == false) {
				f.setModifiers(fd.getModifiers());
				codeChanged = true;
			}

			if(Utils.typeEqual(0, f, fd) == false) {
				f.getVariable(0).setType(fd.getVariable(0).getType());
				codeChanged = true;
			}
		} else {
			cls.addMember(fd);
			codeChanged = true;
		}
	}

	private void addControlField(ClassOrInterfaceDeclaration cls, FieldDeclaration fd) throws AnnotationException {
		FieldDeclaration f = Utils.findWhere(cls.getFields(), fd1 -> Utils.nameEqual(0, fd1, fd));
		if(f != null) {
			if(Utils.modifiersEqual(f, fd) == false) {
				f.setModifiers(fd.getModifiers());
				codeChanged = true;
			}

			if(Utils.typeEqual(0, f, fd) == false) {
				f.getVariable(0).setType(fd.getVariable(0).getType());
				codeChanged = true;
			}

			Expression exp = f.getVariable(0).getInitializer().orElse(null);
			if(exp != null) {
				if(exp.toString().equals(fd.getVariable(0).getInitializer().get().toString()) == false) {
					f.getVariable(0).setInitializer(fd.getVariable(0).getInitializer().get());
					codeChanged = true;
				}
			} else {
				f.getVariable(0).setInitializer(fd.getVariable(0).getInitializer().get());
				codeChanged = true;
			}

		} else {
			cls.addMember(fd);
			codeChanged = true;
		}
	}

	private void addConstructor(ClassOrInterfaceDeclaration cls, String className) throws AnnotationException {
		String methodClause = threadName.equals("") ? "t = new Thread(this);" : "t = new Thread(this, \"" + threadName + "\");";
		ConstructorDeclaration cd = new ConstructorDeclaration()//
			.setName(className);//
		cd.getBody().addStatement(methodClause).addStatement("t.start();");

		ConstructorDeclaration c = Utils.findWhere(cls.getConstructors(), c1 -> Utils.nameEqual(c1, cd));

		if(c != null) {
			BlockStmt body = c.getBody();
			if(body.isEmpty()) {
				c.setBody(cd.getBody());
				codeChanged = true;
			} else {
				final String tmp = methodClause;
				List<Statement> sts = body.getStatements().stream()//
					.filter(st1 -> st1.toString().startsWith("t = new Thread(this"))//
					.collect(Collectors.toList());

				if(sts.size() > 1) {
					sts.forEach(st -> body.remove(st));
					body.addStatement(cd.getBody().getStatement(0));
					codeChanged = true;
				} else if(sts.size() == 1) {
					if(sts.get(0).equals(cd.getBody().getStatement(0)) == false) {
						body.setStatement(body.getStatements().indexOf(sts.get(0)), cd.getBody().getStatement(0));
						codeChanged = true;
					}
				} else if(sts.size() == 0) {
					body.addStatement(cd.getBody().getStatement(0));
					codeChanged = true;
				}

				sts = body.getStatements().stream()//
					.filter(st1 -> st1.toString().equals("t.start();"))//
					.collect(Collectors.toList());
				if(sts.size() > 1) {
					sts.forEach(st -> body.remove(st));
					body.addStatement(cd.getBody().getStatement(1));
					codeChanged = true;
				} else if(sts.size() == 0) {
					body.addStatement(cd.getBody().getStatement(1));
					codeChanged = true;
				}

				Statement st1 = Utils.findWhere(body.getStatements(), st -> st.toString().equals(tmp));
				int index1 = body.getStatements().indexOf(st1);
				Statement st2 = Utils.findWhere(body.getStatements(), st -> st.toString().equals("t.start();"));
				int index2 = body.getStatements().indexOf(st2);
				if(index2 < index1) {
					body.setStatement(index1, st2);
					body.setStatement(index2, st1);
					codeChanged = true;
				}
			}

		} else {
			cls.addMember(cd);
			codeChanged = true;
		}
	}

	private void addDoInThreadMethod(ClassOrInterfaceDeclaration cls, String mName) {
		MethodDeclaration md = new MethodDeclaration()//
			.setModifiers(Keyword.PRIVATE)//
			.setType("void")//
			.setName(mName);

		if(mName.equals("doAfterStop")) {
			md.addParameter(Thread.class.getSimpleName(), "thisThread");
		}

		MethodDeclaration m = Utils.findWhere(cls.getMethods(), m1 -> Utils.nameEqual(m1, md));
		if(m != null) {
			if(Utils.modifiersEqual(m, md) == false) {
				m.setModifiers(md.getModifiers());
				codeChanged = true;
			}

			if(mName.equals("doAfterStop") && m.getParameters().contains(md.getParameter(0)) == false) {
				m.addParameter(md.getParameter(0));
				codeChanged = true;
			}
		} else {
			cls.addMember(md);
			codeChanged = true;
		}
	}

	private void addRunMethod(ClassOrInterfaceDeclaration cls) throws AnnotationException {
		String methodClause = "while(t==thisThread) {try {if(SUSPEND.get()) {synchronized(this) {while(SUSPEND.get() && t==thisThread){wait();}}}}"
								+ "catch(InterruptedException ie) {ie.printStackTrace();}if(t==null)break;doInThread();}";
		BlockStmt mdBody = new BlockStmt();
		if(doBeforeStart)
			mdBody.addStatement("doBeforeStart();");

		mdBody.addStatement("Thread thisThread = Thread.currentThread();")//
			.addStatement(methodClause);

		if(doAfterStop)
			mdBody.addStatement("doAfterStop(thisThread);");

		MethodDeclaration md = new MethodDeclaration()//
			.setModifiers(Keyword.PUBLIC)//
			.setType("void")//
			.setName("run")//
			.setBody(mdBody)//
			.addAnnotation(new MarkerAnnotationExpr(Override.class.getSimpleName()));

		MethodDeclaration m = Utils.findWhere(cls.getMethods(), md1 -> Utils.nameEqual(md1, md));
		if(m != null) {
			if(Utils.modifiersEqual(m, md) == false) {
				m.setModifiers(md.getModifiers());
				codeChanged = true;
			}

			if(Utils.typeEqual(m, md) == false) {
				m.setType(md.getType());
				codeChanged = true;
			}

			AnnotationExpr a = Utils.findWhere(m.getAnnotations(), a1 -> a1.getNameAsString().equals(Override.class.getSimpleName()));
			if(a == null) {
				m.addAnnotation(md.getAnnotation(0));
				codeChanged = true;
			}

			BlockStmt body = m.getBody().orElse(null);
			if(body != null) {
				if(body.isEmpty()) {
					m.setBody(md.getBody().get());
					codeChanged = true;
				} else {
					if(body.equals(md.getBody().get()) == false) {
						m.setBody(md.getBody().get());
						codeChanged = true;
					}
				}
			} else {
				m.setBody(md.getBody().get());
				codeChanged = true;
			}
		} else {
			cls.addMember(md);
			codeChanged = true;
		}
	}

	private MethodDeclaration createResumeMethod() {
		String methodClause = "if(SUSPEND.get()) {SUSPEND.set(false);notify();}";
		return new MethodDeclaration()//
			.setModifiers(Keyword.PUBLIC, Keyword.SYNCHRONIZED)//
			.setName("resume")//
			.setType("void")//
			.setBody(new BlockStmt().addStatement(methodClause));
	}

	private MethodDeclaration createStopMethod() {
		String methodClause = "if(t!=null) {t=null;notify();}";
		return new MethodDeclaration()//
			.setModifiers(Keyword.PUBLIC, Keyword.SYNCHRONIZED)//
			.setName("stop")//
			.setType("void")//
			.setBody(new BlockStmt().addStatement(methodClause));
	}

	private void addResumeMethod(ClassOrInterfaceDeclaration cls, MethodDeclaration md) {
		MethodDeclaration m = Utils.findWhere(cls.getMethods(), m1 -> Utils.nameEqual(m1, md));
		if(m != null) {
			if(Utils.modifiersEqual(m, md) == false) {
				m.setModifiers(md.getModifiers());
				codeChanged = true;
			}

			if(Utils.typeEqual(m, md) == false) {
				m.setType(md.getType());
				codeChanged = true;
			}

			BlockStmt body = m.getBody().orElse(null);
			if(body != null) {
				if(body.getStatements().contains(md.getBody().get().getStatement(0)) == false) {
					body.addStatement(0, md.getBody().get().getStatement(0));
					codeChanged = true;
				}
			} else {
				m.setBody(md.getBody().get());
				codeChanged = true;
			}
		} else {
			cls.addMember(md);
			codeChanged = true;
		}
	}

	private void addSuspendMethod(ClassOrInterfaceDeclaration cls, String mName, String flagName) {
		String methodClause = "if(" + flagName + ".get()==false) " + flagName + ".set(true);";
		MethodDeclaration md = new MethodDeclaration()//
			.setModifiers(Keyword.PUBLIC)//
			.setName(mName)//
			.setType("void")//
			.setBody(new BlockStmt().addStatement(methodClause));

		MethodDeclaration m = Utils.findWhere(cls.getMethods(), m1 -> Utils.nameEqual(m1, md));
		if(m != null) {
			if(Utils.modifiersEqual(m, md) == false) {
				m.setModifiers(md.getModifiers());
				codeChanged = true;
			}

			if(Utils.typeEqual(m, md) == false) {
				m.setType(md.getType());
				codeChanged = true;
			}

			BlockStmt body = m.getBody().orElse(null);
			if(body != null) {
				if(body.getStatements().contains(md.getBody().get().getStatement(0)) == false) {
					body.addStatement(0, md.getBody().get().getStatement(0));
					codeChanged = true;
				}
			} else {
				m.setBody(md.getBody().get());
				codeChanged = true;
			}
		} else {
			cls.addMember(md);
			codeChanged = true;
		}
	}

	private void changeClassSignature(ClassOrInterfaceDeclaration cls) {
		ClassOrInterfaceType type = new ClassOrInterfaceType()//
			.setName(Runnable.class.getSimpleName());

		if(cls.getImplementedTypes().contains(type) == false) {
			cls.addImplementedType(Runnable.class);
			codeChanged = true;
		}
	}
}
