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
 * <li>public methods for thread excecution phase are created (suspend, resume and stop). Body of those methods must contain code according to article. User can add own code, but outside required code
 * block.</li>
 * <li>run method is created. Body of this method is constant, and can't be changed. Method <b>doInThread</b> is place, where user can place code, which will be executed by thread in while loop</li>
 * <li>fields "t" and "SUSPEND" are created, and user for phase execution mechanism</li>
 * <li>attributes <b>doBeforeStart</b> and <b>doAfterStop</b> control generation of additonal methods, which are executed as name suggests in run method. By default they are empty, user can modify
 * them as he wishes.</li>
 * </ul>
 * 
 * @author hex
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface ThreadTemplate {
	/**
	 * Determine thread name. This name is used in {@link Thread} constructor argument.
	 */
	String threadName() default "";
	/**
	 * Creates method, which will be executed before run method while loop. This method should contain code, which is executed only once before thread starts executing main task in while loop.
	 */
	boolean doBeforeStart() default false;
	/**
	 * Creates method, which will be exectued after run method while loop. This method should contain code, which is executed only once after leaving main while loop of thread. 
	 */
	boolean doAfterStop() default false;
}
