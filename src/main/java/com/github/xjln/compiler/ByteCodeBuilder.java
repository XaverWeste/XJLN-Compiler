package com.github.xjln.compiler;

import com.github.xjln.lang.XJLNClass;
import com.github.xjln.lang.XJLNMethod;
import com.github.xjln.lang.XJLNVariable;
import javassist.bytecode.Bytecode;
import javassist.bytecode.CodeAttribute;
import javassist.bytecode.ConstPool;

import java.util.HashMap;

class ByteCodeBuilder {

    private final Bytecode code;
    private final HashMap<String, XJLNVariable> fields;
    private final HashMap<String, XJLNVariable> vars;
    private final String returnType;

    private ByteCodeBuilder(ConstPool cp, XJLNClass clazz, XJLNMethod method) {
        code = new Bytecode(cp);
        fields = clazz.fields;
        vars = new HashMap<>();
        for (String variable : method.parameter) vars.put(variable.split(" ")[1], XJLNVariable.ofString(variable));
        returnType = method.returnType;
    }

    private boolean exists(String name){
        if(vars.containsKey(name)) return true;
        return fields.containsKey(name);
    }

    public CodeAttribute build(){
        if(returnType.equals("void")) code.addReturn(null);
        return code.toCodeAttribute();
    }

    public static ByteCodeBuilder foR(XJLNMethod method, XJLNClass clazz, ConstPool cp){
        return new ByteCodeBuilder(cp, clazz, method);
    }
}
