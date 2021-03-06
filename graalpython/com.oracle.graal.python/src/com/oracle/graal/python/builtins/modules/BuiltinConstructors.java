/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
 * Copyright (c) 2013, Regents of the University of California
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
package com.oracle.graal.python.builtins.modules;

import static com.oracle.graal.python.builtins.objects.cext.NativeCAPISymbols.FUN_ADD_NATIVE_SLOTS;
import static com.oracle.graal.python.builtins.objects.cext.NativeCAPISymbols.FUN_PY_OBJECT_NEW;
import static com.oracle.graal.python.builtins.objects.range.RangeUtils.canBeInt;
import static com.oracle.graal.python.builtins.objects.range.RangeUtils.canBePint;
import static com.oracle.graal.python.nodes.BuiltinNames.BOOL;
import static com.oracle.graal.python.nodes.BuiltinNames.BYTEARRAY;
import static com.oracle.graal.python.nodes.BuiltinNames.BYTES;
import static com.oracle.graal.python.nodes.BuiltinNames.CLASSMETHOD;
import static com.oracle.graal.python.nodes.BuiltinNames.COMPLEX;
import static com.oracle.graal.python.nodes.BuiltinNames.DICT;
import static com.oracle.graal.python.nodes.BuiltinNames.DICT_ITEMITERATOR;
import static com.oracle.graal.python.nodes.BuiltinNames.DICT_ITEMS;
import static com.oracle.graal.python.nodes.BuiltinNames.DICT_KEYITERATOR;
import static com.oracle.graal.python.nodes.BuiltinNames.DICT_KEYS;
import static com.oracle.graal.python.nodes.BuiltinNames.DICT_VALUEITERATOR;
import static com.oracle.graal.python.nodes.BuiltinNames.DICT_VALUES;
import static com.oracle.graal.python.nodes.BuiltinNames.ENUMERATE;
import static com.oracle.graal.python.nodes.BuiltinNames.FLOAT;
import static com.oracle.graal.python.nodes.BuiltinNames.FROZENSET;
import static com.oracle.graal.python.nodes.BuiltinNames.INT;
import static com.oracle.graal.python.nodes.BuiltinNames.LIST;
import static com.oracle.graal.python.nodes.BuiltinNames.MAP;
import static com.oracle.graal.python.nodes.BuiltinNames.MEMORYVIEW;
import static com.oracle.graal.python.nodes.BuiltinNames.MODULE;
import static com.oracle.graal.python.nodes.BuiltinNames.OBJECT;
import static com.oracle.graal.python.nodes.BuiltinNames.RANGE;
import static com.oracle.graal.python.nodes.BuiltinNames.REVERSED;
import static com.oracle.graal.python.nodes.BuiltinNames.SET;
import static com.oracle.graal.python.nodes.BuiltinNames.STATICMETHOD;
import static com.oracle.graal.python.nodes.BuiltinNames.STR;
import static com.oracle.graal.python.nodes.BuiltinNames.SUPER;
import static com.oracle.graal.python.nodes.BuiltinNames.TUPLE;
import static com.oracle.graal.python.nodes.BuiltinNames.TYPE;
import static com.oracle.graal.python.nodes.BuiltinNames.ZIP;
import static com.oracle.graal.python.nodes.ErrorMessages.ARG_MUST_NOT_BE_ZERO;
import static com.oracle.graal.python.nodes.PGuards.isInteger;
import static com.oracle.graal.python.nodes.PGuards.isNoValue;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__BASICSIZE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__CLASSCELL__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__DICTOFFSET__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__DICT__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__ITEMSIZE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__MODULE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__NAME__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__SLOTS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__WEAKREF__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.DECODE;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__COMPLEX__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__EQ__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__HASH__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__INDEX__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__INIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__INT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__NEW__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REPR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__SETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__TRUNC__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.NotImplementedError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.OverflowError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.RuntimeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.SystemError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PEllipsis;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodes;
import com.oracle.graal.python.builtins.objects.bytes.BytesUtils;
import com.oracle.graal.python.builtins.objects.bytes.PByteArray;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.bytes.PIBytesLike;
import com.oracle.graal.python.builtins.objects.cell.PCell;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.PCallCapiFunction;
import com.oracle.graal.python.builtins.objects.cext.CExtNodesFactory;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeClass;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeVoidPtr;
import com.oracle.graal.python.builtins.objects.code.CodeNodes;
import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodes;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorage.DictEntry;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary;
import com.oracle.graal.python.builtins.objects.common.PHashingCollection;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes.GetObjectArrayNode;
import com.oracle.graal.python.builtins.objects.common.SequenceNodesFactory.GetObjectArrayNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.NoGeneralizationNode;
import com.oracle.graal.python.builtins.objects.complex.PComplex;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.enumerate.PEnumerate;
import com.oracle.graal.python.builtins.objects.floats.FloatBuiltins;
import com.oracle.graal.python.builtins.objects.floats.FloatBuiltinsFactory;
import com.oracle.graal.python.builtins.objects.floats.PFloat;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.getsetdescriptor.HiddenKeyDescriptor;
import com.oracle.graal.python.builtins.objects.getsetdescriptor.HiddenPythonKey;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.iterator.PZip;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.map.PMap;
import com.oracle.graal.python.builtins.objects.memoryview.PBuffer;
import com.oracle.graal.python.builtins.objects.memoryview.PMemoryView;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.ObjectBuiltins;
import com.oracle.graal.python.builtins.objects.object.ObjectBuiltinsFactory;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.range.PBigRange;
import com.oracle.graal.python.builtins.objects.range.PIntRange;
import com.oracle.graal.python.builtins.objects.range.RangeNodes;
import com.oracle.graal.python.builtins.objects.set.PFrozenSet;
import com.oracle.graal.python.builtins.objects.set.PSet;
import com.oracle.graal.python.builtins.objects.set.SetNodes;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.superobject.SuperObject;
import com.oracle.graal.python.builtins.objects.traceback.PTraceback;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.PythonAbstractClass;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.builtins.objects.type.PythonManagedClass;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetMroNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetNameNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.IsAcceptableBaseNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.IsTypeNode;
import com.oracle.graal.python.nodes.BuiltinNames;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.attributes.GetAttributeNode;
import com.oracle.graal.python.nodes.attributes.GetAttributeNode.GetAnyAttributeNode;
import com.oracle.graal.python.nodes.attributes.LookupAttributeInMRONode;
import com.oracle.graal.python.nodes.attributes.LookupInheritedAttributeNode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.attributes.SetAttributeNode;
import com.oracle.graal.python.nodes.builtins.TupleNodes;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.call.special.CallUnaryMethodNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallTernaryNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.call.special.LookupSpecialMethodNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.control.GetIteratorExpressionNode.GetIteratorNode;
import com.oracle.graal.python.nodes.control.GetNextNode;
import com.oracle.graal.python.nodes.expression.CastToListExpressionNode.CastToListNode;
import com.oracle.graal.python.nodes.frame.ReadCallerFrameNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonQuaternaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonVarargsBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.subscript.SliceLiteralNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToByteNode;
import com.oracle.graal.python.nodes.util.CastToJavaIntExactNode;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.nodes.util.CastToJavaStringNodeGen;
import com.oracle.graal.python.nodes.util.SplitArgsNode;
import com.oracle.graal.python.runtime.ExecutionContext.ForeignCallContext;
import com.oracle.graal.python.runtime.ExecutionContext.IndirectCallContext;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.storage.ByteSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.ObjectSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.ValueProfile;

@CoreFunctions(defineModule = BuiltinNames.BUILTINS)
public final class BuiltinConstructors extends PythonBuiltins {

    @Override
    protected List<com.oracle.truffle.api.dsl.NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return BuiltinConstructorsFactory.getFactories();
    }

    @Override
    public void initialize(PythonCore core) {
        super.initialize(core);
        builtinConstants.put("NotImplemented", PNotImplemented.NOT_IMPLEMENTED);
    }

    @TypeSystemReference(PythonArithmeticTypes.class)
    protected abstract static class CreateByteOrByteArrayNode extends PythonBuiltinNode {
        @Child private IsBuiltinClassProfile isClassProfile = IsBuiltinClassProfile.create();

        @SuppressWarnings("unused")
        protected Object create(Object cls, byte[] barr) {
            throw new IllegalStateException("should not reach");
        }

        @Specialization(guards = {"isNoValue(source)", "isNoValue(encoding)", "isNoValue(errors)"})
        public Object bytearray(Object cls, @SuppressWarnings("unused") PNone source, @SuppressWarnings("unused") PNone encoding, @SuppressWarnings("unused") PNone errors) {
            return create(cls, new byte[0]);
        }

        @Specialization(guards = {"lib.canBeIndex(capObj)", "isNoValue(encoding)", "isNoValue(errors)"})
        public Object bytearray(VirtualFrame frame, Object cls, Object capObj, @SuppressWarnings("unused") PNone encoding, @SuppressWarnings("unused") PNone errors,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "1") PythonObjectLibrary lib) {
            int cap = lib.asSizeWithState(capObj, PArguments.getThreadState(frame));
            return create(cls, BytesUtils.fromSize(getCore(), cap));
        }

        @Specialization(guards = "isNoValue(errors)")
        public Object fromString(Object cls, String source, String encoding, @SuppressWarnings("unused") PNone errors) {
            return create(cls, BytesUtils.fromStringAndEncoding(getCore(), source, encoding));
        }

        @Specialization(guards = {"isNoValue(encoding)", "isNoValue(errors)"})
        @SuppressWarnings("unused")
        public Object fromString(Object cls, String source, PNone encoding, PNone errors) {
            throw raise(TypeError, ErrorMessages.STRING_ARG_WO_ENCODING);
        }

        protected boolean isSimpleBytes(PBytes iterable) {
            return isClassProfile.profileObject(iterable, PythonBuiltinClassType.PBytes) && iterable.getSequenceStorage() instanceof ByteSequenceStorage;
        }

        @Specialization(guards = {"isSimpleBytes(iterable)", "isNoValue(encoding)", "isNoValue(errors)"})
        public Object bytearray(Object cls, PBytes iterable, @SuppressWarnings("unused") PNone encoding, @SuppressWarnings("unused") PNone errors) {
            return create(cls, (byte[]) ((ByteSequenceStorage) iterable.getSequenceStorage()).getCopyOfInternalArrayObject());
        }

        protected boolean isSimpleBytes(PByteArray iterable) {
            return isClassProfile.profileObject(iterable, PythonBuiltinClassType.PByteArray) && iterable.getSequenceStorage() instanceof ByteSequenceStorage;
        }

        @Specialization(guards = {"isSimpleBytes(iterable)", "isNoValue(encoding)", "isNoValue(errors)"})
        public Object bytearray(Object cls, PByteArray iterable, @SuppressWarnings("unused") PNone encoding, @SuppressWarnings("unused") PNone errors) {
            return create(cls, (byte[]) ((ByteSequenceStorage) iterable.getSequenceStorage()).getCopyOfInternalArrayObject());
        }

        @Specialization(guards = {"!lib.canBeIndex(iterable)", "!isNoValue(iterable)", "isNoValue(encoding)", "isNoValue(errors)"}, limit = "1")
        public Object bytearray(VirtualFrame frame, Object cls, Object iterable, @SuppressWarnings("unused") PNone encoding, @SuppressWarnings("unused") PNone errors,
                        @Cached("create()") GetIteratorNode getIteratorNode,
                        @Cached("create()") GetNextNode getNextNode,
                        @Cached("create()") IsBuiltinClassProfile stopIterationProfile,
                        @Cached("create()") CastToByteNode castToByteNode,
                        @SuppressWarnings("unused") @CachedLibrary("iterable") PythonObjectLibrary lib) {

            Object it = getIteratorNode.executeWith(frame, iterable);
            byte[] arr = new byte[16];
            int i = 0;
            while (true) {
                try {
                    byte item = castToByteNode.execute(frame, getNextNode.execute(frame, it));
                    if (i >= arr.length) {
                        arr = resize(arr, arr.length * 2);
                    }
                    arr[i++] = item;
                } catch (PException e) {
                    e.expectStopIteration(stopIterationProfile);
                    return create(cls, resize(arr, i));
                }
            }
        }

        @TruffleBoundary(transferToInterpreterOnException = false)
        private static byte[] resize(byte[] arr, int len) {
            return Arrays.copyOf(arr, len);
        }
    }

    // bytes([source[, encoding[, errors]]])
    @Builtin(name = BYTES, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 4, constructsClass = PythonBuiltinClassType.PBytes)
    @GenerateNodeFactory
    public abstract static class BytesNode extends CreateByteOrByteArrayNode {
        @Override
        protected final Object create(Object cls, byte[] barr) {
            return factory().createBytes(cls, barr);
        }
    }

    // bytearray([source[, encoding[, errors]]])
    @Builtin(name = BYTEARRAY, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 4, constructsClass = PythonBuiltinClassType.PByteArray)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class ByteArrayNode extends CreateByteOrByteArrayNode {
        @Override
        protected final Object create(Object cls, byte[] barr) {
            return factory().createByteArray(cls, barr);
        }
    }

    // complex([real[, imag]])
    @Builtin(name = COMPLEX, minNumOfPositionalArgs = 1, constructsClass = PythonBuiltinClassType.PComplex, parameterNames = {"$cls", "real",
                    "imag"}, doc = "complex(real[, imag]) -> complex number\n\n" +
                                    "Create a complex number from a real part and an optional imaginary part.\n" +
                                    "This is equivalent to (real + imag*1j) where imag defaults to 0.")
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class ComplexNode extends PythonBuiltinNode {

        @Child private LookupAndCallUnaryNode callComplexFunc;

        @Child private IsBuiltinClassProfile isPrimitiveProfile = IsBuiltinClassProfile.create();
        @Child private IsBuiltinClassProfile isComplexTypeProfile;

        private PComplex createComplex(Object cls, double real, double imaginary) {
            if (isPrimitiveProfile.profileClass(cls, PythonBuiltinClassType.PComplex)) {
                return factory().createComplex(real, imaginary);
            }
            return factory().createComplex(cls, real, imaginary);
        }

        private PComplex createComplex(Object cls, PComplex value) {
            if (isPrimitiveProfile.profileClass(cls, PythonBuiltinClassType.PComplex)) {
                return value;
            }
            return factory().createComplex(cls, value.getReal(), value.getImag());
        }

        @Specialization(guards = {"isNoValue(real)", "isNoValue(imag)"})
        @SuppressWarnings("unused")
        PComplex complexFromNone(Object cls, PNone real, PNone imag) {
            return createComplex(cls, 0, 0);
        }

        @Specialization
        PComplex complexFromIntInt(Object cls, int real, int imaginary) {
            return createComplex(cls, real, imaginary);
        }

        @Specialization
        PComplex complexFromLongLong(Object cls, long real, long imaginary) {
            return createComplex(cls, real, imaginary);
        }

        @Specialization
        PComplex complexFromLongLong(Object cls, PInt real, PInt imaginary) {
            return createComplex(cls, real.doubleValueWithOverflow(getRaiseNode()), imaginary.doubleValueWithOverflow(getRaiseNode()));
        }

        @Specialization
        PComplex complexFromDoubleDouble(Object cls, double real, double imaginary) {
            return createComplex(cls, real, imaginary);
        }

        @Specialization(guards = "isNoValue(imag)")
        PComplex complexFromDouble(Object cls, double real, @SuppressWarnings("unused") PNone imag) {
            return createComplex(cls, real, 0);
        }

        @Specialization(guards = "isNoValue(imag)")
        PComplex complexFromInt(Object cls, int real, @SuppressWarnings("unused") PNone imag) {
            return createComplex(cls, real, 0);
        }

        @Specialization(guards = "isNoValue(imag)")
        PComplex complexFromLong(Object cls, long real, @SuppressWarnings("unused") PNone imag) {
            return createComplex(cls, real, 0);
        }

        @Specialization(guards = "isNoValue(imag)")
        PComplex complexFromLong(Object cls, PInt real, @SuppressWarnings("unused") PNone imag) {
            return createComplex(cls, real.doubleValueWithOverflow(getRaiseNode()), 0);
        }

        @Specialization(guards = {"isNoValue(imag)", "!isNoValue(number)", "!isString(number)"}, limit = "1")
        PComplex complexFromObject(VirtualFrame frame, Object cls, Object number, @SuppressWarnings("unused") PNone imag,
                        @CachedLibrary("number") PythonObjectLibrary lib) {
            PComplex value = getComplexNumberFromObject(frame, number, lib);
            if (value == null) {
                if (lib.canBeJavaDouble(number)) {
                    return createComplex(cls, lib.asJavaDouble(number), 0.0);
                } else {
                    throw raiseFirstArgError(number);
                }
            }
            return createComplex(cls, value);
        }

        @Specialization
        PComplex complexFromLongComplex(Object cls, long one, PComplex two) {
            return createComplex(cls, one - two.getImag(), two.getReal());
        }

        @Specialization
        PComplex complexFromPIntComplex(Object cls, PInt one, PComplex two) {
            return createComplex(cls, one.doubleValueWithOverflow(getRaiseNode()) - two.getImag(), two.getReal());
        }

        @Specialization
        PComplex complexFromDoubleComplex(Object cls, double one, PComplex two) {
            return createComplex(cls, one - two.getImag(), two.getReal());
        }

        @Specialization(guards = "!isString(one)", limit = "1")
        PComplex complexFromComplexLong(VirtualFrame frame, Object cls, Object one, long two,
                        @CachedLibrary("one") PythonObjectLibrary lib) {
            PComplex value = getComplexNumberFromObject(frame, one, lib);
            if (value == null) {
                if (lib.canBeJavaDouble(one)) {
                    return createComplex(cls, lib.asJavaDouble(one), two);
                } else {
                    throw raiseFirstArgError(one);
                }
            }
            return createComplex(cls, value.getReal(), value.getImag() + two);
        }

        @Specialization(guards = "!isString(one)", limit = "1")
        PComplex complexFromComplexDouble(VirtualFrame frame, Object cls, Object one, double two,
                        @CachedLibrary("one") PythonObjectLibrary lib) {
            PComplex value = getComplexNumberFromObject(frame, one, lib);
            if (value == null) {
                if (lib.canBeJavaDouble(one)) {
                    return createComplex(cls, lib.asJavaDouble(one), two);
                } else {
                    throw raiseFirstArgError(one);
                }
            }
            return createComplex(cls, value.getReal(), value.getImag() + two);
        }

        @Specialization(guards = "!isString(one)", limit = "1")
        PComplex complexFromComplexPInt(VirtualFrame frame, Object cls, Object one, PInt two,
                        @CachedLibrary("one") PythonObjectLibrary lib) {
            PComplex value = getComplexNumberFromObject(frame, one, lib);
            if (value == null) {
                if (lib.canBeJavaDouble(one)) {
                    return createComplex(cls, lib.asJavaDouble(one), two.doubleValueWithOverflow(getRaiseNode()));
                } else {
                    throw raiseFirstArgError(one);
                }
            }
            return createComplex(cls, value.getReal(), value.getImag() + two.doubleValueWithOverflow(getRaiseNode()));
        }

        @Specialization(guards = "!isString(one)", limit = "1")
        PComplex complexFromComplexComplex(VirtualFrame frame, Object cls, Object one, PComplex two,
                        @CachedLibrary("one") PythonObjectLibrary lib) {
            PComplex value = getComplexNumberFromObject(frame, one, lib);
            if (value == null) {
                if (lib.canBeJavaDouble(one)) {
                    return createComplex(cls, lib.asJavaDouble(one) - two.getImag(), two.getReal());
                } else {
                    throw raiseFirstArgError(one);
                }
            }
            return createComplex(cls, value.getReal() - two.getImag(), value.getImag() + two.getReal());
        }

        @Specialization(guards = {"!isString(one)", "!isNoValue(two)", "!isPComplex(two)"})
        PComplex complexFromComplexObject(VirtualFrame frame, Object cls, Object one, Object two,
                        @CachedLibrary(limit = "2") PythonObjectLibrary lib) {
            PComplex oneValue = getComplexNumberFromObject(frame, one, lib);
            if (lib.canBeJavaDouble(two)) {
                double twoValue = lib.asJavaDouble(two);
                if (oneValue == null) {
                    if (lib.canBeJavaDouble(one)) {
                        return createComplex(cls, lib.asJavaDouble(one), twoValue);
                    } else {
                        throw raiseFirstArgError(one);
                    }
                }
                return createComplex(cls, oneValue.getReal(), oneValue.getImag() + twoValue);
            } else {
                throw raiseSecondArgError(two);
            }
        }

        @Specialization
        PComplex complexFromString(Object cls, String real, Object imaginary) {
            if (imaginary != PNone.NO_VALUE) {
                throw raise(TypeError, ErrorMessages.COMPLEX_CANT_TAKE_ARG);
            }
            return convertStringToComplex(real, cls);
        }

        private IsBuiltinClassProfile getIsComplexTypeProfile() {
            if (isComplexTypeProfile == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                isComplexTypeProfile = insert(IsBuiltinClassProfile.create());
            }
            return isComplexTypeProfile;
        }

        private LookupAndCallUnaryNode getCallComplexFunc() {
            if (callComplexFunc == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callComplexFunc = insert(LookupAndCallUnaryNode.create(__COMPLEX__));
            }
            return callComplexFunc;
        }

        private PException raiseFirstArgError(Object x) {
            throw raise(PythonBuiltinClassType.TypeError, ErrorMessages.ARG_MUST_BE_STRING_OR_NUMBER, "complex() first", x);
        }

        private PException raiseSecondArgError(Object x) {
            throw raise(PythonBuiltinClassType.TypeError, ErrorMessages.ARG_MUST_BE_NUMBER, "complex() second", x);
        }

        private PComplex getComplexNumberFromObject(VirtualFrame frame, Object object, PythonObjectLibrary lib) {
            if (getIsComplexTypeProfile().profileClass(lib.getLazyPythonClass(object), PythonBuiltinClassType.PComplex)) {
                return (PComplex) object;
            } else {
                Object result = getCallComplexFunc().executeObject(frame, object);
                if (result != PNone.NO_VALUE) {
                    if (result instanceof PComplex) {
                        // TODO we need pass here deprecation warning
                        // DeprecationWarning: __complex__ returned non-complex (type %p).
                        // The ability to return an instance of a strict subclass of complex is
                        // deprecated,
                        // and may be removed in a future version of Python.
                        return (PComplex) result;
                    } else {
                        throw raise(TypeError, ErrorMessages.COMPLEX_SHOULD_RETURN_COMPLEX);
                    }
                }
                if (object instanceof PComplex) {
                    // the class extending PComplex but doesn't have __complex__ method
                    return (PComplex) object;
                }
                return null;
            }
        }

        @Fallback
        @SuppressWarnings("unused")
        Object complexGeneric(Object cls, Object realObj, Object imaginaryObj) {
            throw raise(TypeError, ErrorMessages.IS_NOT_TYPE_OBJ, "complex.__new__(X): X", cls);
        }

        // Taken from Jython PyString's __complex__() method
        @TruffleBoundary(transferToInterpreterOnException = false)
        private PComplex convertStringToComplex(String str, Object cls) {
            boolean gotRe = false;
            boolean gotIm = false;
            boolean done = false;
            boolean swError = false;
            boolean openBracket = false;
            boolean closeBracket = false;
            int s = 0;
            int n = str.length();
            while (s < n && Character.isSpaceChar(str.charAt(s))) {
                s++;
            }

            if (s == n) {
                throw raise(ValueError, ErrorMessages.EMPTY_STR_FOR_COMPLEX);
            }

            double z = -1.0;
            double x = 0.0;
            double y = 0.0;

            int sign = 1;
            do {
                char c = str.charAt(s);

                switch (c) {
                    case '-':
                    case '+':
                        if (c == '-') {
                            sign = -1;
                        }
                        if (done || s + 1 == n) {
                            swError = true;
                            break;
                        }
                        // a character is guaranteed, but it better be a digit
                        // or J or j
                        c = str.charAt(++s);  // eat the sign character
                        // and check the next
                        if (!Character.isDigit(c) && c != 'J' && c != 'j') {
                            swError = true;
                        }
                        break;

                    case 'J':
                    case 'j':
                        if (gotIm || done) {
                            swError = true;
                            break;
                        }
                        if (z < 0.0) {
                            y = sign;
                        } else {
                            y = sign * z;
                        }
                        gotIm = true;
                        done = gotRe;
                        sign = 1;
                        s++; // eat the J or j
                        break;

                    case ' ':
                        while (s < n && Character.isSpaceChar(str.charAt(s))) {
                            s++;
                        }
                        if (s != n && !(openBracket && !closeBracket)) {
                            swError = true;
                        }
                        break;
                    case '(':
                        if (!openBracket) {
                            openBracket = true;
                            s++;
                            while (s < n && Character.isSpaceChar(str.charAt(s))) {
                                s++;
                            }
                        } else {
                            swError = true;
                        }
                        break;
                    case ')':
                        if (!openBracket) {
                            swError = true;
                        } else {
                            if (!closeBracket) {
                                closeBracket = true;
                                s++;
                                while (s < n && Character.isSpaceChar(str.charAt(s))) {
                                    s++;
                                }
                            } else {
                                swError = true;
                            }
                        }
                        break;
                    case '\\':
                        // Handling letters defined through the unicode name database
                        // This is a hack and should be solved in the parser itself? See issue
                        // #GR-18284.
                        // the current state is that we just try to skip such defined characters
                        s++;
                        if (s < n && 'N' == str.charAt(s)) {
                            s++;
                            if (s < n && '{' == str.charAt(s)) {
                                s++;
                                while (s < n && '}' != str.charAt(s)) {
                                    s++;
                                }
                                if (s < n) {
                                    s++; // eat '}'
                                    break;
                                }
                            }
                        }
                        swError = true;
                        break;
                    default:
                        boolean digitOrDot = (c == '.' || Character.isDigit(c));
                        if (!digitOrDot) {
                            swError = true;
                            break;
                        }
                        int end = endDouble(str, s);
                        try {
                            z = Double.valueOf(str.substring(s, end)).doubleValue();
                        } catch (NumberFormatException e) {
                            swError = true;
                            break;
                        }

                        s = end;
                        if (s < n) {
                            c = str.charAt(s);
                            if (c == 'J' || c == 'j') {
                                break;
                            }
                        }
                        if (gotRe || gotIm /* "2j+1" is invalid string */) {
                            swError = true;
                            break;
                        }

                        /* accept a real part */
                        x = sign * z;
                        gotRe = true;
                        done = gotIm;
                        z = -1.0;
                        sign = 1;
                        break;

                } /* end of switch */

            } while (s < n && !swError);

            if (openBracket != closeBracket) {
                swError = true;
            }

            if (swError) {
                throw raise(ValueError, ErrorMessages.MALFORMED_STR_FOR_COMPLEX, str.substring(s));
            }

            return createComplex(cls, x, y);
        }

        // Taken from Jython PyString directly
        public static int endDouble(String string, int s) {
            int end = s;
            int n = string.length();
            while (end < n) {
                char c = string.charAt(end++);
                if (Character.isDigit(c)) {
                    continue;
                }
                if (c == '.') {
                    continue;
                }
                if (c == 'e' || c == 'E') {
                    if (end < n) {
                        c = string.charAt(end);
                        if (c == '+' || c == '-') {
                            end++;
                        }
                        continue;
                    }
                }
                return end - 1;
            }
            return end;
        }
    }

    // dict(**kwarg)
    // dict(mapping, **kwarg)
    // dict(iterable, **kwarg)
    @Builtin(name = DICT, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PDict)
    @GenerateNodeFactory
    public abstract static class DictionaryNode extends PythonBuiltinNode {
        @Specialization
        @SuppressWarnings("unused")
        public PDict dictEmpty(Object cls, Object[] args, PKeyword[] keywordArgs) {
            return factory().createDict(cls);
        }
    }

    // enumerate(iterable, start=0)
    @Builtin(name = ENUMERATE, minNumOfPositionalArgs = 2, parameterNames = {"cls", "iterable", "start"}, constructsClass = PythonBuiltinClassType.PEnumerate)
    @GenerateNodeFactory
    public abstract static class EnumerateNode extends PythonBuiltinNode {

        @Specialization
        public PEnumerate enumerate(VirtualFrame frame, Object cls, Object iterable, @SuppressWarnings("unused") PNone keywordArg,
                        @Cached("create()") GetIteratorNode getIterator) {
            return factory().createEnumerate(cls, getIterator.executeWith(frame, iterable), 0);
        }

        @Specialization
        public PEnumerate enumerate(VirtualFrame frame, Object cls, Object iterable, int start,
                        @Cached("create()") GetIteratorNode getIterator) {
            return factory().createEnumerate(cls, getIterator.executeWith(frame, iterable), start);
        }

        @Specialization
        public PEnumerate enumerate(VirtualFrame frame, Object cls, Object iterable, long start,
                        @Cached("create()") GetIteratorNode getIterator) {
            return factory().createEnumerate(cls, getIterator.executeWith(frame, iterable), start);
        }

        @Specialization
        public PEnumerate enumerate(VirtualFrame frame, Object cls, Object iterable, PInt start,
                        @Cached("create()") GetIteratorNode getIterator) {
            return factory().createEnumerate(cls, getIterator.executeWith(frame, iterable), start);
        }

        static boolean isIntegerIndex(Object idx) {
            return isInteger(idx) || idx instanceof PInt;
        }

        @Specialization(guards = "!isIntegerIndex(start)")
        public void enumerate(@SuppressWarnings("unused") Object cls, @SuppressWarnings("unused") Object iterable, Object start) {
            raise(TypeError, ErrorMessages.OBJ_CANNOT_BE_INTERPRETED_AS_INTEGER, start);
        }
    }

    // reversed(seq)
    @Builtin(name = REVERSED, minNumOfPositionalArgs = 2, constructsClass = PythonBuiltinClassType.PReverseIterator)
    @GenerateNodeFactory
    public abstract static class ReversedNode extends PythonBuiltinNode {

        @Specialization
        public PythonObject reversed(@SuppressWarnings("unused") Object cls, PIntRange range,
                        @Cached RangeNodes.LenOfRangeNode lenOfRangeNode) {
            int lstart = range.getIntStart();
            int lstop = range.getIntStop();
            int lstep = range.getIntStep();
            int ulen = lenOfRangeNode.len(lstart, lstop, lstep);
            int new_stop = lstart - lstep;
            int new_start = new_stop + ulen * lstep;

            return factory().createIntRangeIterator(new_start, -lstep, ulen);
        }

        @Specialization
        @TruffleBoundary
        public PythonObject reversed(@SuppressWarnings("unused") Object cls, PBigRange range,
                        @Cached RangeNodes.LenOfRangeNode lenOfRangeNode) {
            BigInteger lstart = range.getBigIntegerStart();
            BigInteger lstop = range.getBigIntegerStop();
            BigInteger lstep = range.getBigIntegerStep();
            BigInteger ulen = (BigInteger) lenOfRangeNode.execute(lstart, lstop, lstep);

            BigInteger new_stop = lstart.subtract(lstep);
            BigInteger new_start = new_stop.add(ulen.multiply(lstep));

            return factory().createBigRangeIterator(new_start, lstep.negate(), ulen);
        }

        @Specialization
        public PythonObject reversed(Object cls, PString value) {
            return factory().createStringReverseIterator(cls, value.getValue());
        }

        @Specialization
        public PythonObject reversed(Object cls, String value) {
            return factory().createStringReverseIterator(cls, value);
        }

        @Specialization(guards = {"!isString(sequence)", "!isPRange(sequence)"}, limit = "3")
        public Object reversed(VirtualFrame frame, Object cls, Object sequence,
                        @CachedLibrary("sequence") PythonObjectLibrary lib,
                        @Cached("create(__REVERSED__)") LookupSpecialMethodNode reversedNode,
                        @Cached("create()") CallUnaryMethodNode callReversedNode,
                        @Cached("create(__LEN__)") LookupAndCallUnaryNode lenNode,
                        @Cached("create(__GETITEM__)") LookupSpecialMethodNode getItemNode,
                        @Cached("createBinaryProfile()") ConditionProfile noReversedProfile,
                        @Cached("createBinaryProfile()") ConditionProfile noGetItemProfile) {
            Object sequenceKlass = lib.getLazyPythonClass(sequence);
            Object reversed = reversedNode.execute(frame, sequenceKlass, sequence);
            if (noReversedProfile.profile(reversed == PNone.NO_VALUE)) {
                Object getItem = getItemNode.execute(frame, sequenceKlass, sequence);
                if (noGetItemProfile.profile(getItem == PNone.NO_VALUE)) {
                    throw raise(TypeError, ErrorMessages.OBJ_ISNT_REVERSIBLE, sequence);
                } else {
                    try {
                        return factory().createSequenceReverseIterator(cls, sequence, lenNode.executeInt(frame, sequence));
                    } catch (UnexpectedResultException e) {
                        throw raise(TypeError, ErrorMessages.OBJ_CANNOT_BE_INTERPRETED_AS_INTEGER, e.getResult());
                    }
                }
            } else {
                return callReversedNode.executeObject(frame, reversed, sequence);
            }
        }
    }

    // float([x])
    @Builtin(name = FLOAT, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, constructsClass = PythonBuiltinClassType.PFloat)
    @GenerateNodeFactory
    @ReportPolymorphism
    public abstract static class FloatNode extends PythonBuiltinNode {
        @Child private BytesNodes.ToBytesNode toByteArrayNode;

        @Child private IsBuiltinClassProfile isPrimitiveProfile = IsBuiltinClassProfile.create();
        private ConditionProfile isNanProfile;

        public abstract Object executeWith(VirtualFrame frame, Object cls, Object arg);

        protected final boolean isPrimitiveFloat(Object cls) {
            return isPrimitiveProfile.profileClass(cls, PythonBuiltinClassType.PFloat);
        }

        @Specialization(guards = "!isNativeClass(cls)")
        Object floatFromInt(Object cls, int arg) {
            if (isPrimitiveFloat(cls)) {
                return (double) arg;
            }
            return factory().createFloat(cls, arg);
        }

        @Specialization(guards = "!isNativeClass(cls)")
        Object floatFromBoolean(Object cls, boolean arg) {
            if (isPrimitiveFloat(cls)) {
                return arg ? 1d : 0d;
            }
            return factory().createFloat(cls, arg ? 1d : 0d);
        }

        @Specialization(guards = "!isNativeClass(cls)")
        Object floatFromLong(Object cls, long arg) {
            if (isPrimitiveFloat(cls)) {
                return (double) arg;
            }
            return factory().createFloat(cls, arg);
        }

        @Specialization(guards = "!isNativeClass(cls)")
        Object floatFromPInt(Object cls, PInt arg) {
            double value = arg.doubleValue();
            if (Double.isInfinite(value)) {
                throw raise(OverflowError, ErrorMessages.TOO_LARGE_TO_CONVERT_TO, "int", "float");
            }
            if (isPrimitiveFloat(cls)) {
                return value;
            }
            return factory().createFloat(cls, value);
        }

        @Specialization(guards = "!isNativeClass(cls)")
        Object floatFromDouble(Object cls, double arg) {
            if (isPrimitiveFloat(cls)) {
                return arg;
            }
            return factoryCreateFloat(cls, arg);
        }

        @Specialization(guards = "!isNativeClass(cls)")
        Object floatFromString(Object cls, String arg) {
            double value = convertStringToDouble(arg);
            if (isPrimitiveFloat(cls)) {
                return value;
            }
            return factoryCreateFloat(cls, value);
        }

        @Specialization(guards = "!isNativeClass(cls)")
        Object floatFromBytes(VirtualFrame frame, Object cls, PIBytesLike arg) {
            double value = convertBytesToDouble(frame, arg);
            if (isPrimitiveFloat(cls)) {
                return value;
            }
            return factoryCreateFloat(cls, value);
        }

        private double convertBytesToDouble(VirtualFrame frame, PIBytesLike arg) {
            return convertStringToDouble(createString(getByteArray(frame, arg)));
        }

        @TruffleBoundary
        private static String createString(byte[] bytes) {
            return new String(bytes);
        }

        // Taken from Jython PyString's atof() method
        // The last statement throw Py.ValueError is modified
        @TruffleBoundary
        private double convertStringToDouble(String str) {
            StringBuilder s = null;
            int n = str.length();

            for (int i = 0; i < n; i++) {
                char ch = str.charAt(i);
                if (ch == '\u0000') {
                    throw raise(ValueError, ErrorMessages.EMPTY_STR_FOR_COMPLEX);
                }
                if (Character.isDigit(ch)) {
                    if (s == null) {
                        s = new StringBuilder(str);
                    }
                    int val = Character.digit(ch, 10);
                    s.setCharAt(i, Character.forDigit(val, 10));
                }
            }
            String sval = str.trim();
            if (s != null) {
                sval = s.toString();
            }
            try {
                // Double.valueOf allows format specifier ("d" or "f") at the end
                String lowSval = sval.toLowerCase(Locale.ENGLISH);
                if (lowSval.equals("nan") || lowSval.equals("+nan") || lowSval.equals("-nan")) {
                    return Double.NaN;
                } else if (lowSval.equals("inf") || lowSval.equals("+inf") || lowSval.equals("infinity") || lowSval.equals("+infinity")) {
                    return Double.POSITIVE_INFINITY;
                } else if (lowSval.equals("-inf") || lowSval.equals("-infinity")) {
                    return Double.NEGATIVE_INFINITY;
                }
                return Double.valueOf(sval).doubleValue();
            } catch (NumberFormatException exc) {
                // throw Py.ValueError("invalid literal for __float__: " + str);
                throw raise(ValueError, ErrorMessages.COULD_NOT_CONVERT_STRING_TO_FLOAT, str);
            }
        }

        @Specialization(guards = "!isNativeClass(cls)")
        Object floatFromNone(Object cls, @SuppressWarnings("unused") PNone arg) {
            if (isPrimitiveFloat(cls)) {
                return 0.0;
            }
            return factory().createFloat(cls, 0.0);
        }

        @Specialization(guards = "isPrimitiveFloat(cls)")
        double doubleFromObject(VirtualFrame frame, @SuppressWarnings("unused") Object cls, Object obj,
                        @CachedLibrary(limit = "1") PythonObjectLibrary lib) {
            if (obj instanceof String) {
                return convertStringToDouble((String) obj);
            } else if (obj instanceof PString) {
                return convertStringToDouble(((PString) obj).getValue());
            } else if (obj instanceof PNone) {
                return 0.0;
            } else if (obj instanceof PIBytesLike) {
                return convertBytesToDouble(frame, (PIBytesLike) obj);
            }
            if (lib.canBeJavaDouble(obj)) {
                return lib.asJavaDouble(obj);
            } else {
                throw raise(PythonBuiltinClassType.TypeError, ErrorMessages.ARG_MUST_BE_STRING_OR_NUMBER, "float()", obj);
            }
        }

        @Specialization(guards = "!isNativeClass(cls)")
        Object doPythonObject(VirtualFrame frame, Object cls, Object obj,
                        @CachedLibrary(limit = "1") PythonObjectLibrary lib) {
            return floatFromDouble(cls, doubleFromObject(frame, cls, obj, lib));
        }

        // logic similar to float_subtype_new(PyTypeObject *type, PyObject *x) from CPython
        // floatobject.c we have to first create a temporary float, then fill it into
        // a natively allocated subtype structure
        @Specialization(guards = "isSubtypeOfFloat(frame, isSubtype, cls)", limit = "1")
        Object doPythonObject(VirtualFrame frame, PythonNativeClass cls, Object obj,
                        @Cached @SuppressWarnings("unused") IsSubtypeNode isSubtype,
                        @Cached CExtNodes.FloatSubtypeNew subtypeNew,
                        @CachedLibrary(limit = "1") PythonObjectLibrary lib) {
            double realFloat = doubleFromObject(frame, PythonBuiltinClassType.PFloat, obj, lib);
            return subtypeNew.call(cls, realFloat);
        }

        @Fallback
        @TruffleBoundary
        Object floatFromObject(@SuppressWarnings("unused") Object cls, Object arg) {
            throw raise(TypeError, ErrorMessages.CANT_CONVERT_TO_FLOAT, arg.getClass().getSimpleName());
        }

        protected static boolean isSubtypeOfFloat(VirtualFrame frame, IsSubtypeNode isSubtypeNode, PythonNativeClass cls) {
            return isSubtypeNode.execute(frame, cls, PythonBuiltinClassType.PFloat);
        }

        private byte[] getByteArray(VirtualFrame frame, PIBytesLike pByteArray) {
            if (toByteArrayNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                toByteArrayNode = insert(BytesNodes.ToBytesNode.create());
            }
            return toByteArrayNode.execute(frame, pByteArray);
        }

        private PFloat factoryCreateFloat(Object cls, double arg) {
            if (isNaN(arg)) {
                return getCore().getNaN();
            }
            return factory().createFloat(cls, arg);
        }

        private boolean isNaN(double d) {
            if (isNanProfile == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                isNanProfile = ConditionProfile.createBinaryProfile();
            }
            return isNanProfile.profile(Double.isNaN(d));
        }
    }

    // frozenset([iterable])
    @Builtin(name = FROZENSET, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, constructsClass = PythonBuiltinClassType.PFrozenSet)
    @GenerateNodeFactory
    public abstract static class FrozenSetNode extends PythonBuiltinNode {

        @Child private HashingCollectionNodes.SetItemNode setItemNode;

        @Specialization(guards = "isNoValue(arg)")
        public PFrozenSet frozensetEmpty(Object cls, @SuppressWarnings("unused") PNone arg) {
            return factory().createFrozenSet(cls);
        }

        @Specialization
        public PFrozenSet frozenset(VirtualFrame frame, Object cls, String arg) {
            PFrozenSet frozenSet = factory().createFrozenSet(cls);
            for (int i = 0; i < PString.length(arg); i++) {
                getSetItemNode().execute(frame, frozenSet, PString.valueOf(PString.charAt(arg, i)), PNone.NONE);
            }
            return frozenSet;
        }

        @Specialization(guards = "!isNoValue(iterable)")
        public PFrozenSet frozensetIterable(VirtualFrame frame, Object cls, Object iterable,
                        @Cached("create()") GetIteratorNode getIterator,
                        @Cached("create()") GetNextNode next,
                        @Cached("create()") IsBuiltinClassProfile errorProfile) {

            Object iterator = getIterator.executeWith(frame, iterable);
            PFrozenSet frozenSet = factory().createFrozenSet(cls);
            while (true) {
                try {
                    getSetItemNode().execute(frame, frozenSet, next.execute(frame, iterator), PNone.NONE);
                } catch (PException e) {
                    e.expectStopIteration(errorProfile);
                    return frozenSet;
                }
            }
        }

        private HashingCollectionNodes.SetItemNode getSetItemNode() {
            if (setItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                setItemNode = insert(HashingCollectionNodes.SetItemNode.create());
            }
            return setItemNode;
        }
    }

    // int(x=0)
    // int(x, base=10)
    @Builtin(name = INT, minNumOfPositionalArgs = 1, parameterNames = {"cls", "x", "base"}, numOfPositionalOnlyArgs = 2, constructsClass = PythonBuiltinClassType.PInt)
    @GenerateNodeFactory
    @ReportPolymorphism
    public abstract static class IntNode extends PythonTernaryBuiltinNode {

        private final ConditionProfile invalidBase = ConditionProfile.createBinaryProfile();
        private final BranchProfile invalidValueProfile = BranchProfile.create();
        private final BranchProfile bigIntegerProfile = BranchProfile.create();
        private final BranchProfile primitiveIntProfile = BranchProfile.create();
        private final BranchProfile fullIntProfile = BranchProfile.create();
        private final BranchProfile notSimpleDecimalLiteralProfile = BranchProfile.create();

        @Child private BytesNodes.ToBytesNode toByteArrayNode;
        @Child private LookupAndCallUnaryNode callIntNode;
        @Child private LookupAndCallUnaryNode callIndexNode;
        @Child private LookupAndCallUnaryNode callTruncNode;
        @Child private LookupAndCallUnaryNode callReprNode;

        @TruffleBoundary
        private static Object stringToIntInternal(String num, int base) {
            try {
                BigInteger bi = asciiToBigInteger(num, base);
                if (bi.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) > 0 || bi.compareTo(BigInteger.valueOf(Integer.MIN_VALUE)) < 0) {
                    return bi;
                } else {
                    return bi.intValue();
                }
            } catch (NumberFormatException e) {
                return null;
            }
        }

        private Object stringToInt(VirtualFrame frame, Object cls, String number, int base, Object origObj) {
            if (base == 0 || base == 10) {
                Object value = parseSimpleDecimalLiteral(number);
                if (value != null) {
                    return createInt(cls, value);
                }
            }
            notSimpleDecimalLiteralProfile.enter();
            Object value = stringToIntInternal(number, base);
            if (value == null) {
                invalidValueProfile.enter();
                if (callReprNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    callReprNode = insert(LookupAndCallUnaryNode.create(__REPR__));
                }
                Object str = callReprNode.executeObject(frame, origObj);
                if (PGuards.isString(str)) {
                    throw raise(ValueError, ErrorMessages.INVALID_LITERAL_FOR_INT_WITH_BASE, base, str);
                } else {
                    // During the formatting of "ValueError: invalid literal ..." exception,
                    // CPython attempts to raise "TypeError: __repr__ returned non-string",
                    // which gets later overwitten with the original "ValueError",
                    // but without any message (since the message formatting failed)
                    throw raise(ValueError);
                }
            }
            return createInt(cls, value);
        }

        private Object createInt(Object cls, Object value) {
            if (value instanceof BigInteger) {
                bigIntegerProfile.enter();
                return factory().createInt(cls, (BigInteger) value);
            } else if (isPrimitiveInt(cls)) {
                primitiveIntProfile.enter();
                return value;
            } else {
                fullIntProfile.enter();
                if (value instanceof Integer) {
                    return factory().createInt(cls, (Integer) value);
                } else if (value instanceof Long) {
                    return factory().createInt(cls, (Long) value);
                } else if (value instanceof Boolean) {
                    return factory().createInt(cls, (Boolean) value ? 1 : 0);
                } else if (value instanceof PInt) {
                    return factory().createInt(cls, ((PInt) value).getValue());
                }
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalStateException("Unexpected type");
        }

        private void checkBase(int base) {
            if (invalidBase.profile((base < 2 || base > 36) && base != 0)) {
                throw raise(ValueError, ErrorMessages.BASE_OUT_OF_RANGE_FOR_INT);
            }
        }

        // Adapted from Jython
        private static BigInteger asciiToBigInteger(String str, int possibleBase) throws NumberFormatException {
            CompilerAsserts.neverPartOfCompilation();
            int base = possibleBase;
            int b = 0;
            int e = str.length();

            while (b < e && Character.isWhitespace(str.charAt(b))) {
                b++;
            }

            while (e > b && Character.isWhitespace(str.charAt(e - 1))) {
                e--;
            }

            boolean acceptUnderscore = false;
            boolean raiseIfNotZero = false;
            char sign = 0;
            if (b < e) {
                sign = str.charAt(b);
                if (sign == '-' || sign == '+') {
                    b++;
                }

                if (base == 16) {
                    if (str.charAt(b) == '0') {
                        if (b < e - 1 && Character.toUpperCase(str.charAt(b + 1)) == 'X') {
                            b += 2;
                            acceptUnderscore = true;
                        }
                    }
                } else if (base == 0) {
                    if (str.charAt(b) == '0') {
                        if (b < e - 1 && Character.toUpperCase(str.charAt(b + 1)) == 'X') {
                            base = 16;
                            b += 2;
                            acceptUnderscore = true;
                        } else if (b < e - 1 && Character.toUpperCase(str.charAt(b + 1)) == 'O') {
                            base = 8;
                            b += 2;
                            acceptUnderscore = true;
                        } else if (b < e - 1 && Character.toUpperCase(str.charAt(b + 1)) == 'B') {
                            base = 2;
                            b += 2;
                            acceptUnderscore = true;
                        } else {
                            raiseIfNotZero = true;
                        }
                    }
                } else if (base == 8) {
                    if (b < e - 1 && Character.toUpperCase(str.charAt(b + 1)) == 'O') {
                        b += 2;
                        acceptUnderscore = true;
                    }
                } else if (base == 2) {
                    if (b < e - 1 && Character.toUpperCase(str.charAt(b + 1)) == 'B') {
                        b += 2;
                        acceptUnderscore = true;
                    }
                }
            }

            if (base == 0) {
                base = 10;
            }

            int i = b;
            while (i < e) {
                if (str.charAt(i) == '_') {
                    if (!acceptUnderscore || i == e - 1) {
                        throw new NumberFormatException("Illegal underscore in int literal");
                    } else {
                        acceptUnderscore = false;
                    }
                } else {
                    acceptUnderscore = true;
                }
                ++i;
            }

            String s = str;
            if (b > 0 || e < str.length()) {
                s = str.substring(b, e);
            }
            s = s.replace("_", "");

            BigInteger bi;
            if (sign == '-') {
                bi = new BigInteger("-" + s, base);
            } else {
                bi = new BigInteger(s, base);
            }

            if (raiseIfNotZero && !bi.equals(BigInteger.ZERO)) {
                throw new NumberFormatException("Obsolete octal int literal");
            }
            return bi;
        }

        /**
         * Fast path parser of integer literals. Accepts only a subset of allowed literals - no
         * underscores, no leading zeros, no plus sign, no spaces, only ascii digits and the result
         * must be small enough to fit into long.
         *
         * @param arg the string to parse
         * @return parsed integer, long or null if the literal is not simple enough
         */
        private static Object parseSimpleDecimalLiteral(String arg) {
            if (arg.isEmpty()) {
                return null;
            }
            int start = arg.charAt(0) == '-' ? 1 : 0;
            if (arg.length() <= start || arg.length() > 18 + start) {
                return null;
            }
            if (arg.charAt(start) == '0') {
                if (arg.length() > start + 1) {
                    return null;
                }
                return 0;
            }
            long value = 0;
            for (int i = start; i < arg.length(); i++) {
                char c = arg.charAt(i);
                if (c < '0' || c > '9') {
                    return null;
                }
                value = value * 10 + (c - '0');
            }
            if (start != 0) {
                value = -value;
            }
            if (value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) {
                return (int) value;
            }
            return value;
        }

        @Child private IsBuiltinClassProfile isPrimitiveProfile = IsBuiltinClassProfile.create();

        protected boolean isPrimitiveInt(Object cls) {
            return isPrimitiveProfile.profileClass(cls, PythonBuiltinClassType.PInt);
        }

        @Specialization
        Object parseInt(Object cls, boolean arg, @SuppressWarnings("unused") PNone base) {
            if (isPrimitiveInt(cls)) {
                return arg ? 1 : 0;
            } else {
                return factory().createInt(cls, arg ? 1 : 0);
            }
        }

        @Specialization(guards = "isNoValue(base)")
        Object createInt(Object cls, int arg, @SuppressWarnings("unused") PNone base) {
            if (isPrimitiveInt(cls)) {
                return arg;
            }
            return factory().createInt(cls, arg);
        }

        @Specialization(guards = "isNoValue(base)")
        Object createInt(Object cls, long arg, @SuppressWarnings("unused") PNone base,
                        @Cached("createBinaryProfile()") ConditionProfile isIntProfile) {
            if (isPrimitiveInt(cls)) {
                int intValue = (int) arg;
                if (isIntProfile.profile(intValue == arg)) {
                    return intValue;
                } else {
                    return arg;
                }
            }
            return factory().createInt(cls, arg);
        }

        @Specialization(guards = "isNoValue(base)")
        Object createInt(Object cls, double arg, @SuppressWarnings("unused") PNone base,
                        @Cached("createFloatInt()") FloatBuiltins.IntNode floatToIntNode) {
            Object result = floatToIntNode.executeWithDouble(arg);
            return createInt(cls, result);
        }

        // String

        @Specialization(guards = "isNoValue(base)")
        Object createInt(VirtualFrame frame, Object cls, String arg, @SuppressWarnings("unused") PNone base) {
            return stringToInt(frame, cls, arg, 10, arg);
        }

        @Specialization
        Object parsePIntError(VirtualFrame frame, Object cls, String number, int base) {
            checkBase(base);
            return stringToInt(frame, cls, number, base, number);
        }

        @Specialization(guards = "!isNoValue(base)", limit = "getCallSiteInlineCacheMaxDepth()")
        Object createIntError(VirtualFrame frame, Object cls, String number, Object base,
                        @CachedLibrary("base") PythonObjectLibrary lib) {
            int intBase = lib.asSizeWithState(base, null, PArguments.getThreadState(frame));
            checkBase(intBase);
            return stringToInt(frame, cls, number, intBase, number);
        }

        // PIBytesLike

        @Specialization
        Object parseBytesError(VirtualFrame frame, Object cls, PIBytesLike arg, int base) {
            checkBase(base);
            return stringToInt(frame, cls, toString(frame, arg), base, arg);
        }

        @Specialization(guards = "isNoValue(base)")
        Object parseBytesError(VirtualFrame frame, Object cls, PIBytesLike arg, @SuppressWarnings("unused") PNone base) {
            return parseBytesError(frame, cls, arg, 10);
        }

        // PString

        @Specialization(guards = "isNoValue(base)")
        Object parsePInt(VirtualFrame frame, Object cls, PString arg, @SuppressWarnings("unused") PNone base) {
            return stringToInt(frame, cls, arg.getValue(), 10, arg);
        }

        @Specialization
        Object parsePInt(VirtualFrame frame, Object cls, PString arg, int base) {
            checkBase(base);
            return stringToInt(frame, cls, arg.getValue(), base, arg);
        }

        // other

        @Specialization(guards = "isNoValue(base)")
        Object createInt(Object cls, PythonNativeVoidPtr arg, @SuppressWarnings("unused") PNone base) {
            if (isPrimitiveInt(cls)) {
                return arg;
            } else {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new IllegalStateException("cannot wrap void ptr in int subclass");
            }
        }

        @Specialization
        Object createInt(Object cls, @SuppressWarnings("unused") PNone none, @SuppressWarnings("unused") PNone base) {
            if (isPrimitiveInt(cls)) {
                return 0;
            }
            return factory().createInt(cls, 0);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"!isString(arg)", "!isBytes(arg)", "!isNoValue(base)"})
        Object fail(Object cls, Object arg, Object base) {
            throw raise(TypeError, ErrorMessages.INT_CANT_CONVERT_STRING_WITH_EXPL_BASE);
        }

        @Specialization(guards = {"isNoValue(base)", "!isNoValue(obj)", "!isHandledType(obj)"}, limit = "2")
        Object createIntGeneric(VirtualFrame frame, Object cls, Object obj, @SuppressWarnings("unused") PNone base,
                        @CachedLibrary("obj") PythonObjectLibrary lib) {
            // This method (together with callInt and callIndex) reflects the logic of PyNumber_Long
            // in CPython. We don't use PythonObjectLibrary here since the original CPython function
            // does not use any of the conversion functions (such as _PyLong_AsInt or
            // PyNumber_Index) either, but it reimplements the logic in a slightly different way
            // (e.g. trying __int__ before __index__ whereas _PyLong_AsInt does it the other way)
            // and also with specific exception messages which are expected by Python unittests.
            // This unfortunately means that this method relies on the internal logic of NO_VALUE
            // return values representing missing magic methods which should be ideally hidden
            // by PythonObjectLibrary.
            Object result = callInt(frame, obj);
            if (result == PNone.NO_VALUE) {
                result = callIndex(frame, obj);
                if (result == PNone.NO_VALUE) {
                    Object truncResult = callTrunc(frame, obj);
                    if (truncResult == PNone.NO_VALUE) {
                        if (lib.isBuffer(obj)) {
                            try {
                                byte[] bytes = lib.getBufferBytes(obj);
                                return stringToInt(frame, cls, toString(bytes), 10, obj);
                            } catch (UnsupportedMessageException e) {
                                CompilerDirectives.transferToInterpreterAndInvalidate();
                                throw new IllegalStateException("Object claims to be a buffer but does not support getBufferBytes()");
                            }
                        } else {
                            throw raise(TypeError, ErrorMessages.ARG_MUST_BE_STRING_OR_BYTELIKE_OR_NUMBER, "int()", obj);
                        }
                    }
                    if (isIntegerType(truncResult)) {
                        result = truncResult;
                    } else {
                        result = callIndex(frame, truncResult);
                        if (result == PNone.NO_VALUE) {
                            result = callInt(frame, truncResult);
                            if (result == PNone.NO_VALUE) {
                                throw raise(TypeError, ErrorMessages.RETURNED_NON_INTEGRAL, "__trunc__", truncResult);
                            }
                        }
                    }
                }
            }

            // If a subclass of int is returned by __int__ or __index__, a conversion to int is
            // performed and a DeprecationWarning should be triggered (see PyNumber_Long).
            if (!isPrimitiveProfile.profileObject(result, PythonBuiltinClassType.PInt)) {
                // TODO deprecation warning
                if (PGuards.isPInt(result)) {
                    result = ((PInt) result).getValue();
                } else if (PGuards.isBoolean(result)) {
                    result = (boolean) result ? 1 : 0;
                }
            }
            return createInt(cls, result);
        }

        protected static boolean isIntegerType(Object obj) {
            return PGuards.isBoolean(obj) || PGuards.isInteger(obj) || PGuards.isPInt(obj);
        }

        protected static boolean isHandledType(Object obj) {
            return PGuards.isInteger(obj) || obj instanceof Double || obj instanceof Boolean || PGuards.isString(obj) || PGuards.isBytes(obj) || obj instanceof PythonNativeVoidPtr;
        }

        protected static FloatBuiltins.IntNode createFloatInt() {
            return FloatBuiltinsFactory.IntNodeFactory.create();
        }

        private Object callInt(VirtualFrame frame, Object obj) {
            // The case when the result is NO_VALUE (i.e. the object does not provide __int__)
            // is handled in createIntGeneric
            if (callIntNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callIntNode = insert(LookupAndCallUnaryNode.create(__INT__));
            }
            Object result = callIntNode.executeObject(frame, obj);
            if (result != PNone.NO_VALUE && !isIntegerType(result)) {
                throw raise(TypeError, ErrorMessages.RETURNED_NON_INT, "__int__", result);
            }
            return result;
        }

        private Object callIndex(VirtualFrame frame, Object obj) {
            if (callIndexNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callIndexNode = insert(LookupAndCallUnaryNode.create(__INDEX__));
            }
            Object result = callIndexNode.executeObject(frame, obj);
            // the case when the result is NO_VALUE (i.e. the object does not provide __index__)
            // is handled in createIntGeneric
            if (result != PNone.NO_VALUE && !isIntegerType(result)) {
                throw raise(TypeError, ErrorMessages.RETURNED_NON_INT, "__index__", result);
            }
            return result;
        }

        private Object callTrunc(VirtualFrame frame, Object obj) {
            if (callTruncNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callTruncNode = insert(LookupAndCallUnaryNode.create(__TRUNC__));
            }
            return callTruncNode.executeObject(frame, obj);
        }

        private String toString(VirtualFrame frame, PIBytesLike pByteArray) {
            if (toByteArrayNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                toByteArrayNode = insert(BytesNodes.ToBytesNode.create());
            }
            return toString(toByteArrayNode.execute(frame, pByteArray));
        }

        @TruffleBoundary
        private static String toString(byte[] barr) {
            return new String(barr);
        }

    }

    // bool([x])
    @Builtin(name = BOOL, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, constructsClass = PythonBuiltinClassType.Boolean, base = PythonBuiltinClassType.PInt)
    @GenerateNodeFactory
    @SuppressWarnings("unused")
    @ReportPolymorphism
    public abstract static class BoolNode extends PythonBinaryBuiltinNode {
        @Specialization(limit = "getCallSiteInlineCacheMaxDepth()")
        public boolean bool(VirtualFrame frame, Object cls, Object obj,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @CachedLibrary("obj") PythonObjectLibrary lib) {
            if (hasFrame.profile(frame != null)) {
                return lib.isTrueWithState(obj, PArguments.getThreadState(frame));
            } else {
                return lib.isTrue(obj);
            }
        }
    }

    // list([iterable])
    @Builtin(name = LIST, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PList)
    @GenerateNodeFactory
    public abstract static class ListNode extends PythonVarargsBuiltinNode {
        @Specialization(guards = "lib.isLazyPythonClass(cls)")
        protected PList constructList(Object cls, @SuppressWarnings("unused") Object[] arguments, @SuppressWarnings("unused") PKeyword[] keywords,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "3") PythonObjectLibrary lib) {
            return factory().createList(cls);
        }

        @Fallback
        @SuppressWarnings("unused")
        public PList listObject(Object cls, Object[] arguments, PKeyword[] keywords) {
            throw raise(TypeError, ErrorMessages.IS_NOT_TYPE_OBJ, "'cls'", cls);
        }
    }

    // object()
    @Builtin(name = OBJECT, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PythonObject)
    @GenerateNodeFactory
    public abstract static class ObjectNode extends PythonVarargsBuiltinNode {
        @Child private PCallCapiFunction callCapiFunction;
        @Children private CExtNodes.ToSulongNode[] toSulongNodes;
        @Child private CExtNodes.AsPythonObjectNode asPythonObjectNode;
        @Child private SplitArgsNode splitArgsNode;
        @Child private LookupAttributeInMRONode lookupInit;
        @Child private LookupAttributeInMRONode lookupNew;
        @CompilationFinal private ValueProfile profileInit;
        @CompilationFinal private ValueProfile profileNew;

        @Override
        public final Object varArgExecute(VirtualFrame frame, @SuppressWarnings("unused") Object self, Object[] arguments, PKeyword[] keywords) throws VarargsBuiltinDirectInvocationNotSupported {
            if (splitArgsNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                splitArgsNode = insert(SplitArgsNode.create());
            }
            return execute(frame, arguments[0], splitArgsNode.execute(arguments), keywords);
        }

        @Specialization(guards = {"!self.needsNativeAllocation()"})
        Object doManagedObject(PythonManagedClass self, Object[] varargs, PKeyword[] kwargs) {
            checkExcessArgs(self, varargs, kwargs);
            return factory().createPythonObject(self);
        }

        @Specialization
        Object doBuiltinTypeType(PythonBuiltinClassType self, Object[] varargs, PKeyword[] kwargs) {
            checkExcessArgs(self, varargs, kwargs);
            return factory().createPythonObject(self);
        }

        @Specialization(guards = "self.needsNativeAllocation()")
        Object doNativeObjectIndirect(PythonManagedClass self, Object[] varargs, PKeyword[] kwargs,
                        @Cached("create()") GetMroNode getMroNode) {
            checkExcessArgs(self, varargs, kwargs);
            PythonNativeClass nativeBaseClass = findFirstNativeBaseClass(getMroNode.execute(self));
            return callNativeGenericNewNode(nativeBaseClass, varargs, kwargs);
        }

        @Specialization(guards = "isNativeClass(self)")
        Object doNativeObjectIndirect(Object self, Object[] varargs, PKeyword[] kwargs) {
            checkExcessArgs(self, varargs, kwargs);
            return callNativeGenericNewNode(self, varargs, kwargs);
        }

        @SuppressWarnings("unused")
        @Fallback
        Object fallback(Object o, Object[] varargs, PKeyword[] kwargs) {
            throw raise(TypeError, ErrorMessages.IS_NOT_TYPE_OBJ, "object.__new__(X): X", o);
        }

        private static PythonNativeClass findFirstNativeBaseClass(PythonAbstractClass[] methodResolutionOrder) {
            for (PythonAbstractClass cls : methodResolutionOrder) {
                if (PGuards.isNativeClass(cls)) {
                    return PythonNativeClass.cast(cls);
                }
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalStateException("class needs native allocation but has not native base class");
        }

        private Object callNativeGenericNewNode(Object self, Object[] varargs, PKeyword[] kwargs) {
            if (callCapiFunction == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callCapiFunction = insert(PCallCapiFunction.create());
            }
            if (toSulongNodes == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                toSulongNodes = new CExtNodes.ToSulongNode[4];
                for (int i = 0; i < toSulongNodes.length; i++) {
                    toSulongNodes[i] = insert(CExtNodesFactory.ToSulongNodeGen.create());
                }
            }
            if (asPythonObjectNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                asPythonObjectNode = insert(CExtNodesFactory.AsPythonObjectNodeGen.create());
            }
            PKeyword[] kwarr = kwargs.length > 0 ? kwargs : null;
            PTuple targs = factory().createTuple(varargs);
            PDict dkwargs = factory().createDict(kwarr);
            return asPythonObjectNode.execute(
                            callCapiFunction.call(FUN_PY_OBJECT_NEW, toSulongNodes[0].execute(self), toSulongNodes[1].execute(self), toSulongNodes[2].execute(targs),
                                            toSulongNodes[3].execute(dkwargs)));
        }

        private void checkExcessArgs(Object type, Object[] varargs, PKeyword[] kwargs) {
            if (varargs.length != 0 || kwargs.length != 0) {
                if (lookupNew == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    lookupNew = insert(LookupAttributeInMRONode.create(__NEW__));
                }
                if (lookupInit == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    lookupInit = insert(LookupAttributeInMRONode.create(__INIT__));
                }
                if (profileNew == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    profileNew = ValueProfile.createIdentityProfile();
                }
                if (profileInit == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    profileInit = ValueProfile.createIdentityProfile();
                }
                if (ObjectBuiltins.InitNode.overridesBuiltinMethod(type, profileNew, lookupNew, BuiltinConstructorsFactory.ObjectNodeFactory.class)) {
                    throw raise(TypeError, ErrorMessages.NEW_TAKES_ONE_ARG);
                }
                if (!ObjectBuiltins.InitNode.overridesBuiltinMethod(type, profileInit, lookupInit, ObjectBuiltinsFactory.InitNodeFactory.class)) {
                    throw raise(TypeError, ErrorMessages.NEW_TAKES_NO_ARGS, type);
                }
            }
        }
    }

    // range(stop)
    // range(start, stop[, step])
    @Builtin(name = RANGE, minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 4, constructsClass = PythonBuiltinClassType.PRange)
    @GenerateNodeFactory
    @ReportPolymorphism
    public abstract static class RangeNode extends PythonQuaternaryBuiltinNode {
        // stop
        @Specialization(guards = "isStop(start, stop, step)")
        Object doIntStop(Object cls, int stop, @SuppressWarnings("unused") PNone start, @SuppressWarnings("unused") PNone step,
                        @Shared("stepZeroProfile") @Cached ConditionProfile stepZeroProfile,
                        @Shared("exceptionProfile") @Cached BranchProfile exceptionProfile,
                        @Shared("lenOfRangeNode") @Cached RangeNodes.LenOfRangeNode lenOfRangeNode,
                        @Shared("createBigRangeNode") @Cached RangeNodes.CreateBigRangeNode createBigRangeNode) {
            return doInt(cls, 0, stop, 1, stepZeroProfile, exceptionProfile, lenOfRangeNode, createBigRangeNode);
        }

        @Specialization(guards = "isStop(start, stop, step)")
        Object doPintStop(Object cls, PInt stop, @SuppressWarnings("unused") PNone start, @SuppressWarnings("unused") PNone step,
                        @Shared("stepZeroProfile") @Cached ConditionProfile stepZeroProfile,
                        @Shared("lenOfRangeNode") @Cached RangeNodes.LenOfRangeNode lenOfRangeNode) {
            return doPint(cls, factory().createInt(0), stop, factory().createInt(1), stepZeroProfile, lenOfRangeNode);
        }

        @Specialization(guards = "isStop(start, stop, step)")
        Object doGenericStop(Object cls, Object stop, @SuppressWarnings("unused") PNone start, @SuppressWarnings("unused") PNone step,
                        @Shared("stepZeroProfile") @Cached ConditionProfile stepZeroProfile,
                        @Shared("exceptionProfile") @Cached BranchProfile exceptionProfile,
                        @Shared("lenOfRangeNode") @Cached RangeNodes.LenOfRangeNode lenOfRangeNode,
                        @Shared("createBigRangeNode") @Cached RangeNodes.CreateBigRangeNode createBigRangeNode,
                        @Shared("polGeneric") @CachedLibrary(limit = "3") PythonObjectLibrary pol,
                        @Shared("libGeneric") @CachedLibrary(limit = "3") InteropLibrary lib) {
            return doGeneric(cls, 0, stop, 1, stepZeroProfile, exceptionProfile, lenOfRangeNode, createBigRangeNode, pol, lib);
        }

        // start stop
        @Specialization(guards = "isStartStop(start, stop, step)")
        Object doIntStartStop(Object cls, int start, int stop, @SuppressWarnings("unused") PNone step,
                        @Shared("stepZeroProfile") @Cached ConditionProfile stepZeroProfile,
                        @Shared("exceptionProfile") @Cached BranchProfile exceptionProfile,
                        @Shared("lenOfRangeNode") @Cached RangeNodes.LenOfRangeNode lenOfRangeNode,
                        @Shared("createBigRangeNode") @Cached RangeNodes.CreateBigRangeNode createBigRangeNode) {
            return doInt(cls, start, stop, 1, stepZeroProfile, exceptionProfile, lenOfRangeNode, createBigRangeNode);
        }

        @Specialization(guards = "isStartStop(start, stop, step)")
        Object doPintStartStop(Object cls, PInt start, PInt stop, @SuppressWarnings("unused") PNone step,
                        @Shared("stepZeroProfile") @Cached ConditionProfile stepZeroProfile,
                        @Shared("lenOfRangeNode") @Cached RangeNodes.LenOfRangeNode lenOfRangeNode) {
            return doPint(cls, start, stop, factory().createInt(1), stepZeroProfile, lenOfRangeNode);
        }

        @Specialization(guards = "isStartStop(start, stop, step)")
        Object doGenericStartStop(Object cls, Object start, Object stop, @SuppressWarnings("unused") PNone step,
                        @Shared("stepZeroProfile") @Cached ConditionProfile stepZeroProfile,
                        @Shared("exceptionProfile") @Cached BranchProfile exceptionProfile,
                        @Shared("lenOfRangeNode") @Cached RangeNodes.LenOfRangeNode lenOfRangeNode,
                        @Shared("createBigRangeNode") @Cached RangeNodes.CreateBigRangeNode createBigRangeNode,
                        @Shared("polGeneric") @CachedLibrary(limit = "3") PythonObjectLibrary pol,
                        @Shared("libGeneric") @CachedLibrary(limit = "3") InteropLibrary lib) {
            return doGeneric(cls, start, stop, 1, stepZeroProfile, exceptionProfile, lenOfRangeNode, createBigRangeNode, pol, lib);
        }

        // start stop step
        @Specialization
        Object doInt(@SuppressWarnings("unused") Object cls, int start, int stop, int step,
                        @Shared("stepZeroProfile") @Cached ConditionProfile stepZeroProfile,
                        @Shared("exceptionProfile") @Cached BranchProfile exceptionProfile,
                        @Shared("lenOfRangeNode") @Cached RangeNodes.LenOfRangeNode lenOfRangeNode,
                        @Shared("createBigRangeNode") @Cached RangeNodes.CreateBigRangeNode createBigRangeNode) {
            if (stepZeroProfile.profile(step == 0)) {
                throw raise(ValueError, ARG_MUST_NOT_BE_ZERO, "range()", 3);
            }
            try {
                int len = lenOfRangeNode.len(start, stop, step);
                return factory().createIntRange(start, stop, step, len);
            } catch (ArithmeticException e) {
                exceptionProfile.enter();
                return createBigRangeNode.execute(start, stop, step, factory());
            }
        }

        @Specialization
        Object doPint(@SuppressWarnings("unused") Object cls, PInt start, PInt stop, PInt step,
                        @Shared("stepZeroProfile") @Cached ConditionProfile stepZeroProfile,
                        @Shared("lenOfRangeNode") @Cached RangeNodes.LenOfRangeNode lenOfRangeNode) {
            if (stepZeroProfile.profile(step.isZero())) {
                throw raise(ValueError, ARG_MUST_NOT_BE_ZERO, "range()", 3);
            }
            BigInteger len = (BigInteger) lenOfRangeNode.execute(start, stop, step);
            return factory().createBigRange(start, stop, step, factory().createInt(len));
        }

        @Specialization(guards = "isStartStopStep(start, stop, step)")
        Object doGeneric(@SuppressWarnings("unused") Object cls, Object start, Object stop, Object step,
                        @Shared("stepZeroProfile") @Cached ConditionProfile stepZeroProfile,
                        @Shared("exceptionProfile") @Cached BranchProfile exceptionProfile,
                        @Shared("lenOfRangeNode") @Cached RangeNodes.LenOfRangeNode lenOfRangeNode,
                        @Shared("createBigRangeNode") @Cached RangeNodes.CreateBigRangeNode createBigRangeNode,
                        @Shared("polGeneric") @CachedLibrary(limit = "3") PythonObjectLibrary pol,
                        @Shared("libGeneric") @CachedLibrary(limit = "3") InteropLibrary lib) {
            if (canBeInt(start, stop, step, lib)) {
                return doInt(cls, pol.asSize(start), pol.asSize(stop), pol.asSize(step), stepZeroProfile, exceptionProfile, lenOfRangeNode, createBigRangeNode);
            } else if (canBePint(start, stop, step, pol)) {
                return createBigRangeNode.execute(start, stop, step, factory());
            } else {
                Object lstart = pol.asIndex(start);
                Object lstop = pol.asIndex(stop);
                Object lstep = pol.asIndex(step);

                if (canBeInt(start, stop, step, lib)) {
                    return doInt(cls, pol.asSize(start), pol.asSize(stop), pol.asSize(step), stepZeroProfile, exceptionProfile, lenOfRangeNode, createBigRangeNode);
                } else {
                    return createBigRangeNode.execute(lstart, lstop, lstep, factory());
                }
            }
        }

        protected boolean isStop(Object start, Object stop, Object step) {
            return isNoValue(start) && !isNoValue(stop) && isNoValue(step);
        }

        protected boolean isStartStop(Object start, Object stop, Object step) {
            return !isNoValue(start) && !isNoValue(stop) && isNoValue(step);
        }

        protected boolean isStartStopStep(Object start, Object stop, Object step) {
            return !isNoValue(start) && !isNoValue(stop) && !isNoValue(step);
        }
    }

    // set([iterable])
    @Builtin(name = SET, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, constructsClass = PythonBuiltinClassType.PSet)
    @GenerateNodeFactory
    public abstract static class SetNode extends PythonBuiltinNode {

        @Specialization(guards = "lib.isLazyPythonClass(cls)")
        protected PSet constructSet(VirtualFrame frame, Object cls, Object value,
                        @Cached("create()") SetNodes.ConstructSetNode constructSetNode,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "3") PythonObjectLibrary lib) {
            return constructSetNode.execute(frame, cls, value);
        }

        @Fallback
        public PSet listObject(@SuppressWarnings("unused") Object cls, Object arg) {
            throw raise(TypeError, ErrorMessages.SET_DOES_NOT_SUPPORT_ITERABLE_OBJ, arg);
        }
    }

    // str(object='')
    // str(object=b'', encoding='utf-8', errors='strict')
    @Builtin(name = STR, minNumOfPositionalArgs = 1, parameterNames = {"cls", "object", "encoding", "errors"}, constructsClass = PythonBuiltinClassType.PString)
    @GenerateNodeFactory
    public abstract static class StrNode extends PythonBuiltinNode {
        @Child private LookupAndCallTernaryNode callDecodeNode;

        @Child private IsBuiltinClassProfile isPrimitiveProfile = IsBuiltinClassProfile.create();

        @CompilationFinal private ConditionProfile isStringProfile;
        @CompilationFinal private ConditionProfile isPStringProfile;

        public abstract Object executeWith(VirtualFrame frame, Object strClass, Object arg, Object encoding, Object errors);

        @Specialization(guards = {"!isNativeClass(strClass)", "isNoValue(arg)", "isNoValue(encoding)", "isNoValue(errors)"})
        @SuppressWarnings("unused")
        Object strNoArgs(Object strClass, PNone arg, PNone encoding, PNone errors) {
            return asPString(strClass, "");
        }

        @Specialization(guards = {"!isNativeClass(strClass)", "!isNoValue(obj)", "isNoValue(encoding)", "isNoValue(errors)"})
        Object strOneArg(Object strClass, Object obj, @SuppressWarnings("unused") PNone encoding, @SuppressWarnings("unused") PNone errors,
                        @CachedLibrary(limit = "2") PythonObjectLibrary lib) {
            Object result = lib.asPString(obj);

            // try to return a primitive if possible
            if (getIsStringProfile().profile(result instanceof String)) {
                return asPString(strClass, (String) result);
            }

            // PythonObjectLibrary guarantees that the returned object is an instanceof of 'str'
            return result;
        }

        @Specialization(guards = {"!isNativeClass(strClass)", "!isNoValue(encoding) || !isNoValue(errors)"}, limit = "3")
        Object doBuffer(VirtualFrame frame, Object strClass, Object obj, Object encoding, Object errors,
                        @CachedLibrary("obj") PythonObjectLibrary bufferLib) {
            if (bufferLib.isBuffer(obj)) {
                try {
                    // TODO(fa): we should directly call '_codecs.decode'
                    PBytes bytesObj = factory().createBytes(bufferLib.getBufferBytes(obj));
                    Object en = encoding == PNone.NO_VALUE ? "utf-8" : encoding;
                    return decodeBytes(frame, strClass, bytesObj, en, errors);
                } catch (UnsupportedMessageException e) {
                    // fall through
                }
            }
            throw raise(TypeError, ErrorMessages.NEED_BYTELIKE_OBJ, obj);
        }

        private Object decodeBytes(VirtualFrame frame, Object strClass, PBytes obj, Object encoding, Object errors) {
            Object result = getCallDecodeNode().execute(frame, obj, encoding, errors);
            if (getIsStringProfile().profile(result instanceof String)) {
                return asPString(strClass, (String) result);
            } else if (getIsPStringProfile().profile(result instanceof PString)) {
                return result;
            }
            throw raise(TypeError, ErrorMessages.P_S_RETURNED_NON_STRING, obj, "decode", result);
        }

        /**
         * logic similar to
         * {@code unicode_subtype_new(PyTypeObject *type, PyObject *args, PyObject *kwds)} from
         * CPython {@code unicodeobject.c} we have to first create a temporary string, then fill it
         * into a natively allocated subtype structure
         */
        @Specialization(guards = {"isNativeClass(cls)", "isSubtypeOfString(frame, isSubtype, cls)", "isNoValue(encoding)", "isNoValue(errors)"})
        Object doNativeSubclass(@SuppressWarnings("unused") VirtualFrame frame, Object cls, Object obj, @SuppressWarnings("unused") Object encoding,
                        @SuppressWarnings("unused") Object errors,
                        @Cached @SuppressWarnings("unused") IsSubtypeNode isSubtype,
                        @CachedLibrary(limit = "2") PythonObjectLibrary lib,
                        @Cached CExtNodes.StringSubtypeNew subtypeNew) {
            return subtypeNew.call(cls, lib.asPString(obj));
        }

        protected static boolean isSubtypeOfString(VirtualFrame frame, IsSubtypeNode isSubtypeNode, Object cls) {
            return isSubtypeNode.execute(frame, cls, PythonBuiltinClassType.PString);
        }

        private Object asPString(Object cls, String str) {
            if (isPrimitiveProfile.profileClass(cls, PythonBuiltinClassType.PString)) {
                return str;
            } else {
                return factory().createString(cls, str);
            }
        }

        private LookupAndCallTernaryNode getCallDecodeNode() {
            if (callDecodeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callDecodeNode = insert(LookupAndCallTernaryNode.create(DECODE));
            }
            return callDecodeNode;
        }

        private ConditionProfile getIsStringProfile() {
            if (isStringProfile == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                isStringProfile = ConditionProfile.createBinaryProfile();
            }
            return isStringProfile;
        }

        private ConditionProfile getIsPStringProfile() {
            if (isPStringProfile == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                isPStringProfile = ConditionProfile.createBinaryProfile();
            }
            return isPStringProfile;
        }
    }

    // tuple([iterable])
    @Builtin(name = TUPLE, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, constructsClass = PythonBuiltinClassType.PTuple)
    @GenerateNodeFactory
    public abstract static class TupleNode extends PythonBinaryBuiltinNode {

        @Specialization(guards = "!isNativeClass(cls)")
        protected PTuple constructTuple(VirtualFrame frame, Object cls, Object iterable,
                        @Cached("create()") TupleNodes.ConstructTupleNode constructTupleNode) {
            return constructTupleNode.execute(frame, cls, iterable);
        }

        // delegate to tuple_subtype_new(PyTypeObject *type, PyObject *x)
        @Specialization(guards = {"isNativeClass(cls)", "isSubtypeOfTuple(frame, isSubtype, cls)"}, limit = "1")
        Object doNative(@SuppressWarnings("unused") VirtualFrame frame, Object cls, Object iterable,
                        @Cached @SuppressWarnings("unused") IsSubtypeNode isSubtype,
                        @Cached CExtNodes.TupleSubtypeNew subtypeNew) {
            return subtypeNew.call(cls, iterable);
        }

        protected static boolean isSubtypeOfTuple(VirtualFrame frame, IsSubtypeNode isSubtypeNode, Object cls) {
            return isSubtypeNode.execute(frame, cls, PythonBuiltinClassType.PTuple);
        }

        @Fallback
        public PTuple tupleObject(Object cls, @SuppressWarnings("unused") Object arg) {
            throw raise(TypeError, ErrorMessages.IS_NOT_TYPE_OBJ, "'cls'", cls);
        }
    }

    // zip(*iterables)
    @Builtin(name = ZIP, minNumOfPositionalArgs = 1, takesVarArgs = true, constructsClass = PythonBuiltinClassType.PZip)
    @GenerateNodeFactory
    public abstract static class ZipNode extends PythonBuiltinNode {
        @Specialization
        PZip zip(VirtualFrame frame, Object cls, Object[] args,
                        @Cached("create()") GetIteratorNode getIterator) {
            Object[] iterables = new Object[args.length];
            for (int i = 0; i < args.length; i++) {
                Object item = args[i];
                // TODO: check whether the argument supports iteration (has __next__ and __iter__)
                iterables[i] = getIterator.executeWith(frame, item);
            }
            return factory().createZip(cls, iterables);
        }
    }

    // function(code, globals[, name[, argdefs[, closure]]])
    @Builtin(name = "function", minNumOfPositionalArgs = 3, maxNumOfPositionalArgs = 6, constructsClass = PythonBuiltinClassType.PFunction, isPublic = false)
    @GenerateNodeFactory
    public abstract static class FunctionNode extends PythonBuiltinNode {
        @Child private GetNameNode getNameNode;

        @Specialization
        public PFunction function(Object cls, PCode code, PDict globals, String name, @SuppressWarnings("unused") PNone defaultArgs, @SuppressWarnings("unused") PNone closure) {
            return factory().createFunction(name, getTypeName(cls), code, globals, null);
        }

        @Specialization
        public PFunction function(Object cls, PCode code, PDict globals, @SuppressWarnings("unused") PNone name, @SuppressWarnings("unused") PNone defaultArgs, PTuple closure,
                        @Shared("getObjectArrayNode") @Cached GetObjectArrayNode getObjectArrayNode) {
            return factory().createFunction("<lambda>", getTypeName(cls), code, globals, getClosure(getObjectArrayNode.execute(closure)));
        }

        @Specialization
        public PFunction function(Object cls, PCode code, PDict globals, @SuppressWarnings("unused") PNone name, @SuppressWarnings("unused") PNone defaultArgs,
                        @SuppressWarnings("unused") PNone closure,
                        @SuppressWarnings("unused") @Shared("getObjectArrayNode") @Cached GetObjectArrayNode getObjectArrayNode) {
            return factory().createFunction("<lambda>", getTypeName(cls), code, globals, null);
        }

        @Specialization
        public PFunction function(Object cls, PCode code, PDict globals, String name, @SuppressWarnings("unused") PNone defaultArgs, PTuple closure,
                        @Shared("getObjectArrayNode") @Cached GetObjectArrayNode getObjectArrayNode) {
            return factory().createFunction(name, getTypeName(cls), code, globals, getClosure(getObjectArrayNode.execute(closure)));
        }

        @Specialization
        public PFunction function(Object cls, PCode code, PDict globals, String name, PTuple defaultArgs, @SuppressWarnings("unused") PNone closure,
                        @Shared("getObjectArrayNode") @Cached GetObjectArrayNode getObjectArrayNode) {
            // TODO split defaults of positional args from kwDefaults
            return factory().createFunction(name, getTypeName(cls), code, globals, getObjectArrayNode.execute(defaultArgs), null, null);
        }

        @Specialization
        public PFunction function(Object cls, PCode code, PDict globals, String name, PTuple defaultArgs, PTuple closure,
                        @Shared("getObjectArrayNode") @Cached GetObjectArrayNode getObjectArrayNode) {
            // TODO split defaults of positional args from kwDefaults
            return factory().createFunction(name, getTypeName(cls), code, globals, getObjectArrayNode.execute(defaultArgs), null, getClosure(getObjectArrayNode.execute(closure)));
        }

        @ExplodeLoop
        private static PCell[] getClosure(Object[] closure) {
            assert closure != null;
            PCell[] cells = new PCell[closure.length];
            for (int i = 0; i < closure.length; i++) {
                assert closure[i] instanceof PCell;
                cells[i] = (PCell) closure[i];
            }
            return cells;
        }

        @Fallback
        @SuppressWarnings("unused")
        public PFunction function(Object cls, Object code, Object globals, Object name, Object defaultArgs, Object closure) {
            throw raise(TypeError, ErrorMessages.FUNC_CONSTRUCTION_NOT_SUPPORTED, cls, code, globals, name, defaultArgs, closure);
        }

        private String getTypeName(Object typeObj) {
            if (getNameNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getNameNode = insert(TypeNodes.GetNameNode.create());
            }
            return getNameNode.execute(typeObj);
        }
    }

    // builtin-function(method-def, self, module)
    @Builtin(name = "method_descriptor", minNumOfPositionalArgs = 3, maxNumOfPositionalArgs = 6, constructsClass = PythonBuiltinClassType.PBuiltinFunction, isPublic = false)
    @GenerateNodeFactory
    public abstract static class BuiltinFunctionNode extends PythonBuiltinNode {
        @Specialization
        @SuppressWarnings("unused")
        public PFunction function(Object cls, Object method_def, Object def, Object name, Object module) {
            throw raise(TypeError, ErrorMessages.CANNOT_CREATE_INSTANCES, "'method_descriptor'");
        }
    }

    // type(object)
    // type(object, bases, dict)
    @Builtin(name = TYPE, minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 4, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PythonClass)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    @ReportPolymorphism
    public abstract static class TypeNode extends PythonBuiltinNode {
        private static final long SIZEOF_PY_OBJECT_PTR = Long.BYTES;

        @Child private ReadAttributeFromObjectNode readAttrNode;
        @Child private SetAttributeNode.Dynamic writeAttrNode;
        @Child private GetAnyAttributeNode getAttrNode;
        @Child private CastToJavaIntExactNode castToInt;
        @Child private CastToListNode castToList;
        @Child private CastToJavaStringNode castToStringNode;
        @Child private SequenceStorageNodes.LenNode slotLenNode;
        @Child private SequenceStorageNodes.GetItemNode getItemNode;
        @Child private SequenceStorageNodes.AppendNode appendNode;
        @Child private CExtNodes.PCallCapiFunction callAddNativeSlotsNode;
        @Child private CExtNodes.ToSulongNode toSulongNode;
        @Child private ReadCallerFrameNode readCallerFrameNode;
        @Child private GetMroNode getMroNode;
        @Child private IsSubtypeNode isSubtypeNode;
        @Child private GetObjectArrayNode getObjectArrayNode;
        @Child private IsAcceptableBaseNode isAcceptableBaseNode;

        protected abstract Object execute(VirtualFrame frame, Object cls, Object name, Object bases, Object dict, PKeyword[] kwds);

        @Specialization(guards = {"isNoValue(bases)", "isNoValue(dict)"})
        @SuppressWarnings("unused")
        Object type(Object cls, Object obj, PNone bases, PNone dict, PKeyword[] kwds,
                        @Cached GetClassNode getClass) {
            return getClass.execute(obj);
        }

        @Specialization(guards = "isString(wName)")
        Object typeNew(VirtualFrame frame, Object cls, Object wName, PTuple bases, PDict namespace, PKeyword[] kwds,
                        @CachedLibrary(limit = "4") PythonObjectLibrary lib,
                        @CachedLibrary(limit = "2") HashingStorageLibrary nslib,
                        @Cached BranchProfile updatedStorage,
                        @Cached("create(__NEW__)") LookupInheritedAttributeNode getNewFuncNode,
                        @Cached("create(__INIT_SUBCLASS__)") GetAttributeNode getInitSubclassNode,
                        @Cached("create(__SET_NAME__)") LookupInheritedAttributeNode getSetNameNode,
                        @Cached CastToJavaStringNode castStr,
                        @Cached CallNode callSetNameNode,
                        @Cached CallNode callInitSubclassNode,
                        @Cached CallNode callNewFuncNode) {
            // Determine the proper metatype to deal with this
            String name = castStr.execute(wName);
            Object metaclass = calculate_metaclass(frame, cls, bases, lib);
            if (metaclass != cls) {
                Object newFunc = getNewFuncNode.execute(metaclass);
                if (newFunc instanceof PBuiltinFunction && (((PBuiltinFunction) newFunc).getFunctionRootNode() == getRootNode())) {
                    // the new metaclass has the same __new__ function as we are in, continue
                } else {
                    // Pass it to the winner
                    callNewFuncNode.execute(frame, newFunc, new Object[]{metaclass, name, bases, namespace}, kwds);
                }
            }

            try {
                PythonClass newType = typeMetaclass(frame, name, bases, namespace, metaclass, nslib);

                for (DictEntry entry : nslib.entries(namespace.getDictStorage())) {
                    Object setName = getSetNameNode.execute(entry.value);
                    if (setName != PNone.NO_VALUE) {
                        callSetNameNode.execute(frame, setName, entry.value, newType, entry.key);
                    }
                }

                // Call __init_subclass__ on the parent of a newly generated type
                SuperObject superObject = factory().createSuperObject(PythonBuiltinClassType.Super);
                superObject.init(newType, newType, newType);
                callInitSubclassNode.execute(frame, getInitSubclassNode.executeObject(frame, superObject), new Object[0], kwds);

                // set '__module__' attribute
                Object moduleAttr = ensureReadAttrNode().execute(newType, __MODULE__);
                if (moduleAttr == PNone.NO_VALUE) {
                    PFrame callerFrame = getReadCallerFrameNode().executeWith(frame, 0);
                    PythonObject globals = callerFrame.getGlobals();
                    if (globals != null) {
                        String moduleName = getModuleNameFromGlobals(globals, nslib);
                        if (moduleName != null) {
                            ensureWriteAttrNode().execute(frame, newType, __MODULE__, moduleName);
                        }
                    }
                }

                // set __class__ cell contents
                Object classcell = nslib.getItem(namespace.getDictStorage(), __CLASSCELL__);
                if (classcell != null) {
                    if (classcell instanceof PCell) {
                        ((PCell) classcell).setRef(newType);
                    } else {
                        raise(TypeError, ErrorMessages.MUST_BE_A_CELL, "__classcell__");
                    }
                    if (nslib.hasKey(namespace.getDictStorage(), __CLASSCELL__)) {
                        HashingStorage newStore = nslib.delItem(namespace.getDictStorage(), __CLASSCELL__);
                        if (newStore != namespace.getDictStorage()) {
                            updatedStorage.enter();
                            namespace.setDictStorage(newStore);
                        }
                    }
                }

                return newType;
            } catch (PException e) {
                throw e;
            }
        }

        @Fallback
        Object generic(@SuppressWarnings("unused") Object cls, @SuppressWarnings("unused") Object name, Object bases, Object namespace, @SuppressWarnings("unused") Object kwds) {
            if (!(bases instanceof PTuple)) {
                throw raise(TypeError, ErrorMessages.ARG_D_MUST_BE_S_NOT_P, "type.__new__()", 2, "tuple", bases);
            } else if (namespace == PNone.NO_VALUE) {
                throw raise(TypeError, ErrorMessages.TAKES_D_OR_D_ARGS, "type()", 1, 3);
            } else if (!(namespace instanceof PDict)) {
                throw raise(TypeError, ErrorMessages.ARG_D_MUST_BE_S_NOT_P, "type.__new__()", 3, "dict", bases);
            } else {
                throw CompilerDirectives.shouldNotReachHere("type fallback reached incorrectly");
            }
        }

        private String getModuleNameFromGlobals(PythonObject globals, HashingStorageLibrary hlib) {
            Object nameAttr;
            if (globals instanceof PythonModule) {
                nameAttr = ensureReadAttrNode().execute(globals, __NAME__);
            } else if (globals instanceof PDict) {
                nameAttr = hlib.getItem(((PDict) globals).getDictStorage(), __NAME__);
            } else {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new IllegalStateException("invalid globals object");
            }
            try {
                return ensureCastToStringNode().execute(nameAttr);
            } catch (CannotCastException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new IllegalStateException();
            }
        }

        @SuppressWarnings("try")
        private PythonClass typeMetaclass(VirtualFrame frame, String name, PTuple bases, PDict namespace, Object metaclass, HashingStorageLibrary nslib) {

            Object[] array = ensureGetObjectArrayNode().execute(bases);

            PythonAbstractClass[] basesArray;
            if (array.length == 0) {
                // Adjust for empty tuple bases
                basesArray = new PythonAbstractClass[]{getCore().lookupType(PythonBuiltinClassType.PythonObject)};
            } else {
                basesArray = new PythonAbstractClass[array.length];
                for (int i = 0; i < array.length; i++) {
                    // TODO: deal with non-class bases
                    if (!(array[i] instanceof PythonAbstractClass)) {
                        throw raise(NotImplementedError, "creating a class with non-class bases");
                    } else {
                        basesArray[i] = (PythonAbstractClass) array[i];
                    }
                }
            }
            assert metaclass != null;

            if (name.indexOf('\0') != -1) {
                throw raise(ValueError, ErrorMessages.TYPE_NAME_NO_NULL_CHARS);
            }
            PythonClass pythonClass = factory().createPythonClass(metaclass, name, basesArray);

            // copy the dictionary slots over, as CPython does through PyDict_Copy
            // Also check for a __slots__ sequence variable in dict
            Object slots = null;
            for (DictEntry entry : nslib.entries(namespace.getDictStorage())) {
                Object key = entry.getKey();
                Object value = entry.getValue();
                if (__SLOTS__.equals(key)) {
                    slots = value;
                } else if (SpecialMethodNames.__NEW__.equals(key)) {
                    // see CPython: if it's a plain function, make it a static function
                    if (value instanceof PFunction) {
                        pythonClass.setAttribute(key, factory().createStaticmethodFromCallableObj(value));
                    } else {
                        pythonClass.setAttribute(key, value);
                    }
                } else if (SpecialMethodNames.__INIT_SUBCLASS__.equals(key) ||
                                SpecialMethodNames.__CLASS_GETITEM__.equals(key)) {
                    // see CPython: Special-case __init_subclass__ and
                    // __class_getitem__: if they are plain functions, make them
                    // classmethods
                    if (value instanceof PFunction) {
                        pythonClass.setAttribute(key, factory().createClassmethodFromCallableObj(value));
                    } else {
                        pythonClass.setAttribute(key, value);
                    }
                } else if (SpecialAttributeNames.__DOC__.equals(key)) {
                    // CPython sets tp_doc to a copy of dict['__doc__'], if that is a string. It
                    // forcibly encodes the string as UTF-8, and raises an error if that is not
                    // possible.
                    String doc = null;
                    if (value instanceof String) {
                        doc = (String) value;
                    } else if (value instanceof PString) {
                        doc = ((PString) value).getValue();
                    }
                    if (doc != null) {
                        if (!canEncode(doc)) {
                            throw raise(PythonBuiltinClassType.UnicodeEncodeError, ErrorMessages.CANNOT_ENCODE_DOCSTR, doc);
                        }
                    }
                    pythonClass.setAttribute(key, value);
                } else {
                    pythonClass.setAttribute(key, value);
                }
            }

            // CPython masks the __hash__ method with None when __eq__ is overriden, but __hash__ is
            // not
            Object hashMethod = nslib.getItem(namespace.getDictStorage(), __HASH__);
            if (hashMethod == null) {
                Object eqMethod = nslib.getItem(namespace.getDictStorage(), __EQ__);
                if (eqMethod != null) {
                    pythonClass.setAttribute(__HASH__, PNone.NONE);
                }
            }

            boolean addDict = false;
            if (slots == null) {
                // takes care of checking if we may_add_dict and adds it if needed
                addDictIfNative(frame, pythonClass);
                // TODO: tfel - also deal with weaklistoffset
            } else {
                // have slots

                // Make it into a list
                SequenceStorage slotsStorage;
                Object slotsObject;
                if (slots instanceof String) {
                    slotsObject = factory().createList(new Object[]{slots});
                    slotsStorage = ((PList) slotsObject).getSequenceStorage();
                } else if (slots instanceof PTuple) {
                    slotsObject = slots;
                    slotsStorage = ((PTuple) slots).getSequenceStorage();
                } else if (slots instanceof PList) {
                    slotsObject = slots;
                    slotsStorage = ((PList) slots).getSequenceStorage();
                } else {
                    slotsObject = getCastToListNode().execute(frame, slots);
                    slotsStorage = ((PList) slotsObject).getSequenceStorage();
                }
                int slotlen = getListLenNode().execute(slotsStorage);
                // TODO: tfel - check if slots are allowed. They are not if the base class is var
                // sized

                for (int i = 0; i < slotlen; i++) {
                    String slotName;
                    Object element = getSlotItemNode().execute(frame, slotsStorage, i);
                    // Check valid slot name
                    if (element instanceof String) {
                        slotName = (String) element;
                    } else {
                        throw raise(TypeError, ErrorMessages.MUST_BE_STRINGS_NOT_P, "__slots__ items", element);
                    }
                    if (__DICT__.equals(slotName)) {
                        // check that the native base does not already have tp_dictoffset
                        if (addDictIfNative(frame, pythonClass)) {
                            throw raise(TypeError, ErrorMessages.SLOT_DISALLOWED_WE_GOT_ONE, "__dict__");
                        }
                        addDict = true;
                    } else {
                        // TODO: check for __weakref__
                        // TODO avoid if native slots are inherited
                        HiddenPythonKey hiddenSlotKey = new HiddenPythonKey(slotName);
                        HiddenKeyDescriptor slotDesc = factory().createHiddenKeyDescriptor(hiddenSlotKey, pythonClass);
                        pythonClass.setAttribute(slotName, slotDesc);
                    }
                    // Make slots into a tuple
                }
                PythonContext context = getContextRef().get();
                Object state = ForeignCallContext.enter(frame, context, this);
                try {
                    pythonClass.setAttribute(__SLOTS__, slotsObject);
                    if (basesArray.length > 1) {
                        // TODO: tfel - check if secondary bases provide weakref or dict when we
                        // don't already have one
                    }

                    // checks for some name errors too
                    PTuple newSlots = copySlots(name, slotsStorage, slotlen, addDict, false, namespace, nslib);

                    // add native slot descriptors
                    if (pythonClass.needsNativeAllocation()) {
                        addNativeSlots(pythonClass, newSlots);
                    }
                } finally {
                    ForeignCallContext.exit(frame, context, state);
                }
            }

            return pythonClass;
        }

        @TruffleBoundary
        private static boolean canEncode(String doc) {
            return StandardCharsets.UTF_8.newEncoder().canEncode(doc);
        }

        @TruffleBoundary
        private PTuple copySlots(String className, SequenceStorage slotList, int slotlen, boolean add_dict, boolean add_weak, PDict namespace, HashingStorageLibrary nslib) {
            SequenceStorage newSlots = new ObjectSequenceStorage(slotlen - PInt.intValue(add_dict) - PInt.intValue(add_weak));
            int j = 0;
            for (int i = 0; i < slotlen; i++) {
                // the cast is ensured by the previous loop
                // n.b.: passing the null frame here is fine, since the storage and index are known
                // types
                String slotName = (String) getSlotItemNode().execute(null, slotList, i);
                if ((add_dict && __DICT__.equals(slotName)) || (add_weak && __WEAKREF__.equals(slotName))) {
                    continue;
                }

                slotName = mangle(className, slotName);
                if (slotName == null) {
                    return null;
                }

                setSlotItemNode().execute(newSlots, slotName, NoGeneralizationNode.DEFAULT);
                // Passing 'null' frame is fine because the caller already transfers the exception
                // state to the context.
                if (nslib.hasKey(namespace.getDictStorage(), slotName)) {
                    throw raise(PythonBuiltinClassType.ValueError, ErrorMessages.S_S_CONFLICTS_WITH_CLASS_VARIABLE, slotName, "__slots__");
                }
                j++;
            }
            assert j == slotlen - PInt.intValue(add_dict) - PInt.intValue(add_weak);

            // sort newSlots
            Arrays.sort(newSlots.getInternalArray());

            return factory().createTuple(newSlots);

        }

        private String mangle(String privateobj, String ident) {
            // Name mangling: __private becomes _classname__private. This is independent from how
            // the name is used.
            int nlen, plen, ipriv;
            if (privateobj == null || ident.charAt(0) != '_' || ident.charAt(1) != '_') {
                return ident;
            }
            nlen = ident.length();
            plen = privateobj.length();

            // Don't mangle __whatever__ or names with dots.
            if ((ident.charAt(nlen - 1) == '_' && ident.charAt(nlen - 2) == '_') || ident.indexOf('.') != -1) {
                return ident;
            }

            // Strip leading underscores from class name
            ipriv = 0;
            while (privateobj.charAt(ipriv) == '_') {
                ipriv++;
            }

            // Don't mangle if class is just underscores
            if (ipriv == plen) {
                return ident;
            }
            plen -= ipriv;

            if ((long) plen + nlen >= Integer.MAX_VALUE) {
                throw raise(OverflowError, ErrorMessages.PRIVATE_IDENTIFIER_TOO_LARGE_TO_BE_MANGLED);
            }

            /* ident = "_" + priv[ipriv:] + ident # i.e. 1+plen+nlen bytes */
            return "_" + privateobj.substring(ipriv) + ident;
        }

        private SequenceStorageNodes.GetItemNode getSlotItemNode() {
            if (getItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getItemNode = insert(SequenceStorageNodes.GetItemNode.create());
            }
            return getItemNode;
        }

        private SequenceStorageNodes.AppendNode setSlotItemNode() {
            if (appendNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                appendNode = insert(SequenceStorageNodes.AppendNode.create());
            }
            return appendNode;
        }

        private SequenceStorageNodes.LenNode getListLenNode() {
            if (slotLenNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                slotLenNode = insert(SequenceStorageNodes.LenNode.create());
            }
            return slotLenNode;
        }

        private ReadCallerFrameNode getReadCallerFrameNode() {
            if (readCallerFrameNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readCallerFrameNode = insert(ReadCallerFrameNode.create());
            }
            return readCallerFrameNode;
        }

        private void addNativeSlots(PythonManagedClass pythonClass, PTuple slots) {
            if (callAddNativeSlotsNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callAddNativeSlotsNode = insert(CExtNodes.PCallCapiFunction.create());
            }
            callAddNativeSlotsNode.call(FUN_ADD_NATIVE_SLOTS, toSulongNode.execute(pythonClass), toSulongNode.execute(slots));
        }

        private CastToListNode getCastToListNode() {
            if (castToList == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                castToList = insert(CastToListNode.create());
            }
            return castToList;
        }

        private boolean addDictIfNative(VirtualFrame frame, PythonManagedClass pythonClass) {
            boolean addedNewDict = false;
            if (pythonClass.needsNativeAllocation()) {
                for (Object cls : getMro(pythonClass)) {
                    if (PGuards.isNativeClass(cls)) {
                        // Use GetAnyAttributeNode since these are get-set-descriptors
                        long dictoffset = ensureCastToIntNode().execute(ensureGetAttributeNode().executeObject(frame, cls, __DICTOFFSET__));
                        long basicsize = ensureCastToIntNode().execute(ensureGetAttributeNode().executeObject(frame, cls, __BASICSIZE__));
                        long itemsize = ensureCastToIntNode().execute(ensureGetAttributeNode().executeObject(frame, cls, __ITEMSIZE__));
                        if (dictoffset == 0) {
                            addedNewDict = true;
                            // add_dict
                            if (itemsize != 0) {
                                dictoffset = -SIZEOF_PY_OBJECT_PTR;
                            } else {
                                dictoffset = basicsize;
                                basicsize += SIZEOF_PY_OBJECT_PTR;
                            }
                        }
                        ensureWriteAttrNode().execute(frame, pythonClass, __DICTOFFSET__, dictoffset);
                        ensureWriteAttrNode().execute(frame, pythonClass, __BASICSIZE__, basicsize);
                        ensureWriteAttrNode().execute(frame, pythonClass, __ITEMSIZE__, itemsize);
                        break;
                    }
                }
            }
            return addedNewDict;
        }

        private PythonAbstractClass[] getMro(PythonAbstractClass pythonClass) {
            if (getMroNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getMroNode = insert(GetMroNode.create());
            }
            return getMroNode.execute(pythonClass);
        }

        private Object calculate_metaclass(VirtualFrame frame, Object cls, PTuple bases, PythonObjectLibrary lib) {
            Object winner = cls;
            for (Object base : ensureGetObjectArrayNode().execute(bases)) {
                if (!ensureIsAcceptableBaseNode().execute(base)) {
                    throw raise(TypeError, ErrorMessages.TYPE_IS_NOT_ACCEPTABLE_BASE_TYPE, base);
                }
                Object typ = lib.getLazyPythonClass(base);
                if (isSubType(frame, winner, typ)) {
                    continue;
                } else if (isSubType(frame, typ, winner)) {
                    winner = typ;
                    continue;
                }
                throw raise(TypeError, ErrorMessages.METACLASS_CONFLICT);
            }
            return winner;
        }

        protected boolean isSubType(VirtualFrame frame, Object subclass, Object superclass) {
            if (isSubtypeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                isSubtypeNode = insert(IsSubtypeNode.create());
            }
            return isSubtypeNode.execute(frame, subclass, superclass);
        }

        protected static TypeNode create() {
            return BuiltinConstructorsFactory.TypeNodeFactory.create(null);
        }

        @Specialization(guards = {"!isNoValue(bases)", "!isNoValue(dict)"})
        Object typeGeneric(VirtualFrame frame, Object cls, Object name, Object bases, Object dict, PKeyword[] kwds,
                        @Cached("create()") TypeNode nextTypeNode,
                        @CachedLibrary(limit = "3") PythonObjectLibrary lib) {
            if (PGuards.isNoValue(bases) && !PGuards.isNoValue(dict) || !PGuards.isNoValue(bases) && PGuards.isNoValue(dict)) {
                throw raise(TypeError, ErrorMessages.TAKES_D_OR_D_ARGS, "type()", 1, 3);
            } else if (!(name instanceof String || name instanceof PString)) {
                throw raise(TypeError, ErrorMessages.MUST_BE_STRINGS_NOT_P, "type() argument 1", name);
            } else if (!(bases instanceof PTuple)) {
                throw raise(TypeError, ErrorMessages.MUST_BE_STRINGS_NOT_P, "type() argument 2", bases);
            } else if (!(dict instanceof PDict)) {
                throw raise(TypeError, ErrorMessages.MUST_BE_STRINGS_NOT_P, "type() argument 3", dict);
            } else if (!lib.isLazyPythonClass(cls)) {
                // TODO: this is actually allowed, deal with it
                throw raise(NotImplementedError, "creating a class with non-class metaclass");
            }
            return nextTypeNode.execute(frame, cls, name, bases, dict, kwds);
        }

        private ReadAttributeFromObjectNode ensureReadAttrNode() {
            if (readAttrNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readAttrNode = insert(ReadAttributeFromObjectNode.create());
            }
            return readAttrNode;
        }

        private GetAnyAttributeNode ensureGetAttributeNode() {
            if (getAttrNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getAttrNode = insert(GetAnyAttributeNode.create());
            }
            return getAttrNode;
        }

        private SetAttributeNode.Dynamic ensureWriteAttrNode() {
            if (writeAttrNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                writeAttrNode = insert(SetAttributeNode.Dynamic.create());
            }
            return writeAttrNode;
        }

        private CastToJavaIntExactNode ensureCastToIntNode() {
            if (castToInt == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                castToInt = insert(CastToJavaIntExactNode.create());
            }
            return castToInt;
        }

        private CastToJavaStringNode ensureCastToStringNode() {
            if (castToStringNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                castToStringNode = insert(CastToJavaStringNodeGen.create());
            }
            return castToStringNode;
        }

        private GetObjectArrayNode ensureGetObjectArrayNode() {
            if (getObjectArrayNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getObjectArrayNode = insert(GetObjectArrayNodeGen.create());
            }
            return getObjectArrayNode;
        }

        private IsAcceptableBaseNode ensureIsAcceptableBaseNode() {
            if (isAcceptableBaseNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                isAcceptableBaseNode = insert(IsAcceptableBaseNode.create());
            }
            return isAcceptableBaseNode;
        }
    }

    @Builtin(name = MODULE, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PythonModule, isPublic = false)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class ModuleNode extends PythonBuiltinNode {
        @Specialization
        @SuppressWarnings("unused")
        Object doType(PythonBuiltinClass self, Object[] varargs, PKeyword[] kwargs) {
            return factory().createPythonModule(self.getType());
        }

        @Specialization(guards = "!isPythonBuiltinClass(self)")
        @SuppressWarnings("unused")
        Object doManaged(PythonManagedClass self, Object[] varargs, PKeyword[] kwargs) {
            return factory().createPythonModule(self);
        }

        @Specialization
        @SuppressWarnings("unused")
        Object doType(PythonBuiltinClassType self, Object[] varargs, PKeyword[] kwargs) {
            return factory().createPythonModule(self);
        }

        @Specialization(guards = "isTypeNode.execute(self)")
        @SuppressWarnings("unused")
        Object doNative(PythonAbstractNativeObject self, Object[] varargs, PKeyword[] kwargs,
                        @Cached IsTypeNode isTypeNode) {
            return factory().createPythonModule(self);
        }
    }

    @Builtin(name = "NotImplementedType", minNumOfPositionalArgs = 1, constructsClass = PythonBuiltinClassType.PNotImplemented, isPublic = false)
    @GenerateNodeFactory
    public abstract static class NotImplementedTypeNode extends PythonBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        public PNotImplemented module(Object cls) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = "ellipsis", minNumOfPositionalArgs = 1, constructsClass = PythonBuiltinClassType.PEllipsis, isPublic = false)
    @GenerateNodeFactory
    public abstract static class EllipsisTypeNode extends PythonBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        public PEllipsis call(Object cls) {
            return PEllipsis.INSTANCE;
        }
    }

    @Builtin(name = "NoneType", minNumOfPositionalArgs = 1, constructsClass = PythonBuiltinClassType.PNone, isPublic = false)
    @GenerateNodeFactory
    public abstract static class NoneTypeNode extends PythonBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        public PNone module(Object cls) {
            return PNone.NONE;
        }
    }

    @Builtin(name = DICT_KEYS, takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PDictKeysView, isPublic = false)
    @GenerateNodeFactory
    public abstract static class DictKeysTypeNode extends PythonBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        public Object dictKeys(Object args, Object kwargs) {
            throw raise(TypeError, ErrorMessages.CANNOT_CREATE_INSTANCES, parentheses(DICT_KEYS));
        }
    }

    @Builtin(name = DICT_KEYITERATOR, takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PDictKeyIterator, isPublic = false)
    @GenerateNodeFactory
    public abstract static class DictKeysIteratorTypeNode extends PythonBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        public Object dictKeys(Object args, Object kwargs) {
            throw raise(TypeError, ErrorMessages.CANNOT_CREATE_INSTANCES, parentheses(DICT_KEYITERATOR));
        }
    }

    @Builtin(name = DICT_VALUES, takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PDictValuesView, isPublic = false)
    @GenerateNodeFactory
    public abstract static class DictValuesTypeNode extends PythonBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        public Object dictKeys(Object args, Object kwargs) {
            throw raise(TypeError, ErrorMessages.CANNOT_CREATE_INSTANCES, parentheses(DICT_VALUES));
        }
    }

    @Builtin(name = DICT_VALUEITERATOR, takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PDictValueIterator, isPublic = false)
    @GenerateNodeFactory
    public abstract static class DictValuesIteratorTypeNode extends PythonBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        public Object dictKeys(Object args, Object kwargs) {
            throw raise(TypeError, ErrorMessages.CANNOT_CREATE_INSTANCES, parentheses(DICT_VALUEITERATOR));
        }
    }

    @Builtin(name = DICT_ITEMS, takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PDictItemsView, isPublic = false)
    @GenerateNodeFactory
    public abstract static class DictItemsTypeNode extends PythonBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        public Object dictKeys(Object args, Object kwargs) {
            throw raise(TypeError, ErrorMessages.CANNOT_CREATE_INSTANCES, parentheses(DICT_ITEMS));
        }
    }

    @Builtin(name = DICT_ITEMITERATOR, takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PDictItemIterator, isPublic = false)
    @GenerateNodeFactory
    public abstract static class DictItemsIteratorTypeNode extends PythonBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        public Object dictKeys(Object args, Object kwargs) {
            throw raise(TypeError, ErrorMessages.CANNOT_CREATE_INSTANCES, parentheses(DICT_ITEMITERATOR));
        }
    }

    @Builtin(name = "iterator", takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PIterator, isPublic = false)
    @GenerateNodeFactory
    public abstract static class IteratorTypeNode extends PythonBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        Object iterator(Object args, Object kwargs) {
            throw raise(TypeError, ErrorMessages.CANNOT_CREATE_INSTANCES, className());
        }

        protected String className() {
            return "'iterator'";
        }
    }

    @Builtin(name = "arrayiterator", takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PArrayIterator, isPublic = false)
    @GenerateNodeFactory
    public abstract static class ArrayIteratorTypeNode extends IteratorTypeNode {
        @Override
        protected String className() {
            return "'arrayiterator'";
        }
    }

    @Builtin(name = "callable_iterator", takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PSentinelIterator, isPublic = false)
    @GenerateNodeFactory
    public abstract static class CallableIteratorTypeNode extends PythonBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        public Object iterator(Object args, Object kwargs) {
            throw raise(TypeError, ErrorMessages.CANNOT_CREATE_INSTANCES, "'callable_iterator'");
        }
    }

    @Builtin(name = "foreign_iterator", takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PForeignArrayIterator, isPublic = false)
    @GenerateNodeFactory
    public abstract static class ForeignIteratorTypeNode extends PythonBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        public Object iterator(Object args, Object kwargs) {
            throw raise(TypeError, ErrorMessages.CANNOT_CREATE_INSTANCES, "'foreign_iterator'");
        }
    }

    @Builtin(name = "generator", takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PGenerator, isPublic = false)
    @GenerateNodeFactory
    public abstract static class GeneratorTypeNode extends PythonBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        public Object generator(Object args, Object kwargs) {
            throw raise(TypeError, ErrorMessages.CANNOT_CREATE_INSTANCES, "'generator'");
        }
    }

    @Builtin(name = "method", minNumOfPositionalArgs = 3, constructsClass = PythonBuiltinClassType.PMethod, isPublic = false)
    @GenerateNodeFactory
    public abstract static class MethodTypeNode extends PythonTernaryBuiltinNode {
        @Specialization
        Object method(Object cls, PFunction func, Object self) {
            return factory().createMethod(cls, self, func);
        }

        @Specialization
        Object methodBuiltin(@SuppressWarnings("unused") Object cls, PBuiltinFunction func, Object self) {
            return factory().createMethod(self, func);
        }

        @Specialization
        Object methodGeneric(VirtualFrame frame, @SuppressWarnings("unused") Object cls, Object func, Object self,
                        @CachedLibrary(limit = "3") PythonObjectLibrary dataModelLibrary) {
            PythonContext context = getContextRef().get();
            Object state = IndirectCallContext.enter(frame, context, this);
            try {
                if (dataModelLibrary.isCallable(func)) {
                    return factory().createMethod(self, func);
                } else {
                    throw raise(TypeError, ErrorMessages.FIRST_ARG_MUST_BE_CALLABLE);
                }
            } finally {
                IndirectCallContext.exit(frame, context, state);
            }
        }
    }

    @Builtin(name = "builtin_function_or_method", minNumOfPositionalArgs = 3, constructsClass = PythonBuiltinClassType.PBuiltinMethod, isPublic = false)
    @GenerateNodeFactory
    public abstract static class BuiltinMethodTypeNode extends PythonBuiltinNode {
        @Specialization
        Object method(Object cls, Object self, PBuiltinFunction func) {
            return factory().createBuiltinMethod(cls, self, func);
        }
    }

    @Builtin(name = "frame", constructsClass = PythonBuiltinClassType.PFrame, isPublic = false)
    @GenerateNodeFactory
    public abstract static class FrameTypeNode extends PythonBuiltinNode {
        @Specialization
        Object call() {
            throw raise(RuntimeError, ErrorMessages.CANNOT_CALL_CTOR_OF, "frame type");
        }
    }

    @Builtin(name = "TracebackType", constructsClass = PythonBuiltinClassType.PTraceback, isPublic = false, minNumOfPositionalArgs = 5, parameterNames = {"$cls", "tb_next", "tb_frame", "tb_lasti",
                    "tb_lineno"})
    @GenerateNodeFactory
    public abstract static class TracebackTypeNode extends PythonBuiltinNode {
        @Specialization(limit = "1")
        Object createTraceback(@SuppressWarnings("unused") Object cls, PTraceback next, PFrame frame, Object lasti, Object lineno,
                        @CachedLibrary("lasti") PythonObjectLibrary lastiLib,
                        @CachedLibrary("lineno") PythonObjectLibrary linenoLib,
                        @Cached PythonObjectFactory factory) {
            return factory.createTraceback(frame, linenoLib.asSize(lineno), lastiLib.asSize(lasti), next);
        }

        @Specialization(limit = "1")
        Object createTraceback(@SuppressWarnings("unused") Object cls, @SuppressWarnings("unused") PNone next, PFrame frame, Object lasti, Object lineno,
                        @CachedLibrary("lasti") PythonObjectLibrary lastiLib,
                        @CachedLibrary("lineno") PythonObjectLibrary linenoLib,
                        @Cached PythonObjectFactory factory) {
            return factory.createTraceback(frame, linenoLib.asSize(lineno), lastiLib.asSize(lasti), null);
        }

        @Specialization(guards = {"!isPTraceback(next)", "!isNone(next)"})
        @SuppressWarnings("unused")
        Object errorNext(Object cls, Object next, Object frame, Object lasti, Object lineno) {
            throw raise(TypeError, "expected traceback object or None, got '%p'", next);
        }

        @Specialization(guards = "!isPFrame(frame)")
        @SuppressWarnings("unused")
        Object errorFrame(Object cls, Object next, Object frame, Object lasti, Object lineno) {
            throw raise(TypeError, "TracebackType() argument 'tb_frame' must be frame, not %p", frame);
        }

        protected static boolean isPFrame(Object obj) {
            return obj instanceof PFrame;
        }
    }

    @Builtin(name = "code", constructsClass = PythonBuiltinClassType.PCode, isPublic = false, minNumOfPositionalArgs = 15, maxNumOfPositionalArgs = 17)
    @GenerateNodeFactory
    public abstract static class CodeTypeNode extends PythonBuiltinNode {

        // limit is 2 because we expect PBytes or String
        @Specialization(guards = {"codestringBufferLib.isBuffer(codestring)", "lnotabBufferLib.isBuffer(lnotab)"}, limit = "2", rewriteOn = UnsupportedMessageException.class)
        Object call(VirtualFrame frame, Object cls, int argcount,
                        int posonlyargcount, int kwonlyargcount,
                        int nlocals, int stacksize, int flags,
                        Object codestring, PTuple constants, PTuple names,
                        PTuple varnames, Object filename, Object name,
                        int firstlineno, Object lnotab,
                        PTuple freevars, PTuple cellvars,
                        @CachedLibrary("codestring") PythonObjectLibrary codestringBufferLib,
                        @CachedLibrary("lnotab") PythonObjectLibrary lnotabBufferLib,
                        @Cached CodeNodes.CreateCodeNode createCodeNode,
                        @Cached GetObjectArrayNode getObjectArrayNode) throws UnsupportedMessageException {
            byte[] codeBytes = codestringBufferLib.getBufferBytes(codestring);
            byte[] lnotabBytes = lnotabBufferLib.getBufferBytes(lnotab);

            Object[] constantsArr = getObjectArrayNode.execute(constants);
            Object[] namesArr = getObjectArrayNode.execute(names);
            Object[] varnamesArr = getObjectArrayNode.execute(varnames);
            Object[] freevarsArr = getObjectArrayNode.execute(freevars);
            Object[] cellcarsArr = getObjectArrayNode.execute(cellvars);

            return createCodeNode.execute(frame, cls, argcount, posonlyargcount, kwonlyargcount,
                            nlocals, stacksize, flags,
                            codeBytes, constantsArr, namesArr,
                            varnamesArr, freevarsArr, cellcarsArr,
                            getStringArg(filename), getStringArg(name), firstlineno,
                            lnotabBytes);
        }

        @Specialization(guards = {"codestringBufferLib.isBuffer(codestring)", "lnotabBufferLib.isBuffer(lnotab)"}, limit = "2", rewriteOn = UnsupportedMessageException.class)
        Object call(VirtualFrame frame, Object cls, Object argcount,
                        int posonlyargcount, Object kwonlyargcount,
                        Object nlocals, Object stacksize, Object flags,
                        Object codestring, PTuple constants, PTuple names,
                        PTuple varnames, Object filename, Object name,
                        Object firstlineno, Object lnotab,
                        PTuple freevars, PTuple cellvars,
                        @CachedLibrary("codestring") PythonObjectLibrary codestringBufferLib,
                        @CachedLibrary("lnotab") PythonObjectLibrary lnotabBufferLib,
                        @CachedLibrary(limit = "2") PythonObjectLibrary objectLibrary,
                        @Cached CodeNodes.CreateCodeNode createCodeNode,
                        @Cached GetObjectArrayNode getObjectArrayNode) throws UnsupportedMessageException {
            byte[] codeBytes = codestringBufferLib.getBufferBytes(codestring);
            byte[] lnotabBytes = lnotabBufferLib.getBufferBytes(lnotab);

            Object[] constantsArr = getObjectArrayNode.execute(constants);
            Object[] namesArr = getObjectArrayNode.execute(names);
            Object[] varnamesArr = getObjectArrayNode.execute(varnames);
            Object[] freevarsArr = getObjectArrayNode.execute(freevars);
            Object[] cellcarsArr = getObjectArrayNode.execute(cellvars);

            return createCodeNode.execute(frame, cls, objectLibrary.asSize(posonlyargcount),
                            objectLibrary.asSize(argcount), objectLibrary.asSize(kwonlyargcount),
                            objectLibrary.asSize(nlocals), objectLibrary.asSize(stacksize), objectLibrary.asSize(flags),
                            codeBytes, constantsArr, namesArr,
                            varnamesArr, freevarsArr, cellcarsArr,
                            getStringArg(filename), getStringArg(name), objectLibrary.asSize(firstlineno),
                            lnotabBytes);
        }

        @Fallback
        @SuppressWarnings("unused")
        Object call(Object cls, Object argcount, Object kwonlyargcount, Object posonlyargcount,
                        Object nlocals, Object stacksize, Object flags,
                        Object codestring, Object constants, Object names,
                        Object varnames, Object filename, Object name,
                        Object firstlineno, Object lnotab,
                        Object freevars, Object cellvars) {
            throw raise(SystemError, ErrorMessages.BAD_ARG_TO_INTERNAL_FUNC);
        }

        private String getStringArg(Object arg) {
            if (arg instanceof String) {
                return (String) arg;
            } else if (arg instanceof PString) {
                return ((PString) arg).getValue();
            } else {
                throw raise(SystemError, ErrorMessages.BAD_ARG_TO_INTERNAL_FUNC);
            }
        }

        @TruffleBoundary
        private static byte[] toBytes(String data) {
            return data.getBytes();
        }
    }

    @Builtin(name = "cell", constructsClass = PythonBuiltinClassType.PCell, isPublic = false)
    @GenerateNodeFactory
    public abstract static class CellTypeNode extends PythonBuiltinNode {
        @Specialization
        Object call() {
            throw raise(RuntimeError, ErrorMessages.CANNOT_CALL_CTOR_OF, "cell type");
        }
    }

    @Builtin(name = "BaseException", constructsClass = PythonBuiltinClassType.PBaseException, isPublic = true, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class BaseExceptionNode extends PythonBuiltinNode {
        @Specialization(guards = "args.length == 0")
        Object initNoArgs(Object cls, @SuppressWarnings("unused") Object[] args, @SuppressWarnings("unused") PKeyword[] kwargs) {
            return factory().createBaseException(cls);
        }

        @Specialization(guards = "args.length != 0")
        Object initArgs(Object cls, Object[] args, @SuppressWarnings("unused") PKeyword[] kwargs) {
            return factory().createBaseException(cls, factory().createTuple(args));
        }
    }

    @Builtin(name = "mappingproxy", constructsClass = PythonBuiltinClassType.PMappingproxy, isPublic = false, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class MappingproxyNode extends PythonBuiltinNode {
        @Specialization
        Object doMapping(Object klass, PHashingCollection obj,
                        @Cached HashingCollectionNodes.GetDictStorageNode getStorage) {
            return factory().createMappingproxy(klass, getStorage.execute(obj));
        }

        @Specialization(guards = {"isSequence(frame, obj, lib)", "!isBuiltinMapping(obj)"}, limit = "1")
        Object doMapping(VirtualFrame frame, Object klass, PythonObject obj,
                        @Cached("create()") HashingStorage.InitNode initNode,
                        @SuppressWarnings("unused") @CachedLibrary("obj") PythonObjectLibrary lib) {
            return factory().createMappingproxy(klass, initNode.execute(frame, obj, PKeyword.EMPTY_KEYWORDS));
        }

        @Specialization(guards = "isNoValue(none)")
        @SuppressWarnings("unused")
        Object doMissing(Object klass, PNone none) {
            throw raise(TypeError, ErrorMessages.MISSING_D_REQUIRED_S_ARGUMENT_S_POS, "mappingproxy()", "mapping", 1);
        }

        @Specialization(guards = {"!isSequence(frame, obj, lib)", "!isNoValue(obj)"}, limit = "1")
        Object doInvalid(@SuppressWarnings("unused") VirtualFrame frame, @SuppressWarnings("unused") Object klass, Object obj,
                        @SuppressWarnings("unused") @CachedLibrary("obj") PythonObjectLibrary lib) {
            throw raise(TypeError, ErrorMessages.ARG_MUST_BE_S_NOT_P, "mappingproxy()", "mapping", obj);
        }

        protected boolean isBuiltinMapping(Object o) {
            return o instanceof PHashingCollection;
        }

        protected boolean isSequence(VirtualFrame frame, Object o, PythonObjectLibrary library) {
            PythonContext context = getContextRef().get();
            Object state = IndirectCallContext.enter(frame, context, this);
            try {
                return library.isSequence(o);
            } finally {
                IndirectCallContext.exit(frame, context, state);
            }
        }
    }

    @Builtin(name = "getset_descriptor", constructsClass = PythonBuiltinClassType.GetSetDescriptor, isPublic = false, minNumOfPositionalArgs = 1, parameterNames = {"cls", "fget", "fset", "name",
                    "owner"})
    @GenerateNodeFactory
    public abstract static class GetSetDescriptorNode extends PythonBuiltinNode {
        private void denyInstantiationAfterInitialization() {
            if (getCore().isInitialized()) {
                throw raise(TypeError, ErrorMessages.CANNOT_CREATE_INSTANCES, "'getset_descriptor'");
            }
        }

        @Specialization(guards = {"!isNoValue(get)", "!isNoValue(set)"})
        @TruffleBoundary
        Object call(@SuppressWarnings("unused") Object getSetClass, Object get, Object set, String name, PythonAbstractClass owner) {
            denyInstantiationAfterInitialization();
            return factory().createGetSetDescriptor(get, set, name, owner);
        }

        @Specialization(guards = {"!isNoValue(get)", "isNoValue(set)"})
        @TruffleBoundary
        Object call(@SuppressWarnings("unused") Object getSetClass, Object get, @SuppressWarnings("unused") PNone set, String name, PythonAbstractClass owner) {
            denyInstantiationAfterInitialization();
            return factory().createGetSetDescriptor(get, null, name, owner);
        }

        @Specialization(guards = {"isNoValue(get)", "isNoValue(set)"})
        @TruffleBoundary
        @SuppressWarnings("unused")
        Object call(Object getSetClass, PNone get, PNone set, String name, PythonAbstractClass owner) {
            denyInstantiationAfterInitialization();
            return factory().createGetSetDescriptor(null, null, name, owner);
        }
    }

    // slice(stop)
    // slice(start, stop[, step])
    @Builtin(name = "slice", minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 4, constructsClass = PythonBuiltinClassType.PSlice)
    @GenerateNodeFactory
    public abstract static class CreateSliceNode extends PythonBuiltinNode {

        @Specialization(guards = {"isNoValue(second)", "isNoValue(third)"})
        @SuppressWarnings("unused")
        Object stop(VirtualFrame frame, Object cls, Object first, Object second, Object third,
                        @Cached("create()") SliceLiteralNode sliceNode) {
            return sliceNode.execute(frame, PNone.NONE, first, PNone.NONE);
        }

        @Specialization(guards = {"!isNoValue(second)", "isNoValue(third)"})
        @SuppressWarnings("unused")
        Object startStop(VirtualFrame frame, Object cls, Object first, Object second, Object third,
                        @Cached("create()") SliceLiteralNode sliceNode) {
            return sliceNode.execute(frame, first, second, PNone.NONE);
        }

        @Specialization(guards = {"!isNoValue(second)", "!isNoValue(third)"})
        Object slice(VirtualFrame frame, @SuppressWarnings("unused") Object cls, Object first, Object second, Object third,
                        @Cached("create()") SliceLiteralNode sliceNode) {
            return sliceNode.execute(frame, first, second, third);
        }
    }

    // buffer([iterable])
    @Builtin(name = "buffer", minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 3, constructsClass = PythonBuiltinClassType.PBuffer)
    @GenerateNodeFactory
    public abstract static class BufferNode extends PythonBuiltinNode {
        @Child private LookupInheritedAttributeNode getSetItemNode;

        @Specialization(guards = "isNoValue(readOnly)")
        protected PBuffer construct(Object cls, Object delegate, @SuppressWarnings("unused") PNone readOnly) {
            return factory().createBuffer(cls, delegate, !hasSetItem(delegate));
        }

        @Specialization
        protected PBuffer construct(Object cls, Object delegate, boolean readOnly) {
            return factory().createBuffer(cls, delegate, readOnly);
        }

        @Fallback
        public PBuffer doGeneric(@SuppressWarnings("unused") Object cls, Object delegate, @SuppressWarnings("unused") Object readOnly) {
            throw raise(TypeError, ErrorMessages.CANNOT_CREATE_BUFFER_FOR, delegate);
        }

        public boolean hasSetItem(Object object) {
            if (getSetItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getSetItemNode = insert(LookupInheritedAttributeNode.create(__SETITEM__));
            }
            return getSetItemNode.execute(object) != PNone.NO_VALUE;
        }
    }

    // memoryview(obj)
    @Builtin(name = MEMORYVIEW, minNumOfPositionalArgs = 2, constructsClass = PythonBuiltinClassType.PMemoryView)
    @GenerateNodeFactory
    public abstract static class MemoryViewNode extends PythonBuiltinNode {
        @Specialization
        public PMemoryView doGeneric(Object cls, Object value) {
            return factory().createMemoryView(cls, value);
        }
    }

    // super()
    @Builtin(name = SUPER, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 3, constructsClass = PythonBuiltinClassType.Super)
    @GenerateNodeFactory
    public abstract static class SuperInitNode extends PythonTernaryBuiltinNode {
        @Specialization
        Object doObjectIndirect(Object self, @SuppressWarnings("unused") Object type, @SuppressWarnings("unused") Object object) {
            return factory().createSuperObject(self);
        }
    }

    @Builtin(name = CLASSMETHOD, minNumOfPositionalArgs = 2, constructsClass = PythonBuiltinClassType.PClassmethod, doc = "classmethod(function) -> method\n" +
                    "\n" +
                    "Convert a function to be a class method.\n" +
                    "\n" +
                    "A class method receives the class as implicit first argument,\n" +
                    "just like an instance method receives the instance.\n" +
                    "To declare a class method, use this idiom:\n" +
                    "\n" +
                    "  class C:\n" +
                    "      @classmethod\n" +
                    "      def f(cls, arg1, arg2, ...):\n" +
                    "          ...\n" +
                    "\n" +
                    "It can be called either on the class (e.g. C.f()) or on an instance\n" +
                    "(e.g. C().f()).  The instance is ignored except for its class.\n" +
                    "If a class method is called for a derived class, the derived class\n" +
                    "object is passed as the implied first argument.\n" +
                    "\n" +
                    "Class methods are different than C++ or Java static methods.\n" +
                    "If you want those, see the staticmethod builtin.")
    @GenerateNodeFactory
    public abstract static class ClassmethodNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object doObjectIndirect(Object self, @SuppressWarnings("unused") Object callable) {
            return factory().createClassmethod(self);
        }
    }

    @Builtin(name = STATICMETHOD, minNumOfPositionalArgs = 2, constructsClass = PythonBuiltinClassType.PStaticmethod)
    @GenerateNodeFactory
    public abstract static class StaticmethodNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object doObjectIndirect(Object self, @SuppressWarnings("unused") Object callable) {
            return factory().createStaticmethod(self);
        }
    }

    @Builtin(name = MAP, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PMap)
    @GenerateNodeFactory
    public abstract static class MapNode extends PythonVarargsBuiltinNode {
        @Specialization
        PMap doit(Object self, @SuppressWarnings("unused") Object[] args, @SuppressWarnings("unused") PKeyword[] keywords) {
            return factory().createMap(self);
        }
    }

    @TruffleBoundary
    private static String parentheses(String str) {
        return new StringBuilder("'").append(str).append("'").toString();
    }
}
