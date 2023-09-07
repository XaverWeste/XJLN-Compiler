package com.github.xjln.lang;

import java.util.HashMap;

public record XJLNInterface(String name, HashMap<String, XJLNMethod> methods, HashMap<String, String> aliases) implements Compilable {}
