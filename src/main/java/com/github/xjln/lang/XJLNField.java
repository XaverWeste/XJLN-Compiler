package com.github.xjln.lang;

import com.github.xjln.bytecode.AccessFlag;

public record XJLNField(AccessFlag accessFlag, boolean constant, String type) {
}
