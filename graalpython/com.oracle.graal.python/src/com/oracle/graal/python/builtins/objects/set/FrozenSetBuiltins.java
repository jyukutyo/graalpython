/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
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
package com.oracle.graal.python.builtins.objects.set;

import static com.oracle.graal.python.nodes.SpecialMethodNames.__AND__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__CONTAINS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__EQ__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__HASH__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ITER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LEN__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__OR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REDUCE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__SUB__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__XOR__;

import java.util.Iterator;
import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.common.EconomicMapStorage;
import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodes.GetDictStorageNode;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary;
import com.oracle.graal.python.builtins.objects.common.PHashingCollection;
import com.oracle.graal.python.builtins.objects.dict.PDictView;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PArguments.ThreadState;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.set.FrozenSetBuiltinsFactory.BinaryUnionNodeGen;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.nodes.control.GetIteratorExpressionNode.GetIteratorNode;
import com.oracle.graal.python.nodes.control.GetNextNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.ValueProfile;

@CoreFunctions(extendClasses = {PythonBuiltinClassType.PFrozenSet, PythonBuiltinClassType.PSet})
public final class FrozenSetBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return FrozenSetBuiltinsFactory.getFactories();
    }

    @Builtin(name = __ITER__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class IterNode extends PythonUnaryBuiltinNode {
        @Specialization(limit = "1")
        public Object iter(PBaseSet self,
                        @Cached GetDictStorageNode getStorage,
                        @CachedLibrary("getStorage.execute(self)") HashingStorageLibrary lib) {
            HashingStorage storage = getStorage.execute(self);
            return factory().createBaseSetIterator(self, lib.keys(storage).iterator(), storage, lib.length(storage));
        }
    }

    @Builtin(name = __LEN__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class LenNode extends PythonUnaryBuiltinNode {
        @Specialization(limit = "1")
        public int len(PBaseSet self,
                        @Cached GetDictStorageNode getStorage,
                        @CachedLibrary("getStorage.execute(self)") HashingStorageLibrary lib) {
            return lib.length(getStorage.execute(self));
        }
    }

    @Builtin(name = __REDUCE__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReduceNode extends PythonUnaryBuiltinNode {

        @Specialization(limit = "1")
        public Object reduce(PBaseSet self,
                        @Cached GetDictStorageNode getStorage,
                        @CachedLibrary("getStorage.execute(self)") HashingStorageLibrary lib,
                        @CachedLibrary("self") PythonObjectLibrary plib) {
            HashingStorage storage = getStorage.execute(self);
            int len = lib.length(storage);
            Iterator<Object> keys = lib.keys(storage).iterator();
            Object[] keysArray = new Object[len];
            for (int i = 0; keys.hasNext(); i++) {
                keysArray[i] = keys.next();
            }
            PTuple contents = factory().createTuple(new Object[]{factory().createList(keysArray)});
            return factory().createTuple(new Object[]{plib.getLazyPythonClass(self), contents, PNone.NONE});
        }
    }

    @Builtin(name = __EQ__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class EqNode extends PythonBinaryBuiltinNode {
        @Specialization(limit = "1")
        boolean doSetSameType(VirtualFrame frame, PBaseSet self, PBaseSet other,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @Cached GetDictStorageNode getStorage,
                        @CachedLibrary("getStorage.execute(self)") HashingStorageLibrary lib) {
            return lib.compareKeysWithFrame(getStorage.execute(self), getStorage.execute(other), hasFrame, frame) == 0;
        }

        @Fallback
        @SuppressWarnings("unused")
        PNotImplemented doGeneric(Object self, Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    private static HashingStorage getStringAsHashingStorage(VirtualFrame frame, String str, HashingStorageLibrary lib, ConditionProfile hasFrame) {
        HashingStorage storage = EconomicMapStorage.create(PString.length(str));
        for (int i = 0; i < PString.length(str); i++) {
            String key = PString.valueOf(PString.charAt(str, i));
            lib.setItemWithFrame(storage, key, PNone.NONE, hasFrame, frame);
        }
        return storage;
    }

    @Builtin(name = __AND__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class AndNode extends PythonBinaryBuiltinNode {

        @Specialization(limit = "1")
        PBaseSet doPBaseSet(VirtualFrame frame, PSet left, String right,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @Cached GetDictStorageNode getStorage,
                        @CachedLibrary("getStorage.execute(left)") HashingStorageLibrary leftLib,
                        @CachedLibrary(limit = "1") HashingStorageLibrary lib) {
            return factory().createSet(leftLib.intersectWithFrame(getStorage.execute(left), getStringAsHashingStorage(frame, right, lib, hasFrame), hasFrame, frame));
        }

        @Specialization(limit = "1")
        PBaseSet doPBaseSet(VirtualFrame frame, PFrozenSet left, String right,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @Cached GetDictStorageNode getStorage,
                        @CachedLibrary("getStorage.execute(left)") HashingStorageLibrary leftLib,
                        @CachedLibrary(limit = "1") HashingStorageLibrary lib) {
            return factory().createFrozenSet(leftLib.intersectWithFrame(getStorage.execute(left), getStringAsHashingStorage(frame, right, lib, hasFrame), hasFrame, frame));
        }

        @Specialization(limit = "1")
        PBaseSet doPBaseSet(VirtualFrame frame, PSet left, PBaseSet right,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @Cached GetDictStorageNode getStorage,
                        @CachedLibrary("getStorage.execute(left)") HashingStorageLibrary leftLib) {
            HashingStorage storage = leftLib.intersectWithFrame(getStorage.execute(left), getStorage.execute(right), hasFrame, frame);
            return factory().createSet(storage);
        }

        @Specialization(limit = "1")
        PBaseSet doPBaseSet(VirtualFrame frame, PFrozenSet left, PBaseSet right,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @Cached GetDictStorageNode getStorage,
                        @CachedLibrary("getStorage.execute(left)") HashingStorageLibrary leftLib) {
            HashingStorage storage = leftLib.intersectWithFrame(getStorage.execute(left), getStorage.execute(right), hasFrame, frame);
            return factory().createFrozenSet(storage);
        }

        @Specialization(limit = "1")
        PBaseSet doPBaseSet(VirtualFrame frame, PSet left, PDictView right,
                        @Cached("create()") SetNodes.ConstructSetNode constructSetNode,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @Cached GetDictStorageNode getStorage,
                        @CachedLibrary("getStorage.execute(left)") HashingStorageLibrary leftLib) {
            PSet rightSet = constructSetNode.executeWith(frame, right);
            HashingStorage storage = leftLib.intersectWithFrame(getStorage.execute(left), getStorage.execute(rightSet), hasFrame, frame);
            return factory().createSet(storage);
        }

        @Specialization(limit = "1")
        PBaseSet doPBaseSet(VirtualFrame frame, PFrozenSet left, PDictView right,
                        @Cached("create()") SetNodes.ConstructSetNode constructSetNode,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @Cached GetDictStorageNode getStorage,
                        @CachedLibrary("getStorage.execute(left)") HashingStorageLibrary leftLib) {
            PSet rightSet = constructSetNode.executeWith(frame, right);
            HashingStorage storage = leftLib.intersectWithFrame(getStorage.execute(left), getStorage.execute(rightSet), hasFrame, frame);
            return factory().createSet(storage);
        }

        @Fallback
        Object doAnd(Object self, Object other) {
            throw raise(PythonErrorType.TypeError, ErrorMessages.UNSUPPORTED_OPERAND_TYPES_FOR_S_P_AND_P, "&", self, other);
        }
    }

    @Builtin(name = __OR__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class OrNode extends PythonBinaryBuiltinNode {

        protected static HashingStorage union(HashingStorageLibrary lib, HashingStorage left, HashingStorage right) {
            return lib.union(left, right);
        }

        @Specialization(limit = "1")
        PBaseSet doPBaseSet(VirtualFrame frame, PSet left, String right,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @Cached GetDictStorageNode getStorage,
                        @CachedLibrary("getStorage.execute(left)") HashingStorageLibrary leftLib,
                        @CachedLibrary(limit = "1") HashingStorageLibrary lib) {
            return factory().createSet(union(leftLib, getStorage.execute(left), getStringAsHashingStorage(frame, right, lib, hasFrame)));
        }

        @Specialization(limit = "1")
        PBaseSet doPBaseSet(VirtualFrame frame, PFrozenSet left, String right,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @Cached GetDictStorageNode getStorage,
                        @CachedLibrary("getStorage.execute(left)") HashingStorageLibrary leftLib,
                        @CachedLibrary(limit = "1") HashingStorageLibrary lib) {
            return factory().createFrozenSet(union(leftLib, getStorage.execute(left), getStringAsHashingStorage(frame, right, lib, hasFrame)));
        }

        @Specialization(limit = "1")
        PBaseSet doPBaseSet(@SuppressWarnings("unused") VirtualFrame frame, PSet left, PBaseSet right,
                        @Cached GetDictStorageNode getStorage,
                        @CachedLibrary("getStorage.execute(left)") HashingStorageLibrary leftLib) {
            HashingStorage storage = union(leftLib, getStorage.execute(left), getStorage.execute(right));
            return factory().createSet(storage);
        }

        @Specialization(limit = "1")
        PBaseSet doPBaseSet(@SuppressWarnings("unused") VirtualFrame frame, PFrozenSet left, PBaseSet right,
                        @Cached GetDictStorageNode getStorage,
                        @CachedLibrary("getStorage.execute(left)") HashingStorageLibrary leftLib) {
            HashingStorage storage = union(leftLib, getStorage.execute(left), getStorage.execute(right));
            return factory().createFrozenSet(storage);
        }

        @Specialization(limit = "1")
        PBaseSet doPBaseSet(VirtualFrame frame, PSet left, PDictView right,
                        @Cached("create()") SetNodes.ConstructSetNode constructSetNode,
                        @Cached GetDictStorageNode getStorage,
                        @CachedLibrary("getStorage.execute(left)") HashingStorageLibrary leftLib) {
            PSet rightSet = constructSetNode.executeWith(frame, right);
            HashingStorage storage = union(leftLib, getStorage.execute(left), getStorage.execute(rightSet));
            return factory().createSet(storage);
        }

        @Specialization(limit = "1")
        PBaseSet doPBaseSet(VirtualFrame frame, PFrozenSet left, PDictView right,
                        @Cached("create()") SetNodes.ConstructSetNode constructSetNode,
                        @Cached GetDictStorageNode getStorage,
                        @CachedLibrary("getStorage.execute(left)") HashingStorageLibrary leftLib) {
            PSet rightSet = constructSetNode.executeWith(frame, right);
            HashingStorage storage = union(leftLib, getStorage.execute(left), getStorage.execute(rightSet));
            return factory().createSet(storage);
        }

        @Fallback
        Object doOr(Object self, Object other) {
            throw raise(PythonErrorType.TypeError, ErrorMessages.UNSUPPORTED_OPERAND_TYPES_FOR_S_P_AND_P, "|", self, other);
        }
    }

    @Builtin(name = __XOR__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class XorNode extends PythonBinaryBuiltinNode {

        protected static HashingStorage xor(HashingStorageLibrary lib, HashingStorage left, HashingStorage right) {
            return lib.xor(left, right);
        }

        @Specialization(limit = "1")
        PBaseSet doPBaseSet(VirtualFrame frame, PSet left, String right,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @Cached GetDictStorageNode getStorage,
                        @CachedLibrary("getStorage.execute(left)") HashingStorageLibrary leftLib,
                        @CachedLibrary(limit = "1") HashingStorageLibrary lib) {
            return factory().createSet(xor(leftLib, getStorage.execute(left), getStringAsHashingStorage(frame, right, lib, hasFrame)));
        }

        @Specialization(limit = "1")
        PBaseSet doPBaseSet(VirtualFrame frame, PFrozenSet left, String right,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @Cached GetDictStorageNode getStorage,
                        @CachedLibrary("getStorage.execute(left)") HashingStorageLibrary leftLib,
                        @CachedLibrary(limit = "1") HashingStorageLibrary lib) {
            return factory().createFrozenSet(xor(leftLib, getStorage.execute(left), getStringAsHashingStorage(frame, right, lib, hasFrame)));
        }

        @Specialization(limit = "1")
        PBaseSet doPBaseSet(@SuppressWarnings("unused") VirtualFrame frame, PSet left, PBaseSet right,
                        @Cached GetDictStorageNode getStorage,
                        @CachedLibrary("getStorage.execute(left)") HashingStorageLibrary leftLib) {
            HashingStorage storage = xor(leftLib, getStorage.execute(left), getStorage.execute(right));
            return factory().createSet(storage);
        }

        @Specialization(limit = "1")
        PBaseSet doPBaseSet(@SuppressWarnings("unused") VirtualFrame frame, PFrozenSet left, PBaseSet right,
                        @Cached GetDictStorageNode getStorage,
                        @CachedLibrary("getStorage.execute(left)") HashingStorageLibrary leftLib) {
            HashingStorage storage = xor(leftLib, getStorage.execute(left), getStorage.execute(right));
            return factory().createFrozenSet(storage);
        }

        @Specialization(limit = "1")
        PBaseSet doPBaseSet(VirtualFrame frame, PSet left, PDictView right,
                        @Cached("create()") SetNodes.ConstructSetNode constructSetNode,
                        @Cached GetDictStorageNode getStorage,
                        @CachedLibrary("getStorage.execute(left)") HashingStorageLibrary leftLib) {
            PSet rightSet = constructSetNode.executeWith(frame, right);
            HashingStorage storage = xor(leftLib, getStorage.execute(left), getStorage.execute(rightSet));
            return factory().createSet(storage);
        }

        @Specialization(limit = "1")
        PBaseSet doPBaseSet(VirtualFrame frame, PFrozenSet left, PDictView right,
                        @Cached("create()") SetNodes.ConstructSetNode constructSetNode,
                        @Cached GetDictStorageNode getStorage,
                        @CachedLibrary("getStorage.execute(left)") HashingStorageLibrary leftLib) {
            PSet rightSet = constructSetNode.executeWith(frame, right);
            HashingStorage storage = xor(leftLib, getStorage.execute(left), getStorage.execute(rightSet));
            return factory().createSet(storage);
        }

        @Fallback
        Object doOr(Object self, Object other) {
            throw raise(PythonErrorType.TypeError, ErrorMessages.UNSUPPORTED_OPERAND_TYPES_FOR_S_P_AND_P, "^", self, other);
        }
    }

    @Builtin(name = __SUB__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class SubNode extends PythonBinaryBuiltinNode {

        @Specialization(limit = "1")
        PBaseSet doPBaseSet(VirtualFrame frame, PSet left, PBaseSet right,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @Cached GetDictStorageNode getStorage,
                        @CachedLibrary("getStorage.execute(left)") HashingStorageLibrary lib) {
            HashingStorage storage = lib.diffWithFrame(getStorage.execute(left), getStorage.execute(right), hasFrame, frame);
            return factory().createSet(storage);
        }

        @Specialization(limit = "1")
        PBaseSet doPBaseSet(VirtualFrame frame, PFrozenSet left, PBaseSet right,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @Cached GetDictStorageNode getStorage,
                        @CachedLibrary("getStorage.execute(left)") HashingStorageLibrary lib) {
            HashingStorage storage = lib.diffWithFrame(getStorage.execute(left), getStorage.execute(right), hasFrame, frame);
            return factory().createSet(storage);
        }

        @Fallback
        Object doSub(Object self, Object other) {
            throw raise(PythonErrorType.TypeError, ErrorMessages.UNSUPPORTED_OPERAND_TYPES_FOR_S_P_AND_P, "-", self, other);
        }
    }

    @Builtin(name = __CONTAINS__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class ContainsNode extends PythonBinaryBuiltinNode {
        @Specialization(limit = "1")
        boolean contains(VirtualFrame frame, PBaseSet self, Object key,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @Cached GetDictStorageNode getStorage,
                        @CachedLibrary("getStorage.execute(self)") HashingStorageLibrary lib) {
            return lib.hasKeyWithFrame(getStorage.execute(self), key, hasFrame, frame);
        }
    }

    @Builtin(name = "union", minNumOfPositionalArgs = 1, takesVarArgs = true)
    @GenerateNodeFactory
    abstract static class UnionNode extends PythonBuiltinNode {

        @Child private BinaryUnionNode binaryUnionNode;

        @CompilationFinal private ValueProfile setTypeProfile;

        private BinaryUnionNode getBinaryUnionNode() {
            if (binaryUnionNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                binaryUnionNode = insert(BinaryUnionNode.create());
            }
            return binaryUnionNode;
        }

        private ValueProfile getSetTypeProfile() {
            if (setTypeProfile == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                setTypeProfile = ValueProfile.createClassProfile();
            }
            return setTypeProfile;
        }

        @Specialization(guards = {"args.length == len", "args.length < 32"}, limit = "3")
        PBaseSet doCached(VirtualFrame frame, PBaseSet self, Object[] args,
                        @Cached("args.length") int len,
                        @Cached GetDictStorageNode getStorage,
                        @CachedLibrary("getStorage.execute(self)") HashingStorageLibrary lib) {
            HashingStorage result = lib.copy(getStorage.execute(self));
            for (int i = 0; i < len; i++) {
                result = getBinaryUnionNode().execute(frame, result, args[i]);
            }
            return create(self, result);
        }

        @Specialization(replaces = "doCached", limit = "1")
        PBaseSet doGeneric(VirtualFrame frame, PBaseSet self, Object[] args,
                        @Cached GetDictStorageNode getStorage,
                        @CachedLibrary("getStorage.execute(self)") HashingStorageLibrary lib) {
            HashingStorage result = lib.copy(getStorage.execute(self));
            for (int i = 0; i < args.length; i++) {
                result = getBinaryUnionNode().execute(frame, result, args[i]);
            }
            return create(self, result);
        }

        private PBaseSet create(PBaseSet left, HashingStorage storage) {
            if (getSetTypeProfile().profile(left) instanceof PFrozenSet) {
                return factory().createFrozenSet(storage);
            }
            return factory().createSet(storage);
        }
    }

    abstract static class BinaryUnionNode extends PNodeWithContext {
        public abstract HashingStorage execute(VirtualFrame frame, HashingStorage left, Object right);

        @Specialization(limit = "1")
        HashingStorage doHashingCollection(@SuppressWarnings("unused") VirtualFrame frame, EconomicMapStorage selfStorage, PHashingCollection other,
                        @Cached GetDictStorageNode getStorage,
                        @CachedLibrary("selfStorage") HashingStorageLibrary lib) {
            return lib.union(selfStorage, getStorage.execute(other));
        }

        @Specialization
        HashingStorage doIterable(VirtualFrame frame, HashingStorage dictStorage, Object iterable,
                        @Cached("create()") GetIteratorNode getIteratorNode,
                        @Cached("create()") GetNextNode next,
                        @Cached("create()") IsBuiltinClassProfile errorProfile,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @CachedLibrary(limit = "2") HashingStorageLibrary lib) {
            HashingStorage curStorage = dictStorage;
            Object iterator = getIteratorNode.executeWith(frame, iterable);
            while (true) {
                Object key;
                try {
                    key = next.execute(frame, iterator);
                } catch (PException e) {
                    e.expectStopIteration(errorProfile);
                    return curStorage;
                }
                curStorage = lib.setItemWithFrame(curStorage, key, PNone.NONE, hasFrame, frame);
            }
        }

        public static BinaryUnionNode create() {
            return BinaryUnionNodeGen.create();
        }
    }

    @Builtin(name = "issubset", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class IsSubsetNode extends PythonBinaryBuiltinNode {
        @Specialization(limit = "1")
        boolean isSubSet(VirtualFrame frame, PBaseSet self, PBaseSet other,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @Cached GetDictStorageNode getStorage,
                        @CachedLibrary("getStorage.execute(self)") HashingStorageLibrary lib) {
            return lib.compareKeysWithFrame(getStorage.execute(self), getStorage.execute(other), hasFrame, frame) <= 0;
        }

        @Specialization(replaces = "isSubSet", limit = "1")
        boolean isSubSetGeneric(VirtualFrame frame, PBaseSet self, Object other,
                        @Cached SetNodes.ConstructSetNode constructSetNode,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @Cached GetDictStorageNode getStorage,
                        @CachedLibrary("getStorage.execute(self)") HashingStorageLibrary lib) {
            PSet otherSet = constructSetNode.executeWith(frame, other);
            return lib.compareKeysWithFrame(getStorage.execute(self), getStorage.execute(otherSet), hasFrame, frame) <= 0;
        }
    }

    @Builtin(name = "issuperset", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class IsSupersetNode extends PythonBinaryBuiltinNode {
        @Specialization(limit = "1")
        boolean isSuperSet(VirtualFrame frame, PBaseSet self, PBaseSet other,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @Cached GetDictStorageNode getStorage,
                        @CachedLibrary("getStorage.execute(other)") HashingStorageLibrary lib) {
            return lib.compareKeysWithFrame(getStorage.execute(other), getStorage.execute(self), hasFrame, frame) <= 0;
        }

        @Specialization(replaces = "isSuperSet")
        boolean isSuperSetGeneric(VirtualFrame frame, PBaseSet self, Object other,
                        @Cached SetNodes.ConstructSetNode constructSetNode,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @Cached GetDictStorageNode getStorage,
                        @CachedLibrary(limit = "3") HashingStorageLibrary lib) {
            PSet otherSet = constructSetNode.executeWith(frame, other);
            return lib.compareKeysWithFrame(getStorage.execute(otherSet), getStorage.execute(self), hasFrame, frame) <= 0;
        }

    }

    @Builtin(name = "isdisjoint", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class IsDisjointNode extends PythonBinaryBuiltinNode {

        @Specialization(guards = "self == other", limit = "2")
        boolean isDisjointSameObject(VirtualFrame frame, PBaseSet self, @SuppressWarnings("unused") PBaseSet other,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @Cached GetDictStorageNode getStorage,
                        @CachedLibrary("getStorage.execute(self)") HashingStorageLibrary lib) {
            ThreadState state = PArguments.getThreadStateOrNull(frame, hasFrame);
            return lib.lengthWithState(getStorage.execute(self), state) == 0;
        }

        @Specialization(guards = {"self != other", "cannotBeOverridden(pLib.getLazyPythonClass(other))"}, limit = "2")
        boolean isDisjointFastPath(VirtualFrame frame, PBaseSet self, PBaseSet other,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @Cached GetDictStorageNode getStorage,
                        @CachedLibrary("getStorage.execute(self)") HashingStorageLibrary selfLib,
                        @SuppressWarnings("unused") @CachedLibrary("other") PythonObjectLibrary pLib) {
            ThreadState state = PArguments.getThreadStateOrNull(frame, hasFrame);
            return selfLib.isDisjointWithState(getStorage.execute(self), getStorage.execute(other), state);
        }

        @Specialization(guards = {"self != other", "!cannotBeOverridden(pLib.getLazyPythonClass(other))"}, limit = "2")
        boolean isDisjointWithOtherSet(VirtualFrame frame, PBaseSet self, PBaseSet other,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @Cached GetDictStorageNode getStorage,
                        @CachedLibrary("getStorage.execute(self)") HashingStorageLibrary selfLib,
                        @SuppressWarnings("unused") @CachedLibrary("other") PythonObjectLibrary pLib,
                        @Cached GetIteratorNode getIteratorNode,
                        @Cached GetNextNode getNextNode,
                        @Cached IsBuiltinClassProfile errorProfile) {
            return isDisjointGeneric(frame, self, other, hasFrame, getStorage, selfLib, getIteratorNode, getNextNode, errorProfile);
        }

        @Specialization(guards = {"!isAnySet(other)"}, limit = "3")
        boolean isDisjointGeneric(VirtualFrame frame, PBaseSet self, Object other,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @Cached GetDictStorageNode getStorage,
                        @CachedLibrary("getStorage.execute(self)") HashingStorageLibrary selfLib,
                        @Cached GetIteratorNode getIteratorNode,
                        @Cached GetNextNode getNextNode,
                        @Cached IsBuiltinClassProfile errorProfile) {
            ThreadState state = PArguments.getThreadStateOrNull(frame, hasFrame);
            HashingStorage selfStorage = getStorage.execute(self);
            Object iterator = getIteratorNode.executeWith(frame, other);
            while (true) {
                try {
                    Object nextValue = getNextNode.execute(frame, iterator);
                    if (selfLib.hasKeyWithState(selfStorage, nextValue, state)) {
                        return false;
                    }
                } catch (PException e) {
                    e.expectStopIteration(errorProfile);
                    return true;
                }
            }
        }

    }

    @Builtin(name = __LE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class LessEqualNode extends PythonBinaryBuiltinNode {
        @Specialization(limit = "1")
        boolean doLE(VirtualFrame frame, PBaseSet self, PBaseSet other,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @Cached GetDictStorageNode getStorage,
                        @CachedLibrary("getStorage.execute(self)") HashingStorageLibrary lib) {
            return lib.compareKeysWithFrame(getStorage.execute(self), getStorage.execute(other), hasFrame, frame) <= 0;
        }

        @Fallback
        @SuppressWarnings("unused")
        PNotImplemented doNotImplemented(Object self, Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __GE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class GreaterEqualNode extends PythonBinaryBuiltinNode {
        @Specialization(limit = "1")
        boolean doGE(VirtualFrame frame, PBaseSet self, PBaseSet other,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @Cached GetDictStorageNode getStorage,
                        @CachedLibrary("getStorage.execute(other)") HashingStorageLibrary lib) {
            return lib.compareKeysWithFrame(getStorage.execute(other), getStorage.execute(self), hasFrame, frame) <= 0;
        }

        @Fallback
        @SuppressWarnings("unused")
        PNotImplemented doNotImplemented(Object self, Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __LT__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class LessThanNode extends PythonBinaryBuiltinNode {
        @Child private LessEqualNode lessEqualNode;

        private LessEqualNode getLessEqualNode() {
            if (lessEqualNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lessEqualNode = insert(FrozenSetBuiltinsFactory.LessEqualNodeFactory.create());
            }
            return lessEqualNode;
        }

        @Specialization
        boolean isLessThan(VirtualFrame frame, PBaseSet self, PBaseSet other,
                        @Cached("createBinaryProfile()") ConditionProfile sizeProfile) {
            if (sizeProfile.profile(self.size() >= other.size())) {
                return false;
            }
            return (Boolean) getLessEqualNode().execute(frame, self, other);
        }

        @Specialization
        boolean isLessThan(VirtualFrame frame, PBaseSet self, String other,
                        @Cached("createBinaryProfile()") ConditionProfile sizeProfile) {
            if (sizeProfile.profile(self.size() >= other.length())) {
                return false;
            }
            return (Boolean) getLessEqualNode().execute(frame, self, other);
        }

        @Specialization
        Object isLessThan(VirtualFrame frame, PBaseSet self, Object other,
                        @Cached("create(__GT__)") LookupAndCallBinaryNode lookupAndCallBinaryNode) {
            Object result = lookupAndCallBinaryNode.executeObject(frame, other, self);
            if (result != PNone.NO_VALUE) {
                return result;
            }
            throw raise(PythonErrorType.TypeError, ErrorMessages.UNOPERABLE_TYPES_P_P, self, other);
        }
    }

    @Builtin(name = __GT__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class GreaterThanNode extends PythonBinaryBuiltinNode {
        @Child GreaterEqualNode greaterEqualNode;

        private GreaterEqualNode getGreaterEqualNode() {
            if (greaterEqualNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                greaterEqualNode = insert(FrozenSetBuiltinsFactory.GreaterEqualNodeFactory.create());
            }
            return greaterEqualNode;
        }

        @Specialization
        boolean isGreaterThan(VirtualFrame frame, PBaseSet self, PBaseSet other,
                        @Cached("createBinaryProfile()") ConditionProfile sizeProfile) {
            if (sizeProfile.profile(self.size() <= other.size())) {
                return false;
            }
            return (Boolean) getGreaterEqualNode().execute(frame, self, other);
        }

        @Specialization
        boolean isGreaterThan(VirtualFrame frame, PBaseSet self, String other,
                        @Cached("createBinaryProfile()") ConditionProfile sizeProfile) {
            if (sizeProfile.profile(self.size() <= other.length())) {
                return false;
            }
            return (Boolean) getGreaterEqualNode().execute(frame, self, other);
        }

        @Specialization
        Object isLessThan(VirtualFrame frame, PBaseSet self, Object other,
                        @Cached("create(__LT__)") LookupAndCallBinaryNode lookupAndCallBinaryNode) {
            Object result = lookupAndCallBinaryNode.executeObject(frame, other, self);
            if (result != PNone.NO_VALUE) {
                return result;
            }
            throw raise(PythonErrorType.TypeError, ErrorMessages.UNOPERABLE_TYPES_P_P, self, other);
        }
    }

    @Builtin(name = __HASH__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class HashNode extends PythonUnaryBuiltinNode {
        protected static long HASH_UNSET = -1;

        @Specialization(guards = {"self.getHash() != HASH_UNSET"})
        public long getHash(@SuppressWarnings("unused") VirtualFrame frame, PFrozenSet self) {
            return self.getHash();
        }

        @Specialization(guards = {"self.getHash() == HASH_UNSET"}, limit = "1")
        public long computeHash(VirtualFrame frame, PFrozenSet self,
                        @Cached GetDictStorageNode getStorage,
                        @CachedLibrary("getStorage.execute(self)") HashingStorageLibrary hlib,
                        @Cached("createBinaryProfile()") ConditionProfile gotFrame,
                        @CachedLibrary(limit = "getCallSiteInlineCacheMaxDepth()") PythonObjectLibrary lib) {
            // adapted from https://github.com/python/cpython/blob/master/Objects/setobject.c#L758
            HashingStorage storage = getStorage.execute(self);
            int len = hlib.length(storage);
            long m1 = 0x72e8ef4d;
            long m2 = 0x10dcd;
            long c1 = 0x3611c3e3;
            long c2 = 0x2338c7c1;
            long hash = 0;

            for (Object key : hlib.keys(storage)) {
                long tmp = lib.hashWithFrame(hlib.getItem(storage, key), gotFrame, frame);
                hash ^= shuffleBits(tmp);
            }

            // TODO:
            // Remove the effect of an odd number of NULL entries

            // TODO:
            // Remove the effect of an odd number of dummy entries

            // Factor in the number of active entries
            hash ^= (len + 1) * m1;

            // Disperse patterns arising in nested frozensets
            hash ^= (hash >> 11) ^ (hash >> 25);
            hash = hash * m2 + c1;

            // -1 is reserved as an error code
            if (hash == -1) {
                hash = c2;
            }

            self.setHash(hash);
            return hash;
        }

        private static long shuffleBits(long value) {
            return ((value ^ 0x55b4db3) ^ (value << 16)) * 0xd93f34d7;
        }

        @Fallback
        Object genericHash(@SuppressWarnings("unused") Object self) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }
}
