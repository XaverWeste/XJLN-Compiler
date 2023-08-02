package com.github.xjln.lang;

import java.lang.reflect.Field;

public record XJLNField(boolean inner, boolean constant, String type, String name){

    public static XJLNField ofField(Field field){
        return new XJLNField(field.toString().startsWith("private ") || field.toString().startsWith("protected"), field.toString().contains(" final "), field.getType().toString().split(" ", 2)[1], field.getName());
    }

    @Override
    public String toString() {
        return (inner ? "private " : "public ") + (constant ? "final " : "") + type + " " + name;
    }
}
