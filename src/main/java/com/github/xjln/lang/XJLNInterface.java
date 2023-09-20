package com.github.xjln.lang;

import java.util.HashMap;

public record XJLNInterface(String name, HashMap<String, XJLNMethodAbstract> methods, HashMap<String, String> aliases) implements Compilable {
    @Override
    public boolean isGeneric(String type) {
        return false;
    }

    @Override
    public String toString() {
        return name;
    }
}
