import com.github.xjln.compiler.Compiler;
import com.github.xjln.compiler.CompilingMethod;
import com.github.xjln.lang.XJLNMethod;
import com.github.xjln.utility.MatchedList;
import javassist.ClassPool;
import javassist.bytecode.*;

import java.util.HashMap;

public class Main {
    public static void main(String[] args) throws Exception {
        //new Compiler("src/test/java/Clazz", "src/test/java");
        Compiler c = new Compiler(null);
        System.out.println(c.testCompiler(new XJLNMethod(false, false, "test", null, new MatchedList<>(), "void", new String[]{
                "i = 55.5 5 + 777777",
                "end"
        }, new HashMap<>())));
        //printClass("Test");
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