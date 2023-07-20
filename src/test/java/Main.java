import com.github.xjln.compiler.Compiler;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.CtNewMethod;
import javassist.bytecode.*;

public class Main {
    public static void main(String[] args) throws Exception {
        new Compiler("src/test/java/Clazz", "src/test/java");
        //printClass("Main");
    }

    public Main() throws Exception{
        /*
        ClassFile cf = new ClassFile(false, "compiled.test", null);
        CtClass ct = ClassPool.getDefault().makeClass(cf);
        ct.defrost();
        ct.addMethod(CtNewMethod.make("public int test(){return 0;}", ct));
        ct.writeFile();
        */
    }

    public static void printClass(String name) throws Exception{
        ClassPool cp = ClassPool.getDefault();
        if(cp.get(name).isFrozen()) cp.get(name).defrost();
        ClassFile cf = cp.get(name)
                .getClassFile();

        System.out.println(cf.getName() + " " + cf.getSuperclass());

        for(FieldInfo f:cf.getFields()) System.out.println(f.getAccessFlags() + " " + f.getDescriptor() + " " + f.getName());

        System.out.print("\n");

        for(MethodInfo m:cf.getMethods()){
            System.out.print(m.getAccessFlags() + " " + m.getDescriptor() + " " + m.getName());
            CodeAttribute ca = m.getCodeAttribute();

            if(ca != null) {
                CodeIterator ci = ca.iterator();

                int last = -1;

                while (ci.hasNext()) {
                    int index = ci.next();
                    while(index > (last += 1)){
                        System.out.print(" " + ci.byteAt(last));
                    }
                    System.out.println(" ");
                    int op = ci.byteAt(index);
                    System.out.print("   " + index + " " + Mnemonic.OPCODE[op]);
                }
            }

            System.out.print("\n\n");
        }
    }
}