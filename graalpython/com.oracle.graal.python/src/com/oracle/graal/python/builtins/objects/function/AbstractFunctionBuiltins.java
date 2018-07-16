/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
 * Copyright (c) 2014, Regents of the University of California
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.oracle.graal.python.builtins.objects.function;

import static com.oracle.graal.python.nodes.SpecialAttributeNames.__ANNOTATIONS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__CLOSURE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__CODE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__DICT__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__GLOBALS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__MODULE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__NAME__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__CALL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REPR__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.AttributeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.NotImplementedError;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cell.PCell;
import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.nodes.argument.CreateArgumentsNode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.call.CallDispatchNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.subscript.GetItemNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.runtime.PythonParseResult;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;

@CoreFunctions(extendClasses = {PFunction.class, PBuiltinFunction.class})
public class AbstractFunctionBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return AbstractFunctionBuiltinsFactory.getFactories();
    }

    @Builtin(name = __REPR__, fixedNumOfArguments = 1)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    public abstract static class ReprNode extends PythonUnaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        Object repr(PFunction self) {
            return self.toString();
        }

        @Specialization
        @TruffleBoundary
        Object repr(PBuiltinFunction self) {
            return self.toString();
        }
    }

    @Builtin(name = __CALL__, minNumOfArguments = 1, takesVariableArguments = true, takesVariableKeywords = true)
    @GenerateNodeFactory
    public abstract static class CallNode extends PythonBuiltinNode {
        @Child private CallDispatchNode dispatch = CallDispatchNode.create();
        @Child private CreateArgumentsNode createArgs = CreateArgumentsNode.create();

        @Specialization
        protected Object doIt(PFunction self, Object[] arguments, PKeyword[] keywords) {
            return dispatch.executeCall(self, createArgs.execute(arguments), keywords);
        }

        @Specialization
        protected Object doIt(PBuiltinFunction self, Object[] arguments, PKeyword[] keywords) {
            return dispatch.executeCall(self, createArgs.execute(arguments), keywords);
        }
    }

    @Builtin(name = __CLOSURE__, fixedNumOfArguments = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetClosureNode extends PythonBuiltinNode {
        @Specialization(guards = "!isBuiltinFunction(self)")
        Object getClosure(PFunction self) {
            PCell[] closure = self.getClosure();
            if (closure == null) {
                return PNone.NONE;
            }
            return factory().createTuple(closure);
        }

        @SuppressWarnings("unused")
        @Fallback
        Object getClosure(Object self) {
            throw raise(AttributeError, "'builtin_function_or_method' object has no attribute '__closure__'");
        }
    }

    @Builtin(name = __GLOBALS__, fixedNumOfArguments = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetGlobalsNode extends PythonBuiltinNode {
        @Specialization(guards = "!isBuiltinFunction(self)")
        Object getGlobals(PFunction self) {
            // see the make_globals_function from lib-graalpython/functions.py
            return self.getGlobals();
        }

        @SuppressWarnings("unused")
        @Fallback
        Object getGlobals(Object self) {
            throw raise(AttributeError, "'builtin_function_or_method' object has no attribute '__globals__'");
        }
    }

    @Builtin(name = __MODULE__, minNumOfArguments = 1, maxNumOfArguments = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    abstract static class GetModuleNode extends PythonBuiltinNode {
        @Specialization(guards = {"!isBuiltinFunction(self)", "isNoValue(none)"})
        Object getModule(PFunction self, @SuppressWarnings("unused") PNone none,
                        @Cached("create()") ReadAttributeFromObjectNode readObject,
                        @Cached("create()") GetItemNode getItem,
                        @Cached("create()") WriteAttributeToObjectNode writeObject) {
            Object module = readObject.execute(self, __MODULE__);
            if (module == PNone.NO_VALUE) {
                CompilerDirectives.transferToInterpreter();
                PythonObject globals = self.getGlobals();
                if (globals instanceof PythonModule) {
                    module = globals.getAttribute(__NAME__);
                } else {
                    module = getItem.execute(globals, __NAME__);
                }
                writeObject.execute(self, __MODULE__, module);
            }
            return module;
        }

        @Specialization(guards = {"!isBuiltinFunction(self)", "!isNoValue(value)"})
        Object getModule(PFunction self, Object value,
                        @Cached("create()") WriteAttributeToObjectNode writeObject) {
            writeObject.execute(self, __MODULE__, value);
            return PNone.NONE;
        }

        @SuppressWarnings("unused")
        @Specialization
        Object getModule(PBuiltinFunction self, Object value) {
            throw raise(AttributeError, "'builtin_function_or_method' object has no attribute '__module__'");
        }
    }

    @Builtin(name = __ANNOTATIONS__, minNumOfArguments = 1, maxNumOfArguments = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    abstract static class GetAnnotationsNode extends PythonBuiltinNode {
        @Specialization(guards = {"!isBuiltinFunction(self)", "isNoValue(none)"})
        Object getModule(PFunction self, @SuppressWarnings("unused") PNone none,
                        @Cached("create()") ReadAttributeFromObjectNode readObject,
                        @Cached("create()") WriteAttributeToObjectNode writeObject) {
            Object annotations = readObject.execute(self, __ANNOTATIONS__);
            if (annotations == PNone.NO_VALUE) {
                annotations = factory().createDict();
                writeObject.execute(self, __ANNOTATIONS__, annotations);
            }
            return annotations;
        }

        @Specialization(guards = {"!isBuiltinFunction(self)", "!isNoValue(value)"})
        Object getModule(PFunction self, Object value,
                        @Cached("create()") WriteAttributeToObjectNode writeObject) {
            writeObject.execute(self, __ANNOTATIONS__, value);
            return PNone.NONE;
        }

        @SuppressWarnings("unused")
        @Specialization
        Object getModule(PBuiltinFunction self, Object value) {
            throw raise(AttributeError, "'builtin_function_or_method' object has no attribute '__annotations__'");
        }
    }

    @Builtin(name = __CODE__, minNumOfArguments = 1, maxNumOfArguments = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    public abstract static class GetCodeNode extends PythonBuiltinNode {
        @Specialization(guards = {"!isBuiltinFunction(self)", "isNoValue(none)"})
        Object getCode(PFunction self, @SuppressWarnings("unused") PNone none) {
            return factory().createCode(new PythonParseResult(self.getFunctionRootNode()));
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!isBuiltinFunction(self)")
        Object setCode(PFunction self, PCode code) {
            throw raise(NotImplementedError, "setting __code__");
        }

        @SuppressWarnings("unused")
        @Specialization
        Object builtinCode(PBuiltinFunction self, Object none) {
            throw raise(AttributeError, "'builtin_function_or_method' object has no attribute '__code__'");
        }
    }

    @Builtin(name = __DICT__, fixedNumOfArguments = 1, isGetter = true)
    @GenerateNodeFactory
    static abstract class DictNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object dict(PFunction self) {
            return factory().createMappingproxy(self);
        }

        @SuppressWarnings("unused")
        @Specialization
        Object builtinCode(PBuiltinFunction self) {
            throw raise(AttributeError, "'builtin_function_or_method' object has no attribute '__dict__'");
        }
    }
}