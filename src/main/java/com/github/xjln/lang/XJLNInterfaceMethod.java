package com.github.xjln.lang;

import com.github.xjln.utility.MatchedList;

public record XJLNInterfaceMethod(String returnType, MatchedList<String, String> parameters) {
}
