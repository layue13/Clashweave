import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import com.layue13.clashweave.diagnostics.TraceTransform;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Separate JVM per frozen jar; compares observed and unobserved return values and all fields. */
public final class ReadOnlyCheck {
    private static final String NAME="com.layue13.clashweave.core.CameraBearing";
    private static final class Loader extends ClassLoader {
        final byte[] bytes;
        Loader(byte[] b){super(ReadOnlyCheck.class.getClassLoader());bytes=b;}
        protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException {
            if(!name.equals(NAME))return super.loadClass(name,resolve);
            Class<?> c=findLoadedClass(name);if(c==null)c=defineClass(name,bytes,0,bytes.length);if(resolve)resolveClass(c);return c;
        }
    }
    static byte[] entry(JarFile jar,String name)throws Exception{return read(jar.getInputStream(jar.getJarEntry(name.replace('.','/')+".class")));}
    static byte[] read(java.io.InputStream in)throws Exception {java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=in.read(b))>=0)out.write(b,0,n);return out.toByteArray();}
    static int writes(byte[] b){ClassNode n=new ClassNode();new ClassReader(b).accept(n,0);int count=0;for(Object o:n.methods)for(AbstractInsnNode x:((MethodNode)o).instructions.toArray())if(x.getOpcode()==Opcodes.PUTFIELD||x.getOpcode()==Opcodes.PUTSTATIC)count++;return count;}
    public static void main(String[] args)throws Exception {
        JarFile jar=new JarFile(args[0]);TraceTransform transform=new TraceTransform();byte[] plain=entry(jar,NAME);
        System.setProperty("clashweave.trace","false");if(transform.transform(NAME,NAME,plain)!=plain)throw new AssertionError("disabled transformer changed bytes");
        System.setProperty("clashweave.trace","true");
        for(String name:new String[]{NAME,"com.layue13.clashweave.core.Scheduler","com.layue13.clashweave.forge.CombatServer","com.layue13.clashweave.client.ClientProxy"}){byte[] b=entry(jar,name);if(writes(b)!=writes(transform.transform(name,name,b)))throw new AssertionError("new field writes "+name);}
        Object a=new Loader(plain).loadClass(NAME).newInstance(),b=new Loader(transform.transform(NAME,NAME,plain)).loadClass(NAME).newInstance();
        Class<?>[] signature=new Class<?>[8];Arrays.fill(signature,double.class);
        Method ma=a.getClass().getMethod("lead",signature),mb=b.getClass().getMethod("lead",signature);
        Random random=new Random(12345);int checks=0;double bearing=0,x=0,z=8;
        for(int i=0;i<2000;i++){
            if(i%113==0){a.getClass().getMethod("clear").invoke(a);b.getClass().getMethod("clear").invoke(b);}
            double seconds=i%79==0?.2:1.0/(30+random.nextInt(115));bearing+=90*seconds;
            if(i%101==0){x+=10;bearing+=110;}else {x=-Math.sin(Math.toRadians(bearing))*8;z=Math.cos(Math.toRadians(bearing))*8;}
            Object[] values={bearing,x,z,seconds,.15,.1,180.0,4.0};Object ra=ma.invoke(a,values),rb=mb.invoke(b,values);if(!ra.equals(rb))throw new AssertionError("return "+i);checks++;
            for(Field fa:a.getClass().getDeclaredFields()){fa.setAccessible(true);Field fb=b.getClass().getDeclaredField(fa.getName());fb.setAccessible(true);if(!fa.get(a).equals(fb.get(b)))throw new AssertionError("field "+fa.getName()+" frame "+i);checks++;}
        }
        System.out.println("READ_ONLY_CHECK jar="+args[0]+" comparisons="+checks+" result=PASS traceOff=unchanged fieldWrites=unchanged");
    }
}
