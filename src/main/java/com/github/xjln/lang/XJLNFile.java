package com.github.xjln.lang;

import com.github.xjln.bytecode.AccessFlag;
import com.github.xjln.compiler.Compiler;

import java.util.HashMap;

public final class XJLNFile {

    public final HashMap<String, Compilable> classes;
    public final HashMap<String, String> uses;
    public final XJLNClass main;
    public final String path;

    public XJLNFile(String path, XJLNClass main, HashMap<String, Compilable> classes, HashMap<String, String> uses){
        this.path = path;
        this.main = main;
        this.classes = classes;
        this.uses = uses;
    }

    public String getField(String name){
        if(main.staticFields.containsKey(name) && main.staticFields.get(name).accessFlag().equals(AccessFlag.ACC_PUBLIC))
            return path + "&" + main.staticFields.get(name).type();

        for(String file:uses.values()){
            String type = Compiler.getFile(file).getField(name);

            if(type != null)
                return type;
        }

        return null;
    }
}
