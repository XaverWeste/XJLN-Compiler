import com.github.xjln.compiler.Compiler;
import javassist.ClassPool;
import javassist.bytecode.*;

public class Main {
    public static void main(String[] args) throws Exception {
        new Compiler("src/test/java");
        //printClass("Test");
    }

    public static void printClass(String name) throws Exception{
        ClassPool cp = ClassPool.getDefault();
        if(cp.get(name).isFrozen()) cp.get(name).defrost();
        ClassFile cf = cp.get(name)
                .getClassFile();

        System.out.println(cf.getName() + " " + cf.getSuperclass());

        for(FieldInfo f:cf.getFields()) System.out.println(f.getAccessFlags() + " " + f.getDescriptor() + " " + f.getName());

        for(MethodInfo m:cf.getMethods()){
            System.out.println(m.getAccessFlags() + " " + m.getDescriptor() + " " + m.getName());
            CodeAttribute ca = m.getCodeAttribute();

            if(ca != null) {
                CodeIterator ci = ca.iterator();

                while (ci.hasNext()) {
                    int index = ci.next();
                    int op = ci.byteAt(index);
                    System.out.println("   " + index + " " + Mnemonic.OPCODE[op]);
                }
            }

            System.out.println(" ");
        }
    }
}