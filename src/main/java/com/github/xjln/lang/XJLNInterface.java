package com.github.xjln.lang;

import java.util.HashMap;

public record XJLNInterface(HashMap<String, XJLNMethodAbstract> methods, HashMap<String, String> aliases) implements Compilable {}
