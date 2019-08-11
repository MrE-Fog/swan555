//===--- CAstEntityInfo.java ---------------------------------------------===//
//
// This source file is part of the SWAN open source project
//
// Copyright (c) 2019 Maple @ University of Alberta
// All rights reserved. This program and the accompanying materials (unless
// otherwise specified by a license inside of the accompanying material)
// are made available under the terms of the Eclipse Public License v2.0
// which accompanies this distribution, and is available at
// http://www.eclipse.org/legal/epl-v20.html
//
//===---------------------------------------------------------------------===//

package ca.maple.swan.swift.tree;

import com.ibm.wala.cast.tree.CAstNode;
import com.ibm.wala.cast.tree.CAstSourcePositionMap;
import com.ibm.wala.cast.tree.impl.CAstSourcePositionRecorder;

import java.util.ArrayList;
import java.util.LinkedHashMap;

/*
 * This class is parallel to the C++ CAstEntityInfo class. Objects of this class are created on the C++ side using
 * JNI and returned up to the SwiftToCAstTranslator, where they are then used in the call to ScriptEntityBuilder.
 *
 * This class holds all of the information necessary to build the CAstEntities. There are nodes that need to be
 * explicitly added to a container in order to eliminate AST traversal (when looking up CALL node targets,
 * for instance).
 */
public class CAstEntityInfo {

    public String functionName;
    public ArrayList<CAstNode> basicBlocks;
    public String returnType;
    public ArrayList<String> argumentTypes;
    public ArrayList<String> argumentNames;
    public CAstSourcePositionRecorder sourcePositionRecorder;
    public CAstSourcePositionMap.Position functionPosition;
    public ArrayList<CAstSourcePositionMap.Position> argumentPositions;

    CAstEntityInfo(String functionName, ArrayList<CAstNode> basicBlocks,
                   String returnType, ArrayList<String> argumentTypes,
                   ArrayList<String> argumentNames,
                   CAstSourcePositionRecorder sourcePositionRecorder,
                   CAstSourcePositionMap.Position functionPosition,
                   ArrayList<CAstSourcePositionMap.Position> argumentPositions) {
        this.functionName = functionName;
        this.basicBlocks = basicBlocks;
        this.returnType = returnType;
        this.argumentTypes = argumentTypes; // These are currently discarded.
        this.argumentNames = argumentNames;
        this.sourcePositionRecorder = sourcePositionRecorder;
        this.functionPosition = functionPosition;
        this.argumentPositions = argumentPositions;
    }
}
