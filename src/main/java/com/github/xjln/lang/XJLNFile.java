package com.github.xjln.lang;

import java.util.HashMap;

public final class XJLNFile {

    private final HashMap<String, Compilable> classes;
    private final HashMap<String, String> uses;

    public XJLNFile(HashMap<String, Compilable> classes, HashMap<String, String> uses){
        this.classes = classes;
        this.uses = uses;
    }
}
