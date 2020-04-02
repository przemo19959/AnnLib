package application.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation during compilation process, automatically generates thread template code. Generated code is based on atricle
 * <a href="https://docs.oracle.com/javase/10/docs/api/java/lang/doc-files/threadPrimitiveDeprecation.html">Java - Thread Primitive Depracation</a><br>
 * Responsibilites:
 * <ul>
 * <li>atribute <b>threadName</b> lets determine thread name</li>
 * <li>public constructor is created. Contains at least thread creation and starting. Can contain others instruction. another thread creation or starting is removed automatically.</li>
 * <li>public methods for thread excecution phase are created (suspend, resume and stop). Body of those methods must
 * contain code according to article. User can add own code, but outside required code block.</li>
 * <li>run method is created. Body of this method is constant, and can't be changed. Method <b>doInThread</b>
 * is place, where user can place code, which will be executed by thread in while loop</li>
 * <li>fields "t" and "SUSPEND" are created, and user for phase execution mechanism</li>
 * </ul>
 * 
 * @author hex
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface ThreadTemplate {
	String threadName() default "";
	boolean doBeforeStart() default false;
	boolean doAfterStop() default false;
}
